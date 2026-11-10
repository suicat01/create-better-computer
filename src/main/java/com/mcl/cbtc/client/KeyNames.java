package com.mcl.cbtc.client;

import java.util.LinkedHashMap;
import java.util.Map;
import org.lwjgl.glfw.GLFW;

/**
 * GLFW 键码 ↔ 中文键名。
 * <p>
 * 积木里存的是 GLFW 键码，界面显示中文名；航空学打字机的按键也是 GLFW 键码，两边能直接对上。
 */
public final class KeyNames {
    private static final Map<Integer, String> NAMES = new LinkedHashMap<>();

    static {
        for (char c = 'A'; c <= 'Z'; c++) {
            NAMES.put(GLFW.GLFW_KEY_A + (c - 'A'), String.valueOf(c));
        }
        for (int i = 0; i <= 9; i++) {
            NAMES.put(GLFW.GLFW_KEY_0 + i, String.valueOf(i));
        }
        NAMES.put(GLFW.GLFW_KEY_SPACE, "空格");
        NAMES.put(GLFW.GLFW_KEY_ENTER, "回车");
        NAMES.put(GLFW.GLFW_KEY_KP_ENTER, "小键盘回车");
        NAMES.put(GLFW.GLFW_KEY_ESCAPE, "Esc");
        NAMES.put(GLFW.GLFW_KEY_TAB, "Tab");
        NAMES.put(GLFW.GLFW_KEY_BACKSPACE, "退格");
        NAMES.put(GLFW.GLFW_KEY_INSERT, "插入");
        NAMES.put(GLFW.GLFW_KEY_DELETE, "删除");
        NAMES.put(GLFW.GLFW_KEY_RIGHT, "右方向键");
        NAMES.put(GLFW.GLFW_KEY_LEFT, "左方向键");
        NAMES.put(GLFW.GLFW_KEY_DOWN, "下方向键");
        NAMES.put(GLFW.GLFW_KEY_UP, "上方向键");
        NAMES.put(GLFW.GLFW_KEY_PAGE_UP, "Page Up");
        NAMES.put(GLFW.GLFW_KEY_PAGE_DOWN, "Page Down");
        NAMES.put(GLFW.GLFW_KEY_HOME, "Home");
        NAMES.put(GLFW.GLFW_KEY_END, "End");
        NAMES.put(GLFW.GLFW_KEY_CAPS_LOCK, "大写锁定");
        NAMES.put(GLFW.GLFW_KEY_SCROLL_LOCK, "滚动锁定");
        NAMES.put(GLFW.GLFW_KEY_NUM_LOCK, "数字锁定");
        NAMES.put(GLFW.GLFW_KEY_PRINT_SCREEN, "截屏");
        NAMES.put(GLFW.GLFW_KEY_PAUSE, "暂停");
        NAMES.put(GLFW.GLFW_KEY_LEFT_SHIFT, "左Shift");
        NAMES.put(GLFW.GLFW_KEY_RIGHT_SHIFT, "右Shift");
        NAMES.put(GLFW.GLFW_KEY_LEFT_CONTROL, "左Ctrl");
        NAMES.put(GLFW.GLFW_KEY_RIGHT_CONTROL, "右Ctrl");
        NAMES.put(GLFW.GLFW_KEY_LEFT_ALT, "左Alt");
        NAMES.put(GLFW.GLFW_KEY_RIGHT_ALT, "右Alt");
        NAMES.put(GLFW.GLFW_KEY_LEFT_SUPER, "左Win");
        NAMES.put(GLFW.GLFW_KEY_RIGHT_SUPER, "右Win");
        NAMES.put(GLFW.GLFW_KEY_MENU, "菜单键");
        for (int i = 1; i <= 25; i++) {
            NAMES.put(GLFW.GLFW_KEY_F1 + (i - 1), "F" + i);
        }
        for (int i = 0; i <= 9; i++) {
            NAMES.put(GLFW.GLFW_KEY_KP_0 + i, "小键盘" + i);
        }
        NAMES.put(GLFW.GLFW_KEY_KP_ADD, "小键盘+");
        NAMES.put(GLFW.GLFW_KEY_KP_SUBTRACT, "小键盘-");
        NAMES.put(GLFW.GLFW_KEY_KP_MULTIPLY, "小键盘*");
        NAMES.put(GLFW.GLFW_KEY_KP_DIVIDE, "小键盘/");
        NAMES.put(GLFW.GLFW_KEY_KP_DECIMAL, "小键盘.");
        NAMES.put(GLFW.GLFW_KEY_KP_EQUAL, "小键盘=");
        NAMES.put(GLFW.GLFW_KEY_MINUS, "-");
        NAMES.put(GLFW.GLFW_KEY_EQUAL, "=");
        NAMES.put(GLFW.GLFW_KEY_LEFT_BRACKET, "[");
        NAMES.put(GLFW.GLFW_KEY_RIGHT_BRACKET, "]");
        NAMES.put(GLFW.GLFW_KEY_BACKSLASH, "\\");
        NAMES.put(GLFW.GLFW_KEY_SEMICOLON, ";");
        NAMES.put(GLFW.GLFW_KEY_APOSTROPHE, "'");
        NAMES.put(GLFW.GLFW_KEY_GRAVE_ACCENT, "`");
        NAMES.put(GLFW.GLFW_KEY_COMMA, ",");
        NAMES.put(GLFW.GLFW_KEY_PERIOD, ".");
        NAMES.put(GLFW.GLFW_KEY_SLASH, "/");
    }

    private KeyNames() {
    }

    /** 键码 → 中文名；不认识的键回退成 "键{码}"。 */
    public static String display(int keyCode) {
        return NAMES.getOrDefault(keyCode, "键" + keyCode);
    }

    /** 全部可选按键，按登记顺序。 */
    public static java.util.List<Integer> all() {
        return new java.util.ArrayList<>(NAMES.keySet());
    }
}
