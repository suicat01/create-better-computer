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
 * 戴着瞄准组件时用鼠标中键锁定准星上的生物。
 * <p>
 * 只在没有打开界面时生效，锁定请求交给服务端校验并分配序号。
 */
@EventBusSubscriber(modid = "cbtc", value = Dist.CLIENT)
public final class GunnerSightLockInput {
    /** 锁定距离（格）。 */
    private static final double RANGE = 32.0D;

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

    /** 准星指向的实体；没有指点到生物时返回 null。 */
    private static Entity aimEntity(Player player) {
        Vec3 eye = player.getEyePosition(1.0F);
        Vec3 look = player.getViewVector(1.0F);
        Vec3 end = eye.add(look.scale(RANGE));
        EntityHitResult hit = net.minecraft.world.entity.projectile.ProjectileUtil.getEntityHitResult(
                player, eye, end, player.getBoundingBox().expandTowards(look.scale(RANGE)).inflate(1.0D),
                entity -> entity != player && !entity.isSpectator() && entity.isPickable()
                        && entity instanceof net.minecraft.world.entity.LivingEntity,
                RANGE * RANGE);
        return hit == null ? null : hit.getEntity();
    }
}
