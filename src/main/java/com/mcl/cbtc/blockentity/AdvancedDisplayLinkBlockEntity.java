package com.mcl.cbtc.blockentity;

import com.mcl.cbtc.registry.CbcBlockEntities;
import com.simibubi.create.content.redstone.displayLink.DisplayLinkBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 高级显示链接器的方块实体：沿用原版显示链接器的全部数据与界面，
 * 只是把刷新时机从「红石信号边沿」改成「每刻」。
 */
public class AdvancedDisplayLinkBlockEntity extends DisplayLinkBlockEntity {
    public AdvancedDisplayLinkBlockEntity(BlockPos pos, BlockState state) {
        super(CbcBlockEntities.ADVANCED_DISPLAY_LINK.get(), pos, state);
    }

    /**
     * 每刻刷新一次数据源。
     * <p>
     * 原版的 {@code tick()} 只在间隔达到数据源被动刷新周期时才调用 {@code tickSource()}，
     * 而 {@code tickSource()} 在方块处于充能状态时又会直接返回，所以实际刷新全靠红石边沿。
     * 这里在 {@code super.tick()} 之后无条件采集一次数据，效果等同于每刻收到一次红石信号。
     */
    @Override
    public void tick() {
        super.tick();
        if (level == null || level.isClientSide || isVirtual() || activeSource == null) {
            return;
        }
        refreshTicks = 0;
        updateGatheredData();
    }
}
