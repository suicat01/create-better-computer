package com.mcl.cbtc.blockentity;

import com.mcl.cbtc.client.sergeant.SergeantArmAccess;
import com.mcl.cbtc.menu.SergeantMenu;
import com.mcl.cbtc.registry.CbcBlockEntities;
import com.simibubi.create.api.registry.CreateBuiltInRegistries;
import com.simibubi.create.content.kinetics.mechanicalArm.ArmBlockEntity;
import com.simibubi.create.content.kinetics.mechanicalArm.ArmInteractionPoint;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;

/**
 * 瓦列里中士：原版动力臂 + 一条五行的装填指令。
 * <p>
 * 每行要么「留空」（序列在此终止），要么指定一种弹药 / 发射药；有转动动力时，
 * 手臂按行顺序伸向绑定的火炮底座，把中士正下方容器里的对应物品塞进炮膛。
 * 没有速射炮闩（不可自动装填）的火炮不会执行——这一点由 CBC 的交互点模拟装填来判定。
 */
public class SergeantValeryBlockEntity extends ArmBlockEntity implements MenuProvider {
    /** 指令行数，和界面一致。 */
    public static final int ROWS = 5;
    /** 炮座交互点的注册名，用来从 outputs 里挑出火炮底座。 */
    private static final ResourceLocation CANNON_MOUNT_POINT = ResourceLocation.fromNamespaceAndPath(
            "createbigcannons", "cannon_mount");
    /** 所有发射药（药包 + 药筒）。 */
    private static final net.minecraft.tags.TagKey<Item> PROPELLANTS =
            net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.ITEM,
                    ResourceLocation.fromNamespaceAndPath("createbigcannons", "big_cannon_propellant"));
    /** 装填失败后的重试间隔（刻）。 */
    private static final int RETRY_TICKS = 40;

    /** 每行的物品 id；空串表示「留空」。 */
    private final String[] program = new String[ROWS];
    /** 当前执行到第几行。 */
    private int row;
    /** 重试倒计时。 */
    private int retryCooldown;
    /** 是否正在执行序列。 */
    private boolean running;
    /** 是否已就绪、可以在通电后从第一行开始。 */
    private boolean armed = true;
    private boolean lastPowered;
    /** 最近一次材料检查的不足提示，仅用于服务端节流提醒。 */
    private Component lastProblem;
    /** 同步给客户端的装填状态："" = 就绪；"busy"；"missing|物品id|数量"。 */
    private String status = "";
    /** 在炮座前等待炮闩的累计刻数，防止永远卡住。 */
    private int stallTicks;
    /** 客户端炮闩动画当前（或将要）处于打开状态。 */
    private boolean breechVisualOpen;
    /** 炮闩动画当前对应哪个炮座（outputs 下标）；-1 = 没有。 */
    private int breechIndex = -1;
    /** 炮闩开合动画剩余刻数；>0 时 CBC 会忽略下一次切换。 */
    private int breechAnimLock;
    /** 动画结束后要切换到的状态；只有被推迟的切换请求才非 null。 */
    private Boolean pendingBreechOpen;
    /** 本轮选中的炮座在 outputs 里的下标；-1 表示还没有目标。 */
    private int targetIndex = -1;
    /** 上一轮装过的炮座下标，用来按「分配方法」轮换。 */
    private int lastCannonIndex = -1;
    /** busy 状态的日志节流计数。 */
    private int busyLogTicks;

    public SergeantValeryBlockEntity(BlockPos pos, BlockState state) {
        super(CbcBlockEntities.SERGEANT_VALERY.get(), pos, state);
        // 新放置的方块还没读过档，先补成"全留空"，否则写同步包时会 NPE
        java.util.Arrays.fill(program, "");
    }

    private SergeantArmAccess access() {
        return (SergeantArmAccess) (Object) this;
    }

    /** 瓦列里中士的应力消耗：256 × 转速（原版动力臂的 2 × 转速只是参照）。 */
    @Override
    public float calculateStressApplied() {
        float impact = 256.0F;
        this.lastStressApplied = impact;
        return impact;
    }

    // ---------------------------------------------------------------- 菜单

    @Override
    public Component getDisplayName() {
        return Component.translatable("cbtc.sergeant.title");
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return new SergeantMenu(containerId, inventory, this);
    }

    /** 把界面需要的数据塞进打开菜单的附加包。 */
    public void writeMenuData(FriendlyByteBuf buf) {
        buf.writeBlockPos(worldPosition);
        buf.writeVarInt(ROWS);
        for (int i = 0; i < ROWS; i++) {
            buf.writeUtf(programRow(i), 128);
        }
        buf.writeUtf(targetName(), 128);
    }

    public String programRow(int index) {
        String value = index >= 0 && index < ROWS ? program[index] : "";
        return value == null ? "" : value;
    }

    public String targetName() {
        int index = cannonMountIndex();
        if (index < 0 || level == null) {
            return "";
        }
        BlockPos pos = access().cbtc$outputs().get(index).getPos();
        if (!level.isLoaded(pos)) {
            return "";
        }
        return level.getBlockState(pos).getBlock().getName().getString();
    }

    public void applyProgram(List<String> rows) {
        for (int i = 0; i < ROWS; i++) {
            String value = i < rows.size() ? rows.get(i) : "";
            program[i] = value == null ? "" : value.trim();
        }
        resetSequence();
        armed = true;
        status = "";
        setChanged();
        sendData();
    }

    // ---------------------------------------------------------------- 执行

    @Override
    public void tick() {
        if (level == null) {
            return;
        }
        super.tick();
        if (level.isClientSide) {
            // 没有转动动力时 chasedPointProgress 不会推进，手臂会一直僵在上一个目标的角度上
            // （看起来就是"手臂前伸"）；空手时直接落回待机姿态
            if (Math.abs(getSpeed()) < 1.0E-4F && access().cbtc$heldItem().isEmpty()) {
                access().cbtc$settleIdlePose();
            }
            return;
        }
        if (retryCooldown > 0) {
            retryCooldown--;
        }
        // 炮闩开合动画至少要走完一次；被推迟的切换等它结束后补上
        if (breechAnimLock > 0) {
            breechAnimLock--;
        }
        if (breechAnimLock == 0 && pendingBreechOpen != null) {
            boolean open = pendingBreechOpen;
            pendingBreechOpen = null;
            applyBreech(open);
        }
        // 没有配置任何填充行时完全交还原版动力臂，不干扰它自己搬东西
        if (!hasFillRow()) {
            lastPowered = Math.abs(getSpeed()) > 1.0E-4F;
            return;
        }
        // 在炮座前长时间等不到可装填状态就放弃，避免永远卡住
        if (running && "busy".equals(status)) {
            if (++stallTicks > 100) {
                stallTicks = 0;
                warnNoBreech();
                failAtCannon();
                return;
            }
        } else {
            stallTicks = 0;
        }
        // 周期性把卡住时的炮座状态写进日志，方便定位（不依赖 gameTime 取模）
        if ("busy".equals(status) && ++busyLogTicks >= 100) {
            busyLogTicks = 0;
            logBusyState();
        } else if (!"busy".equals(status)) {
            busyLogTicks = 0;
        }
        boolean powered = Math.abs(getSpeed()) > 1.0E-4F;
        if (powered != lastPowered) {
            lastPowered = powered;
            if (!powered) {
                // 断电：把手里的东西还回去，下次通电从第一行重来
                ItemStack held = access().cbtc$heldItem();
                if (!held.isEmpty()) {
                    access().cbtc$setHeldItem(ItemStack.EMPTY);
                    putBack(held);
                }
                closeBreechVisualIfOpen();
                access().cbtc$resetToIdle();
                resetSequence();
            } else {
                armed = true;
            }
            setChanged();
            sendData();
        }
    }

    @Override
    protected void searchForItem() {
        if (level == null || level.isClientSide) {
            return;
        }
        if (!isControlled()) {
            super.searchForItem();
            return;
        }
        if (!running) {
            if (!armed || !canStart()) {
                return;
            }
            // 一次装填要求材料齐全；缺东西就只提示，不伸手
            Component missing = checkMaterials();
            if (missing != null) {
                lastProblem = missing;
                retryCooldown = RETRY_TICKS;
                warnMissing(missing);
                return;
            }
            running = true;
            row = 0;
            armed = false;
            lastProblem = null;
            setStatus("");
        }
        if (retryCooldown > 0 || !access().cbtc$heldItem().isEmpty()) {
            return;
        }
        if (row < 0 || row >= ROWS || programRow(row).isEmpty()) {
            finishSequence();
            return;
        }
        Item wanted = resolveItem(programRow(row));
        if (wanted == Items.AIR) {
            finishSequence();
            return;
        }
        // 本轮已经选定炮座就继续用它；新的一轮按动力臂的「分配方法」挑一个
        int index = targetIndex >= 0 ? targetIndex : pickCannonTarget(wanted);
        if (index < 0) {
            // 目前没有能接装的炮座：等有炮清膛后再来
            setStatus("");
            retryCooldown = RETRY_TICKS;
            return;
        }
        targetIndex = index;
        ArmInteractionPoint point = access().cbtc$outputs().get(index);
        if (!point.isValid() || !level.isLoaded(point.getPos())) {
            targetIndex = -1;
            retryCooldown = RETRY_TICKS;
            return;
        }
        // 炮膛里已经有这一行要的东西了：这一行算完成，直接跳下一行
        if (rowAlreadyLoaded(wanted, point)) {
            row++;
            setChanged();
            return;
        }
        // 炮闩还开着（上次没收尾）：先把它关回去再装
        if (cbcPowderReady()) {
            int progress = com.mcl.cbtc.compat.cbc.CbcPowderCompat.breechOpenProgress(level, point.getPos());
            int direction = com.mcl.cbtc.compat.cbc.CbcPowderCompat.breechOpenDirection(level, point.getPos());
            if (progress > 0 && direction == 0) {
                toggleBreechBothSides(point);
                retryCooldown = 5;
                return;
            }
        }
        if (!canInsertNow(point, wanted)) {
            if (cbcPowderReady()) {
                // 弹头已装好、只是炮闩还在装填冷却：等它，不要结束序列
                if (isPropellant(wanted)
                        && com.mcl.cbtc.compat.cbc.CbcPowderCompat.firstBlockIsProjectile(level, point.getPos())) {
                    lastProblem = Component.translatable("cbtc.sergeant.tooltip.busy");
                    setStatus("busy");
                    retryCooldown = 5;
                    return;
                }
                // 炮膛里已经是完整一发：等玩家开炮清膛后再装（不额外加冷却，开炮后立刻响应）
                if (!com.mcl.cbtc.compat.cbc.CbcPowderCompat.barrelEmpty(level, point.getPos())) {
                    setStatus("");
                    retryCooldown = 0;
                    return;
                }
            }
            if (level.getGameTime() % 100L == 0L) {
                com.mcl.cbtc.CreateBetterComputer.LOGGER.info("[cbtc-sergeant] 装填被拒绝(开始) item={} {}",
                        wanted, cbcPowderReady()
                                ? com.mcl.cbtc.compat.cbc.CbcCannonDebug.describe(level, point.getPos())
                                : "未安装 CBC");
            }
            lastProblem = Component.translatable("cbtc.sergeant.tooltip.busy");
            setStatus("busy");
            retryCooldown = RETRY_TICKS;
            warnNoBreech();
            return;
        }
        ItemStack taken = extractFromBelow(wanted);
        if (taken.isEmpty()) {
            lastProblem = missingComponent(wanted, 1);
            retryCooldown = RETRY_TICKS;
            warnMissing(lastProblem);
            return;
        }
        // 只伸手一次：后面的所有行都在炮座前一次装完
        access().cbtc$setHeldItem(taken.copyWithCount(1));
        // 手臂一开始抬起就同时打开炮闩，关闩留到真正装填的那一刻（不受转速影响）
        if (cbcPowderReady()) {
            openBreechVisual(point);
        }
        access().cbtc$moveToOutput(index);
        setChanged();
    }

    @Override
    protected void depositItem() {
        if (!isControlled()) {
            super.depositItem();
            return;
        }
        if (level == null || level.isClientSide) {
            return;
        }
        SergeantArmAccess acc = access();
        int index = acc.cbtc$chasedIndex();
        List<ArmInteractionPoint> outputs = acc.cbtc$outputs();
        ArmInteractionPoint point = index >= 0 && index < outputs.size() ? outputs.get(index) : null;
        if (point == null || !point.isValid()) {
            failAtCannon();
            return;
        }
        // 停在炮座前，把序列里所有材料一次装完；炮闩动画只是装饰，不挡装填
        for (int guard = 0; guard <= ROWS; guard++) {
            if (row >= ROWS || programRow(row).isEmpty()) {
                finishSequence();
                return;
            }
            Item wanted = resolveItem(programRow(row));
            if (wanted == Items.AIR) {
                finishSequence();
                return;
            }
            // 炮膛里已经有这一行要的东西了：跳过，继续下一行
            if (rowAlreadyLoaded(wanted, point)) {
                ItemStack carried = acc.cbtc$heldItem();
                if (!carried.isEmpty()) {
                    acc.cbtc$setHeldItem(ItemStack.EMPTY);
                    putBack(carried);
                }
                row++;
                setChanged();
                continue;
            }
            ItemStack held = acc.cbtc$heldItem();
            if (held.isEmpty()) {
                if (!canInsertNow(point, wanted)) {
                    if (cbcPowderReady()) {
                        // 弹头已装好、炮闩在冷却：等冷却结束继续装发射药
                        if (isPropellant(wanted)
                                && com.mcl.cbtc.compat.cbc.CbcPowderCompat.firstBlockIsProjectile(level, point.getPos())) {
                            retryCooldown = 2;
                            setChanged();
                            return;
                        }
                        // 炮膛已经满了：这一轮结束，等开炮后再装
                        if (!com.mcl.cbtc.compat.cbc.CbcPowderCompat.barrelEmpty(level, point.getPos())) {
                            if (level.getGameTime() % 100L == 0L) {
                                com.mcl.cbtc.CreateBetterComputer.LOGGER.info("[cbtc-sergeant] 炮膛已满 item={} {}",
                                        wanted, com.mcl.cbtc.compat.cbc.CbcCannonDebug.describe(level, point.getPos()));
                            }
                            finishSequence();
                            return;
                        }
                    }
                    if (level.getGameTime() % 100L == 0L) {
                        com.mcl.cbtc.CreateBetterComputer.LOGGER.info("[cbtc-sergeant] 装填被拒绝(投放) item={} {}",
                                wanted, cbcPowderReady()
                                        ? com.mcl.cbtc.compat.cbc.CbcCannonDebug.describe(level, point.getPos())
                                        : "未安装 CBC");
                    }
                    lastProblem = Component.translatable("cbtc.sergeant.tooltip.busy");
                    setStatus("busy");
                    retryCooldown = 5;
                    setChanged();
                    return;
                }
                ItemStack taken = extractFromBelow(wanted);
                if (taken.isEmpty()) {
                    lastProblem = missingComponent(wanted, 1);
                    warnMissing(lastProblem);
                    failAtCannon();
                    return;
                }
                held = taken.copyWithCount(1);
                acc.cbtc$setHeldItem(held);
            }
            ItemStack remainder = insertAtCannon(point, held);
            boolean installed = !sameAsInput(remainder, held);
            acc.cbtc$setHeldItem(ItemStack.EMPTY);
            if (!remainder.isEmpty()) {
                putBack(remainder);
            }
            if (!installed) {
                retryCooldown = 5;
                setChanged();
                return;
            }
            row++;
            stallTicks = 0;
            retryCooldown = 0;
            // CBC 装完一件会打上 40 刻冷却；整轮是一次性装填，立刻清掉让下一行马上能进膛
            if (cbcPowderReady()) {
                com.mcl.cbtc.compat.cbc.CbcPowderCompat.clearLoadingCooldown(level, point.getPos());
            }
            setChanged();
        }
    }

    /** 开闩（只播客户端动画）；关闩绑定在真正装填完成的那一刻，因此不会和转速脱节。 */
    private void openBreechVisual(ArmInteractionPoint point) {
        requestBreech(point, true);
    }

    /** 关闩（只播客户端动画）。 */
    private void closeBreechVisual(ArmInteractionPoint point) {
        requestBreech(point, false);
    }

    /** 补一次关闩（关在真正开过闩的那个炮座上）。 */
    private void closeBreechVisualIfOpen() {
        int idx = breechIndex;
        if (idx < 0 || idx >= access().cbtc$outputs().size()) {
            breechVisualOpen = false;
            pendingBreechOpen = null;
            breechIndex = -1;
            return;
        }
        requestBreech(access().cbtc$outputs().get(idx), false);
    }

    /**
     * 请求切换某个炮座的炮闩开合。
     * <p>
     * 动画状态始终绑定在具体炮座上：换目标时先把旧炮座上的闩收回去，
     * 否则排队中的切换会落到另一门炮上（表现为"装一门、另一门动"）。
     */
    private void requestBreech(ArmInteractionPoint point, boolean open) {
        int idx = indexOfOutput(point);
        if (idx < 0) {
            return;
        }
        if (breechIndex != idx) {
            // 换目标：旧炮座还开着（或有待执行的切换）就先关掉，再接管新的。
            // 两门炮的炮闩动画互不影响，所以换目标时不沿用上一门的动画锁，
            // 否则连续装两门时后一门的开闩会被排队甚至被取消（看起来没有动画）。
            if (breechVisualOpen || Boolean.TRUE.equals(pendingBreechOpen)) {
                int old = breechIndex;
                if (old >= 0 && old < access().cbtc$outputs().size()) {
                    toggleBreechVisual(access().cbtc$outputs().get(old), false);
                }
            }
            breechIndex = idx;
            breechVisualOpen = false;
            pendingBreechOpen = null;
            breechAnimLock = 0;
        }
        if (!cbcPowderReady()) {
            breechVisualOpen = open;
            return;
        }
        if (breechVisualOpen == open) {
            pendingBreechOpen = null;
            return;
        }
        if (breechAnimLock > 0) {
            pendingBreechOpen = open;
            return;
        }
        applyBreech(open);
    }

    /** 把切换应用到当前炮座。 */
    private void applyBreech(boolean open) {
        int idx = breechIndex;
        if (idx < 0 || idx >= access().cbtc$outputs().size()) {
            pendingBreechOpen = null;
            return;
        }
        toggleBreechVisual(access().cbtc$outputs().get(idx), open);
        breechVisualOpen = open;
        breechAnimLock = breechAnimationTicks() + 2;
        pendingBreechOpen = null;
        setChanged();
    }

    private int indexOfOutput(ArmInteractionPoint point) {
        List<ArmInteractionPoint> outputs = access().cbtc$outputs();
        for (int i = 0; i < outputs.size(); i++) {
            ArmInteractionPoint candidate = outputs.get(i);
            if (candidate == point || candidate.getPos().equals(point.getPos())) {
                return i;
            }
        }
        return -1;
    }

    private static int breechAnimationTicks() {
        return cbcPowderReady()
                ? Math.max(1, com.mcl.cbtc.compat.cbc.CbcPowderCompat.breechOpeningTime())
                : 5;
    }

    /** 只切换客户端那份炮闩（负责画动画），服务端状态不动。 */
    private void toggleBreechVisual(ArmInteractionPoint point, boolean opening) {
        playBreechSound(point, opening);
        sendBreechVisual(point);
    }

    /** 服务端与客户端各切换一次炮闩（用于把玩家手动开着没关的炮闩关回去）。 */
    private void toggleBreechBothSides(ArmInteractionPoint point) {
        playBreechSound(point, false);
        com.mcl.cbtc.compat.cbc.CbcPowderCompat.toggleBreech(level, point.getPos());
        sendBreechVisual(point);
    }

    /** 和右键炮闩一样的声音。 */
    private void playBreechSound(ArmInteractionPoint point, boolean opening) {
        level.playSound(null, point.getPos(),
                opening ? net.minecraft.sounds.SoundEvents.IRON_TRAPDOOR_OPEN
                        : net.minecraft.sounds.SoundEvents.IRON_TRAPDOOR_CLOSE,
                net.minecraft.sounds.SoundSource.BLOCKS, 1.0F, 1.5F);
    }

    private void sendBreechVisual(ArmInteractionPoint point) {
        if (level instanceof net.minecraft.server.level.ServerLevel serverLevel) {
            net.neoforged.neoforge.network.PacketDistributor.sendToPlayersNear(serverLevel, null,
                    point.getPos().getX() + 0.5, point.getPos().getY() + 0.5, point.getPos().getZ() + 0.5,
                    64.0, new com.mcl.cbtc.network.SergeantBreechTogglePayload(point.getPos()));
        }
    }

    /** 卡住时输出一次炮座状态。 */
    private void logBusyState() {
        int index = currentTargetIndex();
        if (index < 0) {
            return;
        }
        BlockPos pos = access().cbtc$outputs().get(index).getPos();
        com.mcl.cbtc.CreateBetterComputer.LOGGER.info(
                "[cbtc-sergeant] busy 状态: breechOpen={} progress={} direction={} opening={} {}",
                breechVisualOpen,
                cbcPowderReady() ? com.mcl.cbtc.compat.cbc.CbcPowderCompat.breechOpenProgress(level, pos) : -1,
                cbcPowderReady() ? com.mcl.cbtc.compat.cbc.CbcPowderCompat.breechOpenDirection(level, pos) : -1,
                cbcPowderReady() ? com.mcl.cbtc.compat.cbc.CbcPowderCompat.breechOpeningTime() : -1,
                cbcPowderReady() ? com.mcl.cbtc.compat.cbc.CbcCannonDebug.describe(level, pos) : "未安装 CBC");
    }

    /** 全部装完：回到空闲并重新就绪，开炮后会再装下一发。 */
    private void finishSequence() {
        // 装填完成的那一瞬关闩（和手臂进度同步，不受转速影响）
        closeBreechVisualIfOpen();
        // 和原版动力臂投放完物品一样：同一刻把 phase / 进度 / 交互点下标复位，
        // 客户端据此自行插值收手，不再额外强推姿态
        access().cbtc$resetToIdle();
        running = false;
        row = 0;
        armed = true;
        retryCooldown = 0;
        lastProblem = null;
        // 记住这一轮装的是哪个炮座，下一轮按「分配方法」轮到下一个
        lastCannonIndex = targetIndex;
        targetIndex = -1;
        setStatus("");
        setChanged();
        sendData();
    }

    /** 装填中途失败：把手里的东西还回去，回到空闲。 */
    private void failAtCannon() {
        SergeantArmAccess acc = access();
        ItemStack held = acc.cbtc$heldItem();
        acc.cbtc$setHeldItem(ItemStack.EMPTY);
        if (!held.isEmpty()) {
            putBack(held);
        }
        closeBreechVisualIfOpen();
        acc.cbtc$resetToIdle();
        stopSequence();
        setStatus("");
        setChanged();
        sendData();
    }

    private boolean isControlled() {
        return hasFillRow() && !cannonMountIndices().isEmpty();
    }

    private boolean hasFillRow() {
        for (String value : program) {
            if (value != null && !value.isEmpty()) {
                return true;
            }
        }
        return false;
    }

    private boolean canStart() {
        return !redstoneLocked && Math.abs(getSpeed()) > 1.0E-4F && isControlled();
    }

    private void stopSequence() {
        running = false;
        armed = false;
        row = 0;
    }

    private void resetSequence() {
        running = false;
        row = 0;
        retryCooldown = 0;
        targetIndex = -1;
    }

    /** outputs 里所有 CBC 火炮底座交互点的下标。 */
    private List<Integer> cannonMountIndices() {
        List<Integer> indices = new java.util.ArrayList<>();
        if (level == null) {
            return indices;
        }
        List<ArmInteractionPoint> outputs = access().cbtc$outputs();
        for (int i = 0; i < outputs.size(); i++) {
            ArmInteractionPoint point = outputs.get(i);
            ResourceLocation id = CreateBuiltInRegistries.ARM_INTERACTION_POINT_TYPE.getKey(point.getType());
            if (CANNON_MOUNT_POINT.equals(id)) {
                indices.add(i);
            }
        }
        return indices;
    }

    /** outputs 里第一个 CBC 火炮底座交互点；没有则 -1（界面显示用）。 */
    private int cannonMountIndex() {
        List<Integer> indices = cannonMountIndices();
        return indices.isEmpty() ? -1 : indices.get(0);
    }

    /** 当前正在服务的炮座；还没选过就用第一个。 */
    private int currentTargetIndex() {
        List<ArmInteractionPoint> outputs = access().cbtc$outputs();
        if (targetIndex >= 0 && targetIndex < outputs.size()) {
            return targetIndex;
        }
        return cannonMountIndex();
    }

    /**
     * 按动力臂的「分配方法」挑一个要装填的炮座：
     * 优先第一个 = 总是第一个；轮流 = 从上一轮之后找第一个能接装的；
     * 强制轮流 = 一定轮到下一个（装不下就等它清膛）。
     */
    private int pickCannonTarget(Item firstWanted) {
        List<Integer> candidates = cannonMountIndices();
        if (candidates.isEmpty()) {
            return -1;
        }
        ArmBlockEntity.SelectionMode mode = selectionMode == null
                ? ArmBlockEntity.SelectionMode.ROUND_ROBIN
                : selectionMode.get();
        if (mode == ArmBlockEntity.SelectionMode.PREFER_FIRST) {
            // 优先第一个：从第一个开始找能接装的，第一个满了才轮到后面的
            for (int idx : candidates) {
                ArmInteractionPoint point = access().cbtc$outputs().get(idx);
                if (point.isValid() && level != null && level.isLoaded(point.getPos())
                        && canInsertNow(point, firstWanted)) {
                    return idx;
                }
            }
            return -1;
        }
        int start = 0;
        int lastPos = candidates.indexOf(lastCannonIndex);
        if (lastPos >= 0) {
            start = (lastPos + 1) % candidates.size();
        }
        if (mode == ArmBlockEntity.SelectionMode.FORCED_ROUND_ROBIN) {
            return candidates.get(start);
        }
        // 轮流：从上一轮的下一个开始，挑第一个还能装的
        for (int i = 0; i < candidates.size(); i++) {
            int idx = candidates.get((start + i) % candidates.size());
            ArmInteractionPoint point = access().cbtc$outputs().get(idx);
            if (point.isValid() && level != null && level.isLoaded(point.getPos())
                    && canInsertNow(point, firstWanted)) {
                return idx;
            }
        }
        return -1;
    }

    private static Item resolveItem(String id) {
        if (id.isEmpty()) {
            return Items.AIR;
        }
        ResourceLocation key = ResourceLocation.tryParse(id);
        if (key == null) {
            return Items.AIR;
        }
        Item item = BuiltInRegistries.ITEM.get(key);
        if (item == Items.AIR || !key.equals(BuiltInRegistries.ITEM.getKey(item))) {
            return Items.AIR;
        }
        return item;
    }

    /** 从中士正下方的容器里取出 1 个指定物品。 */
    private ItemStack extractFromBelow(Item wanted) {
        IItemHandler handler = level.getCapability(Capabilities.ItemHandler.BLOCK,
                worldPosition.below(), Direction.UP);
        if (handler == null) {
            return ItemStack.EMPTY;
        }
        for (int slot = 0; slot < handler.getSlots(); slot++) {
            ItemStack in = handler.getStackInSlot(slot);
            if (in.isEmpty() || !in.is(wanted)) {
                continue;
            }
            ItemStack extracted = handler.extractItem(slot, 1, false);
            if (!extracted.isEmpty()) {
                return extracted.copyWithCount(1);
            }
        }
        return ItemStack.EMPTY;
    }

    /** 把没装进去的余量 / 空药筒塞回下方容器，塞不下就丢在方块旁。 */
    private void putBack(ItemStack stack) {
        ItemStack remaining = stack.copy();
        IItemHandler handler = level.getCapability(Capabilities.ItemHandler.BLOCK,
                worldPosition.below(), Direction.UP);
        if (handler != null) {
            for (int slot = 0; slot < handler.getSlots() && !remaining.isEmpty(); slot++) {
                remaining = handler.insertItem(slot, remaining, false);
            }
        }
        if (!remaining.isEmpty()) {
            Block.popResource(level, worldPosition, remaining);
        }
    }

    private void warnNoBreech() {
        if (level == null || level.getGameTime() % 100L != 0L) {
            return;
        }
        var player = level.getNearestPlayer(worldPosition.getX() + 0.5, worldPosition.getY() + 0.5,
                worldPosition.getZ() + 0.5, 10.0, false);
        if (player != null) {
            player.displayClientMessage(
                    Component.translatable("cbtc.sergeant.msg.no_breech").withStyle(ChatFormatting.RED), true);
        }
    }

    /** CBC 的返回约定：装不进去时原样返回同一个内容；装进去返回数量变少的副本或换成的残壳。 */
    private static boolean sameAsInput(ItemStack result, ItemStack input) {
        return ItemStack.isSameItemSameComponents(result, input) && result.getCount() == input.getCount();
    }

    /** 是不是发射药（药包 / 药筒）。 */
    private static boolean isPropellant(Item item) {
        return new ItemStack(item).is(PROPELLANTS);
    }

    /**
     * 这一行要的东西是否已经在这门炮里了。
     * <p>
     * 按「整条炮膛里这种物品的数量 &gt; 本行之前同种物品的行数」判断，
     * 这样连续几行同种发射药不会被误认为"已经装好"而跳过。
     */
    private boolean rowAlreadyLoaded(Item wanted, ArmInteractionPoint point) {
        if (!cbcPowderReady()) {
            return false;
        }
        String id = BuiltInRegistries.ITEM.getKey(wanted).toString();
        int previousRows = 0;
        for (int i = 0; i < row; i++) {
            if (id.equals(programRow(i))) {
                previousRows++;
            }
        }
        return com.mcl.cbtc.compat.cbc.CbcPowderCompat.countLoaded(level, point.getPos(), wanted) > previousRows;
    }

    /** CBC 是否安装；没装时发射药不可用。 */
    private static boolean cbcPowderReady() {
        return com.mcl.cbtc.compat.cbc.CbcCannons.loaded();
    }

    /** 现在往这个炮座插一个物品能否成功（炮闩冷却 / 炮膛状态不符时为 false）。 */
    private boolean canInsertNow(ArmInteractionPoint point, Item wanted) {
        ItemStack probe = new ItemStack(wanted, 1);
        if (isPropellant(wanted)) {
            // 药包与药筒都走自己的"整条链前移"装填，能装到炮管允许的上限
            return cbcPowderReady()
                    && com.mcl.cbtc.compat.cbc.CbcPowderCompat.canLoad(level, point.getPos(), probe);
        }
        return !sameAsInput(point.insert(this, probe.copy(), true), probe);
    }

    private ItemStack insertAtCannon(ArmInteractionPoint point, ItemStack held) {
        if (isPropellant(held.getItem()) && cbcPowderReady()) {
            return com.mcl.cbtc.compat.cbc.CbcPowderCompat.load(level, point.getPos(), held.copy());
        }
        return point.insert(this, held.copy(), false);
    }

    /**
     * 检查中士正下方容器是否够装完整个序列。
     * 返回 null 表示齐全，否则返回第一条不足提示（同时把状态同步给客户端）。
     */
    private Component checkMaterials() {
        java.util.Map<Item, Integer> need = new java.util.LinkedHashMap<>();
        for (int i = 0; i < ROWS; i++) {
            String id = programRow(i);
            if (id.isEmpty()) {
                break;
            }
            Item item = resolveItem(id);
            if (item == Items.AIR) {
                continue;
            }
            need.merge(item, 1, Integer::sum);
        }
        if (need.isEmpty()) {
            return null;
        }
        java.util.Map<Item, Integer> have = new java.util.HashMap<>();
        IItemHandler handler = belowHandler();
        if (handler != null) {
            for (int slot = 0; slot < handler.getSlots(); slot++) {
                ItemStack stack = handler.getStackInSlot(slot);
                if (!stack.isEmpty()) {
                    have.merge(stack.getItem(), stack.getCount(), Integer::sum);
                }
            }
        }
        for (var entry : need.entrySet()) {
            int available = have.getOrDefault(entry.getKey(), 0);
            if (available < entry.getValue()) {
                return missingComponent(entry.getKey(), entry.getValue() - available);
            }
        }
        return null;
    }

    private Component missingComponent(Item item, int count) {
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
        setStatus("missing|" + id + "|" + count);
        return Component.translatable("cbtc.sergeant.tooltip.missing",
                new ItemStack(item).getHoverName(), Component.literal(String.valueOf(count)));
    }

    private void setStatus(String value) {
        if (!value.equals(status)) {
            status = value;
            setChanged();
            sendData();
        }
    }

    private IItemHandler belowHandler() {
        return level.getCapability(Capabilities.ItemHandler.BLOCK, worldPosition.below(), Direction.UP);
    }

    private void warnMissing(Component message) {
        if (level == null || level.getGameTime() % 100L != 0L) {
            return;
        }
        var player = level.getNearestPlayer(worldPosition.getX() + 0.5, worldPosition.getY() + 0.5,
                worldPosition.getZ() + 0.5, 10.0, false);
        if (player != null) {
            player.displayClientMessage(message.copy().withStyle(ChatFormatting.RED), true);
        }
    }

    /** 看向方块时的状态提示，和原版动力臂的"没有目标"提示同一处显示。 */
    @Override
    public boolean addToTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        boolean added = super.addToTooltip(tooltip, isPlayerSneaking);
        if (!hasFillRow()) {
            return added;
        }
        if (status.startsWith("missing|")) {
            String[] parts = status.split("\\|", 3);
            Item item = parts.length > 1 ? resolveItem(parts[1]) : Items.AIR;
            int count = 1;
            try {
                if (parts.length > 2) {
                    count = Integer.parseInt(parts[2]);
                }
            } catch (NumberFormatException ignored) {
            }
            tooltip.add(Component.translatable("cbtc.sergeant.tooltip.missing",
                    new ItemStack(item).getHoverName(), Component.literal(String.valueOf(count)))
                    .withStyle(ChatFormatting.RED));
        } else if ("busy".equals(status)) {
            tooltip.add(Component.translatable("cbtc.sergeant.tooltip.busy").withStyle(ChatFormatting.GOLD));
        } else if (cannonMountIndex() >= 0) {
            tooltip.add(Component.translatable("cbtc.sergeant.tooltip.ready").withStyle(ChatFormatting.GRAY));
        }
        return true;
    }

    // ---------------------------------------------------------------- 交互点刷新

    @Override
    protected void initInteractionPoints() {
        super.initInteractionPoints();
        if (level != null && !level.isClientSide) {
            running = false;
            row = 0;
            armed = true;
            setChanged();
            sendData();
        }
    }

    /** 服务端收到新的交互点后由 Mixin 调用。 */
    public void onPointsBound() {
        if (level == null || level.isClientSide) {
            return;
        }
        resetSequence();
        armed = true;
        setChanged();
        sendData();
    }

    // ---------------------------------------------------------------- 存档

    @Override
    public void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        ListTag list = new ListTag();
        for (String value : program) {
            list.add(StringTag.valueOf(value == null ? "" : value));
        }
        tag.put("SergeantProgram", list);
        tag.putString("SergeantTarget", level == null ? "" : targetName());
        tag.putString("SergeantStatus", status);
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        ListTag list = tag.getList("SergeantProgram", Tag.TAG_STRING);
        for (int i = 0; i < ROWS; i++) {
            program[i] = i < list.size() ? list.getString(i) : "";
        }
        status = tag.getString("SergeantStatus");
        if (!clientPacket) {
            running = false;
            row = 0;
            retryCooldown = 0;
            armed = true;
            targetIndex = -1;
            breechVisualOpen = false;
            breechIndex = -1;
            breechAnimLock = 0;
            pendingBreechOpen = null;
            lastPowered = Math.abs(getSpeed()) > 1.0E-4F;
        }
    }
}
