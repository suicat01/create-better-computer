package com.mcl.cbtc.network;

import com.mcl.cbtc.CreateBetterComputer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * 服务端 → 客户端：让客户端在本机播放一次音效。
 * <p>
 * 播放交给客户端而不是服务端广播声音，是为了按接收者与电脑的距离做范围过滤。
 * <p>
 * 音效 id 用空字符串表示"不播放音效"；固定键 {@link #STOP_SOUND} 表示"停止本机声音"。
 */
public record SoundPlayPayload(BlockPos pos, String sound, float volume, int range)
        implements CustomPacketPayload {
    /** 特殊音效名：停止本机正在播放的声音。 */
    public static final String STOP_SOUND = "$stop";

    public static final Type<SoundPlayPayload> TYPE =
            new Type<>(CreateBetterComputer.asResource("sound_play"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SoundPlayPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, payload) -> {
                        BlockPos.STREAM_CODEC.encode(buf, payload.pos());
                        buf.writeUtf(payload.sound(), 256);
                        buf.writeFloat(payload.volume());
                        buf.writeVarInt(payload.range());
                    },
                    buf -> new SoundPlayPayload(
                            BlockPos.STREAM_CODEC.decode(buf),
                            buf.readUtf(256),
                            buf.readFloat(),
                            buf.readVarInt()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
