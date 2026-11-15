package com.mcl.cbtc.compat.cbc;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import rbasamoyai.createbigcannons.cannon_control.config.CannonMountBlockPropertiesProvider;
import rbasamoyai.createbigcannons.index.CBCBlocks;

/**
 * 智能炮座的俯仰范围代理。
 * <p>
 * CBC 的炮座属性数据以「方块状态」为键，新方块的方块状态命中不了这些键，
 * 会落到 0/0 的兜底值，于是每 tick 把俯仰角夹回 0（表现为"完全不能控制上下角度"）。
 * 这里把智能炮座的状态换成 CBC 对应炮座的等效状态，再交给原版数据查表。
 */
public final class SmartMountProperties implements CannonMountBlockPropertiesProvider {
    private final CannonMountBlockPropertiesProvider delegate;
    private final boolean fixed;

    public SmartMountProperties(CannonMountBlockPropertiesProvider delegate, boolean fixed) {
        this.delegate = delegate;
        this.fixed = fixed;
    }

    @Override
    public float maximumElevation(Level level, BlockState state, BlockPos pos) {
        return delegate.maximumElevation(level, cbcState(state), pos);
    }

    @Override
    public float maximumDepression(Level level, BlockState state, BlockPos pos) {
        return delegate.maximumDepression(level, cbcState(state), pos);
    }

    /**
     * 把智能炮座的方块状态映射成 CBC 数据表里的键。
     * <p>
     * 数据表的键只由 {@code vertical_direction} 决定（固定炮座则没有条件），
     * 所以这里只复制这一个属性，其余保持默认值，才能精确命中键。
     */
    private BlockState cbcState(BlockState state) {
        if (fixed) {
            return CBCBlocks.FIXED_CANNON_MOUNT.get().defaultBlockState();
        }
        BlockState cbc = CBCBlocks.CANNON_MOUNT.get().defaultBlockState();
        if (state.hasProperty(BlockStateProperties.VERTICAL_DIRECTION)) {
            cbc = cbc.setValue(BlockStateProperties.VERTICAL_DIRECTION,
                    state.getValue(BlockStateProperties.VERTICAL_DIRECTION));
        }
        return cbc;
    }
}
