package com.mcl.cbtc.network;

import com.mcl.cbtc.CreateBetterComputer;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.InteractionHand;

/** 目标定位模块：提交要写入的 X / Z 坐标。 */
public record TargetLocatorSetPayload(InteractionHand hand, double x, double z) implements CustomPacketPayload {
    public static final Type<TargetLocatorSetPayload> TYPE =
            new Type<>(CreateBetterComputer.asResource("target_locator_set"));
    public static final StreamCodec<RegistryFriendlyByteBuf, TargetLocatorSetPayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeEnum(payload.hand());
                buf.writeDouble(payload.x());
                buf.writeDouble(payload.z());
            },
            buf -> new TargetLocatorSetPayload(buf.readEnum(InteractionHand.class),
                    buf.readDouble(), buf.readDouble()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
