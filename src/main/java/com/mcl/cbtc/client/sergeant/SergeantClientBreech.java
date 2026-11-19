package com.mcl.cbtc.client.sergeant;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import rbasamoyai.createbigcannons.cannon_control.cannon_mount.CannonMountBlockEntity;
import rbasamoyai.createbigcannons.cannon_control.cannon_mount.ExtendsCannonMount;
import rbasamoyai.createbigcannons.cannon_control.contraption.MountedBigCannonContraption;
import rbasamoyai.createbigcannons.cannon_control.contraption.PitchOrientedContraptionEntity;
import rbasamoyai.createbigcannons.cannon_control.fixed_cannon_mount.FixedCannonMountBlockEntity;
import rbasamoyai.createbigcannons.cannons.big_cannons.breeches.quickfiring_breech.QuickfiringBreechBlockEntity;

/**
 * 客户端侧切换炮闩开合：客户端 contraption 里的炮闩方块实体才是画动画的那一份。
 */
public final class SergeantClientBreech {
    private SergeantClientBreech() {
    }

    public static void toggle(BlockPos mountPos) {
        var level = Minecraft.getInstance().level;
        if (level == null) {
            return;
        }
        BlockEntity blockEntity = level.getBlockEntity(mountPos);
        PitchOrientedContraptionEntity entity = null;
        if (blockEntity instanceof ExtendsCannonMount mount) {
            CannonMountBlockEntity cannonMount = mount.getCannonMount();
            if (cannonMount != null) {
                entity = cannonMount.getContraption();
            }
        } else if (blockEntity instanceof FixedCannonMountBlockEntity fixedMount) {
            entity = fixedMount.getContraption();
        }
        if (entity == null || !(entity.getContraption() instanceof MountedBigCannonContraption cannon)) {
            return;
        }
        BlockPos breechPos = cannon.getStartPos().relative(cannon.initialOrientation().getOpposite());
        QuickfiringBreechBlockEntity primary =
                cannon.presentBlockEntities.get(breechPos) instanceof QuickfiringBreechBlockEntity breech
                        ? breech : null;
        QuickfiringBreechBlockEntity clientCopy =
                cannon.getBlockEntityClientSide(breechPos) instanceof QuickfiringBreechBlockEntity copy
                        ? copy : null;
        // 两份可能是同一个对象，只切一次，否则会开了又立刻关（看起来播了两次）
        if (clientCopy != null && clientCopy != primary) {
            clientCopy.toggleOpening();
            primary.toggleOpening();
        } else if (primary != null) {
            primary.toggleOpening();
        }
    }
}
