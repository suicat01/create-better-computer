package com.mcl.cbtc.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/**
 * 预测落点的世界渲染。
 * <p>
 * 在落点所在平面画一个琥珀色圆环，并沿炮弹入射方向插一条直线表示落角；
 * 地面与墙体都用同一套「法线决定平面」的逻辑。落点落在航空学物理结构上时，
 * 先按结构姿态把局部坐标插值到主世界，标记会随结构一起移动、倾斜。
 */
public final class ImpactMarkerRenderer {
    private static final float RED = 1.0F;
    private static final float GREEN = 0.69F;
    private static final float BLUE = 0.13F;
    private static final float ALPHA = 1.0F;
    /** 外圈半径（格）。 */
    private static final double RADIUS = 2.0D;
    /** 内圈半径（格）。 */
    private static final double INNER_RADIUS = 0.5D;
    /** 圆环分段数。 */
    private static final int SEGMENTS = 48;
    /** 角度线长度（格）。 */
    private static final double ANGLE_LINE = 8.0D;
    /** 贴面偏移，避免圆环与方块表面深度冲突。 */
    private static final double SURFACE_OFFSET = 0.05D;

    private ImpactMarkerRenderer() {
    }

    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null || minecraft.options.hideGui) {
            return;
        }
        GunnerSightImpact.Mark mark = GunnerSightImpact.active(minecraft);
        if (mark == null || !GunnerSightOverlay.isWearing(minecraft.player)) {
            return;
        }
        float partialTick = event.getPartialTick().getGameTimeDeltaPartialTick(true);
        if (mark.subLevelId() != null && ModList.get().isLoaded("sable")) {
            try {
                mark = ImpactSubLevelResolver.resolve(minecraft, mark, partialTick);
            } catch (Throwable ignored) {
                // 结构姿态插值失败时保持世界坐标，标记仍然可画
            }
        }
        Vec3 normal = safeNormal(mark.normal());
        Vec3 direction = safeNormal(mark.direction());
        Vec3 center = mark.point().add(normal.scale(SURFACE_OFFSET));
        PoseStack poseStack = event.getPoseStack();
        if (poseStack == null) {
            return;
        }
        Vec3 camera = event.getCamera().getPosition();
        poseStack.pushPose();
        poseStack.translate(-camera.x, -camera.y, -camera.z);
        PoseStack.Pose pose = poseStack.last();
        MultiBufferSource.BufferSource buffer = minecraft.renderBuffers().bufferSource();
        VertexConsumer lines = buffer.getBuffer(RenderType.lines());
        drawRing(lines, pose, center, normal, RADIUS);
        // 外圈画两条贴得很近的线，避免 1px 线在远处太细
        drawRing(lines, pose, center, normal, RADIUS - 0.06D);
        drawRing(lines, pose, center, normal, INNER_RADIUS);
        drawCross(lines, pose, center, normal);
        drawAngleLine(lines, pose, center, direction);
        buffer.endBatch(RenderType.lines());
        poseStack.popPose();
    }

    private static Vec3 safeNormal(Vec3 vector) {
        return vector.lengthSqr() < 1.0E-8D ? new Vec3(0.0D, 1.0D, 0.0D) : vector.normalize();
    }

    /** 在法线决定的平面上画一个圆环。 */
    private static void drawRing(VertexConsumer lines, PoseStack.Pose pose, Vec3 center,
                                 Vec3 normal, double radius) {
        Vec3 axis = Math.abs(normal.y) > 0.9D ? new Vec3(1.0D, 0.0D, 0.0D)
                : new Vec3(0.0D, 1.0D, 0.0D);
        Vec3 tangent = normal.cross(axis).normalize();
        Vec3 bitangent = normal.cross(tangent).normalize();
        Vec3 previous = center.add(tangent.scale(radius));
        for (int i = 1; i <= SEGMENTS; i++) {
            double angle = Math.PI * 2.0D * i / SEGMENTS;
            Vec3 current = center.add(tangent.scale(Math.cos(angle) * radius))
                    .add(bitangent.scale(Math.sin(angle) * radius));
            line(lines, pose, previous, current);
            previous = current;
        }
    }

    /** 圆心的十字，方便远处也能看清落点中心。 */
    private static void drawCross(VertexConsumer lines, PoseStack.Pose pose, Vec3 center, Vec3 normal) {
        Vec3 axis = Math.abs(normal.y) > 0.9D ? new Vec3(1.0D, 0.0D, 0.0D)
                : new Vec3(0.0D, 1.0D, 0.0D);
        Vec3 tangent = normal.cross(axis).normalize();
        Vec3 bitangent = normal.cross(tangent).normalize();
        line(lines, pose, center.subtract(tangent.scale(RADIUS * 0.35D)),
                center.add(tangent.scale(RADIUS * 0.35D)));
        line(lines, pose, center.subtract(bitangent.scale(RADIUS * 0.35D)),
                center.add(bitangent.scale(RADIUS * 0.35D)));
    }

    /** 沿炮弹入射方向插一条直线，反向延长到空中，直观表示落角。 */
    private static void drawAngleLine(VertexConsumer lines, PoseStack.Pose pose, Vec3 center,
                                      Vec3 direction) {
        line(lines, pose, center.subtract(direction.scale(ANGLE_LINE)),
                center.add(direction.scale(1.5D)));
    }

    private static void line(VertexConsumer lines, PoseStack.Pose pose, Vec3 from, Vec3 to) {
        Vec3 direction = to.subtract(from);
        if (direction.lengthSqr() < 1.0E-8D) {
            return;
        }
        direction = direction.normalize();
        float nx = (float) direction.x;
        float ny = (float) direction.y;
        float nz = (float) direction.z;
        lines.addVertex(pose, (float) from.x, (float) from.y, (float) from.z)
                .setColor(RED, GREEN, BLUE, ALPHA)
                .setNormal(pose, nx, ny, nz);
        lines.addVertex(pose, (float) to.x, (float) to.y, (float) to.z)
                .setColor(RED, GREEN, BLUE, ALPHA)
                .setNormal(pose, nx, ny, nz);
    }
}
