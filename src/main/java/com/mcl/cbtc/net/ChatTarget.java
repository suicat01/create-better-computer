package com.mcl.cbtc.net;

import java.util.List;

/**
 * 聊天发信目标解析：把积木里填的目标字符串（{@code @a}、{@code @玩家名}、{@code @PC:电脑id} 等）
 * 解析成"发给谁"。
 */
public final class ChatTarget {
    /** 收件方类别。 */
    public enum TargetKind {
        /** 全体玩家的聊天框。 */
        PLAYERS,
        /** 某个玩家的聊天框。 */
        PLAYER,
        /** 全体玩家 + 全体电脑。 */
        ALL,
        /** 某台电脑。 */
        COMPUTER
    }

    /** 已解析的目标。 */
    public record Target(TargetKind kind, String name) {
    }

    public static final String ALL_PLAYERS = "@a";
    public static final String EVERYONE = "@e";
    public static final String COMPUTER_PREFIX = "@PC:";

    private ChatTarget() {
    }

    /**
     * 解析目标字符串。留空按全体玩家处理；
     * {@code @a} 全体玩家、{@code @e} 全体玩家和电脑、{@code @PC:名字} 指定电脑、{@code @名字} 指定玩家。
     */
    public static Target parse(String raw) {
        String text = raw == null ? "" : raw.trim();
        if (text.isEmpty()) {
            return new Target(TargetKind.PLAYERS, "");
        }
        String lower = text.toLowerCase(java.util.Locale.ROOT);
        if (lower.equals(ALL_PLAYERS)) {
            return new Target(TargetKind.PLAYERS, "");
        }
        if (lower.equals(EVERYONE)) {
            return new Target(TargetKind.ALL, "");
        }
        if (lower.startsWith(COMPUTER_PREFIX.toLowerCase(java.util.Locale.ROOT))) {
            return new Target(TargetKind.COMPUTER, text.substring(COMPUTER_PREFIX.length()).trim());
        }
        if (text.startsWith("@")) {
            return new Target(TargetKind.PLAYER, text.substring(1).trim());
        }
        // 不带 @ 时按玩家名处理，避免玩家手滑漏写前缀就发不出去
        return new Target(TargetKind.PLAYER, text);
    }

    /** 输入框里的候选提示，按顺序展示给玩家。 */
    public static List<String> suggestions() {
        return List.of(ALL_PLAYERS, EVERYONE, "@玩家名", "@PC:电脑id");
    }
}
