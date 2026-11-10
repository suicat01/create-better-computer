package com.mcl.cbtc.network;

import com.mcl.cbtc.CreateBetterComputer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * 服务端 → 客户端：让客户端播放一首网络歌曲。
 * <p>
 * 网络音频只能在客户端解码，所以服务端只负责把歌单里选中的那首歌的信息送过去。
 * {@code source} 是发声位置，客户端据此计算音量衰减与立体声定位。
 * {@code url} 为空时表示停止播放。
 */
public record MusicPlayPayload(BlockPos source, String url, String name, int seconds,
                               String artists, float volume, int range)
        implements CustomPacketPayload {
    public static final Type<MusicPlayPayload> TYPE =
            new Type<>(CreateBetterComputer.asResource("music_play"));

    public static final StreamCodec<RegistryFriendlyByteBuf, MusicPlayPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, payload) -> {
                        BlockPos.STREAM_CODEC.encode(buf, payload.source());
                        buf.writeUtf(payload.url(), 512);
                        buf.writeUtf(payload.name(), 256);
                        buf.writeVarInt(payload.seconds());
                        buf.writeUtf(payload.artists(), 512);
                        buf.writeFloat(payload.volume());
                        buf.writeVarInt(payload.range());
                    },
                    buf -> new MusicPlayPayload(
                            BlockPos.STREAM_CODEC.decode(buf),
                            buf.readUtf(512),
                            buf.readUtf(256),
                            buf.readVarInt(),
                            buf.readUtf(512),
                            buf.readFloat(),
                            buf.readVarInt()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
