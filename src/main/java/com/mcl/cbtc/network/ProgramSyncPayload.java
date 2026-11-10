package com.mcl.cbtc.network;

import com.mcl.cbtc.CreateBetterComputer;
import com.mcl.cbtc.core.Program;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * 服务端 → 客户端：把电脑的名字与程序整体发给编辑者。
 */
public record ProgramSyncPayload(BlockPos pos, Program program, String computerName)
        implements CustomPacketPayload {
    public static final Type<ProgramSyncPayload> TYPE =
            new Type<>(CreateBetterComputer.asResource("program_sync"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ProgramSyncPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, payload) -> {
                        BlockPos.STREAM_CODEC.encode(buf, payload.pos());
                        writeProgram(buf, payload.program());
                        buf.writeUtf(payload.computerName() == null ? "" : payload.computerName(), 64);
                    },
                    buf -> new ProgramSyncPayload(BlockPos.STREAM_CODEC.decode(buf),
                            readProgram(buf), buf.readUtf(64)));

    /** 程序用 NBT 字节数组传输，避开不同缓冲实现的 NBT 空值差异。 */
    static void writeProgram(RegistryFriendlyByteBuf buf, Program program) {
        byte[] data = program.save().toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
        buf.writeByteArray(data);
    }

    static Program readProgram(RegistryFriendlyByteBuf buf) {
        byte[] data = buf.readByteArray();
        String json = new String(data, java.nio.charset.StandardCharsets.UTF_8);
        try {
            return Program.load(net.minecraft.nbt.TagParser.parseTag(json));
        } catch (Exception e) {
            return new Program();
        }
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
