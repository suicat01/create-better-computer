package com.mcl.cbtc.blockentity;

import com.mcl.cbtc.registry.CbcBlockEntities;
import com.simibubi.create.content.kinetics.transmission.sequencer.Instruction;
import com.simibubi.create.content.kinetics.transmission.sequencer.InstructionSpeedModifiers;
import com.simibubi.create.content.kinetics.transmission.sequencer.SequencedGearshiftBlockEntity;
import com.simibubi.create.content.kinetics.transmission.sequencer.SequencerInstructions;
import java.util.Vector;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.state.BlockState;

/**
 * CB编程齿轮箱的方块实体。
 * <p>
 * 角度旋转的执行链路与原版序列齿轮箱完全相同：把积木下来的角度写进
 * {@link #instructions}，再由原版 {@link #run(int)} 推进。
 * 换句话说，计时、倍率、{@code +2} 补偿、{@code detachKinetics} 全部是
 * Create 自己的代码在跑，本类不参与角度运算。
 * <p>
 * 与原版唯一的区别是“谁往里写指令”：原版由配置界面写，
 * 本类由积木指令写。
 */
public class CBGearboxBlockEntity extends SequencedGearshiftBlockEntity {
    /** 角度比较容差（度）。 */
    public static final double ANGLE_EPSILON = 0.5;

    /** 点动保活容差（刻）：超过这么久没收到续期就认为按键已松开。 */
    public static final int JOG_GRACE_TICKS = 2;

    /** 指令卡住多少刻后强制复位（刻）。 */
    private static final int STALL_TICKS = 40;

    /** 目标转速（RPM，带符号），仅用于持续/点动这类不定角的模式。 */
    private float targetSpeed;
    /** 点动模式：积木要求保持转动时为 true。 */
    private boolean jogHeld;
    /** 点动指令最后一次被续期的游戏刻。 */
    private long jogLastSeen = -1;
    /** 上一条收到的指令签名，用来识别同一条指令被重复下发。 */
    private String lastCommandMode = "";
    private float lastCommandSpeed;
    private double lastCommandValue = Double.NaN;
    /** 被当作 0° 的朝向，由标定器物品写入。 */
    private double angleZero;
    /** 累计转过的角度（度），每刻按原版公式累加，只用于视觉，不参与判定。 */
    private double angleAccum;
    /**
     * 账面角度（度）：每条指令成功跑完后，按指令的下发量精确加减一次。
     * <p>
     * 它不参与物理积分，所以不携带原版 {@code +2} 刻补偿、取整、低转速丢步等误差。
     * “旋转至”判定只看这个账面值，物理侧多转或少转都不影响下一次比较。
     */
    private double bookAngle;
    /** 待写入账面角度的增量；NaN 表示当前没有在等指令跑完。 */
    private double pendingBookDelta = Double.NaN;
    /** 等指令跑完却毫无进展的连续刻数，超过上限就判定序列卡死。 */
    private int stallTicks;
    /** 旋转至的连续跟随状态：正在跟随时为 true。 */
    private boolean tracking;
    /** 跟随的目标角度（度）。 */
    private double trackTarget;
    /** 跟随使用的转速（RPM，带符号，绝对值即倍率档位）。 */
    private float trackSpeed;
    /** 跟随时是否走最短路径。 */
    private boolean trackShortest;
    /** 非最短路径跟随时的绕行方向：true 表示逆时针。 */
    private boolean trackCounterClockwise;

    public CBGearboxBlockEntity(BlockPos pos, BlockState state) {
        super(CbcBlockEntities.GEARBOX.get(), pos, state);
    }

    /**
     * 红石入口关闭：积木是唯一的指令来源。
     * 原版由红石上升沿推进序列，这里若一并放开会和积木触发打架。
     */
    @Override
    public void onRedstoneUpdate(boolean powered, boolean pulse) {
        // 忽略
    }

    // ---------------------------------------------------------------- 角度读数

    /**
     * 当前朝向（度）。
     * <p>
     * 返回账面角度：由指令精确累计，不含物理侧误差。
     * 视觉上的真实朝向另存在 {@link #angleAccum}，两者会逐渐有细微差异，
     * 但“旋转至”的判定必须用账面值，否则每次都被原版 {@code +2} 刻补偿带偏。
     */
    public double outputAngle() {
        return bookAngle;
    }

    /** 当前朝向相对零点，归一到 -180 ~ +180。 */
    public double relativeAngle() {
        return wrapAngle(outputAngle() - angleZero);
    }

    /** 把当前朝向记作 0°。 */
    public void setAngleZero() {
        angleZero = outputAngle();
        setChanged();
        sendData();
    }

    /** 角度归一到 -180 ~ +180。 */
    public static double wrapAngle(double degrees) {
        double value = degrees % 360;
        if (value > 180) {
            value -= 360;
        } else if (value <= -180) {
            value += 360;
        }
        return value;
    }

    // ---------------------------------------------------------------- 积木控制

    /**
     * 施加一次积木指令。
     *
     * @param speed 目标转速（RPM），正负表示方向
     * @param mode  steady / timed / angle / jog / rotate_to
     * @param value 定角与旋转至为角度，定时为刻数，其余忽略
     */
    public void applyCommand(float speed, String mode, double value) {
        // 上游缺数据时会输出 NaN（例如连线方位角的 B 未绑定），这里直接忽略，
        // 避免 NaN 被当成角度写进账面，之后整个齿轮箱的角度都变成 NaN
        if (Double.isNaN(value)) {
            return;
        }
        String key = mode == null ? "" : mode;
        // 先把已跑完的指令结账，否则这一刻读到的账面角度还是旧值，
        // 会把"已到位"误判成"没到位"，于是再转一整圈。
        settlePendingBook();
        String baseMode = "rotate_to_shortest".equals(key) ? "rotate_to" : key;
        if ("rotate_to".equals(baseMode)) {
            // 旋转至改成连续跟随，不再切成一段一段地转：
            // 每段转完轴都要停一下再接下一段，动作会断成一节一节。
            // 跟随时轴一直转，每刻只决定往哪边转、什么时候停，既连贯又不会错过新目标。
            startTracking(speed, value, "rotate_to_shortest".equals(key), speed < 0);
            return;
        }
        // 定角一旦开跑，就不再接受新值：上游每刻都在变，
        // 若每刻都重写序列，原版 run(0) 会把 timer 清零，这条 TURN_ANGLE 永远跑不完，
        // 账面角度也就永远不结算，表现为角度读写恒为 0。
        // 这里只记录最后一次收到的值，等当前指令跑完后由下一 tick 自然接续。
        if ("angle".equals(baseMode) && !isIdle()) {
            lastCommandMode = key;
            lastCommandSpeed = speed;
            lastCommandValue = value;
            return;
        }
        boolean sameCommand = key.equals(lastCommandMode)
                && Math.abs(speed - lastCommandSpeed) < 1e-3f
                && Math.abs(value - lastCommandValue) < 1e-3;
        if (sameCommand) {
            if ("jog".equals(key)) {
                jogHeld = true;
                // 序列可能已被保活超时停掉：这时必须重新起转，否则按键再按也永远不动
                if (isIdle()) {
                    startSteady(speed);
                }
                return;
            }
            // 序列还在跑就不重来：重来会把 timer 清零，多转一遍
            if (!isIdle()) {
                return;
            }
        }
        lastCommandMode = key;
        lastCommandSpeed = speed;
        lastCommandValue = value;
        // 其它模式一律终止连续跟随
        tracking = false;

        switch (key) {
            case "angle" -> startAngleTurn(speed, value);
            case "steady" -> {
                // 持续转：写无限长的同向指令，由停止指令收尾
                startSteady(speed);
            }
            case "jog" -> {
                jogHeld = true;
                startSteady(speed);
            }
            default -> stopRotation();
        }
    }

    /**
     * 定角：往序列表写一条 {@code TURN_ANGLE}，交给原版跑。
     * <p>
     * 原版 {@code getDuration} 会算 {@code ceil(|角度| / convertToAngular(转速×倍率)) + 2}，
     * 这就是“转这么多度”的原生实现。倍率取正号正转、负号反转。
     */
    private void startAngleTurn(float speed, double degrees) {
        int magnitude = (int) Math.round(Math.abs(degrees));
        if (magnitude <= 0) {
            stopRotation();
            return;
        }
        if (!hasRotationInput()) {
            return;
        }
        // 倍率相对输入轴：输入反向时 FORWARD 的实际旋转也反向，
        // 所以这里把目标方向与输入方向异或后再选倍率
        InstructionSpeedModifiers modifier = directionModifier((degrees >= 0) ? speed : -speed);
        // 记账：本次实际要转的度数，等原版把这条指令跑完再精确加到账面角度上
        pendingBookDelta = degrees;
        replaceSequence(new Instruction(SequencerInstructions.TURN_ANGLE, modifier, magnitude));
        startSequence();
    }

    // ---------------------------------------------------------------- 连续跟随

    /**
     * 开始 / 更新“旋转至”的连续跟随。
     * <p>
     * 不切段、不排队：序列里常驻一条极长的同向 {@code TURN_ANGLE}，轴一直在转，
     * 每刻只判断残差要不要换向、要不要停。
     */
    private void startTracking(float speed, double target, boolean shortest, boolean counterClockwise) {
        tracking = true;
        trackTarget = target;
        trackSpeed = speed;
        trackShortest = shortest;
        trackCounterClockwise = counterClockwise;
        lastCommandMode = shortest ? "rotate_to_shortest" : "rotate_to";
        lastCommandSpeed = speed;
        lastCommandValue = target;
        tickTracking();
    }

    /** 连续跟随的一次推进：本刻要不要换向、要不要停。 */
    private void tickTracking() {
        if (!tracking) {
            return;
        }
        if (!hasRotationInput()) {
            // 轴没转，转了也推不动；等它转起来自然接上
            return;
        }
        double residual = wrapAngle(wrapAngle(trackTarget) - relativeAngle());
        // 本装置一刻能转过的度数：输入转速(RPM) × 倍率档位 × 0.3（原版的角度换算）
        double perTick = Math.abs(speed) * Math.abs(trackSpeed) * 0.3;
        // 已经近到一步就能跨过去时再转只会来回抖，直接停
        if (Math.abs(residual) <= Math.max(ANGLE_EPSILON, perTick * 0.6)) {
            stopTracking();
            return;
        }
        boolean increase = residual > 0;
        if (!trackShortest) {
            // 绕过去：把残差按积木选的时钟方向换算成“还要往这边走多少度”
            double along = trackCounterClockwise
                    ? (residual > 0 ? residual - 360 : residual)
                    : (residual < 0 ? residual + 360 : residual);
            increase = along > 0;
        }
        float direction = increase ? Math.abs(trackSpeed) : -Math.abs(trackSpeed);
        boolean sameAsRunning = !isIdle()
                && Math.signum(getModifier()) == Math.signum(direction)
                && Math.abs(Math.abs(getModifier()) - Math.abs(trackSpeed)) < 1e-3f;
        if (sameAsRunning) {
            // 已经朝需要的方向、需要的档位在转，保持不动，绝不能每刻重写序列打断转动
            return;
        }
        if (!isIdle()) {
            run(-1);
        }
        replaceSequence(new Instruction(SequencerInstructions.TURN_ANGLE,
                directionModifier(direction), 36000));
        run(0);
        sendData();
        setChanged();
    }

    /** 结束跟随：停在当前位置。 */
    private void stopTracking() {
        if (!tracking) {
            return;
        }
        tracking = false;
        if (level == null || level.isClientSide) {
            return;
        }
        if (!isIdle()) {
            run(-1);
        }
        sendData();
        setChanged();
    }

    /** 持续转 / 点动：写一条极长的同向 TURN_ANGLE，靠停止指令打断。 */
    private void startSteady(float speed) {
        InstructionSpeedModifiers modifier = directionModifier(speed);
        if (!isIdle()) {
            // 方向没变就沿用，避免每刻重置 timer
            return;
        }
        if (!hasRotationInput()) {
            return;
        }
        replaceSequence(new Instruction(SequencerInstructions.TURN_ANGLE, modifier, 36000));
        startSequence();
    }

    /**
     * 输入轴是否在转。
     * <p>
     * 原版计算指令时长用的是 {@code ceil(|角度| / convertToAngular(转速))}，
     * 转速为 0 时会算出无穷大、转成 int 后溢出成负数，序列永远推进不下去；
     * 更糟的是此时 {@code isIdle()} 一直为 false，之后所有指令都会被“还没跑完”挡掉，
     * 表现就是卡住不转。所以轴不转的时候干脆不发起指令，等它转起来那一刻再发。
     */
    private boolean hasRotationInput() {
        return Math.abs(getTheoreticalSpeed()) > 1e-3f;
    }

    /**
     * 把“玩家视角的时钟方向 + 倍率档位”换算成速率倍率。
     * <p>
     * 倍率只有 ×1 / ×2 两档，与原版序列齿轮箱的档位一致：
     * 正向用 FORWARD / FORWARD_FAST，反向用 BACK / BACK_FAST。
     * 倍率是相对输入轴的：输入反向时 FORWARD 的实际旋转也跟着反向，
     * 所以正转要按输入方向翻转，不能直接把正转速当成正转。
     */
    private static InstructionSpeedModifiers directionModifier(float speed) {
        boolean fast = Math.abs(speed) > 1.5f;
        boolean forward = speed > 0;
        // 枚举按 value 分正负：FORWARD=+1 / FORWARD_FAST=+2、BACK=-1 / BACK_FAST=-2
        if (forward) {
            return fast ? InstructionSpeedModifiers.FORWARD_FAST : InstructionSpeedModifiers.FORWARD;
        }
        return fast ? InstructionSpeedModifiers.BACK_FAST : InstructionSpeedModifiers.BACK;
    }

    /**
     * 用一条指令重写序列表，末尾补 END。
     * <p>
     * 不能 {@code clear()} 原列表：原版 {@link #run(int)} 退货时仍用
     * {@code currentInstruction} 索引同一个 Vector，清空会让它越界抛异常、
     * 方块实体被移除，看起来就是方块消失。这里先跑 END 归位，再原地替换元素。
     */
    private void replaceSequence(Instruction instruction) {
        Vector<Instruction> list = getInstructions();
        while (list.size() < 2) {
            list.add(new Instruction(SequencerInstructions.END));
        }
        list.set(0, instruction);
        list.set(1, new Instruction(SequencerInstructions.END));
        if (list.size() > 2) {
            list.subList(2, list.size()).clear();
        }
    }

    /** 从第 0 条开始跑，交给原版 {@link #run(int)}。 */
    private void startSequence() {
        if (level == null || level.isClientSide) {
            return;
        }
        run(0);
        sendData();
        setChanged();
    }

    /** 立即停转：直接跑 END，原版会 detachKinetics 并清空状态。 */
    public void stopRotation() {
        targetSpeed = 0;
        jogHeld = false;
        tracking = false;
        // 被打断的定角不计入账面角度：这笔账没有跑完
        pendingBookDelta = Double.NaN;
        if (level == null || level.isClientSide) {
            return;
        }
        run(-1);
        sendData();
        setChanged();
    }

    public float targetSpeed() {
        return targetSpeed;
    }

    // ---------------------------------------------------------------- 转速

    /**
     * 转速倍率。
     * <p>
     * 输入端恒定 1（与原版一致），输出端取原版 {@link #getModifier()}：
     * 它就是当前指令的倍率，指令结束时原版 {@code run()} 会把它变成 0。
     * 不再自己缓存倍率 —— 缓存的副本正是上一版所有偏差的来源。
     */
    @Override
    public float getRotationSpeedModifier(Direction face) {
        if (!hasSource() || face == getSourceFacing()) {
            return 1;
        }
        return getModifier();
    }

    @Override
    public void onSpeedChanged(float previousSpeed) {
        super.onSpeedChanged(previousSpeed);
        // 原版在这里会用新转速重算当前指令的剩余时长，正是我们想要的
    }

    @Override
    public void tick() {
        // 完全走原版：SplitShaft 动能 tick + isIdle 判断 + timer 推进 + run(next)
        super.tick();
        if (level == null || level.isClientSide) {
            return;
        }
        // 物理侧积分只用于视觉，不参与任何判定
        angleAccum += getModifier() * speed * 0.3;
        if (tracking) {
            // 跟随时轴一直在转、也不会跑到指令末尾，账面角度按原版换算逐刻累积，
            // 这样 relativeAngle 始终等于平台真实朝向
            bookAngle += getModifier() * speed * 0.3;
        }
        // 每刻都尝试结账：本刻可能刚好跑完。settlePendingBook 自己会判断是否空闲，
        // 否则一次性定角在跑完后再也没有积木调用 applyCommand，账面角度就永远不加。
        settlePendingBook();
        // 连续跟随：每刻决定换不换向、要不要停
        tickTracking();
        watchStalledTurn();
        // 点动：积木停止续期就停
        if (jogHeld && level.getGameTime() - jogLastSeen > JOG_GRACE_TICKS) {
            stopRotation();
        }
    }

    /**
     * 结账：若上一条定角/旋转至已经跑完，把它的下发量精确加到账面角度上。
     * <p>
     * 只在指令确实结束时生效，所以被打断的旋转不会留下半截账。
     * {@code applyCommand} 和 {@code tick} 都会调用它，避免时序差导致误判。
     */
    private void settlePendingBook() {
        if (Double.isNaN(pendingBookDelta) || !isIdle()) {
            return;
        }
        bookAngle += pendingBookDelta;
        pendingBookDelta = Double.NaN;
        setChanged();
    }

    /**
     * 看门狗：序列里挂着一条指令，输入轴却一直没转。
     * <p>
     * 这类指令在原版里是按“转够多少度”折算时长的，轴不转就永远跑不完，
     * {@code isIdle()} 会一直是 false，之后每条指令都被挡在门外，表现就是彻底卡住不转
     * （存档里遗留一条没跑完的指令也会这样）。这里直接丢掉这笔账并强制回空闲，
     * 让积木下一刻重新下发。
     */
    private void watchStalledTurn() {
        if (isIdle() || hasRotationInput()) {
            stallTicks = 0;
            return;
        }
        if (++stallTicks < STALL_TICKS) {
            return;
        }
        stallTicks = 0;
        pendingBookDelta = Double.NaN;
        run(-1);
        sendData();
        setChanged();
    }

    /** 由积木每刻调用：本刻仍要求点动，刷新保活时间戳。 */
    public void keepJogging() {
        refreshJogSeen();
    }

    /** 刷新点动保活时间戳；没有世界时什么也不做。 */
    private void refreshJogSeen() {
        if (level == null) {
            return;
        }
        jogLastSeen = level.getGameTime();
    }

    // ---------------------------------------------------------------- 存档

    @Override
    public void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        tag.putFloat("CbcTargetSpeed", targetSpeed);
        tag.putBoolean("CbcJogHeld", jogHeld);
        tag.putString("CbcLastMode", lastCommandMode);
        tag.putFloat("CbcLastSpeed", lastCommandSpeed);
        tag.putDouble("CbcLastValue", lastCommandValue);
        tag.putDouble("CbcAngleZero", angleZero);
        tag.putDouble("CbcAngleAccum", angleAccum);
        tag.putDouble("CbcBookAngle", bookAngle);
        if (!Double.isNaN(pendingBookDelta)) {
            tag.putDouble("CbcPendingBook", pendingBookDelta);
        }
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        targetSpeed = tag.getFloat("CbcTargetSpeed");
        jogHeld = tag.getBoolean("CbcJogHeld");
        lastCommandMode = tag.getString("CbcLastMode");
        lastCommandSpeed = tag.getFloat("CbcLastSpeed");
        lastCommandValue = tag.contains("CbcLastValue") ? tag.getDouble("CbcLastValue") : Double.NaN;
        angleZero = tag.getDouble("CbcAngleZero");
        angleAccum = tag.getDouble("CbcAngleAccum");
        // 旧存档没有账面角度时，用物理累计值起步，保证读数和原来一致
        bookAngle = tag.contains("CbcBookAngle") ? tag.getDouble("CbcBookAngle") : angleAccum;
        pendingBookDelta = tag.contains("CbcPendingBook")
                ? tag.getDouble("CbcPendingBook") : Double.NaN;
    }
}
