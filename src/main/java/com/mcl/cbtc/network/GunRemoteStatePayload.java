package com.mcl.cbtc.network;

import com.mcl.cbtc.CreateBetterComputer;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** 客户端 → 服务端：进入或退出枪式遥控器的使用状态。 */
public record GunRemoteStatePayload(String remoteId, boolean active) implements CustomPacketPayload {
    public static final Type<GunRemoteStatePayload> TYPE =
            new Type<>(CreateBetterComputer.asResource("gun_remote_state"));
    public static final StreamCodec<RegistryFriendlyByteBuf, GunRemoteStatePayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, payload) -> {
                        buf.writeUtf(payload.remoteId());
                        buf.writeBoolean(payload.active());
                    },
                    buf -> new GunRemoteStatePayload(buf.readUtf(), buf.readBoolean()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
