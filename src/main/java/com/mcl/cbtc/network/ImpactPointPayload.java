package com.mcl.cbtc.network;

import com.mcl.cbtc.CreateBetterComputer;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

/**
 * 预测炮弹落点所需的发射参数。
 * <p>
 * 服务端每刻把当前炮口位置、方向、初速与弹道属性同步给戴着瞄准组件的玩家，
 * 由客户端自己模拟弹道并渲染落点，避免网络往返带来的延迟。
 */
public record ImpactPointPayload(ResourceLocation dimension, Vec3 muzzle, Vec3 direction,
                                 Vec3 inheritedVelocity, double speed, double gravity,
                                 double drag, boolean quadratic, double dragDensity)
        implements CustomPacketPayload {
    public static final Type<ImpactPointPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(CreateBetterComputer.MODID, "impact_point"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ImpactPointPayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeUtf(payload.dimension().toString(), 128);
                writeVec(buf, payload.muzzle());
                writeVec(buf, payload.direction());
                writeVec(buf, payload.inheritedVelocity());
                buf.writeDouble(payload.speed());
                buf.writeDouble(payload.gravity());
                buf.writeDouble(payload.drag());
                buf.writeBoolean(payload.quadratic());
                buf.writeDouble(payload.dragDensity());
            },
            buf -> new ImpactPointPayload(ResourceLocation.parse(buf.readUtf(128)),
                    readVec(buf), readVec(buf), readVec(buf),
                    buf.readDouble(), buf.readDouble(), buf.readDouble(),
                    buf.readBoolean(), buf.readDouble()));

    private static void writeVec(RegistryFriendlyByteBuf buf, Vec3 vec) {
        buf.writeDouble(vec.x);
        buf.writeDouble(vec.y);
        buf.writeDouble(vec.z);
    }

    private static Vec3 readVec(RegistryFriendlyByteBuf buf) {
        return new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
