package com.mcl.cbtc.lan;

import com.mcl.cbtc.core.Value;
import com.mcl.cbtc.signal.ChannelId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.server.MinecraftServer;

/**
 * 局域网运行时（服务端）。
 * <p>
 * 一个局域网由 ID 唯一标识，跨维度存在；电脑装入局域网模块卡后加入对应局域网。
 * 广播与广播变量都是"每 tick 有效"或"整网共享"的运行时状态，不写入存档。
 */
public final class LanBus {
    /** 收广播的一方：一台电脑。 */
    public interface Listener {
        void onLanBroadcast(LanMessage message);
    }

    private static final Map<String, LanNetwork> NETWORKS = new HashMap<>();

    private LanBus() {
    }

    /** 取（必要时创建）一个局域网；密码校验由调用方负责。 */
    public static LanNetwork network(String id) {
        return NETWORKS.computeIfAbsent(id.toLowerCase(java.util.Locale.ROOT), k -> new LanNetwork(k));
    }

    public static void reset(MinecraftServer server) {
        NETWORKS.clear();
    }

    /** 每 tick 由服务器事件调用，派发所有局域网里待处理的广播。 */
    public static void flushAll() {
        for (LanNetwork network : List.copyOf(NETWORKS.values())) {
            network.flush();
        }
    }

    /** 一个局域网：成员、广播队列与共享变量。 */
    public static final class LanNetwork {
        private final String id;
        /**
         * 成员用强引用：电脑跨维度时区块会卸载重建，靠 tick 里的重新加入来维持；
         * 若用弱引用，切换维度途中可能被回收，导致局域网丢成员。
         */
        private final Set<Listener> members = java.util.LinkedHashSet.newLinkedHashSet(8);
        private final Map<String, Value> variables = new HashMap<>();
        private final List<LanMessage> pending = new ArrayList<>();

        private LanNetwork(String id) {
            this.id = id;
        }

        public String id() {
            return id;
        }

        public void join(Listener listener) {
            members.add(listener);
        }

        public void leave(Listener listener) {
            members.remove(listener);
        }

        public void publish(ChannelId.Message message) {
            pending.add(new LanMessage(id, message.channel(), message.value()));
        }

        /** 每 tick 派发一次广播；派发前投递的成员都能收到。 */
        public void flush() {
            if (pending.isEmpty()) {
                return;
            }
            List<LanMessage> batch = new ArrayList<>(pending);
            pending.clear();
            for (LanMessage message : batch) {
                for (Listener listener : List.copyOf(members)) {
                    listener.onLanBroadcast(message);
                }
            }
        }

        public Value variable(String name) {
            return variables.getOrDefault(name, Value.ZERO);
        }

        public void setVariable(String name, Value value) {
            variables.put(name, value);
        }
    }

    /** 一次局域网广播内容。 */
    public record LanMessage(String networkId, ChannelId channel, Value value) {
    }
}
