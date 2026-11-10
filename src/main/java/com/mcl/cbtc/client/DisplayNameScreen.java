package com.mcl.cbtc.client;

import com.mcl.cbtc.network.DisplayBindPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

/** 绑定显示设备时的命名界面。 */
public class DisplayNameScreen extends Screen {
    private final net.minecraft.world.InteractionHand hand;
    private final BlockPos pos;
    private final String deviceName;
    private EditBox name;

    private final String initialName;

    private DisplayNameScreen(net.minecraft.world.InteractionHand hand, BlockPos pos, String deviceName,
            String initialName) {
        super(Component.translatable("cbtc.display.name.title"));
        this.hand = hand;
        this.pos = pos;
        this.deviceName = deviceName;
        this.initialName = initialName;
    }

    public static void open(net.minecraft.world.InteractionHand hand, BlockPos pos, String deviceName,
            String initialName) {
        net.minecraft.client.Minecraft.getInstance().setScreen(
                new DisplayNameScreen(hand, pos, deviceName, initialName));
    }

    @Override
    protected void init() {
        int x = width / 2 - 100;
        int y = height / 2 - 10;
        name = new EditBox(font, x, y, 200, 20, Component.translatable("cbtc.display.name.title"));
        name.setMaxLength(48);
        name.setHint(Component.translatable("cbtc.display.name.default_hint", deviceName));
        name.setValue(initialName);
        name.setFocused(true);
        addRenderableWidget(name);
        addRenderableWidget(Button.builder(Component.translatable("cbtc.display.name.confirm"), button -> confirm())
                .bounds(x, y + 32, 96, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("cbtc.display.name.default"), button -> {
            name.setValue("");
            confirm();
        }).bounds(x + 104, y + 32, 96, 20).build());
    }

    private void confirm() {
        PacketDistributor.sendToServer(new DisplayBindPayload(hand, pos, name.getValue()));
        onClose();
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 257 || keyCode == 335) {
            confirm();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g, mouseX, mouseY, partialTick);
        g.drawCenteredString(font, title, width / 2, height / 2 - 45, 0xFFFFFFFF);
        g.drawCenteredString(font, deviceName, width / 2, height / 2 - 28, 0xFFA0A0A0);
        super.render(g, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
