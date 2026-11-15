package com.mcl.cbtc.client;

import com.mcl.cbtc.compat.Curios;
import com.mcl.cbtc.compat.CuriosCompat;
import com.mcl.cbtc.registry.CbcItems;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderGuiEvent;

/**
 * 枪炮手的瞄准组件的取景器式屏幕效果。
 * <p>
 * 戴在头部槽位或饰品栏头部槽位时，屏幕四角出现取景框角标、中心出现准星与刻度，
 * 上下边缘带轻微暗角，模仿摄影机取景器的观感。
 */
public final class GunnerSightOverlay {
    /** 取景框与准星的颜色（半透明白）。 */
    private static final int FRAME_COLOR = 0xCCF2F2F2;
    /** 上下暗角的颜色。 */
    private static final int VIGNETTE_COLOR = 0x88000000;
    /**
     * 角标与准星的线宽。
     * <p>
     * 必须是 1：原版准星按 15×15 贴图居中绘制，中心落在 {@code width / 2 + 0.5}，
     * 用 1 像素宽的线从 {@code width / 2} 起画，中心正好与原版准星重合，任何界面缩放下都不会有偏差。
     */
    private static final int LINE = 1;
    /** 雷达目标的锁定框与箭头颜色：始终鲜红，不参与滤镜。 */
    private static final int TARGET_COLOR = 0xFFFF3B30;
    /** 「色彩修复」附魔的注册名。 */
    private static final net.minecraft.resources.ResourceLocation COLOR_RESTORE =
            com.mcl.cbtc.CreateBetterComputer.asResource("color_restore");
    /** 目标框的半宽（格）。 */
    private static final double TARGET_HALF_WIDTH = 0.45D;

    private GunnerSightOverlay() {
    }

    public static void onRenderGui(RenderGuiEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        Player player = minecraft.player;
        if (player == null || minecraft.options.hideGui || minecraft.screen != null) {
            return;
        }
        if (!isWearing(player)) {
            return;
        }
        GuiGraphics graphics = event.getGuiGraphics();
        int width = graphics.guiWidth();
        int height = graphics.guiHeight();
        // 先套老式摄影机滤镜（去色 + 像素化 + 颗粒 + 扫描线），再画清晰的取景框和准星；
        // 瞄准组件附了「色彩修复」时改用保留色彩的滤镜
        GunnerSightFilter.apply(minecraft, event.getPartialTick().getGameTimeDeltaPartialTick(false),
                hasColorRestore(player));
        drawVignette(graphics, width, height);
        drawCorners(graphics, width, height);
        // 原版准星是 15×15 的贴图，绘制在 (guiWidth - 15) / 2（整数除法）处，
        // 贴图里十字的第 0 列从 3 开始、中心在第 7 列，这里照抄同样的整数算法，
        // 笔画从 (guiWidth - 15) / 2 + 7 起画，中心才和原版准星完全重合。
        int centerX = (width - 15) / 2 + 7;
        int centerY = (height - 15) / 2 + 7;
        drawReticle(graphics, centerX, centerY);
        // 缩放倍率显示在准星下方
        double zoom = GunnerSightZoom.zoom();
        if (zoom > 1.001D) {
            graphics.drawCenteredString(minecraft.font, GunnerSightZoom.describe(),
                    centerX + 1, centerY + 24, 0xDDF2F2F2);
        }
        drawTargets(graphics, minecraft, width, height);
    }

    /**
     * 雷达目标：视野内画鲜红方框，视野外在屏幕边缘画鲜红箭头。
     * <p>
     * 两者都在滤镜之后绘制成 HUD，不会被偏灰滤镜压暗。
     */
    private static void drawTargets(GuiGraphics graphics, Minecraft minecraft, int width, int height) {
        var targets = GunnerSightTargets.active(minecraft);
        if (targets.isEmpty()) {
            return;
        }
        net.minecraft.client.Camera camera = minecraft.gameRenderer.getMainCamera();
        Vec3 eye = camera.getPosition();
        Vec3 look = toVec(camera.getLookVector());
        Vec3 up = toVec(camera.getUpVector());
        Vec3 left = toVec(camera.getLeftVector());
        double fov = Math.toRadians(minecraft.options.fov().get());
        double tanY = Math.tan(fov / 2.0);
        double tanX = tanY * width / (double) height;
        for (var target : targets) {
            double minX = Double.MAX_VALUE;
            double maxX = -Double.MAX_VALUE;
            double minY = Double.MAX_VALUE;
            double maxY = -Double.MAX_VALUE;
            boolean projectable = true;
            // 把包围盒的 8 个角投到屏幕上，取屏幕包围矩形
            for (int corner = 0; corner < 8; corner++) {
                double x = target.x() + ((corner & 1) == 0 ? -TARGET_HALF_WIDTH : TARGET_HALF_WIDTH);
                double y = target.y() + ((corner & 2) == 0 ? -0.05D : target.height() + 0.05D);
                double z = target.z() + ((corner & 4) == 0 ? -TARGET_HALF_WIDTH : TARGET_HALF_WIDTH);
                Vec3 delta = new Vec3(x - eye.x, y - eye.y, z - eye.z);
                double depth = delta.dot(look);
                if (depth <= 0.1D) {
                    projectable = false;
                    break;
                }
                double screenX = width / 2.0D - delta.dot(left) / depth / tanX * (width / 2.0D);
                double screenY = height / 2.0D - delta.dot(up) / depth / tanY * (height / 2.0D);
                minX = Math.min(minX, screenX);
                maxX = Math.max(maxX, screenX);
                minY = Math.min(minY, screenY);
                maxY = Math.max(maxY, screenY);
            }
            boolean onScreen = projectable && maxX > 0.0D && minX < width && maxY > 0.0D && minY < height;
            if (onScreen) {
                drawTargetBox(graphics, minecraft, width, height, target, minX, maxX, minY, maxY);
            } else {
                drawEdgeArrow(graphics, width, height, target, eye, look, up, left, tanX, tanY);
            }
        }
    }

    /** 屏幕内的目标方框：纯矩形，线宽随投影尺寸变化（近大远小），右下角标注名称与血量。 */
    private static void drawTargetBox(GuiGraphics graphics, Minecraft minecraft, int width, int height,
                                      GunnerSightTargets.Target target,
                                      double minX, double maxX, double minY, double maxY) {
        int left = (int) Math.max(0.0D, Math.floor(minX));
        int right = (int) Math.min(width - 1.0D, Math.ceil(maxX));
        int top = (int) Math.max(0.0D, Math.floor(minY));
        int bottom = (int) Math.min(height - 1.0D, Math.ceil(maxY));
        if (right - left < 3 || bottom - top < 3) {
            return;
        }
        // 近大远小：框越大线越粗
        int thickness = Math.max(1, Math.min(4, (int) Math.round(Math.max(right - left, bottom - top) / 120.0D)));
        graphics.fill(left, top, right, top + thickness, TARGET_COLOR);
        graphics.fill(left, bottom - thickness, right, bottom, TARGET_COLOR);
        graphics.fill(left, top, left + thickness, bottom, TARGET_COLOR);
        graphics.fill(right - thickness, top, right, bottom, TARGET_COLOR);
        drawTargetLabel(graphics, minecraft, width, height, target, right, bottom);
    }

    /** 框右下角的实体名称与剩余血量。 */
    private static void drawTargetLabel(GuiGraphics graphics, Minecraft minecraft, int width, int height,
                                        GunnerSightTargets.Target target, int right, int bottom) {
        String label = target.name() == null ? "" : target.name();
        if (target.health() >= 0.0F) {
            String health = String.format(java.util.Locale.ROOT, "%.0f", target.health());
            label = label.isBlank() ? health : label + " " + health;
        }
        if (label.isBlank()) {
            return;
        }
        int textWidth = minecraft.font.width(label);
        int textX = Math.max(0, right - textWidth);
        int textY = bottom + 2;
        if (textY + 9 > height) {
            textY = bottom - 11;
        }
        if (textX + textWidth > width) {
            textX = Math.max(0, width - textWidth);
        }
        graphics.drawString(minecraft.font, label, textX, textY, TARGET_COLOR, false);
    }

    /** 屏幕外的目标：在屏幕边缘画一个指向它的箭头。 */
    private static void drawEdgeArrow(GuiGraphics graphics, int width, int height,
                                      GunnerSightTargets.Target target, Vec3 eye, Vec3 look, Vec3 up, Vec3 left,
                                      double tanX, double tanY) {
        Vec3 delta = new Vec3(target.x() - eye.x,
                target.y() + target.height() * 0.5D - eye.y,
                target.z() - eye.z);
        double depth = delta.dot(look);
        double right = -delta.dot(left);
        double upward = delta.dot(up);
        // 目标在身后时左右/上下方向要反过来，箭头才指向"转头能看见"的那一侧
        double dirX = depth <= 0.0D ? -right : right;
        double dirY = depth <= 0.0D ? -upward : upward;
        if (Math.abs(dirX) < 1.0E-4D && Math.abs(dirY) < 1.0E-4D) {
            dirY = 1.0D;
        }
        double halfWidth = width / 2.0D - 18.0D;
        double halfHeight = height / 2.0D - 18.0D;
        double scale = Math.min(halfWidth / Math.max(1.0E-4D, Math.abs(dirX)),
                halfHeight / Math.max(1.0E-4D, Math.abs(dirY)));
        float arrowX = (float) (width / 2.0D + dirX * scale);
        float arrowY = (float) (height / 2.0D - dirY * scale);
        drawArrow(graphics, arrowX, arrowY, dirX, -dirY, TARGET_COLOR);
    }

    /** 相机方向向量是 JOML 的 Vector3f，转成 Vec3 方便做点乘。 */
    private static Vec3 toVec(org.joml.Vector3f vector) {
        return new Vec3(vector.x, vector.y, vector.z);
    }

    /** 画一个指向 (dirX, dirY)（屏幕坐标，Y 向下）的小三角。 */
    private static void drawArrow(GuiGraphics graphics, float x, float y, double dirX, double dirY, int color) {
        float angle = (float) Math.atan2(dirY, dirX);
        PoseStack poseStack = graphics.pose();
        poseStack.pushPose();
        poseStack.translate(x, y, 0.0F);
        poseStack.mulPose(Axis.ZP.rotation(angle));
        for (int i = 0; i < 10; i++) {
            int half = 1 + (9 - i) / 2;
            graphics.fill(-9 + i, -half, -8 + i, half + 1, color);
        }
        poseStack.popPose();
    }

    /** 玩家是否戴着瞄准组件（原版头部槽位或饰品栏头部槽位）。 */
    static boolean isWearing(Player player) {
        if (player.getItemBySlot(EquipmentSlot.HEAD).is(CbcItems.GUNNER_SIGHT.get())) {
            return true;
        }
        return Curios.loaded() && CuriosCompat.isWearing(player, CbcItems.GUNNER_SIGHT.get());
    }

    /** 玩家戴着的瞄准组件（头部槽位优先，其次饰品栏）；没戴时返回空栈。 */
    private static ItemStack wornSight(Player player) {
        ItemStack head = player.getItemBySlot(EquipmentSlot.HEAD);
        if (head.is(CbcItems.GUNNER_SIGHT.get())) {
            return head;
        }
        return Curios.loaded() ? CuriosCompat.findWearing(player, CbcItems.GUNNER_SIGHT.get()) : ItemStack.EMPTY;
    }

    /** 瞄准组件是否附了「色彩修复」。 */
    private static boolean hasColorRestore(Player player) {
        ItemStack stack = wornSight(player);
        if (stack.isEmpty()) {
            return false;
        }
        var enchantments = stack.getOrDefault(net.minecraft.core.component.DataComponents.ENCHANTMENTS,
                net.minecraft.world.item.enchantment.ItemEnchantments.EMPTY);
        for (var entry : enchantments.entrySet()) {
            if (entry.getIntValue() > 0 && entry.getKey().unwrapKey()
                    .map(key -> key.location().equals(COLOR_RESTORE)).orElse(false)) {
                return true;
            }
        }
        return false;
    }

    /** 上下边缘由深到浅的暗角。 */
    private static void drawVignette(GuiGraphics graphics, int width, int height) {
        int band = Math.max(24, height / 6);
        graphics.fillGradient(0, 0, width, band, VIGNETTE_COLOR, 0x00000000);
        graphics.fillGradient(0, height - band, width, height, 0x00000000, VIGNETTE_COLOR);
    }

    /** 四角的取景框角标。 */
    private static void drawCorners(GuiGraphics graphics, int width, int height) {
        int margin = 10;
        int arm = Math.max(16, Math.min(width, height) / 9);
        // 左上
        horizontal(graphics, margin, margin, arm);
        vertical(graphics, margin, margin, arm);
        // 右上
        horizontal(graphics, width - margin - arm, margin, arm);
        vertical(graphics, width - margin - LINE, margin, arm);
        // 左下
        horizontal(graphics, margin, height - margin - LINE, arm);
        vertical(graphics, margin, height - margin - arm, arm);
        // 右下
        horizontal(graphics, width - margin - arm, height - margin - LINE, arm);
        vertical(graphics, width - margin - LINE, height - margin - arm, arm);
    }

    /** 中心准星与两侧刻度；中心空出一块，让原版准星正好落在里面。 */
    private static void drawReticle(GuiGraphics graphics, int centerX, int centerY) {
        int gap = 9;
        int length = 9;
        // 中心十字：四段等长的线，中间留出原版准星的 15×15 区域
        horizontal(graphics, centerX - gap - length, centerY, length);
        horizontal(graphics, centerX + gap, centerY, length);
        vertical(graphics, centerX, centerY - gap - length, length);
        vertical(graphics, centerX, centerY + gap, length);
        // 左右两侧由近到远逐渐变短的刻度
        for (int i = 0; i < 3; i++) {
            int distance = 24 + i * 8;
            int tick = 7 - i * 2;
            vertical(graphics, centerX - distance, centerY - tick / 2, tick);
            vertical(graphics, centerX + distance, centerY - tick / 2, tick);
        }
    }

    private static void horizontal(GuiGraphics graphics, int x, int y, int length) {
        graphics.fill(x, y, x + length, y + LINE, FRAME_COLOR);
    }

    private static void vertical(GuiGraphics graphics, int x, int y, int length) {
        graphics.fill(x, y, x + LINE, y + length, FRAME_COLOR);
    }
}
