package com.mcl.cbtc.client;

import com.mcl.cbtc.item.GunRemoteItem;
import com.mcl.cbtc.network.GunRemoteInputPayload;
import com.mcl.cbtc.network.GunRemoteStatePayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

/**
 * 枪式遥控器的客户端使用状态。
 * <p>
 * 手持右键进入：此后鼠标左右键被当成遥控器的两个"按键"发给服务端，并吞掉原本的攻击/使用；
 * 按 Esc、切换到别的物品或打开界面都会退出。
 */
@EventBusSubscriber(modid = "cbtc", value = Dist.CLIENT)
public final class GunRemoteInput {
    private static String remoteId;
    private static InteractionHand hand;
    /** Esc 退出使用状态时，短暂屏蔽随之而来的暂停菜单。 */
    private static long pauseSuppressUntil;

    private GunRemoteInput() {
    }

    /** 进入使用状态：记录遥控器 ID 并通知服务端。 */
    public static void beginSession(ItemStack stack, InteractionHand hand) {
        String id = GunRemoteItem.ensureId(stack);
        if (id.isBlank()) {
            return;
        }
        if (id.equals(remoteId) && hand == GunRemoteInput.hand) {
            // 按住右键时 use 每 tick 都会进来一次，已经在这个遥控器上就不要再发包
            return;
        }
        if (remoteId != null) {
            endSession();
        }
        remoteId = id;
        GunRemoteInput.hand = hand;
        PacketDistributor.sendToServer(new GunRemoteStatePayload(id, true));
        showMessage("item.cbtc.gun_remote.enter");
        // 松开原版的"使用"键状态，避免右键按住时继续触发物品使用和挥手动画
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.options.keyUse.consumeClick();
        minecraft.options.keyUse.setDown(false);
        minecraft.options.keyAttack.consumeClick();
        minecraft.options.keyAttack.setDown(false);
    }

    /** 退出使用状态。 */
    public static void endSession() {
        if (remoteId != null) {
            PacketDistributor.sendToServer(new GunRemoteStatePayload(remoteId, false));
            showMessage("item.cbtc.gun_remote.exit");
        }
        remoteId = null;
        hand = null;
    }

    /** 动作栏提示（进入/退出控制模式）。 */
    private static void showMessage(String key) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null) {
            minecraft.player.displayClientMessage(net.minecraft.network.chat.Component.translatable(key), true);
        }
    }

    public static boolean isActive() {
        return remoteId != null;
    }

    @SubscribeEvent
    public static void onMouse(InputEvent.MouseButton.Pre event) {
        if (remoteId == null) {
            return;
        }
        int button = event.getButton();
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT && button != GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
            return;
        }
        boolean down = event.getAction() == GLFW.GLFW_PRESS;
        if (down) {
            playClickSound();
        }
        PacketDistributor.sendToServer(new GunRemoteInputPayload(remoteId, button, down));
        // 吞掉这次点击，避免同时打出攻击或触发方块/物品交互
        event.setCanceled(true);
    }

    /** 按下的机械"咔哒"声，纯客户端播放。 */
    private static void playClickSound() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) {
            return;
        }
        minecraft.level.playLocalSound(minecraft.player.getX(), minecraft.player.getY(), minecraft.player.getZ(),
                net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK.value(), net.minecraft.sounds.SoundSource.PLAYERS,
                0.45F, 1.35F, false);
    }

    @SubscribeEvent
    public static void onKey(InputEvent.Key event) {
        if (remoteId != null && event.getAction() != GLFW.GLFW_RELEASE
                && event.getKey() == GLFW.GLFW_KEY_ESCAPE) {
            // 记下这一下 Esc，接下来一小段时间内不让暂停菜单停留
            pauseSuppressUntil = System.currentTimeMillis() + 500L;
            endSession();
            // 与打字机一致：Esc 直接关掉当前界面，避免连带弹出暂停菜单
            Minecraft.getInstance().setScreen(null);
        }
    }

    /** Esc 退出时如果有人直接开暂停界面，这一小段时间内也一并拦掉。 */
    @SubscribeEvent
    public static void onScreenOpening(ScreenEvent.Opening event) {
        if (event.getNewScreen() instanceof PauseScreen
                && System.currentTimeMillis() <= pauseSuppressUntil) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        // Esc 退出后的短暂窗口里，如果暂停界面已经被打开，直接关掉
        if (System.currentTimeMillis() <= pauseSuppressUntil && minecraft.screen instanceof PauseScreen) {
            minecraft.setScreen(null);
        }
        if (remoteId == null) {
            return;
        }
        if (minecraft.player == null || minecraft.level == null || minecraft.screen != null) {
            endSession();
            return;
        }
        ItemStack held = minecraft.player.getItemInHand(hand);
        if (!(held.getItem() instanceof GunRemoteItem) || !remoteId.equals(GunRemoteItem.id(held))) {
            endSession();
            return;
        }
        // 使用状态期间持续压住攻击/使用键，保证左右键只作为遥控器输入
        minecraft.options.keyAttack.consumeClick();
        minecraft.options.keyAttack.setDown(false);
        minecraft.options.keyUse.consumeClick();
        minecraft.options.keyUse.setDown(false);
    }
}
