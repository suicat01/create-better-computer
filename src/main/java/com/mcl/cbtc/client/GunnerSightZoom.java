package com.mcl.cbtc.client;

import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;

/**
 * 枪炮手的瞄准组件的画面缩放。
 * <p>
 * 戴着瞄准组件时按住 Ctrl 滚动鼠标滚轮，按 1.2 倍一档调整视野缩放，最高 6 倍。
 * 缩放通过压低 FOV 实现，取下组件或滚轮缩回 1 倍后恢复正常视野。
 */
public final class GunnerSightZoom {
    private static final double MIN_ZOOM = 1.0D;
    private static final double MAX_ZOOM = 6.0D;
    /** 每一格滚轮的缩放倍率。 */
    private static final double STEP = 1.2D;

    private static double zoom = MIN_ZOOM;

    private GunnerSightZoom() {
    }

    /** 当前缩放倍率，1 表示不缩放。 */
    public static double zoom() {
        return zoom;
    }

    public static void onMouseScroll(InputEvent.MouseScrollingEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.screen != null) {
            return;
        }
        if (!GunnerSightOverlay.isWearing(minecraft.player)) {
            zoom = MIN_ZOOM;
            return;
        }
        if (!Screen.hasControlDown()) {
            return;
        }
        double delta = event.getScrollDeltaY();
        if (delta == 0.0D) {
            return;
        }
        zoom = Mth.clamp(zoom * Math.pow(STEP, delta), MIN_ZOOM, MAX_ZOOM);
        // 缩放时不切换快捷栏
        event.setCanceled(true);
    }

    public static void onComputeFov(ViewportEvent.ComputeFov event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return;
        }
        if (!GunnerSightOverlay.isWearing(minecraft.player)) {
            zoom = MIN_ZOOM;
            return;
        }
        if (zoom > MIN_ZOOM + 0.001D) {
            event.setFOV(event.getFOV() / zoom);
        }
    }

    /** 缩放倍率的显示文本，例如 “×3.0”。 */
    public static String describe() {
        return String.format(Locale.ROOT, "×%.1f", zoom);
    }
}
