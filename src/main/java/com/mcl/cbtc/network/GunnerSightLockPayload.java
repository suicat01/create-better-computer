package com.mcl.cbtc.network;

import com.mcl.cbtc.CreateBetterComputer;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * 客户端请求锁定准星上的生物：携带瞄准组件 ID 与目标实体网络 id。
 */
public record GunnerSightLockPayload(String sightId, int entityId) implements CustomPacketPayload {
    public static final Type<GunnerSightLockPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(CreateBetterComputer.MODID, "sight_lock"));

    public static final StreamCodec<RegistryFriendlyByteBuf, GunnerSightLockPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, payload) -> {
                        buf.writeUtf(payload.sightId(), 128);
                        buf.writeVarInt(payload.entityId());
                    },
                    buf -> new GunnerSightLockPayload(buf.readUtf(128), buf.readVarInt()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
