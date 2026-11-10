package com.mcl.cbtc.client;

import net.minecraft.client.gui.GuiGraphics;

/**
 * 积木形状绘制工具。
 * <p>
 * 编程猫风格的积木：圆角矩形 + 上沿缺口 + 下沿凸起。这里用矩形拼出近似效果，
 * 不依赖纹理，保证在没有额外资源时也能画出来。
 */
public final class BlockShapes {
    private BlockShapes() {
    }

    /** 圆角矩形。 */
    public static void roundedRect(GuiGraphics g, int x, int y, int w, int h, int radius, int color) {
        int r = Math.min(radius, Math.min(w, h) / 2);
        g.fill(x + r, y, x + w - r, y + h, color);
        g.fill(x, y + r, x + w, y + h - r, color);
        // 四角用小块近似
        for (int i = 0; i < r; i++) {
            int inset = r - (int) Math.ceil(Math.sqrt(Math.max(0, r * r - (r - 1 - i) * (r - 1 - i))));
            g.fill(x + inset, y + i, x + w - inset, y + i + 1, color);
            g.fill(x + inset, y + h - 1 - i, x + w - inset, y + h - i, color);
        }
    }

    /** 直角矩形描边。 */
    public static void squareOutline(GuiGraphics g, int x, int y, int w, int h, int color) {
        g.fill(x, y, x + w, y + 1, color);
        g.fill(x, y + h - 1, x + w, y + h, color);
        g.fill(x, y, x + 1, y + h, color);
        g.fill(x + w - 1, y, x + w, y + h, color);
    }

    /**
     * 语句积木主体。凹口是轮廓本身没有填充的区域，不会用背景色覆盖其他内容。
     * 底部凸起使用 {@link #stackTab}，两者共用同一个逐行轮廓。
     */
    public static void stackBlock(GuiGraphics g, int x, int y, int w, int h, boolean notch,
                                  int notchX, int notchW, int notchH, int radius, int color) {
        for (int row = 0; row < h; row++) {
            int outer = cornerInset(row, h, radius);
            int left = x + outer;
            int right = x + w - outer;
            if (notch && row < notchH) {
                int inset = jointInset(row, notchW, notchH);
                int notchLeft = x + notchX + inset;
                int notchRight = x + notchX + notchW - inset;
                if (notchLeft > left) {
                    g.fill(left, y + row, notchLeft, y + row + 1, color);
                }
                if (right > notchRight) {
                    g.fill(notchRight, y + row, right, y + row + 1, color);
                }
            } else if (right > left) {
                g.fill(left, y + row, right, y + row + 1, color);
            }
        }
    }

    /** 与积木顶部凹口完全一致的底部凸起。 */
    public static void stackTab(GuiGraphics g, int x, int y, int w, int h, int color) {
        for (int row = 0; row < h; row++) {
            int inset = jointInset(row, w, h);
            g.fill(x + inset, y + row, x + w - inset, y + row + 1, color);
        }
    }

    private static int cornerInset(int row, int height, int radius) {
        int r = Math.max(0, Math.min(radius, height / 2));
        if (r == 0) {
            return 0;
        }
        int edgeRow = row < r ? row : height - 1 - row;
        if (edgeRow >= r) {
            return 0;
        }
        double dy = r - edgeRow - 0.5;
        return r - (int) Math.floor(Math.sqrt(Math.max(0, r * r - dy * dy)));
    }

    /** 经典拼图接头：平直根部、收窄的颈部和圆缓的末端。 */
    private static int jointInset(int row, int width, int height) {
        int shoulder = Math.max(1, Math.round(width / 6.0F));
        if (row < Math.max(1, height * 2 / 5)) {
            return 0;
        }
        return row >= height - 1 ? shoulder + 1 : shoulder;
    }

    /** 下拉小三角，表示这一块有可选参数。 */
    public static void caretDown(GuiGraphics g, int x, int y, int color) {
        g.fill(x, y, x + 5, y + 1, color);
        g.fill(x + 1, y + 1, x + 4, y + 2, color);
        g.fill(x + 2, y + 2, x + 3, y + 3, color);
    }

    /** 圆头接头。凹口向下挖、凸起向下伸；尺寸相同才能严丝合缝地咬合。 */
    public static void joint(GuiGraphics g, int x, int y, int w, int h, boolean notch, int color) {
        int radius = Math.max(2, Math.min(w / 3, h - 1));
        for (int row = 0; row < h; row++) {
            int inset;
            if (row < radius) {
                double dy = radius - row - 0.5;
                inset = radius - (int) Math.floor(Math.sqrt(Math.max(0, radius * radius - dy * dy)));
            } else {
                inset = 0;
            }
            int yy = notch ? y + row : y + h - 1 - row;
            g.fill(x + inset, yy, x + w - inset, yy + 1, color);
        }
    }

    /**
     * 两头半圆的胶囊形色块，用来画积木的咬合缺口/凸起。
     * 半径为高度的一半，边缘按圆形逐行缩进，视觉上与圆角积木一致。
     */
    public static void roundHole(GuiGraphics g, int x, int y, int w, int h, int color) {
        int r = Math.max(1, h / 2);
        for (int row = 0; row < h; row++) {
            int dy = row - h / 2;
            int inset = 0;
            if (Math.abs(dy) >= h / 2 - r) {
                double norm = (Math.abs(dy) - (h / 2 - r)) / (double) r;
                inset = (int) Math.round(r - Math.sqrt(Math.max(0, 1 - norm * norm)) * r);
            }
            g.fill(x + inset, y + row, x + w - inset, y + row + 1, color);
        }
    }

    /** 内嵌参数框（白色底、深色直角描边）。 */
    public static void pill(GuiGraphics g, int x, int y, int w, int h, int fill, int border) {
        g.fill(x, y, x + w, y + h, border);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, fill);
    }
}
