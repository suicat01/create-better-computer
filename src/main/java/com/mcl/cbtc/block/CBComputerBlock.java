package com.mcl.cbtc.block;

import com.mojang.serialization.MapCodec;
import com.mcl.cbtc.blockentity.CBComputerBlockEntity;
import com.mcl.cbtc.registry.CbcBlockEntities;
import com.mcl.cbtc.registry.CbcBlocks;
import com.simibubi.create.content.equipment.wrench.IWrenchable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * CB电脑主机方块。
 */
public class CBComputerBlock extends Block implements EntityBlock, IWrenchable {
    public static final MapCodec<CBComputerBlock> CODEC = simpleCodec(CBComputerBlock::new);
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    public static final BooleanProperty RUNNING = BooleanProperty.create("running");
    /** 开机动画帧，0/1 循环交替，用于播放屏幕上的字符动画。 */
    public static final IntegerProperty FRAME = IntegerProperty.create("frame", 0, 1);

    /** 模型是满格方块（0,0,0 → 16,16,16），碰撞箱与选中框都按整格算。 */
    private static final VoxelShape SHAPE = Block.box(0, 0, 0, 16, 16, 16);

    public CBComputerBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState()
                .setValue(FACING, Direction.NORTH)
                .setValue(RUNNING, false)
                .setValue(FRAME, 0));
    }

    @Override
    protected MapCodec<? extends CBComputerBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, RUNNING, FRAME);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                               BlockHitResult hit) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (!(level.getBlockEntity(pos) instanceof CBComputerBlockEntity computer)) {
            return InteractionResult.PASS;
        }
        // 潜行右键：切换开关机；普通右键：打开积木编辑界面（关机状态也可以编辑）
        if (player.isShiftKeyDown()) {
            computer.togglePower();
            return InteractionResult.CONSUME;
        }
        player.openMenu(computer, pos);
        return InteractionResult.CONSUME;
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer,
                            ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level.isClientSide) {
            return;
        }
        if (level.getBlockEntity(pos) instanceof CBComputerBlockEntity computer) {
            // 物品里带着程序（拆下来再放回去）时以物品为准，只有空电脑才补一份示范程序
            if (computer.program().isEmpty()) {
                computer.installSampleProgram();
            }
        }
    }

    // ---------------------------------------------------------------- 扳手

    /** 扳手作用在侧面或顶面时绕竖轴旋转 90°，程序保存在方块实体里，旋转不会丢。 */
    @Override
    public BlockState getRotatedBlockState(BlockState state, Direction face) {
        if (face.getAxis().isVertical()) {
            return state.setValue(FACING, state.getValue(FACING).getClockWise());
        }
        return state;
    }

    // ---------------------------------------------------------------- 数据随方块走

    /**
     * 挖掘或扳手拆除时把程序、名字等数据挂到掉落物上，重新放置后自动恢复。
     * 蓝图打印同样走存档 NBT，因此也会保留程序。
     */
    @Override
    public java.util.List<ItemStack> getDrops(BlockState state,
                                              net.minecraft.world.level.storage.loot.LootParams.Builder params) {
        java.util.List<ItemStack> drops = super.getDrops(state, params);
        BlockEntity blockEntity = params.getOptionalParameter(
                net.minecraft.world.level.storage.loot.parameters.LootContextParams.BLOCK_ENTITY);
        if (blockEntity instanceof CBComputerBlockEntity computer) {
            for (ItemStack drop : drops) {
                computer.saveToStack(drop);
            }
        }
        return drops;
    }

    /** 创造模式中键取方块时也带上程序。 */
    @Override
    public ItemStack getCloneItemStack(net.minecraft.world.level.LevelReader level, BlockPos pos,
                                       BlockState state) {
        ItemStack stack = new ItemStack(CbcBlocks.COMPUTER.get());
        if (level.getBlockEntity(pos) instanceof CBComputerBlockEntity computer) {
            computer.saveToStack(stack);
        }
        return stack;
    }

    @Override
    public void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighborBlock,
                                BlockPos neighborPos, boolean movedByPiston) {
        super.neighborChanged(state, level, pos, neighborBlock, neighborPos, movedByPiston);
        // 相邻方块变动（比如拓展器被放上/拆掉）时，刷新外设连接状态
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof CBComputerBlockEntity computer) {
            computer.onNeighborChanged();
        }
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new CBComputerBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                  BlockEntityType<T> type) {
        if (level.isClientSide || type != CbcBlockEntities.COMPUTER.get()) {
            return null;
        }
        return (lvl, pos, st, be) -> {
            if (be instanceof CBComputerBlockEntity computer) {
                computer.serverTick();
            }
        };
    }
}
