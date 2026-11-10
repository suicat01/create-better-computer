package com.mcl.cbtc.block;

import com.mcl.cbtc.blockentity.SmartSpeedControllerBlockEntity;
import com.mcl.cbtc.registry.CbcBlockEntities;
import com.simibubi.create.content.kinetics.base.HorizontalAxisKineticBlock;
import com.simibubi.create.foundation.block.IBE;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntityTicker;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * 智能转速控制器：轴向方块，两端带传动轴，正面复刻机械动力原版的拉条数值选择器。
 * <p>
 * 输入端接入应力后，输出端按拉条设定的转速与方向转动。
 */
public class SmartSpeedControllerBlock extends HorizontalAxisKineticBlock
        implements IBE<SmartSpeedControllerBlockEntity> {
    /** 仪表块体：x/y 的 2~14，沿轴方向 z 的 1~15（与 {@code gauge} 模型一致）。 */
    private static final VoxelShape BODY =
            Shapes.box(2 / 16.0, 2 / 16.0, 1 / 16.0, 14 / 16.0, 14 / 16.0, 15 / 16.0);
    /** 贯穿两端的传动轴：截面 x/y 的 6~10。 */
    private static final VoxelShape SHAFT =
            Shapes.box(6 / 16.0, 6 / 16.0, 0 / 16.0, 10 / 16.0, 10 / 16.0, 16 / 16.0);
    /** 沿 Z 轴的形状（模型本体朝向）。 */
    private static final VoxelShape SHAPE_Z = Shapes.or(BODY, SHAFT);
    /** 沿 X 轴的形状：把 Z 轴的形状绕竖轴转 90°。 */
    private static final VoxelShape SHAPE_X = Shapes.or(
            Shapes.box(1 / 16.0, 2 / 16.0, 2 / 16.0, 15 / 16.0, 14 / 16.0, 14 / 16.0),
            Shapes.box(0 / 16.0, 6 / 16.0, 6 / 16.0, 16 / 16.0, 10 / 16.0, 10 / 16.0));

    public SmartSpeedControllerBlock(Properties properties) {
        super(properties);
    }

    @Override
    public Class<SmartSpeedControllerBlockEntity> getBlockEntityClass() {
        return SmartSpeedControllerBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends SmartSpeedControllerBlockEntity> getBlockEntityType() {
        return CbcBlockEntities.SPEED_CONTROLLER.get();
    }

    @Override
    public Direction.Axis getRotationAxis(BlockState state) {
        return state.getValue(HORIZONTAL_AXIS);
    }

    /**
     * 手持物品右键时一律放行，让手持物品（外设绑定模块卡、扳手等）自己的逻辑先跑。
     * <p>
     * 不放行的话右键会被方块消费掉，外设绑定模块卡永远收不到 {@code useOn}，也就绑不上。
     */
    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                              Player player, InteractionHand hand, BlockHitResult hitResult) {
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    /**
     * 显式提供方块实体 tick。
     * <p>
     * 只靠 {@code IBE} 的默认实现时，新注册的实体类型拿不到 ticker，
     * 表现为方块能放、能显示，但动力学从不更新、传动轴也接不进应力网络。
     */
    @Override
    @Nullable
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                 BlockEntityType<T> type) {
        return new SmartBlockEntityTicker<>();
    }

    /** 碰撞箱贴合模型，按旋转轴选 Z 向或 X 向形状。 */
    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return shapeOf(state);
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos,
                                        CollisionContext context) {
        return shapeOf(state);
    }

    private static VoxelShape shapeOf(BlockState state) {
        return state.getValue(HORIZONTAL_AXIS) == Direction.Axis.X ? SHAPE_X : SHAPE_Z;
    }
}
