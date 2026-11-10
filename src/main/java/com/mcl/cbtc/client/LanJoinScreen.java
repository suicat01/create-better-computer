package com.mcl.cbtc.client;

import com.mcl.cbtc.network.LanJoinPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.neoforged.neoforge.network.PacketDistributor;

/** 局域网模块卡的 ID / 密码输入界面。 */
public class LanJoinScreen extends Screen {
    private final InteractionHand hand;
    private final String initialId;
    private final String initialPassword;
    private EditBox idField;
    private EditBox passwordField;

    private LanJoinScreen(InteractionHand hand, String initialId, String initialPassword) {
        super(Component.translatable("cbtc.lan.title"));
        this.hand = hand;
        this.initialId = initialId;
        this.initialPassword = initialPassword;
    }

    public static void open(InteractionHand hand, String initialId, String initialPassword) {
        net.minecraft.client.Minecraft.getInstance().setScreen(
                new LanJoinScreen(hand, initialId, initialPassword));
    }

    @Override
    protected void init() {
        int x = width / 2 - 100;
        int y = height / 2 - 40;
        idField = new EditBox(font, x, y, 200, 20, Component.translatable("cbtc.lan.id"));
        idField.setMaxLength(48);
        idField.setHint(Component.translatable("cbtc.lan.id.hint"));
        idField.setValue(initialId);
        idField.setFocused(true);
        addRenderableWidget(idField);

        passwordField = new EditBox(font, x, y + 32, 200, 20, Component.translatable("cbtc.lan.password"));
        passwordField.setMaxLength(48);
        passwordField.setHint(Component.translatable("cbtc.lan.password.hint"));
        passwordField.setValue(initialPassword);
        addRenderableWidget(passwordField);

        addRenderableWidget(Button.builder(Component.translatable("cbtc.lan.confirm"), button -> confirm())
                .bounds(x, y + 64, 200, 20).build());
    }

    private void confirm() {
        PacketDistributor.sendToServer(new LanJoinPayload(hand, idField.getValue(), passwordField.getValue()));
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
        g.drawCenteredString(font, title, width / 2, height / 2 - 70, 0xFFFFFFFF);
        g.drawString(font, Component.translatable("cbtc.lan.id"), width / 2 - 100, height / 2 - 52, 0xFFA0A0A0, false);
        g.drawString(font, Component.translatable("cbtc.lan.password"), width / 2 - 100, height / 2 - 20, 0xFFA0A0A0,
                false);
        super.render(g, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
