package com.mcl.cbtc.block;

import com.mcl.cbtc.blockentity.LectureStoneBlockEntity;
import com.mojang.serialization.MapCodec;
import com.simibubi.create.content.equipment.wrench.IWrenchable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import org.jetbrains.annotations.Nullable;

/**
 * 讲解石：可以被外设绑定模块卡绑定，作为"播放声音"等积木的发声位置。
 * <p>
 * 与 CB音响的差别是没有升级槽（右键不打开界面，无法插入升级卡），
 * 发声范围固定 6 格。放置时朝向玩家，可用旋转来改变外观朝向。
 */
public class LectureStoneBlock extends Block implements EntityBlock, IWrenchable {
    public static final MapCodec<LectureStoneBlock> CODEC = simpleCodec(LectureStoneBlock::new);
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;

    public LectureStoneBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends LectureStoneBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new LectureStoneBlockEntity(pos, state);
    }

    /** 扳手作用在侧面或顶面时绕竖轴旋转 90°。 */
    @Override
    public BlockState getRotatedBlockState(BlockState state, Direction face) {
        if (face.getAxis().isVertical()) {
            return state.setValue(FACING, state.getValue(FACING).getClockWise());
        }
        return state;
    }
}
