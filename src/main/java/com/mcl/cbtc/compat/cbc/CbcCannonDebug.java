package com.mcl.cbtc.compat.cbc;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import rbasamoyai.createbigcannons.cannon_control.cannon_mount.CannonMountBlockEntity;
import rbasamoyai.createbigcannons.cannon_control.cannon_mount.ExtendsCannonMount;
import rbasamoyai.createbigcannons.cannon_control.contraption.MountedBigCannonContraption;
import rbasamoyai.createbigcannons.cannon_control.contraption.PitchOrientedContraptionEntity;
import rbasamoyai.createbigcannons.cannon_control.fixed_cannon_mount.FixedCannonMountBlockEntity;
import rbasamoyai.createbigcannons.cannons.big_cannons.IBigCannonBlockEntity;
import rbasamoyai.createbigcannons.cannons.big_cannons.breeches.quickfiring_breech.QuickfiringBreechBlockEntity;

/**
 * 装填失败时的诊断信息：把炮座当前状态拼成一行日志，方便判断到底卡在哪一步。
 */
public final class CbcCannonDebug {
    private CbcCannonDebug() {
    }

    public static String describe(Level level, BlockPos pos) {
        StringBuilder info = new StringBuilder();
        BlockEntity blockEntity = level.getBlockEntity(pos);
        info.append("be=").append(blockEntity == null ? "null" : blockEntity.getClass().getSimpleName());
        PitchOrientedContraptionEntity entity = null;
        if (blockEntity instanceof ExtendsCannonMount mount) {
            CannonMountBlockEntity cannonMount = mount.getCannonMount();
            info.append(" mount=").append(cannonMount == null ? "null" : "ok");
            if (cannonMount != null) {
                entity = cannonMount.getContraption();
            }
        } else if (blockEntity instanceof FixedCannonMountBlockEntity fixedMount) {
            entity = fixedMount.getContraption();
        }
        info.append(" contraption=").append(entity == null ? "null" : entity.getClass().getSimpleName());
        if (entity == null) {
            return info.toString();
        }
        info.append(" cannon=").append(entity.getContraption() == null
                ? "null" : entity.getContraption().getClass().getSimpleName());
        if (!(entity.getContraption() instanceof MountedBigCannonContraption cannon)) {
            return info.toString();
        }
        Direction dir = cannon.initialOrientation();
        BlockPos start = cannon.getStartPos();
        BlockPos breechPos = start.relative(dir.getOpposite());
        BlockEntity breech = cannon.presentBlockEntities.get(breechPos);
        info.append(" breech=").append(breech == null ? "null" : breech.getClass().getSimpleName());
        if (breech instanceof QuickfiringBreechBlockEntity quickfiring) {
            info.append(" canAuto=").append(quickfiring.canBeAutomaticallyLoaded())
                    .append(" open=").append(quickfiring.isOpen());
        }
        BlockEntity startEntity = cannon.presentBlockEntities.get(start);
        if (startEntity instanceof IBigCannonBlockEntity bigCannon) {
            info.append(" first=").append(bigCannon.cannonBehavior().block().state());
        }
        // 把整条炮膛的装填链打出来：哪一格有东西、下一格是什么
        info.append(" chain=[");
        BlockPos cursor = start;
        for (int i = 0; i < 16; i++) {
            BlockPos current = cursor;
            BlockEntity part = cannon.presentBlockEntities.get(current);
            if (part instanceof IBigCannonBlockEntity partEntity) {
                info.append(i).append(':').append(partEntity.cannonBehavior().block().state().getBlock()).append(' ');
            } else {
                info.append(i).append(":end");
                break;
            }
            cursor = current.relative(dir);
        }
        info.append(']');
        return info.toString();
    }
}
