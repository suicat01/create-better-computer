package com.mcl.cbtc.network;

import com.mcl.cbtc.CreateBetterComputer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * 客户端 → 服务端：编辑界面打开后请求一次程序同步。
 * 界面打开时服务端已经推过一次，但那次可能早于界面创建完成，因此界面就绪后主动再要一次。
 */
public record ProgramRequestPayload(BlockPos pos) implements CustomPacketPayload {
    public static final Type<ProgramRequestPayload> TYPE =
            new Type<>(CreateBetterComputer.asResource("program_request"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ProgramRequestPayload> STREAM_CODEC =
            StreamCodec.composite(BlockPos.STREAM_CODEC, ProgramRequestPayload::pos,
                    ProgramRequestPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
