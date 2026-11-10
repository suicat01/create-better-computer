package com.mcl.cbtc.net;

import com.mcl.cbtc.core.Value;
import com.mcl.cbtc.signal.ChannelId;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.minecraft.server.MinecraftServer;

/**
 * 互联网运行时（服务端）。
 * <p>
 * 互联网全服只有一张：装入互联网模块卡的电脑都接在同一张网上，可跨维度互相收发广播、
 * 共享广播变量。与局域网一样，广播与变量都是运行时状态，不写入存档。
 */
public final class InternetBus {
    /** 接入互联网的一方：一台电脑。 */
    public interface Listener {
        void onInternetBroadcast(Message message);
        /** 是否还挂着互联网模块；拔卡后由总线自动踢出，避免成员表越积越多。 */
        boolean internetConnected();
    }

    /** 一次互联网广播。 */
    public record Message(ChannelId channel, Value value) {
    }

    /** 成员用强引用：电脑跨维度时区块会卸载重建，靠缓存里的重新加入来维持。 */
    private static final Set<Listener> MEMBERS = java.util.LinkedHashSet.newLinkedHashSet(8);
    private static final List<Message> PENDING = new ArrayList<>();

    private InternetBus() {
    }

    public static void join(Listener listener) {
        MEMBERS.add(listener);
    }

    public static void leave(Listener listener) {
        MEMBERS.remove(listener);
    }

    public static void publish(ChannelId.Message message) {
        PENDING.add(new Message(message.channel(), message.value()));
    }

    /** 每 tick 由服务器事件调用：派发前先踢掉已经拔卡的电脑。 */
    public static void flush() {
        MEMBERS.removeIf(listener -> !listener.internetConnected());
        if (PENDING.isEmpty()) {
            return;
        }
        List<Message> batch = new ArrayList<>(PENDING);
        PENDING.clear();
        for (Message message : batch) {
            for (Listener listener : List.copyOf(MEMBERS)) {
                listener.onInternetBroadcast(message);
            }
        }
    }

    public static void reset(MinecraftServer server) {
        MEMBERS.clear();
        PENDING.clear();
    }
}
