package com.mcl.cbtc.core;

/**
 * 端口类型：决定一个输出口能不能接到某个输入口上。
 */
public enum PortKind {
    /** 执行流（顺序控制） */
    FLOW,
    /** 数值 */
    NUMBER,
    /** 布尔 */
    BOOL,
    /** 文本 */
    TEXT,
    /** 固定角度（-180~180 的圆周角度），只有角度类积木能产出，不当作普通数字使用 */
    ANGLE,
    /** 时间点（含游戏时间与现实时间两个分量），只有时间类积木能产出 */
    TIME,
    /** 数列（一串数值），显示成"数，数，数" */
    SEQ,
    /** 任意值，赋值时会做隐式转换 */
    ANY;

    public boolean accepts(PortKind other) {
        if (this == ANY || other == ANY) {
            return true;
        }
        // 角度槽只收角度，避免和普通数字混淆
        if (this == ANGLE) {
            return other == ANGLE;
        }
        // 角度输出可以当数值/文本用，能接进数值、文本、数列槽
        if (other == ANGLE) {
            return this == NUMBER || this == TEXT || this == SEQ;
        }
        // 时间槽只收时间；时间输出可以接进文本槽（显示成带单位的文本）
        if (this == TIME) {
            return other == TIME;
        }
        if (other == TIME) {
            return this == TEXT;
        }
        // 数列槽只收数列；数列的输出也能接进文本槽（显示成"数，数，数"）
        // 以及数值槽（比较时逐项判断，有一项满足即成立）
        if (this == SEQ || other == SEQ) {
            return this == SEQ ? other == SEQ : this == TEXT || this == NUMBER;
        }
        return this == other || (this == TEXT && other == NUMBER);
    }
}
