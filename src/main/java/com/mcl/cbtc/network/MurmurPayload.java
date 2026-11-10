package com.mcl.cbtc.network;

import com.mcl.cbtc.CreateBetterComputer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * 服务端 → 客户端：在某个方块上方浮现一行小字（默念）。
 * <p>
 * 与音效分开成一个包，是因为默念完全不发声、只做画面文字，
 * 客户端要把它记到一个短命的浮字列表里逐帧绘制。
 * <p>
 * {@code pos} 是发声设备自身的方块坐标（结构内的存储坐标，不投影到主世界）：
 * 客户端在方块实体渲染阶段画字，交给 Sable 施加结构姿态，文字才会跟着结构移动和倾斜。
 * <p>
 * {@code key} 是发声设备的稳定身份：同一设备重复默念时客户端覆盖旧的一条，不会叠字。
 */
public record MurmurPayload(BlockPos pos, String text, String key) implements CustomPacketPayload {
    public static final Type<MurmurPayload> TYPE =
            new Type<>(CreateBetterComputer.asResource("murmur"));

    public static final StreamCodec<RegistryFriendlyByteBuf, MurmurPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, payload) -> {
                        BlockPos.STREAM_CODEC.encode(buf, payload.pos());
                        buf.writeUtf(payload.text(), 512);
                        buf.writeUtf(payload.key(), 128);
                    },
                    buf -> new MurmurPayload(BlockPos.STREAM_CODEC.decode(buf), buf.readUtf(512),
                            buf.readUtf(128)));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
