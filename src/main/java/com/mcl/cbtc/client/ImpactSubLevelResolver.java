package com.mcl.cbtc.client;

import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.BoundingBox3ic;
import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.plot.LevelPlot;
import net.minecraft.client.Minecraft;
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
        for (SubLevel sub : container.getAllSubLevels()) {
            Vec3 local = sub.logicalPose().transformPositionInverse(point);
            LevelPlot plot = sub.getPlot();
            if (plot == null || !plot.contains(local)) {
                continue;
            }
            // 地块只按 X/Z 判定，再用高度范围排除"结构正上方/正下方"的落点
            BoundingBox3ic bounds = plot.getBoundingBox();
            if (bounds != null
                    && (local.y < bounds.minY() - 2.0D || local.y > bounds.maxY() + 2.0D)) {
                continue;
            }
            Pose3dc pose = sub.logicalPose();
            return new GunnerSightImpact.Mark(point, normal, direction, ticks, sub.getUniqueId(),
                    local, pose.transformNormalInverse(normal), pose.transformNormalInverse(direction));
        }
        return world;
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
