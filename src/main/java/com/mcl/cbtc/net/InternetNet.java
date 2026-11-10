package com.mcl.cbtc.net;

import com.mcl.cbtc.core.Value;
import java.util.HashMap;
import java.util.Map;

/**
 * 互联网的共享广播变量：全服一张表，所有接入互联网的电脑按名字读写同一份数据。
 */
public final class InternetNet {
    private static final Map<String, Value> VARIABLES = new HashMap<>();

    private InternetNet() {
    }

    public static Value variable(String name) {
        return VARIABLES.getOrDefault(name, Value.ZERO);
    }

    public static void setVariable(String name, Value value) {
        VARIABLES.put(name, value);
    }

    public static void reset() {
        VARIABLES.clear();
    }
}
