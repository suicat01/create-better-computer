package com.mcl.cbtc.compat.cbc;

import net.minecraft.core.Direction;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.core.BlockPos;
import rbasamoyai.createbigcannons.cannon_control.cannon_mount.CannonMountBlock;
import rbasamoyai.createbigcannons.cannon_control.cannon_mount.CannonMountBlockEntity;

/**
 * 智能炮座：完全沿用 CBC 的可旋转火炮底座，只是不接受传动轴。
 * <p>
 * 底座的旋转、组装、开火、扳手拆卸等逻辑全部继承自 {@link CannonMountBlock} /
 * {@link CannonMountBlockEntity}，唯一的行为差异是 {@link #hasShaftTowards} 恒为 false，
 * 因此六个面都不会连上传动轴。
 */
public class SmartCannonMountBlock extends CannonMountBlock {
    public SmartCannonMountBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    public BlockEntityType<? extends CannonMountBlockEntity> getBlockEntityType() {
        return CbcCannons.SMART_CANNON_MOUNT_BE.get();
    }

    /** 智能炮座没有传动杆，任何方向都不连接传动轴。 */
    @Override
    public boolean hasShaftTowards(LevelReader level, BlockPos pos, BlockState state, Direction direction) {
        return false;
    }
}
