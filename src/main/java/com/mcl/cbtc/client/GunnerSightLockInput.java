package com.mcl.cbtc.client;

import com.mcl.cbtc.item.GunnerSightItem;
import com.mcl.cbtc.network.GunnerSightLockPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

/**
 * 戴着瞄准组件时用鼠标中键锁定准星基准线对上的生物。
 * <p>
 * 只在没有打开界面时生效，锁定请求交给服务端校验并分配序号。
 * 先按准星射线与碰撞箱精确相交找；隔着玻璃等透明方块时视觉上对齐、
 * 碰撞箱却常常差一点点，再把包围盒放宽一圈重试。
 * 判定全程不做方块射线检测，隔着任何方块都能锁定。
 */
@EventBusSubscriber(modid = "cbtc", value = Dist.CLIENT)
public final class GunnerSightLockInput {
    /** 锁定距离（格）。 */
    private static final double RANGE = 96.0D;
    /** 索敌圆柱的基础半径（格）：近距离不会因为锥角太小而失效。 */
    private static final double ALIGN_MIN_RADIUS = 0.6D;
    /** 索敌圆柱半径随距离增长的比例（约 1.5°/格），远距离放宽便于锁移动目标。 */
    private static final double ALIGN_RADIUS_PER_BLOCK = 0.0262D;
    /** 索敌圆柱半径上限（格）。 */
    private static final double ALIGN_MAX_RADIUS = 3.0D;

    private GunnerSightLockInput() {
    }

    @SubscribeEvent
    public static void onMouse(InputEvent.MouseButton.Pre event) {
        if (event.getButton() != GLFW.GLFW_MOUSE_BUTTON_3 || event.getAction() != GLFW.GLFW_PRESS) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        Player player = minecraft.player;
        if (player == null || minecraft.level == null || minecraft.screen != null) {
            return;
        }
        ItemStack sight = GunnerSightOverlay.wornSight(player);
        if (sight.isEmpty()) {
            return;
        }
        String sightId = GunnerSightItem.id(sight);
        if (sightId.isBlank()) {
            return;
        }
        Entity target = aimEntity(player);
        if (target == null) {
            return;
        }
        PacketDistributor.sendToServer(new GunnerSightLockPayload(sightId, target.getId()));
        event.setCanceled(true);
    }

    /**
     * 准星基准线圆柱内的生物。
     * <p>
     * 先按射线与碰撞箱的精确相交取；没有命中时，以准星基准线为轴做一个圆柱：
     * 半径随距离线性增长（近距离只有 {@link #ALIGN_MIN_RADIUS} 格，远处放宽到
     * {@link #ALIGN_MAX_RADIUS} 格），近距离不会误锁准星旁的生物，远距离也容易
     * 锁住移动目标。多个目标时取最贴近中轴的，偏差相同再取沿视线更近的。
     * 全程不做方块射线检测，隔墙也能锁。
     */
    private static Entity aimEntity(Player player) {
        Vec3 eye = player.getEyePosition(1.0F);
        Vec3 look = player.getViewVector(1.0F);
        Vec3 end = eye.add(look.scale(RANGE));
        java.util.function.Predicate<Entity> filter = entity -> entity != player
                && !entity.isSpectator() && entity.isPickable()
                && entity instanceof net.minecraft.world.entity.LivingEntity;
        // 精确命中优先，保持原有手感
        EntityHitResult hit = net.minecraft.world.entity.projectile.ProjectileUtil.getEntityHitResult(
                player, eye, end, player.getBoundingBox().expandTowards(look.scale(RANGE)).inflate(1.0D),
                filter, RANGE * RANGE);
        if (hit != null) {
            return hit.getEntity();
        }
        // 圆柱索敌：把实体中心投影到基准线上，比较实体包围盒到中轴的距离
        Entity best = null;
        double bestOffset = Double.MAX_VALUE;
        double bestAlong = Double.MAX_VALUE;
        net.minecraft.world.phys.AABB search = new net.minecraft.world.phys.AABB(eye, end)
                .inflate(ALIGN_MAX_RADIUS + 2.0D);
        for (Entity entity : player.level().getEntities(player, search, filter)) {
            net.minecraft.world.phys.AABB box = entity.getBoundingBox();
            // 沿视线的距离：只认准星前方、远小于射程的目标
            double along = box.getCenter().subtract(eye).dot(look);
            if (along <= 0.0D || along > RANGE) {
                continue;
            }
            // 近处收紧、远处放宽的圆柱半径
            double radius = Math.max(ALIGN_MIN_RADIUS,
                    Math.min(ALIGN_MAX_RADIUS, along * ALIGN_RADIUS_PER_BLOCK));
            Vec3 axisPoint = eye.add(look.scale(along));
            double offset = box.distanceToSqr(axisPoint);
            if (offset > radius * radius) {
                continue;
            }
            if (offset < bestOffset - 1.0E-4D
                    || (Math.abs(offset - bestOffset) <= 1.0E-4D && along < bestAlong)) {
                bestOffset = offset;
                bestAlong = along;
                best = entity;
            }
        }
        return best;
    }
}
