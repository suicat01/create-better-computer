package com.mcl.cbtc.blockentity;

import com.mcl.cbtc.device.DeviceIdentity;
import com.mcl.cbtc.device.Devices;
import com.mcl.cbtc.registry.CbcBlockEntities;
import com.simibubi.create.content.kinetics.transmission.SplitShaftBlockEntity;
import com.simibubi.create.content.kinetics.motor.KineticScrollValueBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueBoxTransform;
import com.simibubi.create.infrastructure.config.AllConfigs;
import java.util.List;
import net.createmod.catnip.math.VecHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * 智能转速控制器的方块实体。
 * <p>
 * 用 {@link SplitShaftBlockEntity} 的倍率机制控制输出端：输入端转速乘以倍率后输出，
 * 倍率 = 拉条设定值 / 输入端转速，因此输出端转速恒等于拉条上的 RPM，正负号即方向。
 * <p>
 * 拉条本身是原版 {@link KineticScrollValueBehaviour}，右键点拉条弹出原版数值选择界面。
 */
public class SmartSpeedControllerBlockEntity extends SplitShaftBlockEntity implements DeviceIdentity {
    /** 拉条设定值（RPM，带符号）。 */
    public KineticScrollValueBehaviour targetSpeed;
    /** 方块外设的持久身份：绑定靠它指到这台方块，结构搬动也不丢。 */
    @Nullable
    private java.util.UUID deviceId;

    public SmartSpeedControllerBlockEntity(BlockPos pos, BlockState state) {
        super(CbcBlockEntities.SPEED_CONTROLLER.get(), pos, state);
    }

    @Nullable
    @Override
    public java.util.UUID deviceId() {
        return deviceId;
    }

    @Override
    public void setDeviceId(@Nullable java.util.UUID id) {
        deviceId = id;
        setChanged();
    }

    @Override
    public void onLoad() {
        super.onLoad();
        Devices.registerIdentity(level, this);
    }

    // setRemoved 在 Create 的 SmartBlockEntity 里是 final：区块卸载也会走到那里，
    // 而 remove() 只在方块真的被移除时调用，正好用来注销身份
    @Override
    public void remove() {
        Devices.forgetIdentity(level, this);
        super.remove();
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        int max = AllConfigs.server().kinetics.maxRotationSpeed.get();
        KineticScrollValueBehaviour behaviour = new KineticScrollValueBehaviour(
                Component.translatable("cbtc.gui.speed_controller.rotation_speed"),
                this, new ControllerValueBox());
        behaviour.between(-max, max);
        behaviour.withCallback(this::onTargetChanged);
        behaviour.value = 16;
        targetSpeed = behaviour;
        behaviours.add(behaviour);
    }

    /**
     * 设定值变化后重建动力网络。
     * <p>
     * 倍率只在网络建立时读取一次并缓存到相邻节点，光改数值不会生效；
     * 这里走原版转速控制器的同一套做法：先脱离网络，再重新接入，
     * 让传播器按新的倍率重算整条动力链。
     * 注意只能服务端执行，且必须用 detach/attach 而不是直接改网络，否则网络状态会错乱。
     */
    private void onTargetChanged(int value) {
        setChanged();
        sendData();
        if (level == null || level.isClientSide) {
            return;
        }
        detachKinetics();
        attachKinetics();
    }

    /**
     * 输出端实际转速（RPM，带符号）。未接入动力或设定为 0 时返回 0。
     * <p>
     * 供积木读取当前输出转速。
     */
    public float outputSpeed() {
        if (!hasSource() || targetSpeed == null) {
            return 0;
        }
        return getSpeed();
    }

    /**
     * 由积木设置目标转速（RPM，带符号：正数正转、负数反转）。
     * <p>
     * 拉条是整数，这里四舍五入到最近的整数 RPM，并夹在允许范围内。
     */
    public void applySpeedSetting(float rpm) {
        if (targetSpeed == null) {
            return;
        }
        int max = AllConfigs.server().kinetics.maxRotationSpeed.get();
        int value = Math.round(Math.max(-max, Math.min(max, rpm)));
        if (value == targetSpeed.getValue()) {
            return;
        }
        targetSpeed.value = value;
        onTargetChanged(value);
    }

    /**
     * 输出端倍率 = 设定转速 / 输入端转速。
     * <p>
     * 输入端（以及未接入动力的任意面）恒为 1，输入端看到的始终是它实际收到的转速。
     */
    @Override
    public float getRotationSpeedModifier(Direction face) {
        if (!hasSource() || face == getSourceFacing()) {
            return 1;
        }
        if (targetSpeed == null) {
            return 1;
        }
        float target = targetSpeed.getValue();
        if (target == 0) {
            return 0;
        }
        float input = Math.abs(getTheoreticalSpeed());
        if (input < 1e-4f) {
            return 0;
        }
        return target / input;
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        if (targetSpeed != null) {
            tag.putInt("TargetSpeed", targetSpeed.getValue());
        }
        DeviceIdentity.writeId(tag, deviceId);
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        if (targetSpeed != null && tag.contains("TargetSpeed")) {
            targetSpeed.value = tag.getInt("TargetSpeed");
        }
        deviceId = DeviceIdentity.readId(tag);
    }

    /**
     * 拉条显示在垂直于旋转轴的两个侧面，位置与缩放沿用原版转速控制器。
     */
    private static class ControllerValueBox extends ValueBoxTransform.Sided {
        @Override
        protected Vec3 getSouthLocation() {
            // 模型正面在 z=15，拉条比原版更薄：向内收 2px；y 再下移 3px 贴到仪表盘中心
            return VecHelper.voxelSpace(8, 8, 13.5);
        }

        @Override
        protected boolean isSideActive(BlockState state, Direction direction) {
            if (direction.getAxis().isVertical()) {
                return false;
            }
            return direction.getAxis() != state.getValue(
                    com.mcl.cbtc.block.SmartSpeedControllerBlock.HORIZONTAL_AXIS);
        }

        @Override
        public float getScale() {
            return 0.5F;
        }
    }
}
