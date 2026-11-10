package com.mcl.cbtc.signal;

import com.mcl.cbtc.core.Value;

/**
 * 广播频道 id。格式为 {@code 命名空间:名称}，允许纯名称（补默认命名空间）。
 */
public record ChannelId(String namespace, String path) {
    public static final String DEFAULT_NAMESPACE = "cbtc";

    public static ChannelId parse(String raw) {
        String s = raw == null ? "" : raw.trim();
        int colon = s.indexOf(':');
        if (colon < 0) {
            return new ChannelId(DEFAULT_NAMESPACE, normalize(s));
        }
        return new ChannelId(normalize(s.substring(0, colon)), normalize(s.substring(colon + 1)));
    }

    private static String normalize(String s) {
        StringBuilder sb = new StringBuilder();
        for (char c : s.toLowerCase().toCharArray()) {
            if (Character.isLetterOrDigit(c) || c == '_' || c == '-' || c == '.') {
                sb.append(c);
            }
        }
        return sb.isEmpty() ? "unnamed" : sb.toString();
    }

    @Override
    public String toString() {
        return namespace + ":" + path;
    }

    /**
     * 一次广播内容：频道 + 值 + 是否来自计算机组内部。
     */
    public record Message(ChannelId channel, Value value, boolean groupLocal) {
    }
}
