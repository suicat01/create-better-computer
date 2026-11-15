package com.mcl.cbtc.compat.cbc;

import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import rbasamoyai.createbigcannons.cannon_control.fixed_cannon_mount.FixedCannonMountBlock;
import rbasamoyai.createbigcannons.cannon_control.fixed_cannon_mount.FixedCannonMountBlockEntity;

/**
 * 智能固定炮座：完全沿用 CBC 的固定式火炮底座（单方块、四向模型、滚轮设定俯仰/偏航）。
 */
public class SmartFixedCannonMountBlock extends FixedCannonMountBlock {
    public SmartFixedCannonMountBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    public BlockEntityType<? extends FixedCannonMountBlockEntity> getBlockEntityType() {
        return CbcCannons.SMART_FIXED_CANNON_MOUNT_BE.get();
    }
}
