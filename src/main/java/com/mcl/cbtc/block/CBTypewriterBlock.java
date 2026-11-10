package com.mcl.cbtc.block;

import com.mcl.cbtc.blockentity.CBTypewriterBlockEntity;
import com.mcl.cbtc.registry.CbcBlockEntities;
import dev.simulated_team.simulated.content.blocks.redstone.linked_typewriter.LinkedTypewriterBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/** CB 自有打字机，只复用航空学的表现与底层外设能力。 */
public class CBTypewriterBlock extends LinkedTypewriterBlock {
    public CBTypewriterBlock(Properties properties) {
        super(properties);
    }

    @Override
    public BlockEntityType<? extends CBTypewriterBlockEntity> getBlockEntityType() {
        return CbcBlockEntities.TYPEWRITER.get();
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
            Player player, InteractionHand hand, BlockHitResult hit) {
        if (!stack.isEmpty() || hand != InteractionHand.MAIN_HAND || player.isShiftKeyDown()) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (!(level.getBlockEntity(pos) instanceof CBTypewriterBlockEntity typewriter)) {
            return ItemInteractionResult.FAIL;
        }
        if (typewriter.checkUser(player.getUUID())) {
            typewriter.disconnectUser();
            return ItemInteractionResult.SUCCESS;
        }
        return typewriter.checkAndStartUsing(player.getUUID())
                ? ItemInteractionResult.SUCCESS
                : ItemInteractionResult.FAIL;
    }
}
