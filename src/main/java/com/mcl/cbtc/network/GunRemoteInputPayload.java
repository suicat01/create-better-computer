package com.mcl.cbtc.network;

import com.mcl.cbtc.CreateBetterComputer;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** 客户端 → 服务端：枪式遥控器的一次鼠标键变化。 */
public record GunRemoteInputPayload(String remoteId, int key, boolean down) implements CustomPacketPayload {
    public static final Type<GunRemoteInputPayload> TYPE =
            new Type<>(CreateBetterComputer.asResource("gun_remote_input"));
    public static final StreamCodec<RegistryFriendlyByteBuf, GunRemoteInputPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, payload) -> {
                        buf.writeUtf(payload.remoteId());
                        buf.writeVarInt(payload.key());
                        buf.writeBoolean(payload.down());
                    },
                    buf -> new GunRemoteInputPayload(buf.readUtf(), buf.readVarInt(), buf.readBoolean()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
