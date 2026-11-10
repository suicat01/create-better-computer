package com.mcl.cbtc.blockentity;

import com.mcl.cbtc.registry.CbcBlocks;
import dev.simulated_team.simulated.content.blocks.redstone.linked_typewriter.LinkedTypewriterBlockEntity;
import dev.simulated_team.simulated.mixin_interface.PlayerTypewriterExtension;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** 独立的 CB 打字机方块实体；按键由 CB 输入通道写入。 */
public class CBTypewriterBlockEntity extends LinkedTypewriterBlockEntity {
    public CBTypewriterBlockEntity(BlockPos pos, BlockState state) {
        super(com.mcl.cbtc.registry.CbcBlockEntities.TYPEWRITER.get(), pos, state);
    }

    @Override
    public boolean checkAndStartUsing(UUID userId) {
        Player player = level == null ? null : level.getPlayerByUUID(userId);
        if (isInUse() || player == null) {
            return false;
        }
        BlockPos previous = ((PlayerTypewriterExtension) player).simulated$getCurrentTypewriter();
        if (previous != null && !previous.equals(worldPosition)) {
            BlockEntity old = level.getBlockEntity(previous);
            if (old instanceof LinkedTypewriterBlockEntity oldTypewriter) {
                oldTypewriter.disconnectUser();
            }
        }
        ((PlayerTypewriterExtension) player).simulated$setCurrentTypewriter(worldPosition);
        powered = true;
        if (level.isClientSide) {
            com.mcl.cbtc.client.TypewriterKeyInput.beginSession(worldPosition);
        } else {
            setChanged();
        }
        return super.checkAndStartUsing(userId);
    }

    @Override
    public void disconnectUser() {
        if (level != null && level.isClientSide) {
            com.mcl.cbtc.client.TypewriterKeyInput.endSession();
        }
        super.disconnectUser();
    }

    @Override
    public void pressKey(int key) {
        if (!getPressedKeys().contains(key)) {
            super.pressKey(key);
        }
    }

    @Override
    public Component getDisplayName() {
        return CbcBlocks.TYPEWRITER.get().getName();
    }
}
