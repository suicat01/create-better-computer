package com.mcl.cbtc.network;

import com.mcl.cbtc.CreateBetterComputer;
import com.mcl.cbtc.core.Program;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * 客户端 → 服务端：编辑完成，把整段程序与电脑名字写回方块。
 */
public record ProgramSavePayload(BlockPos pos, Program program, String computerName)
        implements CustomPacketPayload {
    public static final Type<ProgramSavePayload> TYPE =
            new Type<>(CreateBetterComputer.asResource("program_save"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ProgramSavePayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, payload) -> {
                        BlockPos.STREAM_CODEC.encode(buf, payload.pos());
                        ProgramSyncPayload.writeProgram(buf, payload.program());
                        buf.writeUtf(payload.computerName() == null ? "" : payload.computerName(), 32);
                    },
                    buf -> new ProgramSavePayload(BlockPos.STREAM_CODEC.decode(buf),
                            ProgramSyncPayload.readProgram(buf), buf.readUtf(32)));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
