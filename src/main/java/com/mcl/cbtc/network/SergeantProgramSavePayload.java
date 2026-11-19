package com.mcl.cbtc.network;

import com.mcl.cbtc.CreateBetterComputer;
import com.mcl.cbtc.blockentity.SergeantValeryBlockEntity;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * 客户端 → 服务端：保存瓦列里中士的装填指令。
 */
public record SergeantProgramSavePayload(BlockPos pos, List<String> rows) implements CustomPacketPayload {
    public static final Type<SergeantProgramSavePayload> TYPE =
            new Type<>(CreateBetterComputer.asResource("sergeant_program_save"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SergeantProgramSavePayload> STREAM_CODEC =
            StreamCodec.of((buf, payload) -> {
                buf.writeBlockPos(payload.pos());
                buf.writeVarInt(payload.rows().size());
                for (String row : payload.rows()) {
                    buf.writeUtf(row == null ? "" : row, 128);
                }
            }, buf -> {
                BlockPos pos = buf.readBlockPos();
                int count = Math.min(buf.readVarInt(), 32);
                List<String> rows = new ArrayList<>(count);
                for (int i = 0; i < count; i++) {
                    rows.add(buf.readUtf(128));
                }
                return new SergeantProgramSavePayload(pos, rows);
            });

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /** 服务端应用入口，供网络层调用。 */
    public static void apply(SergeantValeryBlockEntity blockEntity, List<String> rows) {
        blockEntity.applyProgram(rows);
    }
}
