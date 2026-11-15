package com.mcl.cbtc.client;

import com.mcl.cbtc.network.SightTargetsPayload;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.Util;
import net.minecraft.world.entity.Entity;

/**
 * 客户端保存"被雷达扫描到、要在瞄准组件视野里框住的实体"。
 * <p>
 * 每次收到服务端同步就整表替换；记录只保留 {@link #LIFETIME_MS} 毫秒，
 * 积木停止执行后框会在 2 秒内自然消失。
 */
public final class GunnerSightTargets {
    /** 框的保留时间。 */
    public static final long LIFETIME_MS = 2000L;

    /** 一个目标：客户端会优先跟随活实体，取不到时用服务端快照坐标。 */
    public record Target(int entityId, double x, double y, double z, float height, boolean friendly,
                         String name, float health, long receivedAt) {
    }

    private static ResourceLocation dimension;
    private static List<Target> targets = List.of();

    private GunnerSightTargets() {
    }

    /** 收到一次雷达同步，整表替换。 */
    public static void accept(ResourceLocation dim, List<SightTargetsPayload.Target> list) {
        long now = Util.getMillis();
        List<Target> next = new ArrayList<>(list.size());
        for (SightTargetsPayload.Target target : list) {
            next.add(new Target(target.entityId(), target.x(), target.y(), target.z(),
                    target.height(), target.friendly(), target.name(), target.health(), now));
        }
        dimension = dim;
        targets = next;
    }

    /** 当前仍有效的目标；过期的直接丢弃，活实体的名字与血量实时刷新。 */
    public static List<Target> active(Minecraft minecraft) {
        if (targets.isEmpty() || minecraft.level == null) {
            return List.of();
        }
        if (dimension != null && !dimension.equals(minecraft.level.dimension().location())) {
            return List.of();
        }
        long now = Util.getMillis();
        int selfId = minecraft.player == null ? -1 : minecraft.player.getId();
        List<Target> result = new ArrayList<>(targets.size());
        for (Target target : targets) {
            if (now - target.receivedAt() > LIFETIME_MS) {
                continue;
            }
            // 默认不框装备者自己
            if (selfId >= 0 && target.entityId() == selfId) {
                continue;
            }
            Entity entity = target.entityId() >= 0 ? minecraft.level.getEntity(target.entityId()) : null;
            if (entity == null) {
                result.add(target);
                continue;
            }
            float health = entity instanceof net.minecraft.world.entity.LivingEntity living
                    ? living.getHealth()
                    : target.health();
            result.add(new Target(target.entityId(), entity.getX(), entity.getY(), entity.getZ(),
                    entity.getBbHeight(), target.friendly(), entity.getDisplayName().getString(),
                    health, target.receivedAt()));
        }
        return result;
    }
}
