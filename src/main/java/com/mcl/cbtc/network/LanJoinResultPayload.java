package com.mcl.cbtc.network;

import com.mcl.cbtc.CreateBetterComputer;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** 服务端 → 客户端：局域网加入结果，ok 为 false 时 message 是失败原因。 */
public record LanJoinResultPayload(boolean ok, String message) implements CustomPacketPayload {
    public static final Type<LanJoinResultPayload> TYPE =
            new Type<>(CreateBetterComputer.asResource("lan_join_result"));
    public static final StreamCodec<RegistryFriendlyByteBuf, LanJoinResultPayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeBoolean(payload.ok());
                buf.writeUtf(payload.message(), 128);
            },
            buf -> new LanJoinResultPayload(buf.readBoolean(), buf.readUtf(128)));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
