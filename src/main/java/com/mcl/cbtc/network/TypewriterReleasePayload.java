package com.mcl.cbtc.network;

import com.mcl.cbtc.CreateBetterComputer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** 客户端请求退出正在使用的 CB 打字机. */
public record TypewriterReleasePayload(BlockPos pos) implements CustomPacketPayload {
    public static final Type<TypewriterReleasePayload> TYPE = new Type<>(CreateBetterComputer.asResource("typewriter_release"));
    public static final StreamCodec<RegistryFriendlyByteBuf, TypewriterReleasePayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> BlockPos.STREAM_CODEC.encode(buf, payload.pos()),
            buf -> new TypewriterReleasePayload(BlockPos.STREAM_CODEC.decode(buf)));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
