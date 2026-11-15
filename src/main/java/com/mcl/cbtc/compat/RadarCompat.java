package com.mcl.cbtc.compat;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Create: Radars 软依赖对接层。
 * <p>
 * 只有确认装了 create_radar 才会加载本类，未安装时不会触碰它的任何类型。
 */
public final class RadarCompat {
    private RadarCompat() {
    }

    /** 方块实体是不是网络控制器（Network Controller）。 */
    public static boolean isNetworkController(BlockEntity blockEntity) {
        return blockEntity instanceof com.happysg.radar.block.controller.networkcontroller.NetworkFiltererBlockEntity;
    }

    /** 雷达扫描到的一个实体快照；entityId 为 -1 表示服务端也找不到对应实体（例如舰船目标）。 */
    public record TrackedEntity(int entityId, double x, double y, double z, float height,
                                boolean friendly, String name, float health) {
    }

    /**
     * 取网络控制器当前对外提供的目标。
     * <p>
     * 用控制器自己的 {@code getRadarContacts()}，它已经过控制器的识别过滤、安全区与网络设置，
     * 和雷达显示器上看到的目标是同一份。
     */
    public static List<TrackedEntity> tracked(BlockEntity blockEntity) {
        if (!(blockEntity instanceof com.happysg.radar.block.controller.networkcontroller.NetworkFiltererBlockEntity controller)) {
            return List.of();
        }
        if (!(blockEntity.getLevel() instanceof ServerLevel serverLevel)) {
            return List.of();
        }
        List<TrackedEntity> result = new ArrayList<>();
        for (com.happysg.radar.block.radar.track.RadarTrack track : List.copyOf(controller.getRadarContacts())) {
            Vec3 position = track.getPosition();
            if (position == null) {
                continue;
            }
            int entityId = -1;
            String name = "";
            float health = -1.0F;
            try {
                UUID uuid = UUID.fromString(track.getId());
                Entity entity = serverLevel.getEntity(uuid);
                if (entity != null) {
                    entityId = entity.getId();
                    name = entity.getDisplayName().getString();
                    if (entity instanceof net.minecraft.world.entity.LivingEntity living) {
                        health = living.getHealth();
                    }
                }
            } catch (IllegalArgumentException ignored) {
                // 舰船目标的 id 不是实体 UUID，保留快照坐标即可
            }
            if (name.isBlank() && track.getDisplayName() != null) {
                name = track.getDisplayName();
            }
            result.add(new TrackedEntity(entityId, position.x, position.y, position.z,
                    Math.max(0.5F, track.getEnityHeight()), track.isFriendly(), name, health));
        }
        return result;
    }
}
