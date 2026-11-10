package com.mcl.cbtc.client;

import com.mcl.cbtc.network.TypewriterKeyPayload;
import com.mcl.cbtc.network.TypewriterReleasePayload;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/** CB 打字机的独立客户端输入与键帽状态，不接管航空学原版打字机. */
@EventBusSubscriber(modid = "cbtc", value = Dist.CLIENT)
public final class TypewriterKeyInput {
    private static final Map<Integer, Integer> PRESET_KEYS = new HashMap<>();
    private static final Set<Integer> PRESSED_KEY_CAPS = new HashSet<>();
    private static BlockPos session;

    static {
        PRESET_KEYS.put(81, 0);
        PRESET_KEYS.put(87, 1);
        PRESET_KEYS.put(69, 2);
        PRESET_KEYS.put(65, 6);
        PRESET_KEYS.put(83, 7);
        PRESET_KEYS.put(68, 8);
        PRESET_KEYS.put(265, 4);
        PRESET_KEYS.put(263, 10);
        PRESET_KEYS.put(264, 11);
        PRESET_KEYS.put(262, 12);
        PRESET_KEYS.put(32, 13);
        PRESET_KEYS.put(48, 12);
        PRESET_KEYS.put(320, 12);
        for (int i = 0; i < 9; i++) {
            PRESET_KEYS.put(49 + i, i);
            PRESET_KEYS.put(321 + i, i);
        }
    }

    private TypewriterKeyInput() {
    }

    public static void beginSession(BlockPos pos) {
        session = pos;
        PRESSED_KEY_CAPS.clear();
        CBTypewriterRenderer.resetKeys();
    }

    public static void endSession() {
        session = null;
        PRESSED_KEY_CAPS.clear();
        CBTypewriterRenderer.resetKeys();
    }

    public static boolean isActive() {
        return session != null;
    }

    public static boolean isKeyCapPressed(int index) {
        return PRESSED_KEY_CAPS.contains(index);
    }

    @SubscribeEvent
    public static void onKey(InputEvent.Key event) {
        if (session == null || event.getAction() == 2) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null || minecraft.screen != null) {
            return;
        }
        int key = event.getKey();
        if (key == 256) {
            release();
            return;
        }
        boolean down = event.getAction() == 1;
        preventPress(key, event.getScanCode());
        int keyCap = PRESET_KEYS.computeIfAbsent(key,
                code -> RandomSource.create(code).nextInt(13));
        if (down) {
            PRESSED_KEY_CAPS.add(keyCap);
        } else {
            PRESSED_KEY_CAPS.remove(keyCap);
        }
        var sound = down
                ? dev.simulated_team.simulated.index.SimSoundEvents.LINKED_TYPEWRITER_TAP
                : dev.simulated_team.simulated.index.SimSoundEvents.LINKED_TYPEWRITER_UNTAP;
        sound.playAt(minecraft.level, minecraft.player.blockPosition(), 1.0F, 1.0F, true);
        PacketDistributor.sendToServer(new TypewriterKeyPayload(session, key, down));
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        CBTypewriterRenderer.tick();
        if (session == null) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null || minecraft.screen != null
                || !(minecraft.level.getBlockEntity(session)
                        instanceof com.mcl.cbtc.blockentity.CBTypewriterBlockEntity typewriter)
                || !com.mcl.cbtc.blockentity.CBTypewriterBlockEntity.playerInRange(
                        minecraft.player, minecraft.level, session)) {
            release();
        }
    }

    private static void release() {
        if (session != null) {
            PacketDistributor.sendToServer(new TypewriterReleasePayload(session));
        }
        endSession();
    }

    private static void preventPress(int key, int scanCode) {
        for (KeyMapping mapping : Minecraft.getInstance().options.keyMappings) {
            if (mapping.matches(key, scanCode)) {
                mapping.consumeClick();
                mapping.setDown(false);
                break;
            }
        }
    }
}
