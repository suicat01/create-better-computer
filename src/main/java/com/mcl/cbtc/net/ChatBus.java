package com.mcl.cbtc.net;

import com.mcl.cbtc.CreateBetterComputer;
import java.util.List;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.ServerChatEvent;

/**
 * 聊天框监听：把玩家在聊天框发出的每句话转成"互联网聊天消息"派发给所有接入互联网的电脑。
 * <p>
 * 玩家自己说的内容按 @all 处理，发给全体电脑；电脑之间用 {@link #computerMessage} 互相点名。
 */
@EventBusSubscriber(modid = CreateBetterComputer.MODID)
public final class ChatBus {
    /** 一次聊天事件。target 为 ALL 表示全体电脑，PLAYERS 表示全体玩家，PLAYER 表示某个玩家。 */
    public record ChatEvent(String text, ChatTarget.TargetKind kind, String target) {
        /** 是否发给电脑（全体电脑、全体玩家和电脑、或点名某台电脑）。 */
        public boolean reachesComputers() {
            return kind == ChatTarget.TargetKind.ALL || kind == ChatTarget.TargetKind.COMPUTER;
        }

        /** 是否发给玩家（全体玩家或某个玩家）。 */
        public boolean reachesPlayers() {
            return kind == ChatTarget.TargetKind.ALL || kind == ChatTarget.TargetKind.PLAYERS
                    || kind == ChatTarget.TargetKind.PLAYER;
        }

        /**
         * 这条消息是否指定了某台电脑。
         * ALL 表示全体电脑都算，COMPUTER 只有被点名的那台算。
         */
        public boolean reachesComputer(String computerId) {
            if (kind == ChatTarget.TargetKind.ALL) {
                return true;
            }
            return kind == ChatTarget.TargetKind.COMPUTER && !target.isBlank()
                    && target.equalsIgnoreCase(computerId);
        }
    }

    public interface Listener {
        void onChat(ChatEvent event);
        /** 是否还挂着互联网模块；拔卡后总线自动踢出。 */
        boolean internetConnected();
    }

    /** 成员用强引用：电脑跨维度时区块会卸载重建，靠缓存里的重新加入来维持。 */
    private static final List<Listener> MEMBERS = new java.util.ArrayList<>();
    private static final List<ChatEvent> PENDING = new java.util.ArrayList<>();

    private ChatBus() {
    }

    public static void join(Listener listener) {
        if (!MEMBERS.contains(listener)) {
            MEMBERS.add(listener);
        }
    }

    public static void leave(Listener listener) {
        MEMBERS.remove(listener);
    }

    /** 玩家在聊天框说话：直接派发给接入互联网的电脑，下一刻即可被条件积木读取。 */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onServerChat(ServerChatEvent event) {
        if (event.isCanceled()) {
            return;
        }
        ChatEvent chat = new ChatEvent(event.getRawText(), ChatTarget.TargetKind.ALL, "");
        MEMBERS.removeIf(listener -> !listener.internetConnected());
        for (Listener listener : List.copyOf(MEMBERS)) {
            if (listener instanceof com.mcl.cbtc.blockentity.CBComputerBlockEntity computer) {
                computer.onPlayerChat(event.getUsername(), chat.text());
            } else {
                listener.onChat(chat);
            }
        }
    }

    /** 电脑之间互发：target 决定收件方。 */
    public static void computerMessage(String text, ChatTarget.TargetKind kind, String target) {
        dispatch(new ChatEvent(text == null ? "" : text, kind, target == null ? "" : target));
    }

    /** 投递一条聊天消息，等服务器 tick 末尾统一派发。 */
    public static void dispatch(ChatEvent event) {
        if (PENDING.size() < 256) {
            PENDING.add(event);
        }
    }

    public static void flush() {
        MEMBERS.removeIf(listener -> !listener.internetConnected());
        if (PENDING.isEmpty()) {
            return;
        }
        List<ChatEvent> batch = new java.util.ArrayList<>(PENDING);
        PENDING.clear();
        for (ChatEvent event : batch) {
            for (Listener listener : List.copyOf(MEMBERS)) {
                listener.onChat(event);
            }
        }
    }

    public static void reset(MinecraftServer server) {
        MEMBERS.clear();
        PENDING.clear();
        InternetNet.reset();
    }
}
