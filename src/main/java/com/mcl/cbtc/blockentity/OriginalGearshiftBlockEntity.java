package com.mcl.cbtc.blockentity;

import com.mcl.cbtc.registry.CbcBlockEntities;
import com.simibubi.create.content.kinetics.transmission.sequencer.SequencedGearshiftBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 原版可编程齿轮箱的复刻方块实体。
 * <p>
 * 与 {@link CBGearboxBlockEntity} 的区别：这里<b>不覆写 {@code tick}、不覆写 {@code run}</b>，
 * 完全走 {@code SequencedGearshiftBlockEntity} 自己的计时推进逻辑，
 * 也就是原版"把角度换算成刻数、timer 走满再进下一条"的那套机制。
 * <p>
 * 序列指令表由方块自带的配置界面维护，不与积木系统交互，
 * 因此它的定角行为可用来与原版可编程齿轮箱直接对照。
 */
public class OriginalGearshiftBlockEntity extends SequencedGearshiftBlockEntity {
    public OriginalGearshiftBlockEntity(BlockPos pos, BlockState state) {
        super(CbcBlockEntities.ORIGINAL_GEARSHIFT.get(), pos, state);
    }
}
