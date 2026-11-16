package com.mcl.cbtc.compat.cbc;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.items.IItemHandler;
import rbasamoyai.createbigcannons.cannon_control.cannon_mount.CannonMountBlockEntity;
import rbasamoyai.createbigcannons.cannon_control.contraption.PitchOrientedContraptionEntity;

/**
 * 智能炮座的方块实体：逻辑完全继承 CBC 的火炮底座。
 * <p>
 * CBC 内部有几处用 {@code CBCBlocks.CANNON_MOUNT.has(state)} 判断"这是不是炮座"，
 * 由 Mixin 把本方块也视为炮座，因此组装/俯仰/偏航等逻辑不需要重写。
 */
public class SmartCannonMountBlockEntity extends CannonMountBlockEntity {
    public SmartCannonMountBlockEntity(BlockPos pos, BlockState state) {
        super(CbcCannons.SMART_CANNON_MOUNT_BE.get(), pos, state);
    }

    /** 火控单位：让已组装的炮管瞄准世界坐标中的一点。 */
    public void cbtc$aimAt(Vec3 target) {
        PitchOrientedContraptionEntity cannon = getContraption();
        if (cannon == null || target == null || getLevel() == null) {
            return;
        }
        // 炮座在物理结构上时方块坐标在结构存储区，先把世界目标换算到同一坐标系
        Vec3 localTarget = com.mcl.cbtc.device.Devices.toLocalSpace(getLevel(), getBlockPos(), target);
        Vec3 dir = localTarget.subtract(getInteractionLocation());
        if (dir.lengthSqr() < 1.0E-6D) {
            return;
        }
        double horizontal = Math.sqrt(dir.x * dir.x + dir.z * dir.z);
        setYaw((float) (Mth.atan2(-dir.x, dir.z) * (180.0D / Math.PI)));
        setPitch((float) (Mth.atan2(dir.y, horizontal) * (180.0D / Math.PI)));
        applyRotation();
        sendData();
    }

    /** 火控单位：炮内可用炮弹数。 */
    public int cbtc$ammo() {
        IItemHandler handler = getItemHandler(Direction.UP);
        return CannonMountCompat.countAmmo(handler);
    }
}
