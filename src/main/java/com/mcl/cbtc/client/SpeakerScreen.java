package com.mcl.cbtc.client;

import com.mcl.cbtc.menu.SpeakerMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

/**
 * CB音响界面：上方一个扬声器升级槽，下方玩家背包。
 * 面板与槽位全部用代码绘制，不依赖原版贴图，避免格子错位。
 */
public class SpeakerScreen extends AbstractContainerScreen<SpeakerMenu> {
    /** 面板底色。 */
    private static final int PANEL = 0xFFC6C6C6;
    /** 最外层描边。 */
    private static final int OUTLINE = 0xFF000000;
    /** 立体边高光。 */
    private static final int BEVEL_LIGHT = 0xFFFFFFFF;
    /** 立体边阴影。 */
    private static final int BEVEL_DARK = 0xFF373737;
    /** 槽位凹槽底色。 */
    private static final int SLOT_BG = 0xFF8B8B8B;
    /** 文字颜色（原版容器同款深灰）。 */
    private static final int LABEL = 0x404040;

    /** 升级槽外框：包住那一格。 */
    private static final int SLOT_PANEL_X = 78;
    private static final int SLOT_PANEL_Y = 33;
    private static final int SLOT_PANEL_W = 21;
    private static final int SLOT_PANEL_H = 21;

    public SpeakerScreen(SpeakerMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = 176;
        this.imageHeight = 166;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        renderTooltip(g, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        drawRaised(g, leftPos, topPos, imageWidth, imageHeight);
        // 升级槽周围的凹槽底板
        drawInset(g, leftPos + SLOT_PANEL_X - 1, topPos + SLOT_PANEL_Y - 1,
                SLOT_PANEL_W, SLOT_PANEL_H);
        // 背包与升级槽之间的分隔线
        g.fill(leftPos + 8, topPos + 68, leftPos + imageWidth - 8, topPos + 69, BEVEL_DARK);
        g.fill(leftPos + 8, topPos + 69, leftPos + imageWidth - 8, topPos + 70, BEVEL_LIGHT);
        // 每个槽位的凹槽，坐标直接取自菜单，保证严丝合缝
        for (Slot slot : menu.slots) {
            drawInset(g, leftPos + slot.x - 1, topPos + slot.y - 1, 18, 18);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        g.drawString(font, title, titleLabelX, titleLabelY, LABEL, false);
        g.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, LABEL, false);
    }

    /** 凸起面板：外黑框 + 上左高光 + 下右阴影。 */
    private void drawRaised(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + h, OUTLINE);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, PANEL);
        g.fill(x + 1, y + 1, x + w - 1, y + 2, BEVEL_LIGHT);
        g.fill(x + 1, y + 1, x + 2, y + h - 1, BEVEL_LIGHT);
        g.fill(x + 1, y + h - 2, x + w - 1, y + h - 1, BEVEL_DARK);
        g.fill(x + w - 2, y + 1, x + w - 1, y + h - 1, BEVEL_DARK);
    }

    /** 凹陷槽位：上左阴影 + 下右高光。 */
    private void drawInset(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + h, SLOT_BG);
        g.fill(x, y, x + w, y + 1, BEVEL_DARK);
        g.fill(x, y, x + 1, y + h, BEVEL_DARK);
        g.fill(x + w - 1, y, x + w, y + h, BEVEL_LIGHT);
        g.fill(x, y + h - 1, x + w, y + h, BEVEL_LIGHT);
    }
}
