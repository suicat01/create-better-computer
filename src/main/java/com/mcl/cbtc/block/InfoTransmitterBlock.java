package com.mcl.cbtc.block;

import com.mojang.serialization.MapCodec;
import com.mcl.cbtc.blockentity.InfoTransmitterBlockEntity;
import com.simibubi.create.content.equipment.wrench.IWrenchable;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * 信息转发器：六个面都可以贴 Create 的显示链接器（Display Link）。
 * <p>
 * 它作为显示链接的输出目标，把收到的文本/数字暂存起来，供 CB 电脑的
 * "信息接收"积木按信息源读取。
 */
public class InfoTransmitterBlock extends Block implements EntityBlock, IWrenchable {
    public static final MapCodec<InfoTransmitterBlock> CODEC = simpleCodec(InfoTransmitterBlock::new);

    public InfoTransmitterBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends InfoTransmitterBlock> codec() {
        return CODEC;
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new InfoTransmitterBlockEntity(pos, state);
    }

    /** 无朝向，扳手只用于潜行拆卸（拆卸逻辑由 IWrenchable 默认实现提供）。 */
    @Override
    public BlockState getRotatedBlockState(BlockState state, net.minecraft.core.Direction face) {
        return state;
    }
}
