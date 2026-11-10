package com.mcl.cbtc.block;

import com.mojang.serialization.MapCodec;
import com.mcl.cbtc.blockentity.CBSpeakerBlockEntity;
import com.mcl.cbtc.registry.CbcBlocks;
import com.simibubi.create.content.equipment.wrench.IWrenchable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/**
 * CB音响：可以被外设绑定模块卡绑定，供"播放声音"等积木选定发声位置。
 * <p>
 * 自带一个升级槽，右键打开；放入扬声器升级后传播范围从 20 格提升到 80 格。
 */
public class CBSpeakerBlock extends Block implements EntityBlock, IWrenchable {
    public static final MapCodec<CBSpeakerBlock> CODEC = simpleCodec(CBSpeakerBlock::new);
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;

    public CBSpeakerBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends CBSpeakerBlock> codec() {
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

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                               BlockHitResult hit) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (!(level.getBlockEntity(pos) instanceof CBSpeakerBlockEntity speaker)) {
            return InteractionResult.PASS;
        }
        player.openMenu(speaker, pos);
        return InteractionResult.CONSUME;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new CBSpeakerBlockEntity(pos, state);
    }

    // ---------------------------------------------------------------- 扳手

    /** 扳手作用在侧面或顶面时绕竖轴旋转 90°。 */
    @Override
    public BlockState getRotatedBlockState(BlockState state, Direction face) {
        if (face.getAxis().isVertical()) {
            return state.setValue(FACING, state.getValue(FACING).getClockWise());
        }
        return state;
    }

    // ---------------------------------------------------------------- 数据随方块走

    /** 拆下时把升级槽里的模块卡一起放进掉落物。 */
    @Override
    public java.util.List<ItemStack> getDrops(BlockState state, LootParams.Builder params) {
        java.util.List<ItemStack> drops = super.getDrops(state, params);
        BlockEntity blockEntity = params.getOptionalParameter(LootContextParams.BLOCK_ENTITY);
        if (blockEntity instanceof CBSpeakerBlockEntity speaker) {
            speaker.saveToStack(drops);
        }
        return drops;
    }

    /** 创造模式中键取方块时同样带上已插入的升级模块。 */
    @Override
    public ItemStack getCloneItemStack(net.minecraft.world.level.LevelReader level, BlockPos pos,
                                       BlockState state) {
        ItemStack stack = new ItemStack(CbcBlocks.SPEAKER.get());
        if (level.getBlockEntity(pos) instanceof CBSpeakerBlockEntity speaker) {
            speaker.saveToStack(java.util.List.of(stack));
        }
        return stack;
    }
}
