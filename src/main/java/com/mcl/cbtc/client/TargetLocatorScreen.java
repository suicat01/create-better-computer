package com.mcl.cbtc.client;

import com.mcl.cbtc.network.TargetLocatorSetPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

/** 目标定位模块的坐标编辑界面：写入要指向的 X / Z。 */
public class TargetLocatorScreen extends Screen {
    private final net.minecraft.world.InteractionHand hand;
    private final double initialX;
    private final double initialZ;
    private EditBox xBox;
    private EditBox zBox;

    private TargetLocatorScreen(net.minecraft.world.InteractionHand hand, double x, double z) {
        super(Component.translatable("cbtc.target_locator.title"));
        this.hand = hand;
        this.initialX = x;
        this.initialZ = z;
    }

    public static void open(net.minecraft.world.InteractionHand hand, double x, double z) {
        net.minecraft.client.Minecraft.getInstance().setScreen(new TargetLocatorScreen(hand, x, z));
    }

    @Override
    protected void init() {
        int left = width / 2 - 100;
        int top = height / 2 - 20;
        xBox = new EditBox(font, left, top, 96, 20, Component.translatable("cbtc.target_locator.x"));
        xBox.setMaxLength(24);
        xBox.setValue(format(initialX));
        xBox.setFocused(true);
        addRenderableWidget(xBox);
        zBox = new EditBox(font, left + 104, top, 96, 20, Component.translatable("cbtc.target_locator.z"));
        zBox.setMaxLength(24);
        zBox.setValue(format(initialZ));
        addRenderableWidget(zBox);
        addRenderableWidget(Button.builder(Component.translatable("cbtc.target_locator.save"), button -> confirm())
                .bounds(left, top + 32, 200, 20).build());
    }

    private void confirm() {
        double x = parse(xBox.getValue(), initialX);
        double z = parse(zBox.getValue(), initialZ);
        PacketDistributor.sendToServer(new TargetLocatorSetPayload(hand, x, z));
        onClose();
    }

    private static double parse(String text, double fallback) {
        try {
            return Double.parseDouble(text.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static String format(double value) {
        return Math.abs(value - Math.round(value)) < 1.0E-6
                ? Long.toString(Math.round(value))
                : String.format("%.2f", value);
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
        // super.render 里会再画一次背景蒙版，必须先画它，否则标题和标签会被蒙版压暗
        super.render(g, mouseX, mouseY, partialTick);
        g.drawCenteredString(font, title, width / 2, height / 2 - 56, 0xFFFFFFFF);
        g.drawCenteredString(font, Component.translatable("cbtc.target_locator.x").getString(),
                width / 2 - 52, height / 2 - 34, 0xFFA0A0A0);
        g.drawCenteredString(font, Component.translatable("cbtc.target_locator.z").getString(),
                width / 2 + 52, height / 2 - 34, 0xFFA0A0A0);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
