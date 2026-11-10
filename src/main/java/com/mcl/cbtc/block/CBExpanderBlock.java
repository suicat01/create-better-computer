package com.mcl.cbtc.block;

import com.mojang.serialization.MapCodec;
import com.mcl.cbtc.blockentity.ExpanderBlockEntity;
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
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * CB拓展器：背部贴紧 CB 电脑背部来连接，提供 6 个升级模块槽位。
 * <p>
 * 贴紧判断以"拓展器的正面朝向"为准：拓展器正面朝向电脑背部时才算连接，
 * 也就是拓展器的 front 与电脑的 facing 相反方向相邻。
 */
public class CBExpanderBlock extends Block implements EntityBlock, IWrenchable {
    public static final MapCodec<CBExpanderBlock> CODEC = simpleCodec(CBExpanderBlock::new);
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;

    /** 碰撞箱与选中框统一按整格方块算。 */
    private static final VoxelShape SHAPE = Block.box(0, 0, 0, 16, 16, 16);

    public CBExpanderBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends CBExpanderBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        // 优先按"贴着哪台电脑放置"来定朝向：点电脑的背面时，正面朝电脑，自动连上。
        Direction attach = attachedSide(context);
        if (attach != null) {
            return defaultBlockState().setValue(FACING, attach);
        }
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    /** 若玩家是把拓展器贴在一台电脑上放置，返回拓展器该朝向的方向。 */
    @Nullable
    private static Direction attachedSide(BlockPlaceContext context) {
        Level level = context.getLevel();
        BlockPos target = context.getClickedPos();
        Direction face = context.getClickedFace();
        // 点的是电脑的某个面：拓展器放在该面外侧，正面朝向电脑
        if (level.getBlockState(target).getBlock() instanceof CBComputerBlock && face.getAxis().isHorizontal()) {
            return face;
        }
        // 点的是别的方块，但落位正好贴在电脑侧面：正面朝向那台电脑
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            if (level.getBlockState(target.relative(dir)).getBlock() instanceof CBComputerBlock) {
                return dir;
            }
        }
        return null;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos,
                                           CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                               BlockHitResult hit) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (!(level.getBlockEntity(pos) instanceof ExpanderBlockEntity expander)) {
            return InteractionResult.PASS;
        }
        player.openMenu(expander, pos);
        return InteractionResult.CONSUME;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ExpanderBlockEntity(pos, state);
    }

    // ---------------------------------------------------------------- 扳手

    /** 扳手作用在侧面或顶面时绕竖轴旋转 90°，槽内模块卡随方块实体保留。 */
    @Override
    public BlockState getRotatedBlockState(BlockState state, Direction face) {
        if (face.getAxis().isVertical()) {
            return state.setValue(FACING, state.getValue(FACING).getClockWise());
        }
        return state;
    }

    // ---------------------------------------------------------------- 数据随方块走

    /** 拆下时把 6 个槽位里的模块卡一起放进掉落物，避免装配好的模块丢失。 */
    @Override
    public java.util.List<ItemStack> getDrops(BlockState state, LootParams.Builder params) {
        java.util.List<ItemStack> drops = super.getDrops(state, params);
        BlockEntity blockEntity = params.getOptionalParameter(LootContextParams.BLOCK_ENTITY);
        if (blockEntity instanceof ExpanderBlockEntity expander) {
            expander.saveToStack(drops);
        }
        return drops;
    }

    /** 创造模式中键取方块时同样带上已插入的模块卡。 */
    @Override
    public ItemStack getCloneItemStack(net.minecraft.world.level.LevelReader level, BlockPos pos,
                                       BlockState state) {
        ItemStack stack = new ItemStack(CbcBlocks.EXPANDER.get());
        if (level.getBlockEntity(pos) instanceof ExpanderBlockEntity expander) {
            expander.saveToStack(java.util.List.of(stack));
        }
        return stack;
    }
}
