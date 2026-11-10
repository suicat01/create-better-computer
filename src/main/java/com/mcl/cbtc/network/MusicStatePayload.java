package com.mcl.cbtc.network;

import com.mcl.cbtc.CreateBetterComputer;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * 客户端 → 服务端：上报本机当前正在播放的网络歌曲状态。
 * <p>
 * 歌曲、歌词、进度只在客户端存在，读取类积木靠这条消息拿到数据。
 * {@code source} 是声源坐标串（"维度|x|y|z"），用来区分是电脑还是某台音响在发声；
 * {@code elapsed < 0} 表示该声源已停止。
 */
public record MusicStatePayload(String source, String songName, String artist, int total, int elapsed,
                                String lyricOriginal, String lyricTranslated)
        implements CustomPacketPayload {
    public static final Type<MusicStatePayload> TYPE =
            new Type<>(CreateBetterComputer.asResource("music_state"));

    public static final StreamCodec<RegistryFriendlyByteBuf, MusicStatePayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, payload) -> {
                        buf.writeUtf(payload.source(), 128);
                        buf.writeUtf(payload.songName(), 256);
                        buf.writeUtf(payload.artist(), 512);
                        buf.writeVarInt(payload.total());
                        buf.writeVarInt(payload.elapsed());
                        buf.writeUtf(payload.lyricOriginal(), 1024);
                        buf.writeUtf(payload.lyricTranslated(), 1024);
                    },
                    buf -> new MusicStatePayload(
                            buf.readUtf(128),
                            buf.readUtf(256),
                            buf.readUtf(512),
                            buf.readVarInt(),
                            buf.readVarInt(),
                            buf.readUtf(1024),
                            buf.readUtf(1024)));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
