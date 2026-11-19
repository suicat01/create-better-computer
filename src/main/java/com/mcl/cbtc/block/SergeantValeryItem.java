package com.mcl.cbtc.block;

import com.simibubi.create.content.kinetics.mechanicalArm.ArmInteractionPoint;
import com.simibubi.create.content.kinetics.mechanicalArm.ArmPlacementPacket;
import net.createmod.catnip.platform.CatnipServices;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 瓦列里中士的方块物品。
 * <p>
 * 完全照搬 Create 的 {@code ArmItem}：手持时右键可交互方块只做"选点"不放置；
 * 真正放下方块时通知客户端把选中的交互点写进新方块里，从而沿用原版动力臂的绑定流程。
 */
public class SergeantValeryItem extends BlockItem {
    public SergeantValeryItem(Block block, Properties properties) {
        super(block, properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        // 点到的方块能当交互点（比如火炮底座）时只选点，不放置方块
        return ArmInteractionPoint.isInteractable(level, pos, level.getBlockState(pos))
                ? InteractionResult.SUCCESS
                : super.useOn(context);
    }

    @Override
    protected boolean updateCustomBlockEntityTag(BlockPos pos, Level level, net.minecraft.world.entity.player.Player player,
                                                 ItemStack stack, BlockState state) {
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            // 让客户端把当前选点通过 ArmPlacementPacket 写回刚放下的方块
            CatnipServices.NETWORK.sendToClient(serverPlayer, new ArmPlacementPacket.ClientBoundRequest(pos));
        }
        return super.updateCustomBlockEntityTag(pos, level, player, stack, state);
    }

    @Override
    public boolean canAttackBlock(BlockState state, Level level, BlockPos pos, Player player) {
        return !ArmInteractionPoint.isInteractable(level, pos, state);
    }
}
