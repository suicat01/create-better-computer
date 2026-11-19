package com.mcl.cbtc.block;

import com.mcl.cbtc.blockentity.SergeantValeryBlockEntity;
import com.mcl.cbtc.registry.CbcBlockEntities;
import com.simibubi.create.AllItems;
import com.simibubi.create.content.kinetics.mechanicalArm.ArmBlock;
import com.simibubi.create.content.kinetics.mechanicalArm.ArmBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * 瓦列里中士：原版动力臂的换皮复刻，并额外带一个可编程装填界面。
 * <p>
 * 空手右键打开界面；潜行 + 扳手右键把客户端当前选中的交互点写回这台手臂（重绑）；
 * 其余手持物品的交互仍交给原版动力臂（护目镜、把手里的东西交回玩家）。
 */
public class SergeantValeryBlock extends ArmBlock {
    public SergeantValeryBlock(Properties properties) {
        super(properties);
    }

    @Override
    public BlockEntityType<? extends ArmBlockEntity> getBlockEntityType() {
        return CbcBlockEntities.SERGEANT_VALERY.get();
    }

    @Override
    public InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (level.getBlockEntity(pos) instanceof SergeantValeryBlockEntity blockEntity) {
            player.openMenu(blockEntity, blockEntity::writeMenuData);
            return InteractionResult.CONSUME;
        }
        return InteractionResult.PASS;
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                              Player player, InteractionHand hand, BlockHitResult hitResult) {
        // 空手交还给 useWithoutItem，否则原版动力臂会把手里的东西当成"要交回玩家"而吞掉界面
        if (stack.isEmpty()) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        // 潜行 + 扳手：应用当前选点（已放置的方块重绑）
        if (player.isShiftKeyDown() && AllItems.WRENCH.isIn(stack)) {
            if (level.isClientSide) {
                com.mcl.cbtc.client.sergeant.SergeantClientBinding.flush(pos);
            }
            return ItemInteractionResult.SUCCESS;
        }
        return super.useItemOn(stack, state, level, pos, player, hand, hitResult);
    }
}
