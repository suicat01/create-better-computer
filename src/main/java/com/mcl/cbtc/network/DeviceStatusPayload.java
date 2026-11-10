package com.mcl.cbtc.network;

import com.mcl.cbtc.CreateBetterComputer;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * 服务端同步电脑当前可用的外设与积木能力。
 * lanNetworks 是电脑当前已加入的局域网 ID 清单，客户端用它做积木"局域网"参数的下拉候选。
 * songs / playlists 是拓展器里音乐模块卡提供的内容，供音乐积木的下拉使用。
 */
public record DeviceStatusPayload(BlockPos pos, List<String> lines, boolean storageModule,
        boolean wirelessRedstoneModule, List<String> lanNetworks, boolean internetModule,
        boolean speakerModule, boolean hardDrive, List<String> songs, List<String> playlists)
        implements CustomPacketPayload {
    public static final Type<DeviceStatusPayload> TYPE =
            new Type<>(CreateBetterComputer.asResource("device_status"));

    public static final StreamCodec<RegistryFriendlyByteBuf, DeviceStatusPayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> {
                BlockPos.STREAM_CODEC.encode(buf, payload.pos());
                buf.writeBoolean(payload.storageModule());
                buf.writeBoolean(payload.wirelessRedstoneModule());
                buf.writeBoolean(payload.internetModule());
                buf.writeBoolean(payload.speakerModule());
                buf.writeBoolean(payload.hardDrive());
                buf.writeVarInt(payload.lanNetworks().size());
                for (String id : payload.lanNetworks()) {
                    buf.writeUtf(id, 64);
                }
                buf.writeVarInt(payload.lines().size());
                for (String line : payload.lines()) {
                    buf.writeUtf(line, 128);
                }
                buf.writeVarInt(payload.songs().size());
                for (String song : payload.songs()) {
                    buf.writeUtf(song, 256);
                }
                buf.writeVarInt(payload.playlists().size());
                for (String id : payload.playlists()) {
                    buf.writeUtf(id, 64);
                }
            },
            buf -> {
                BlockPos pos = BlockPos.STREAM_CODEC.decode(buf);
                boolean storageModule = buf.readBoolean();
                boolean wirelessRedstoneModule = buf.readBoolean();
                boolean internetModule = buf.readBoolean();
                boolean speakerModule = buf.readBoolean();
                boolean hardDrive = buf.readBoolean();
                int networkCount = buf.readVarInt();
                List<String> lanNetworks = new java.util.ArrayList<>(networkCount);
                for (int i = 0; i < networkCount; i++) {
                    lanNetworks.add(buf.readUtf(64));
                }
                int count = buf.readVarInt();
                List<String> lines = new java.util.ArrayList<>(count);
                for (int i = 0; i < count; i++) {
                    lines.add(buf.readUtf(128));
                }
                int songCount = buf.readVarInt();
                List<String> songs = new java.util.ArrayList<>(songCount);
                for (int i = 0; i < songCount; i++) {
                    songs.add(buf.readUtf(256));
                }
                int playlistCount = buf.readVarInt();
                List<String> playlists = new java.util.ArrayList<>(playlistCount);
                for (int i = 0; i < playlistCount; i++) {
                    playlists.add(buf.readUtf(64));
                }
                return new DeviceStatusPayload(pos, lines, storageModule, wirelessRedstoneModule, lanNetworks,
                        internetModule, speakerModule, hardDrive, songs, playlists);
            });

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
