package com.mcl.cbtc.client;

import com.mcl.cbtc.menu.MusicModuleMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

/**
 * 网络音乐模块卡的歌单界面：上方 6 行 9 列的唱片槽，下方玩家背包。
 * 面板与槽位全部用代码绘制，不依赖原版贴图，避免格子错位。
 */
public class MusicModuleScreen extends AbstractContainerScreen<MusicModuleMenu> {
    private static final int PANEL = 0xFFC6C6C6;
    private static final int OUTLINE = 0xFF000000;
    private static final int BEVEL_LIGHT = 0xFFFFFFFF;
    private static final int BEVEL_DARK = 0xFF373737;
    private static final int SLOT_BG = 0xFF8B8B8B;
    private static final int LABEL = 0x404040;

    /** 歌单区凹槽外框：正好包住 6 行 9 列（槽位从 y=18 起，6 行到 y=125）。 */
    private static final int SONGS_PANEL_X = 6;
    private static final int SONGS_PANEL_Y = 16;
    private static final int SONGS_PANEL_W = 164;
    private static final int SONGS_PANEL_H = 111;
    /** 分隔线位置：歌单区底边与背包标题之间。 */
    private static final int SEPARATOR_Y = 132;

    public MusicModuleScreen(MusicModuleMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = 176;
        this.imageHeight = 226;
        // 默认标题位置按 166 高的原版界面算，这里界面更高，得手动放到背包正上方
        this.inventoryLabelY = MusicModuleMenu.INVENTORY_Y - 12;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        renderTooltip(g, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        drawRaised(g, leftPos, topPos, imageWidth, imageHeight);
        drawInset(g, leftPos + SONGS_PANEL_X, topPos + SONGS_PANEL_Y,
                SONGS_PANEL_W, SONGS_PANEL_H);
        // 歌单与背包之间的分隔线
        g.fill(leftPos + 8, topPos + SEPARATOR_Y, leftPos + imageWidth - 8, topPos + SEPARATOR_Y + 1, BEVEL_DARK);
        g.fill(leftPos + 8, topPos + SEPARATOR_Y + 1, leftPos + imageWidth - 8, topPos + SEPARATOR_Y + 2,
                BEVEL_LIGHT);
        for (Slot slot : menu.slots) {
            drawInset(g, leftPos + slot.x - 1, topPos + slot.y - 1, 18, 18);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        g.drawString(font, title, titleLabelX, titleLabelY, LABEL, false);
        g.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, LABEL, false);
    }

    private void drawRaised(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + h, OUTLINE);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, PANEL);
        g.fill(x + 1, y + 1, x + w - 1, y + 2, BEVEL_LIGHT);
        g.fill(x + 1, y + 1, x + 2, y + h - 1, BEVEL_LIGHT);
        g.fill(x + 1, y + h - 2, x + w - 1, y + h - 1, BEVEL_DARK);
        g.fill(x + w - 2, y + 1, x + w - 1, y + h - 1, BEVEL_DARK);
    }

    private void drawInset(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + h, SLOT_BG);
        g.fill(x, y, x + w, y + 1, BEVEL_DARK);
        g.fill(x, y, x + 1, y + h, BEVEL_DARK);
        g.fill(x + w - 1, y, x + w, y + h, BEVEL_LIGHT);
        g.fill(x, y + h - 1, x + w, y + h, BEVEL_LIGHT);
    }
}
