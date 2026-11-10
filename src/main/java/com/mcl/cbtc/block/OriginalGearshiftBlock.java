package com.mcl.cbtc.block;

import com.mcl.cbtc.blockentity.OriginalGearshiftBlockEntity;
import com.mcl.cbtc.registry.CbcBlockEntities;
import com.simibubi.create.content.kinetics.transmission.sequencer.SequencedGearshiftBlock;
import com.simibubi.create.content.kinetics.transmission.sequencer.SequencedGearshiftBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;

/**
 * 原版可编程齿轮箱的复刻方块。
 * <p>
 * 与 {@link CBGearboxBlock} 的唯一区别：保留原版的序列配置界面（右键打开），
 * 序列指令由玩家在界面里直接填写，不经过积木。
 */
public class OriginalGearshiftBlock extends SequencedGearshiftBlock {
    public OriginalGearshiftBlock(Properties properties) {
        super(properties);
    }

    @Override
    @SuppressWarnings("unchecked")
    public Class<SequencedGearshiftBlockEntity> getBlockEntityClass() {
        return (Class<SequencedGearshiftBlockEntity>) (Class<?>) OriginalGearshiftBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends SequencedGearshiftBlockEntity> getBlockEntityType() {
        return CbcBlockEntities.ORIGINAL_GEARSHIFT.get();
    }

    // displayScreen 不覆写：保留原版右键打开序列配置界面的行为
}
