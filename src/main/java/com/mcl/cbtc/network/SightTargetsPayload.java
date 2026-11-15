package com.mcl.cbtc.network;

import com.mcl.cbtc.CreateBetterComputer;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * 雷达映射：把雷达扫描到的实体同步给戴着瞄准组件的玩家。
 * <p>
 * 每条记录带实体网络 id（找不到实体时为 -1）与快照坐标，客户端优先用活实体，取不到就用快照画框。
 */
public record SightTargetsPayload(ResourceLocation dimension, List<Target> targets) implements CustomPacketPayload {
    public static final Type<SightTargetsPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(CreateBetterComputer.MODID, "sight_targets"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SightTargetsPayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeUtf(payload.dimension().toString(), 128);
                buf.writeVarInt(payload.targets().size());
                for (Target target : payload.targets()) {
                    buf.writeVarInt(target.entityId());
                    buf.writeDouble(target.x());
                    buf.writeDouble(target.y());
                    buf.writeDouble(target.z());
                    buf.writeFloat(target.height());
                    buf.writeBoolean(target.friendly());
                    buf.writeUtf(target.name(), 128);
                    buf.writeFloat(target.health());
                }
            },
            buf -> {
                ResourceLocation dimension = ResourceLocation.parse(buf.readUtf(128));
                int size = buf.readVarInt();
                List<Target> targets = new ArrayList<>(size);
                for (int i = 0; i < size; i++) {
                    targets.add(new Target(buf.readVarInt(), buf.readDouble(), buf.readDouble(),
                            buf.readDouble(), buf.readFloat(), buf.readBoolean(),
                            buf.readUtf(128), buf.readFloat()));
                }
                return new SightTargetsPayload(dimension, targets);
            });

    /** 一个被框住的目标。health 为负数表示该目标没有生命值。 */
    public record Target(int entityId, double x, double y, double z, float height, boolean friendly,
                         String name, float health) {
    }

    /** 用雷达扫描结果构造一次同步包。 */
    public static SightTargetsPayload of(ResourceLocation dimension,
                                         List<com.mcl.cbtc.compat.RadarCompat.TrackedEntity> tracked) {
        List<Target> targets = new ArrayList<>(tracked.size());
        for (var entity : tracked) {
            targets.add(new Target(entity.entityId(), entity.x(), entity.y(), entity.z(),
                    entity.height(), entity.friendly(), entity.name(), entity.health()));
        }
        return new SightTargetsPayload(dimension, targets);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
