package com.mcl.cbtc.client;

import com.mcl.cbtc.CreateBetterComputer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

/**
 * 枪炮手的瞄准组件的老式摄影机滤镜。
 * <p>
 * 用原版的后处理链 {@link PostChain} 把整帧画面做去色 + 像素化 + 颗粒 + 扫描线，
 * 滤镜结束后重新绑定主帧缓冲，随后绘制的 HUD（取景框、准星）仍然是清晰的。
 */
public final class GunnerSightFilter {
    private static final ResourceLocation CHAIN = ResourceLocation.fromNamespaceAndPath(
            CreateBetterComputer.MODID, "shaders/post/gunner_sight.json");
    /** 附魔「色彩修复」后使用的滤镜：保留完整色彩。 */
    private static final ResourceLocation CHAIN_COLOR = ResourceLocation.fromNamespaceAndPath(
            CreateBetterComputer.MODID, "shaders/post/gunner_sight_color.json");

    /** 资源重载（F3+T）后原版会重建着色器，这里跟着丢弃旧链。 */
    public static final SimplePreparableReloadListener<Void> RELOAD_LISTENER =
            new SimplePreparableReloadListener<>() {
                @Override
                protected Void prepare(ResourceManager resourceManager, ProfilerFiller profiler) {
                    return null;
                }

                @Override
                protected void apply(Void unused, ResourceManager resourceManager, ProfilerFiller profiler) {
                    close();
                    available = true;
                }
            };

    private static PostChain chain;
    private static PostChain colorChain;
    private static int width = -1;
    private static int height = -1;
    private static boolean available = true;

    private GunnerSightFilter() {
    }

    /**
     * 把滤镜套到当前帧上。
     *
     * @param restoreColor 装了「色彩修复」附魔时为 true，改用保留色彩的滤镜
     */
    static void apply(Minecraft minecraft, float partialTicks, boolean restoreColor) {
        if (!available) {
            return;
        }
        try {
            if (restoreColor ? colorChain == null : chain == null) {
                PostChain created = new PostChain(minecraft.getTextureManager(), minecraft.getResourceManager(),
                        minecraft.getMainRenderTarget(), restoreColor ? CHAIN_COLOR : CHAIN);
                if (restoreColor) {
                    colorChain = created;
                } else {
                    chain = created;
                }
            }
            PostChain active = restoreColor ? colorChain : chain;
            int windowWidth = minecraft.getWindow().getWidth();
            int windowHeight = minecraft.getWindow().getHeight();
            if (windowWidth != width || windowHeight != height) {
                if (chain != null) {
                    chain.resize(windowWidth, windowHeight);
                }
                if (colorChain != null) {
                    colorChain.resize(windowWidth, windowHeight);
                }
                width = windowWidth;
                height = windowHeight;
            }
            active.process(partialTicks);
            // 后处理结束后主帧缓冲处于未绑定状态，重新绑回来，后面的 HUD 绘制才不会丢
            minecraft.getMainRenderTarget().bindWrite(true);
        } catch (Exception exception) {
            available = false;
            close();
            CreateBetterComputer.LOGGER.error("枪炮手的瞄准组件滤镜加载失败，本次游戏内已停用", exception);
        }
    }

    private static void close() {
        if (chain != null) {
            chain.close();
            chain = null;
        }
        if (colorChain != null) {
            colorChain.close();
            colorChain = null;
        }
        width = -1;
        height = -1;
    }
}
