package com.mcl.cbtc.core;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 带单位的数值文本解析。
 * <p>
 * 支持"标签 + 数字 + 单位"的写法，例如 {@code 转速 120 RPM}、{@code 1,234}、{@code 90°}、{@code 50%}：
 * 取中间的数字，剩下的尾巴原样留作单位，用于显示与运算传播。
 */
public final class Amounts {
    /**
     * 数字前允许任意非数字标签，数字后允许任意非数字单位，但单位里不能再出现数字，
     * 这样 {@code 12/34}、{@code 3:25} 这类"多个数字"的文本不会被误读成第一个数。
     */
    private static final Pattern PATTERN =
            Pattern.compile("^[^0-9+\\-]*([+\\-]?[0-9][0-9,]*(?:\\.[0-9]+)?)\\s*([^0-9]*)$");

    private Amounts() {
    }

    /** 解析结果：数值 + 单位后缀（没有单位时为空串）。 */
    public record Parsed(double value, String unit) {
    }

    /** 解析失败返回 null。 */
    public static Parsed parse(String raw) {
        if (raw == null) {
            return null;
        }
        String text = raw.trim();
        if (text.isEmpty()) {
            return null;
        }
        Matcher matcher = PATTERN.matcher(text);
        if (!matcher.matches()) {
            return null;
        }
        try {
            return new Parsed(Double.parseDouble(matcher.group(1).replace(",", "")),
                    matcher.group(2).trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** 解析成数值；失败按 0，单位为空。 */
    public static Value.Num num(String raw) {
        Parsed parsed = parse(raw);
        return parsed == null ? new Value.Num(0) : new Value.Num(parsed.value(), false, parsed.unit());
    }
}
