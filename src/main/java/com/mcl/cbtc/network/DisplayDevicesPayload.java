package com.mcl.cbtc.network;

import com.mcl.cbtc.CreateBetterComputer;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record DisplayDevicesPayload(BlockPos pos, List<Entry> devices) implements CustomPacketPayload {
    public record Entry(String id, String name, int maxLines) {
    }

    public static final Type<DisplayDevicesPayload> TYPE = new Type<>(CreateBetterComputer.asResource("display_devices"));
    public static final StreamCodec<RegistryFriendlyByteBuf, DisplayDevicesPayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> {
                BlockPos.STREAM_CODEC.encode(buf, payload.pos());
                buf.writeVarInt(payload.devices().size());
                for (Entry entry : payload.devices()) {
                    buf.writeUtf(entry.id(), 64);
                    buf.writeUtf(entry.name(), 64);
                    buf.writeVarInt(entry.maxLines());
                }
            },
            buf -> {
                BlockPos pos = BlockPos.STREAM_CODEC.decode(buf);
                int size = buf.readVarInt();
                java.util.List<Entry> entries = new java.util.ArrayList<>(size);
                for (int i = 0; i < size; i++) {
                    entries.add(new Entry(buf.readUtf(64), buf.readUtf(64), buf.readVarInt()));
                }
                return new DisplayDevicesPayload(pos, entries);
            });

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
