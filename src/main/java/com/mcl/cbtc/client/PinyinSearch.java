package com.mcl.cbtc.client;

import java.lang.reflect.Method;

/**
 * 拼音搜索工具：装了 Just Enough Characters 时借用它的匹配器，
 * 输入 "hsf" 或 "hongshifen" 也能匹配「红石粉」；没装时返回 false。
 */
public final class PinyinSearch {
    private static boolean checked;
    private static Method method;

    private PinyinSearch() {
    }

    public static boolean contains(String text, String query) {
        if (!checked) {
            checked = true;
            if (net.neoforged.fml.ModList.get().isLoaded("jecharacters")) {
                try {
                    Class<?> match = Class.forName("me.towdium.jecharacters.utils.Match");
                    for (Method candidate : match.getMethods()) {
                        if ("contains".equals(candidate.getName()) && candidate.getParameterCount() == 2
                                && candidate.getParameterTypes()[0] == String.class) {
                            method = candidate;
                            break;
                        }
                    }
                } catch (Throwable ignored) {
                    method = null;
                }
            }
        }
        if (method == null) {
            return false;
        }
        try {
            return (Boolean) method.invoke(null, text, query);
        } catch (Throwable ignored) {
            method = null;
            return false;
        }
    }
}
