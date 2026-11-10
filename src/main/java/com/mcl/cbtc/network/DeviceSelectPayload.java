package com.mcl.cbtc.network;

import com.mcl.cbtc.CreateBetterComputer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * 服务端 → 客户端：外设绑定模块卡的选中状态变化。
 * <p>
 * 选中与命名都发生在服务端，客户端只负责显示提示或打开命名框，
 * 不再需要客户端自己维护"选中了哪个方块"。
 *
 * @param kind     0 = 已选中（画描边 + 提示），1 = 打开命名框，2 = 清除选中
 * @param pos      目标方块坐标
 * @param deviceName 方块显示名
 * @param kindName 外设种类显示名
 * @param currentName 该外设已绑定的名称（未绑定时为空）
 */
public record DeviceSelectPayload(int kind, BlockPos pos, String deviceName, String kindName,
        String currentName) implements CustomPacketPayload {
    public static final int SELECTED = 0;
    public static final int OPEN_NAME = 1;
    public static final int CLEARED = 2;

    public static final Type<DeviceSelectPayload> TYPE =
            new Type<>(CreateBetterComputer.asResource("device_select"));

    public static final StreamCodec<RegistryFriendlyByteBuf, DeviceSelectPayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeVarInt(payload.kind());
                BlockPos.STREAM_CODEC.encode(buf, payload.pos());
                buf.writeUtf(payload.deviceName(), 128);
                buf.writeUtf(payload.kindName(), 64);
                buf.writeUtf(payload.currentName(), 48);
            },
            buf -> new DeviceSelectPayload(buf.readVarInt(), BlockPos.STREAM_CODEC.decode(buf),
                    buf.readUtf(128), buf.readUtf(64), buf.readUtf(48)));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
