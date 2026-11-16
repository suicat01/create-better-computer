package com.mcl.cbtc.network;

import com.mcl.cbtc.CreateBetterComputer;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * 服务端把某件瞄准组件的锁定表同步给佩戴者：实体网络 id + 锁定序号。
 * <p>
 * 实体网络 id 只在同一个维度内有效，所以带上维度名，客户端换维度后不会误标。
 */
public record GunnerSightLocksPayload(String sightId, String dimension, List<Entry> entries)
        implements CustomPacketPayload {
    public static final Type<GunnerSightLocksPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(CreateBetterComputer.MODID, "sight_locks"));

    public static final StreamCodec<RegistryFriendlyByteBuf, GunnerSightLocksPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, payload) -> {
                        buf.writeUtf(payload.sightId(), 128);
                        buf.writeUtf(payload.dimension(), 128);
                        buf.writeVarInt(payload.entries().size());
                        for (Entry entry : payload.entries()) {
                            buf.writeVarInt(entry.entityId());
                            buf.writeVarInt(entry.seq());
                        }
                    },
                    buf -> {
                        String sightId = buf.readUtf(128);
                        String dimension = buf.readUtf(128);
                        int size = buf.readVarInt();
                        List<Entry> entries = new ArrayList<>(size);
                        for (int i = 0; i < size; i++) {
                            entries.add(new Entry(buf.readVarInt(), buf.readVarInt()));
                        }
                        return new GunnerSightLocksPayload(sightId, dimension, entries);
                    });

    /** 一条同步记录。 */
    public record Entry(int entityId, int seq) {
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
