package com.mcl.cbtc.compat.cbc;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.items.IItemHandler;
import rbasamoyai.createbigcannons.cannon_control.fixed_cannon_mount.FixedCannonMountBlockEntity;

/**
 * 智能固定炮座的方块实体：逻辑完全继承 CBC 的固定式火炮底座。
 */
public class SmartFixedCannonMountBlockEntity extends FixedCannonMountBlockEntity {
    public SmartFixedCannonMountBlockEntity(BlockPos pos, BlockState state) {
        super(CbcCannons.SMART_FIXED_CANNON_MOUNT_BE.get(), pos, state);
    }

    /**
     * 火控单位：把固定炮座的偏航/俯仰滚轮值设成瞄准目标所需的角度。
     * <p>
     * 滚轮存的是相对炮座朝向的偏移量，并且原版范围固定为 -45..45（{@code setValue} 会自动夹紧），
     * 所以这里写入相对角后，超出范围时炮座会停在允许的最大角度上，而不是指向错误方向。
     */
    public void cbtc$aimAt(Vec3 target) {
        if (mountedContraption == null || target == null || getLevel() == null) {
            return;
        }
        // 炮座在物理结构上时方块坐标在结构存储区，先把世界目标换算到同一坐标系
        Vec3 localTarget = com.mcl.cbtc.device.Devices.toLocalSpace(getLevel(), getBlockPos(), target);
        Vec3 dir = localTarget.subtract(getInteractionLocation());
        if (dir.lengthSqr() < 1.0E-6D) {
            return;
        }
        double horizontal = Math.sqrt(dir.x * dir.x + dir.z * dir.z);
        float yaw = (float) (Mth.atan2(-dir.x, dir.z) * (180.0D / Math.PI));
        float pitch = (float) (Mth.atan2(dir.y, horizontal) * (180.0D / Math.PI));
        // 相对炮座朝向的偏航偏移；wrap 到 -180..180 后再交给滚轮夹紧，取朝向目标一侧的最大角
        float relativeYaw = Mth.wrapDegrees(yaw - getContraptionDirection().toYRot());
        cbtc$setSlot(FixedCannonMountBlockEntity.FixedCannonMountScrollValueBehaviour.PITCH_TYPE,
                Math.round(pitch));
        cbtc$setSlot(FixedCannonMountBlockEntity.FixedCannonMountScrollValueBehaviour.YAW_TYPE,
                Math.round(relativeYaw));
    }

    private void cbtc$setSlot(
            com.simibubi.create.foundation.blockEntity.behaviour.BehaviourType<FixedCannonMountBlockEntity.FixedCannonMountScrollValueBehaviour> type,
            int value) {
        if (getLevel() == null) {
            return;
        }
        FixedCannonMountBlockEntity.FixedCannonMountScrollValueBehaviour slot =
                com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour.get(
                        getLevel(), getBlockPos(), type);
        if (slot != null) {
            slot.setValue(value);
            sendData();
        }
    }

    /** 火控单位：炮内可用炮弹数。 */
    public int cbtc$ammo() {
        IItemHandler handler = getItemHandler(Direction.UP);
        return CannonMountCompat.countAmmo(handler);
    }
}
