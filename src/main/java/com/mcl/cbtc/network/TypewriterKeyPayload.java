package com.mcl.cbtc.network;

import com.mcl.cbtc.CreateBetterComputer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** 客户端 → 服务端：CB 打字机的一次按键。 */
public record TypewriterKeyPayload(BlockPos pos, int keyCode, boolean down) implements CustomPacketPayload {
    public static final Type<TypewriterKeyPayload> TYPE = new Type<>(CreateBetterComputer.asResource("typewriter_key"));
    public static final StreamCodec<RegistryFriendlyByteBuf, TypewriterKeyPayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> {
                BlockPos.STREAM_CODEC.encode(buf, payload.pos());
                buf.writeVarInt(payload.keyCode());
                buf.writeBoolean(payload.down());
            },
            buf -> new TypewriterKeyPayload(BlockPos.STREAM_CODEC.decode(buf), buf.readVarInt(), buf.readBoolean()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
