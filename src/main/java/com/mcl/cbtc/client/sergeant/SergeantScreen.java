package com.mcl.cbtc.client.sergeant;

import com.mcl.cbtc.CreateBetterComputer;
import com.mcl.cbtc.menu.SergeantMenu;
import com.mcl.cbtc.network.SergeantProgramSavePayload;
import com.simibubi.create.foundation.gui.AllIcons;
import com.simibubi.create.foundation.gui.widget.IconButton;
import com.simibubi.create.foundation.gui.widget.ScrollInput;
import com.simibubi.create.foundation.gui.widget.SelectionScrollInput;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * 瓦列里中士的装填指令界面。
 * <p>
 * 结构与 Create「可编程齿轮箱」完全一致：同样用 {@link SelectionScrollInput} / {@link ScrollInput}
 * 作为可滚动的输入框，文字画在原版的位置上（指令 x=36、参数 x=90、修饰 x=127），
 * 「留空」只作为序列结尾出现一次；把某一行改成「填充」时才会自动长出下一行。
 */
public final class SergeantScreen extends AbstractContainerScreen<SergeantMenu> {
    private static final ResourceLocation TEXTURE = CreateBetterComputer.asResource("textures/gui/sergeant_valery.png");
    private static final int BG_W = 173;
    private static final int BG_H = 161;
    private static final int ROW_W = 162;
    private static final int ROW_H = 22;
    private static final int ROW_FILL_V = 16;
    private static final int ROW_BLANK_V = 82;
    private static final int ROW_EMPTY_V = 104;
    private static final TagKey<Item> PROJECTILES =
            TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath("createbigcannons", "big_cannon_projectiles"));
    private static final TagKey<Item> PROPELLANTS =
            TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath("createbigcannons", "big_cannon_propellant"));

    private static List<ItemStack> cachedItems;

    private final List<ScrollInput> rowWidgets = new ArrayList<>();
    private IconButton confirmButton;

    public SergeantScreen(SergeantMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = BG_W;
        this.imageHeight = BG_H;
    }

    @Override
    protected void init() {
        super.init();
        // 和原版一样整体左偏 20 像素
        this.leftPos -= 20;
        this.confirmButton = new IconButton(leftPos + BG_W - 33, topPos + BG_H - 24, AllIcons.I_CONFIRM);
        this.confirmButton.withCallback(this::onClose);
        addRenderableWidget(this.confirmButton);
        rebuildRows();
    }

    // ---------------------------------------------------------------- 行数据

    /** 当前显示到第几行：第一个「留空」及其之前，最多 5 行。 */
    private int visibleRows() {
        List<String> rows = menu.rows();
        for (int i = 0; i < rows.size(); i++) {
            if (rows.get(i).isEmpty()) {
                return i + 1;
            }
        }
        return rows.size();
    }

    private void rebuildRows() {
        for (ScrollInput widget : rowWidgets) {
            removeWidget(widget);
        }
        rowWidgets.clear();
        int visible = visibleRows();
        for (int row = 0; row < visible; row++) {
            final int rowIndex = row;
            int x = leftPos + 30;
            int y = topPos + 20 + ROW_H * row;
            boolean blank = menu.rows().get(row).isEmpty();

            ScrollInput type = new SelectionScrollInput(x, y, 50, 18)
                    .forOptions(List.of(
                            Component.translatable("cbtc.sergeant.op.fill"),
                            Component.translatable("cbtc.sergeant.op.blank")))
                    .titled(Component.translatable("cbtc.sergeant.op"))
                    .setState(blank ? 1 : 0)
                    .calling(state -> onTypeChanged(rowIndex, state));
            rowWidgets.add(type);
            addRenderableWidget(type);

            if (!blank) {
                List<ItemStack> items = candidates();
                ScrollInput value = new ScrollInput(x + 58, y, 28, 18)
                        .withRange(0, Math.max(1, items.size()))
                        .format(index -> itemName(items, index))
                        .titled(Component.translatable("cbtc.sergeant.item"))
                        .setState(itemIndex(items, menu.rows().get(row)))
                        .calling(index -> onItemChanged(rowIndex, items, index));
                rowWidgets.add(value);
                addRenderableWidget(value);
            }
        }
    }

    private void onTypeChanged(int row, int state) {
        if (state == 0) {
            // 填充：没选过物品时给一个默认值，并保证下一行是留空
            if (menu.rows().get(row).isEmpty()) {
                List<ItemStack> items = candidates();
                menu.rows().set(row, items.isEmpty() ? ""
                        : BuiltInRegistries.ITEM.getKey(items.get(0).getItem()).toString());
            }
            if (row + 1 < menu.rows().size()) {
                menu.rows().set(row + 1, "");
            }
        } else {
            // 留空：截断后面的行
            menu.rows().set(row, "");
            for (int i = row + 1; i < menu.rows().size(); i++) {
                menu.rows().set(i, "");
            }
        }
        rebuildRows();
    }

    private void onItemChanged(int row, List<ItemStack> items, int index) {
        if (index < 0 || index >= items.size()) {
            return;
        }
        menu.rows().set(row, BuiltInRegistries.ITEM.getKey(items.get(index).getItem()).toString());
    }

    private Component itemName(List<ItemStack> items, int index) {
        if (items.isEmpty() || index < 0 || index >= items.size()) {
            return Component.translatable("cbtc.sergeant.unknown");
        }
        return items.get(index).getHoverName();
    }

    private int itemIndex(List<ItemStack> items, String id) {
        for (int i = 0; i < items.size(); i++) {
            if (BuiltInRegistries.ITEM.getKey(items.get(i).getItem()).toString().equals(id)) {
                return i;
            }
        }
        return 0;
    }

    // ---------------------------------------------------------------- 绘制

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.blit(TEXTURE, leftPos, topPos, 0, 0, BG_W, BG_H, 256, 256);
        int visible = visibleRows();
        // 背景图集本身烤进了几行格子，多出来的槽位要用"空行"盖掉，
        // 这样留空之后的行和最后一行一样是空的
        for (int row = 0; row < menu.rows().size(); row++) {
            int v;
            if (row < visible) {
                v = menu.rows().get(row).isEmpty() ? ROW_BLANK_V : ROW_FILL_V;
            } else {
                v = ROW_EMPTY_V;
            }
            graphics.blit(TEXTURE, leftPos, topPos + 16 + ROW_H * row, 0, v,
                    ROW_W, ROW_H, 256, 256);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);

        Component title = Component.translatable("cbtc.sergeant.title");
        graphics.drawString(font, title, leftPos + (BG_W - font.width(title)) / 2, topPos + 4, 0xF2F2F2, true);

        List<ItemStack> items = candidates();
        int visible = visibleRows();
        for (int row = 0; row < visible; row++) {
            int textY = topPos + 25 + ROW_H * row;
            boolean blank = menu.rows().get(row).isEmpty();
            Component type = Component.translatable(blank ? "cbtc.sergeant.op.blank" : "cbtc.sergeant.op.fill");
            graphics.drawString(font, type, leftPos + 36, textY, blank ? 0x6B625A : 0x2B2118, false);
            if (blank) {
                continue;
            }
            int index = itemIndex(items, menu.rows().get(row));
            Component name = itemName(items, index);
            String text = font.plainSubstrByWidth(name.getString(), 62);
            graphics.drawString(font, text, leftPos + 90, textY, 0x2B2118, false);
            if (!items.isEmpty()) {
                ItemStack stack = items.get(Math.min(index, items.size() - 1));
                graphics.renderItem(stack, leftPos + 127, topPos + 18 + ROW_H * row);
                if (mouseX >= leftPos + 127 && mouseX < leftPos + 145
                        && mouseY >= topPos + 18 + ROW_H * row && mouseY < topPos + 34 + ROW_H * row) {
                    graphics.renderTooltip(font, stack, mouseX, mouseY);
                }
            }
        }

        ((net.createmod.catnip.gui.element.GuiGameElement.GuiRenderBuilder)
                net.createmod.catnip.gui.element.GuiGameElement
                        .of(new ItemStack(com.mcl.cbtc.registry.CbcBlocks.SERGEANT_VALERY.get()))
                        .at(leftPos + BG_W + 6, topPos + BG_H - 56, 100.0F))
                .scale(5.0F)
                .render(graphics);

        if (!menu.bound()) {
            String hint = font.plainSubstrByWidth(
                    Component.translatable("cbtc.sergeant.hint.bind").getString(), 132);
            graphics.drawString(font, hint, leftPos + 6, topPos + 148, 0x5A5148, false);
        }
    }

    @Override
    public void removed() {
        super.removed();
        PacketDistributor.sendToServer(new SergeantProgramSavePayload(menu.pos(), menu.rows()));
    }

    // ---------------------------------------------------------------- 候选物品

    private static List<ItemStack> candidates() {
        if (cachedItems != null) {
            return cachedItems;
        }
        Set<Item> found = new LinkedHashSet<>();
        for (TagKey<Item> tag : List.of(PROJECTILES, PROPELLANTS)) {
            for (Holder<Item> holder : BuiltInRegistries.ITEM.getTagOrEmpty(tag)) {
                found.add(holder.value());
            }
        }
        cachedItems = found.stream()
                .map(ItemStack::new)
                .filter(stack -> stack.getItem() != Items.AIR)
                .sorted(Comparator.comparing(stack -> stack.getHoverName().getString()))
                .toList();
        return cachedItems;
    }
}
