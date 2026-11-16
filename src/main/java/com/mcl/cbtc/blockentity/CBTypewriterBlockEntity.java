package com.mcl.cbtc.blockentity;

import com.mcl.cbtc.device.DeviceIdentity;
import com.mcl.cbtc.device.Devices;
import com.mcl.cbtc.registry.CbcBlocks;
import dev.simulated_team.simulated.content.blocks.redstone.linked_typewriter.LinkedTypewriterBlockEntity;
import dev.simulated_team.simulated.mixin_interface.PlayerTypewriterExtension;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/** 独立的 CB 打字机方块实体；按键由 CB 输入通道写入。 */
public class CBTypewriterBlockEntity extends LinkedTypewriterBlockEntity implements DeviceIdentity {
    /** 方块外设的持久身份：绑定靠它指到这台方块，结构搬动也不丢。 */
    @Nullable
    private UUID deviceId;

    public CBTypewriterBlockEntity(BlockPos pos, BlockState state) {
        super(com.mcl.cbtc.registry.CbcBlockEntities.TYPEWRITER.get(), pos, state);
    }

    @Nullable
    @Override
    public UUID deviceId() {
        return deviceId;
    }

    @Override
    public void setDeviceId(@Nullable UUID id) {
        deviceId = id;
        setChanged();
    }

    @Override
    public void onLoad() {
        super.onLoad();
        Devices.registerIdentity(level, this);
    }

    // setRemoved 在 Create 的 SmartBlockEntity 里是 final：区块卸载也会走到那里，
    // 而 remove() 只在方块真的被移除时调用，正好用来注销身份
    @Override
    public void remove() {
        Devices.forgetIdentity(level, this);
        super.remove();
    }

    // saveAdditional 在 SmartBlockEntity 里是 final，数据统一走 write/read
    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        DeviceIdentity.writeId(tag, deviceId);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        deviceId = DeviceIdentity.readId(tag);
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
