package com.mcl.cbtc.block;

import com.mcl.cbtc.registry.CbcBlockEntities;
import com.mojang.serialization.MapCodec;
import com.simibubi.create.content.redstone.displayLink.DisplayLinkBlock;
import com.simibubi.create.content.redstone.displayLink.DisplayLinkBlockEntity;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntityType;

/**
 * 高级显示链接器：完全复用机械动力原版显示链接器的方块与界面。
 * <p>
 * 唯一的区别在于刷新方式：原版必须靠高频红石信号反复触发，这里由
 * {@link AdvancedDisplayLinkBlockEntity} 每刻自行刷新数据，不需要接红石。
 */
public class AdvancedDisplayLinkBlock extends DisplayLinkBlock {
    public static final MapCodec<AdvancedDisplayLinkBlock> CODEC =
            simpleCodec(AdvancedDisplayLinkBlock::new);

    public AdvancedDisplayLinkBlock(Properties properties) {
        super(properties);
    }

    @Override
    public Class<DisplayLinkBlockEntity> getBlockEntityClass() {
        return DisplayLinkBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends DisplayLinkBlockEntity> getBlockEntityType() {
        return CbcBlockEntities.ADVANCED_DISPLAY_LINK.get();
    }

    @Override
    protected MapCodec<? extends DirectionalBlock> codec() {
        return CODEC;
    }
}
