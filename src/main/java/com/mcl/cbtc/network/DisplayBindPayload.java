package com.mcl.cbtc.network;

import com.mcl.cbtc.CreateBetterComputer;
import com.mcl.cbtc.item.DisplayLinkModuleItem;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.InteractionHand;

public record DisplayBindPayload(InteractionHand hand, BlockPos pos, String name) implements CustomPacketPayload {
    public static final Type<DisplayBindPayload> TYPE = new Type<>(CreateBetterComputer.asResource("display_bind"));
    public static final StreamCodec<RegistryFriendlyByteBuf, DisplayBindPayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeEnum(payload.hand());
                BlockPos.STREAM_CODEC.encode(buf, payload.pos());
                buf.writeUtf(payload.name(), 48);
            },
            buf -> new DisplayBindPayload(buf.readEnum(InteractionHand.class), BlockPos.STREAM_CODEC.decode(buf),
                    buf.readUtf(48)));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
