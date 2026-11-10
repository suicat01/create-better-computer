package com.mcl.cbtc.block;

import com.mcl.cbtc.blockentity.DiagramSensorBlockEntity;
import com.simibubi.create.content.equipment.wrench.IWrenchable;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * CB结构图解传感器（占位方块）。
 * <p>
 * 结构图解本身是实体，不能直接当成方块外设来绑定。
 * 这个方块用来"代表"它：把它贴在结构上，外设模块卡绑定它，
 * 积木就能读到该结构解图对应的质量与各项受力。
 */
public class DiagramSensorBlock extends Block implements EntityBlock, IWrenchable {
    public DiagramSensorBlock(Properties properties) {
        super(properties);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new DiagramSensorBlockEntity(pos, state);
    }

    @Override
    public com.mojang.serialization.MapCodec<? extends Block> codec() {
        return simpleCodec(DiagramSensorBlock::new);
    }

    /** 无朝向，扳手只用于潜行拆卸（拆卸逻辑由 IWrenchable 默认实现提供）。 */
    @Override
    public BlockState getRotatedBlockState(BlockState state, net.minecraft.core.Direction face) {
        return state;
    }
}
