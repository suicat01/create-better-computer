package com.mcl.cbtc.network;

import com.mcl.cbtc.CreateBetterComputer;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.InteractionHand;

/** 局域网模块卡：提交要加入的局域网 ID 与密码。 */
public record LanJoinPayload(InteractionHand hand, String lanId, String password) implements CustomPacketPayload {
    public static final Type<LanJoinPayload> TYPE = new Type<>(CreateBetterComputer.asResource("lan_join"));
    public static final StreamCodec<RegistryFriendlyByteBuf, LanJoinPayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeEnum(payload.hand());
                buf.writeUtf(payload.lanId(), 48);
                buf.writeUtf(payload.password(), 48);
            },
            buf -> new LanJoinPayload(buf.readEnum(InteractionHand.class), buf.readUtf(48), buf.readUtf(48)));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
