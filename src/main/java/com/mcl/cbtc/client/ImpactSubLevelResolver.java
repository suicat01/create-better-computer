package com.mcl.cbtc.client;

import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.plot.LevelPlot;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * 把结构局部的落点按当前结构姿态插值到主世界坐标。
 * <p>
 * 只有确认装了 Sable 时才会调用本类；结构姿态用上一刻与当前刻插值，
 * 标记就能随飞艇等物理结构平滑移动和倾斜。
 */
final class ImpactSubLevelResolver {
    private ImpactSubLevelResolver() {
    }

    /**
     * 判断世界落点是否落在物理结构上；是则把坐标 / 法线 / 入射方向换算到结构局部。
     * 不在结构上时返回只带世界坐标的标记。
     */
    static GunnerSightImpact.Mark attach(Minecraft minecraft, Vec3 point, Vec3 normal,
                                         Vec3 direction, int ticks) {
        GunnerSightImpact.Mark world = new GunnerSightImpact.Mark(point, normal, direction, ticks,
                null, null, null, null);
        if (minecraft.level == null) {
            return world;
        }
        SubLevelContainer container = SubLevelContainer.getContainer(minecraft.level);
        if (container == null) {
            return world;
        }
        SubLevel fallback = null;
        int candidates = 0;
        for (SubLevel sub : container.getAllSubLevels()) {
            Vec3 local = sub.logicalPose().transformPositionInverse(point);
            LevelPlot plot = sub.getPlot();
            if (plot == null || !plot.contains(local)) {
                continue;
            }
            // 客户端的 LevelPlot 不维护包围盒（updateBoundingBox 在客户端直接返回），
            // 改为检查结构存储区里这个位置附近是否真的有方块，排除"结构正上方/正下方"的落点
            if (nearStructureBlock(minecraft.level, local)) {
                return attachTo(sub, point, normal, direction, ticks, local);
            }
            // 结构区块还没在客户端加载时拿不到方块，记下来做唯一候选兜底
            fallback = sub;
            candidates++;
        }
        if (candidates == 1) {
            Vec3 local = fallback.logicalPose().transformPositionInverse(point);
            return attachTo(fallback, point, normal, direction, ticks, local);
        }
        return world;
    }

    private static GunnerSightImpact.Mark attachTo(SubLevel sub, Vec3 point, Vec3 normal,
                                                   Vec3 direction, int ticks, Vec3 local) {
        Pose3dc pose = sub.logicalPose();
        return new GunnerSightImpact.Mark(point, normal, direction, ticks, sub.getUniqueId(),
                local, pose.transformNormalInverse(normal), pose.transformNormalInverse(direction));
    }

    /** 结构存储区坐标附近是否真的有方块（客户端用它判断落点是否贴着结构表面）。 */
    private static boolean nearStructureBlock(Level level, Vec3 local) {
        BlockPos center = BlockPos.containing(local);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (!level.getBlockState(center.offset(dx, dy, dz)).isAir()) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    static GunnerSightImpact.Mark resolve(Minecraft minecraft, GunnerSightImpact.Mark mark,
                                          float partialTick) {
        if (mark.subLevelId() == null || mark.localPoint() == null
                || mark.localNormal() == null || mark.localDirection() == null
                || minecraft.level == null) {
            return mark;
        }
        SubLevelContainer container = SubLevelContainer.getContainer(minecraft.level);
        if (container == null) {
            return mark;
        }
        SubLevel sub = container.getSubLevel(mark.subLevelId());
        if (sub == null) {
            return mark;
        }
        Pose3d pose = sub.lastPose().lerp(sub.logicalPose(), partialTick, new Pose3d());
        Vec3 point = pose.transformPosition(mark.localPoint());
        Vec3 normal = pose.transformNormal(mark.localNormal()).normalize();
        Vec3 direction = pose.transformNormal(mark.localDirection()).normalize();
        return new GunnerSightImpact.Mark(point, normal, direction, mark.flightTicks(),
                null, null, null, null);
    }
}
