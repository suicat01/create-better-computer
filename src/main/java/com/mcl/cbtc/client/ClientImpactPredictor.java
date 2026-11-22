package com.mcl.cbtc.client;

import com.happysg.radar.targeting.ProjectileSimulator;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * 客户端本地弹道模拟。
 * <p>
 * 用服务端同步过来的炮口参数与雷达模组的模拟器，在本地世界逐刻积分；
 * 客户端每收到一次参数只算一次，渲染时直接复用结果，因此没有网络延迟。
 */
final class ClientImpactPredictor {
    /** 模拟上限（刻）：20 秒足够大口径炮弹落地。 */
    private static final int MAX_TICKS = 400;
    /** 离炮口这么近的命中当作炮管自碰撞忽略（格）。 */
    private static final double SELF_CLEARANCE = 1.0D;

    /** 本地模拟结果：落点、表面法线、入射方向与飞行时间（刻）。 */
    record Hit(Vec3 point, Vec3 normal, Vec3 direction, int ticks) {
    }

    private ClientImpactPredictor() {
    }

    @Nullable
    static Hit compute(Level level, GunnerSightImpact.Shot shot) {
        if (level == null || shot == null) {
            return null;
        }
        Vec3 muzzle = shot.muzzle();
        BlockHitResult[] blocked = new BlockHitResult[1];
        Vec3[] blockedDirection = new Vec3[1];
        ProjectileSimulator.SimulationResult result = new ProjectileSimulator().simulate(
                muzzle, shot.direction(), shot.inheritedVelocity(), shot.speed(), shot.gravity(),
                shot.drag(), shot.quadratic(), false, shot.dragDensity(), MAX_TICKS, level,
                (previous, current) -> {
                    BlockHitResult hit = level.clip(new ClipContext(previous.position(), current.position(),
                            ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE,
                            (net.minecraft.world.entity.Entity) null));
                    if (hit.getType() != HitResult.Type.BLOCK) {
                        return false;
                    }
                    // 只忽略炮口附近的炮管自碰撞；按距离而不是按 tick，
                    // 否则炮弹一 tick 飞好几格，近处的墙/地板会被整段跳过
                    if (hit.getLocation().distanceToSqr(muzzle) < SELF_CLEARANCE * SELF_CLEARANCE) {
                        return false;
                    }
                    blocked[0] = hit;
                    blockedDirection[0] = current.position().subtract(previous.position());
                    return true;
                });
        if (blocked[0] != null) {
            net.minecraft.core.Direction face = blocked[0].getDirection();
            Vec3 normal = new Vec3(face.getStepX(), face.getStepY(), face.getStepZ());
            Vec3 direction = blockedDirection[0] == null ? new Vec3(0.0D, -1.0D, 0.0D)
                    : blockedDirection[0];
            return new Hit(blocked[0].getLocation(), normal, direction, result.ticks());
        }
        // 没撞到方块：从模拟终点向下找地面
        Vec3 end = result.endPosition();
        BlockHitResult ground = level.clip(new ClipContext(end, end.add(0.0D, -256.0D, 0.0D),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE,
                (net.minecraft.world.entity.Entity) null));
        if (ground.getType() == HitResult.Type.BLOCK) {
            return new Hit(ground.getLocation(), new Vec3(0.0D, 1.0D, 0.0D),
                    new Vec3(0.0D, -1.0D, 0.0D), result.ticks());
        }
        // 兜底：落在虚空上方时至少给出模拟终点，避免完全看不到标记
        if (end.y < level.getMinBuildHeight()) {
            return null;
        }
        return new Hit(end, new Vec3(0.0D, 1.0D, 0.0D), new Vec3(0.0D, -1.0D, 0.0D), result.ticks());
    }
}
