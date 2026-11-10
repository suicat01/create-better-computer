package com.mcl.cbtc.core;

/**
 * 节点在材料区里的分类。
 */
public enum NodeCategory {
    EVENT("event"),
    CONTROL("control"),
    DATA("data"),
    MATH("math"),
    REDSTONE("redstone"),
    BROADCAST("broadcast"),
    LAN("lan"),
    INTERNET("internet"),
    DISPLAY("display"),
    AERO("aero"),
    SOUND("sound");

    public final String key;

    NodeCategory(String key) {
        this.key = key;
    }

    public String translationKey() {
        return "cbtc.category." + key;
    }
}
