package com.mcl.cbtc.network;

import com.mcl.cbtc.CreateBetterComputer;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * 服务端 → 客户端：本机可用的外设清单，供积木下拉里选择。
 * <p>
 * 只下发 {@link com.mcl.cbtc.device.DeviceKind} 的 id；支持的数据项由客户端本地查表，
 * 避免数据项多的外设把字符串撑爆包体积上限。
 */
public record DeviceListPayload(BlockPos pos, List<Entry> devices) implements CustomPacketPayload {
    public record Entry(String id, String name, String kind, int soundRange) {
    }

    public static final Type<DeviceListPayload> TYPE = new Type<>(CreateBetterComputer.asResource("device_list"));
    public static final StreamCodec<RegistryFriendlyByteBuf, DeviceListPayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> {
                BlockPos.STREAM_CODEC.encode(buf, payload.pos());
                buf.writeVarInt(payload.devices().size());
                for (Entry entry : payload.devices()) {
                    buf.writeUtf(entry.id(), 64);
                    buf.writeUtf(entry.name(), 64);
                    buf.writeUtf(entry.kind(), 32);
                    // 发声设备自带的范围上限：电脑按扬声器模块，音响按自身升级槽
                    buf.writeVarInt(entry.soundRange());
                }
            },
            buf -> {
                BlockPos pos = BlockPos.STREAM_CODEC.decode(buf);
                int size = buf.readVarInt();
                List<Entry> entries = new ArrayList<>(size);
                for (int i = 0; i < size; i++) {
                    entries.add(new Entry(buf.readUtf(64), buf.readUtf(64), buf.readUtf(32), buf.readVarInt()));
                }
                return new DeviceListPayload(pos, entries);
            });

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
