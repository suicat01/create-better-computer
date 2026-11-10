package com.mcl.cbtc.signal;

import com.mcl.cbtc.core.Value;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import net.minecraft.server.MinecraftServer;

/**
 * 全局广播总线。
 * <p>
 * 设计取舍：广播是"全局"的，不按维度隔离；每次发送只推送给订阅了该频道的计算机，
 * 没有订阅者的频道直接丢弃，不保留历史。消息在下一次 tick 前消费，避免执行器递归调用。
 */
public final class BroadcastBus {
    public interface Listener {
        void onBroadcast(ChannelId.Message message);
    }

    private static final Map<ChannelId, Set<Listener>> SUBSCRIBERS = new HashMap<>();
    private static final List<ChannelId.Message> PENDING = new ArrayList<>();

    private BroadcastBus() {
    }

    public static void subscribe(ChannelId channel, Listener listener) {
        SUBSCRIBERS.computeIfAbsent(channel, k -> java.util.Collections.newSetFromMap(new WeakHashMap<>()))
                .add(listener);
    }

    public static void unsubscribe(ChannelId channel, Listener listener) {
        Set<Listener> set = SUBSCRIBERS.get(channel);
        if (set == null) {
            return;
        }
        set.remove(listener);
        if (set.isEmpty()) {
            SUBSCRIBERS.remove(channel);
        }
    }

    public static void unsubscribeAll(Listener listener) {
        SUBSCRIBERS.values().forEach(set -> set.remove(listener));
        SUBSCRIBERS.entrySet().removeIf(e -> e.getValue().isEmpty());
    }

    /**
     * 投递一条广播。当前 tick 内不立即派发，等服务器 tick 结束时统一 flush。
     */
    public static void publish(ChannelId channel, Value value, boolean groupLocal) {
        PENDING.add(new ChannelId.Message(channel, value, groupLocal));
    }

    /**
     * 每 tick 由服务器事件调用，派发所有待处理广播。
     */
    public static void flush() {
        if (PENDING.isEmpty()) {
            return;
        }
        List<ChannelId.Message> batch = new ArrayList<>(PENDING);
        PENDING.clear();
        for (ChannelId.Message message : batch) {
            Set<Listener> set = SUBSCRIBERS.get(message.channel());
            if (set == null || set.isEmpty()) {
                continue;
            }
            for (Listener listener : List.copyOf(set)) {
                listener.onBroadcast(message);
            }
        }
    }

    /**
     * 世界卸载时清理，防止残留订阅者。
     */
    public static void reset(MinecraftServer server) {
        PENDING.clear();
        SUBSCRIBERS.clear();
    }

    public static int pendingCount() {
        return PENDING.size();
    }
}
