package com.mcl.cbtc.client;

import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.ModList;
import org.jetbrains.annotations.Nullable;

/**
 * 预测落点的客户端数据与本地计算。
 * <p>
 * 服务端每刻同步一发炮弹的发射参数，客户端在这里用本地世界做弹道模拟，
 * 计算结果缓存到参数更新为止；真正的绘制在 {@link ImpactMarkerRenderer}。
 */
public final class GunnerSightImpact {
    /** 参数多久没更新就认为火炮不再预测（毫秒）。 */
    public static final long LIFETIME_MS = 1000L;

    /** 一发炮弹的发射参数。 */
    public record Shot(Vec3 muzzle, Vec3 direction, Vec3 inheritedVelocity, double speed,
                       double gravity, double drag, boolean quadratic, double dragDensity) {
    }

    /**
     * 计算出的落点。
     * <p>
     * {@code subLevelId} 不为空时，{@code localPoint / localNormal / localDirection}
     * 是结构局部坐标，渲染时按结构姿态插值，让标记跟着航空学物理结构移动。
     */
    public record Mark(Vec3 point, Vec3 normal, Vec3 direction, int flightTicks,
                       @Nullable UUID subLevelId, @Nullable Vec3 localPoint,
                       @Nullable Vec3 localNormal, @Nullable Vec3 localDirection) {
    }

    private static ResourceLocation dimension;
    private static Shot shot;
    private static long receivedAt;
    private static Mark computed;
    private static long computedAt = Long.MIN_VALUE;

    private GunnerSightImpact() {
    }

    /** 收到一次发射参数同步，覆盖上一次的预测。 */
    public static void accept(ResourceLocation dim, Vec3 muzzle, Vec3 direction,
                              Vec3 inheritedVelocity, double speed, double gravity,
                              double drag, boolean quadratic, double dragDensity) {
        dimension = dim;
        shot = new Shot(muzzle, direction, inheritedVelocity, speed, gravity, drag, quadratic, dragDensity);
        receivedAt = net.minecraft.Util.getMillis();
    }

    /** 当前仍有效的落点；过期、不在当前维度或模拟失败时返回 null。 */
    @Nullable
    public static Mark active(Minecraft minecraft) {
        if (shot == null || minecraft.level == null) {
            return null;
        }
        if (dimension != null && !dimension.equals(minecraft.level.dimension().location())) {
            return null;
        }
        if (net.minecraft.Util.getMillis() - receivedAt > LIFETIME_MS) {
            return null;
        }
        if (computedAt != receivedAt) {
            computedAt = receivedAt;
            computed = compute(minecraft, shot);
        }
        return computed;
    }

    @Nullable
    private static Mark compute(Minecraft minecraft, Shot shot) {
        ClientImpactPredictor.Hit hit;
        try {
            hit = ClientImpactPredictor.compute(minecraft.level, shot);
        } catch (Throwable ignored) {
            return null;
        }
        if (hit == null) {
            return null;
        }
        if (ModList.get().isLoaded("sable")) {
            try {
                return ImpactSubLevelResolver.attach(minecraft, hit.point(), hit.normal(),
                        hit.direction(), hit.ticks());
            } catch (Throwable ignored) {
                // 结构姿态解析失败时退回纯世界坐标，至少保证标记还能显示
            }
        }
        return new Mark(hit.point(), hit.normal(), hit.direction(), hit.ticks(),
                null, null, null, null);
    }
}
