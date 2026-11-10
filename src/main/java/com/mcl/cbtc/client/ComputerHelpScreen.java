package com.mcl.cbtc.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * CB电脑的使用说明页。右上角问号按钮进入本页；
 * 本页只讲"怎么用"，外设链接放在设备页（切页按钮在右下）。
 */
public class ComputerHelpScreen extends Screen {
    private static final int BAR_BG = 0xFF2A241C;
    private static final int SCREEN_BG = 0xFF15120F;
    private static final int PANEL_BG = 0xFF1F1B15;
    private static final int BRASS = 0xFFB08D3C;
    private static final int BRASS_DARK = 0xFF6B4E1E;
    private static final int BRASS_LIGHT = 0xFFF0C86A;
    private static final int TEXT = 0xFFE8DCC0;
    private static final int TEXT_DIM = 0xFF9A9285;
    private static final int TOP_BAR = 24;

    public enum Page {
        GUIDE,
        DEVICES
    }

    private final Screen parent;
    private final Page page;
    private int scroll;

    private static final String[][] GUIDE_LINES = {
            {"cbtc.help.guide.intro"},
            {"cbtc.help.guide.control"},
            {"cbtc.help.guide.control1"},
            {"cbtc.help.guide.control2"},
            {"cbtc.help.guide.control3"},
            {"cbtc.help.guide.control4"},
            {"cbtc.help.guide.control5"},
            {"cbtc.help.guide.control6"},
            {"cbtc.help.guide.control7"},
            {"cbtc.help.guide.event"},
            {"cbtc.help.guide.event1"},
            {"cbtc.help.guide.ctrl"},
            {"cbtc.help.guide.ctrl1"},
            {"cbtc.help.guide.ctrl2"},
            {"cbtc.help.guide.data"},
            {"cbtc.help.guide.data1"},
            {"cbtc.help.guide.data2"},
            {"cbtc.help.guide.data3"},
            {"cbtc.help.guide.data4"},
            {"cbtc.help.guide.data5"},
            {"cbtc.help.guide.math"},
            {"cbtc.help.guide.math1"},
            {"cbtc.help.guide.math2"},
            {"cbtc.help.guide.math3"},
            {"cbtc.help.guide.math4"},
            {"cbtc.help.guide.math5"},
            {"cbtc.help.guide.seq"},
            {"cbtc.help.guide.seq1"},
            {"cbtc.help.guide.angle"},
            {"cbtc.help.guide.angle1"},
            {"cbtc.help.guide.angle2"},
            {"cbtc.help.guide.broadcast"},
            {"cbtc.help.guide.broadcast1"},
            {"cbtc.help.guide.broadcast2"},
            {"cbtc.help.guide.redstone"},
            {"cbtc.help.guide.redstone1"},
            {"cbtc.help.guide.redstone2"},
            {"cbtc.help.guide.redstone3"},
            {"cbtc.help.guide.lan"},
            {"cbtc.help.guide.lan1"},
            {"cbtc.help.guide.net"},
            {"cbtc.help.guide.net1"},
            {"cbtc.help.guide.net2"},
            {"cbtc.help.guide.display"},
            {"cbtc.help.guide.display1"},
            {"cbtc.help.guide.display2"},
            {"cbtc.help.guide.sound"},
            {"cbtc.help.guide.sound1"},
            {"cbtc.help.guide.sound2"},
            {"cbtc.help.guide.sound3"},
            {"cbtc.help.guide.aero"},
            {"cbtc.help.guide.aero1"},
            {"cbtc.help.guide.aero2"},
            {"cbtc.help.guide.aero3"},
            {"cbtc.help.guide.aero4"},
            {"cbtc.help.guide.aero5"},
            {"cbtc.help.guide.logistics"},
            {"cbtc.help.guide.logistics1"},
            {"cbtc.help.guide.logistics2"},
            {"cbtc.help.guide.expander"},
            {"cbtc.help.guide.expander1"},
            {"cbtc.help.guide.expander2"},
    };

    private static final String[][] DEVICE_LINES = {
            {"cbtc.help.device.intro"},
            {"cbtc.help.device.computer"},
            {"cbtc.help.device.computer1"},
            {"cbtc.help.device.expander"},
            {"cbtc.help.device.expander1"},
            {"cbtc.help.device.expander2"},
            {"cbtc.help.device.types"},
            {"cbtc.help.device.types1"},
            {"cbtc.help.device.types2"},
            {"cbtc.help.device.types3"},
            {"cbtc.help.device.types4"},
            {"cbtc.help.device.types5"},
            {"cbtc.help.device.types6"},
            {"cbtc.help.device.bind"},
            {"cbtc.help.device.bind1"},
            {"cbtc.help.device.bind2"},
            {"cbtc.help.device.bind3"},
            {"cbtc.help.device.modules"},
            {"cbtc.help.device.modules1"},
            {"cbtc.help.device.modules2"},
            {"cbtc.help.device.modules3"},
            {"cbtc.help.device.modules4"},
            {"cbtc.help.device.modules5"},
            {"cbtc.help.device.modules6"},
            {"cbtc.help.device.modules7"},
            {"cbtc.help.device.modules8"},
    };

    public ComputerHelpScreen(Screen parent) {
        this(parent, Page.GUIDE);
    }

    public ComputerHelpScreen(Screen parent, Page page) {
        super(Component.translatable("cbtc.help.title"));
        this.parent = parent;
        this.page = page;
    }

    @Override
    protected void init() {
        // 右上角"返回"是自绘按钮，不用原版控件。
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0
                && mx >= closeX() && mx < closeX() + 52
                && my >= closeY() && my < closeY() + 18) {
            back();
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }

    private void back() {
        if (parent instanceof ComputerScreen computer) {
            computer.reopenFromHelp();
        } else {
            minecraft.setScreen(parent);
        }
    }

    @Override
    public void onClose() {
        back();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /** 不要原版菜单背景与模糊，本界面自己铺满底色。 */
    @Override
    public void renderBackground(net.minecraft.client.gui.GuiGraphics g, int mouseX, int mouseY, float partialTick) {
    }

    private String[][] lines() {
        return page == Page.GUIDE ? GUIDE_LINES : DEVICE_LINES;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // 不用 super.render：父屏幕（编程界面）会连同蒙版一起重绘，只在这里铺底色。
        g.fill(0, 0, width, height, SCREEN_BG);

        // 顶栏
        g.fill(0, 0, width, TOP_BAR, BAR_BG);
        g.fill(0, TOP_BAR - 2, width, TOP_BAR, BRASS_DARK);
        g.fill(0, 0, width, 2, BRASS);
        String brand = Component.translatable("block.cbtc.computer").getString();
        g.drawString(font, brand, 8, 8, TEXT, false);
        String title = Component.translatable(page == Page.GUIDE
                ? "cbtc.help.title.guide" : "cbtc.help.title.device").getString();
        g.drawString(font, title, 8 + font.width(brand) + 12, 8, BRASS_LIGHT, false);
        drawCloseButton(g, mouseX, mouseY);

        // 没有左栏导航，内容面板从左侧直接开始
        int px = 8;
        int py = TOP_BAR + 8;
        int pw = width - px - 8;
        int ph = height - py - 8;
        g.fill(px, py, px + pw, py + ph, PANEL_BG);
        g.renderOutline(px, py, pw, ph, BRASS_DARK);

        g.enableScissor(px + 1, py + 1, px + pw - 1, py + ph - 1);
        int y = py + 8 - scroll;
        for (String[] line : lines()) {
            String key = line[0];
            boolean header = isHeader(key);
            int color = header ? BRASS_LIGHT : TEXT_DIM;
            int x = px + 8 + (header ? 0 : 6);
            for (String row : wrap(Component.translatable(key).getString(), pw - 20)) {
                g.drawString(font, row, x, y, color, false);
                y += header ? 12 : 10;
            }
            y += header ? 5 : 3;
        }
        int contentH = y + scroll - (py + 8);
        g.disableScissor();

        // 滚动条
        if (contentH > ph - 16) {
            int trackH = ph - 8;
            int barH = Math.max(16, trackH * (ph - 16) / contentH);
            int maxScroll = contentH - (ph - 16);
            int barY = py + 4 + (trackH - barH) * Math.min(scroll, maxScroll) / Math.max(1, maxScroll);
            g.fill(px + pw - 5, py + 4, px + pw - 3, py + 4 + trackH, 0xFF2A241C);
            g.fill(px + pw - 6, barY, px + pw - 2, barY + barH, BRASS);
        }

        super.render(g, mouseX, mouseY, partialTick);
    }

    /** 顶栏右侧的关闭按钮位置。 */
    private int closeX() {
        return width - 8 - 52;
    }

    private int closeY() {
        return 3;
    }

    private void drawCloseButton(GuiGraphics g, int mouseX, int mouseY) {
        boolean hover = mouseX >= closeX() && mouseX < closeX() + 52
                && mouseY >= closeY() && mouseY < closeY() + 18;
        BlockShapes.roundedRect(g, closeX(), closeY(), 52, 18, 4, hover ? BRASS_LIGHT : BRASS);
        BlockShapes.roundedRect(g, closeX() + 1, closeY() + 1, 50, 16, 3, BRASS_DARK);
        String label = Component.translatable("cbtc.help.back").getString();
        g.drawString(font, label, closeX() + (52 - font.width(label)) / 2, closeY() + 5, TEXT, false);
    }

    /** 按单词换行（中文逐字），避免英文长句被硬切。 */
    private List<String> wrap(String text, int maxWidth) {
        List<String> out = new ArrayList<>();
        String[] paragraphs = text.split("\n", -1);
        for (String paragraph : paragraphs) {
            addWrapped(out, paragraph, maxWidth);
        }
        return out;
    }

    private void addWrapped(List<String> out, String paragraph, int maxWidth) {
        List<String> tokens = new ArrayList<>();
        StringBuilder token = new StringBuilder();
        for (char c : paragraph.toCharArray()) {
            if (c == ' ') {
                if (token.length() > 0) {
                    tokens.add(token.toString());
                    token.setLength(0);
                }
                tokens.add(" ");
            } else if (c > 0x2E80) {
                if (token.length() > 0) {
                    tokens.add(token.toString());
                    token.setLength(0);
                }
                tokens.add(String.valueOf(c));
            } else {
                token.append(c);
            }
        }
        if (token.length() > 0) {
            tokens.add(token.toString());
        }

        StringBuilder line = new StringBuilder();
        for (String t : tokens) {
            if (font.width(line.toString() + t) > maxWidth && line.length() > 0) {
                out.add(line.toString());
                line.setLength(0);
                if (" ".equals(t)) {
                    continue;
                }
            }
            line.append(t);
        }
        if (line.length() > 0) {
            out.add(line.toString());
        }
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double scrollX, double scrollY) {
        int ph = height - (TOP_BAR + 8) - 34;
        int total = measureContentHeight(ph);
        int maxScroll = Math.max(0, total - (ph - 16));
        scroll = Math.max(0, Math.min((int) (scroll - scrollY * 16), maxScroll));
        return true;
    }

    /** 按当前换行规则算出内容总高度，用于滚动范围。 */
    private int measureContentHeight(int ph) {
        int pw = width - (8 + 92 + 8) - 8;
        int y = 0;
        for (String[] line : lines()) {
            String key = line[0];
            boolean header = isHeader(key);
            int count = wrap(Component.translatable(key).getString(), pw - 20).size();
            y += count * (header ? 12 : 10);
            y += header ? 5 : 3;
        }
        return y;
    }

    private static boolean isHeader(String key) {
        return key.endsWith(".intro") || key.endsWith(".control") || key.endsWith(".event")
                || key.endsWith(".ctrl") || key.endsWith(".data") || key.endsWith(".math")
                || key.endsWith(".seq") || key.endsWith(".angle") || key.endsWith(".broadcast")
                || key.endsWith(".redstone") || key.endsWith(".lan") || key.endsWith(".net")
                || key.endsWith(".display") || key.endsWith(".sound") || key.endsWith(".aero")
                || key.endsWith(".logistics") || key.endsWith(".expander")
                || key.endsWith(".computer") || key.endsWith(".types")
                || key.endsWith(".bind") || key.endsWith(".modules");
    }
}
