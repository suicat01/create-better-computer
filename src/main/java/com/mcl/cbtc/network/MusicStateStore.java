package com.mcl.cbtc.network;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 服务端的网络歌曲播放状态表。
 * <p>
 * 歌曲与歌词都只在客户端存在，客户端会定时把状态回传到这里，
 * "读取歌曲信息"类积木从本表取值。
 * <p>
 * 一个玩家可能同时听到多台设备放的歌（例如自家电脑与邻居的音响），
 * 所以每个玩家维护一张"声源坐标 → 状态"的表。
 * 键统一用纯坐标串（{@code x,y,z}），因为客户端上报时只带得上坐标。
 */
public final class MusicStateStore {
    /** 每个玩家最近上报的各声源状态。 */
    private static final Map<UUID, Map<String, MusicStatePayload>> STATES = new ConcurrentHashMap<>();

    private MusicStateStore() {
    }

    /** 记录指定玩家的某个声源状态；{@code elapsed < 0} 表示停止，直接清掉。 */
    public static void accept(@org.jetbrains.annotations.Nullable UUID player, MusicStatePayload payload) {
        if (player == null || payload == null) {
            return;
        }
        Map<String, MusicStatePayload> bySource = STATES.computeIfAbsent(player,
                key -> new ConcurrentHashMap<>());
        if (payload.elapsed() < 0) {
            bySource.remove(payload.source());
            if (bySource.isEmpty()) {
                STATES.remove(player);
            }
        } else {
            bySource.put(payload.source(), payload);
        }
    }

    /** 该玩家在该声源上正在播放的状态；没有时返回 null。 */
    public static MusicStatePayload state(@org.jetbrains.annotations.Nullable UUID player, String source) {
        if (player == null || source == null) {
            return null;
        }
        Map<String, MusicStatePayload> bySource = STATES.get(player);
        return bySource == null ? null : bySource.get(source);
    }

    /** 玩家下线时清掉他的状态，避免内存泄漏。 */
    public static void forget(@org.jetbrains.annotations.Nullable UUID player) {
        if (player != null) {
            STATES.remove(player);
        }
    }

    /** 按声源坐标清除所有玩家在该处的状态。 */
    public static void forgetSource(net.minecraft.core.BlockPos pos) {
        if (pos == null) {
            return;
        }
        forgetSourceKey(sourceKey(pos));
    }

    /** 按已拼好的声源串清除状态。 */
    public static void forgetSourceKey(String key) {
        if (key == null || key.isEmpty()) {
            return;
        }
        for (Map.Entry<UUID, Map<String, MusicStatePayload>> entry : STATES.entrySet()) {
            entry.getValue().remove(key);
            if (entry.getValue().isEmpty()) {
                STATES.remove(entry.getKey());
            }
        }
    }

    /** 声源坐标串；客户端上报、服务端查询用的都是这同一个格式。 */
    public static String sourceKey(net.minecraft.core.BlockPos pos) {
        return pos.getX() + "," + pos.getY() + "," + pos.getZ();
    }
}
