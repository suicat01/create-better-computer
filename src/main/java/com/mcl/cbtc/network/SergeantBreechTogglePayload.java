package com.mcl.cbtc.network;

import com.mcl.cbtc.CreateBetterComputer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * 服务端 → 客户端：让客户端那侧的速射炮闩也切换一次开合。
 * <p>
 * 组装成大炮后，炮闩的开合动画是客户端 contraption 里的方块实体自己跑的，
 * 只改服务端状态客户端看不到任何动作，所以两边都要触发。
 */
public record SergeantBreechTogglePayload(BlockPos mountPos) implements CustomPacketPayload {
    public static final Type<SergeantBreechTogglePayload> TYPE =
            new Type<>(CreateBetterComputer.asResource("sergeant_breech_toggle"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SergeantBreechTogglePayload> STREAM_CODEC =
            StreamCodec.composite(BlockPos.STREAM_CODEC, SergeantBreechTogglePayload::mountPos,
                    SergeantBreechTogglePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
