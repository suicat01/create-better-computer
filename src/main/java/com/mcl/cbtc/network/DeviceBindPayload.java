package com.mcl.cbtc.network;

import com.mcl.cbtc.CreateBetterComputer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.InteractionHand;

public record DeviceBindPayload(InteractionHand hand, BlockPos pos, String name) implements CustomPacketPayload {
    public static final Type<DeviceBindPayload> TYPE = new Type<>(CreateBetterComputer.asResource("device_bind"));
    public static final StreamCodec<RegistryFriendlyByteBuf, DeviceBindPayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeEnum(payload.hand());
                BlockPos.STREAM_CODEC.encode(buf, payload.pos());
                buf.writeUtf(payload.name(), 48);
            },
            buf -> new DeviceBindPayload(buf.readEnum(InteractionHand.class), BlockPos.STREAM_CODEC.decode(buf),
                    buf.readUtf(48)));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
