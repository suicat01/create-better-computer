package com.mcl.cbtc.device;

import com.mcl.cbtc.CreateBetterComputer;
import com.mcl.cbtc.item.GunRemoteItem;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/**
 * 枪式遥控器的服务端输入状态。
 * <p>
 * 客户端进入使用状态时登记"玩家 → 遥控器 ID"，之后鼠标左右键的按下/松开都记到这个遥控器名下；
 * 积木读取某个已绑定遥控器的按键时直接查这里。玩家登出或不再持有该遥控器时自动清空。
 */
@EventBusSubscriber(modid = CreateBetterComputer.MODID)
public final class GunRemoteState {
    /** 玩家 UUID → 正在使用的遥控器 ID。 */
    private static final Map<UUID, String> ACTIVE = new ConcurrentHashMap<>();
    /** 遥控器 ID → 当前按下的鼠标键（GLFW 鼠标键码）。 */
    private static final Map<String, Set<Integer>> PRESSED = new ConcurrentHashMap<>();

    private GunRemoteState() {
    }

    /** 进入使用状态。玩家手里没有这件遥控器时不登记。 */
    public static void start(ServerPlayer player, String remoteId) {
        if (remoteId == null || remoteId.isBlank() || !holds(player, remoteId)) {
            return;
        }
        ACTIVE.put(player.getUUID(), remoteId);
        PRESSED.put(remoteId, ConcurrentHashMap.newKeySet());
    }

    /** 退出使用状态，同时清掉这个遥控器的按键记录。 */
    public static void stop(Player player, String remoteId) {
        if (remoteId == null) {
            return;
        }
        ACTIVE.remove(player.getUUID(), remoteId);
        PRESSED.remove(remoteId);
    }

    /** 记录一次鼠标键变化；玩家已经不再持有该遥控器时顺手结束使用状态。 */
    public static void setPressed(Player player, String remoteId, int key, boolean down) {
        if (remoteId == null || !remoteId.equals(ACTIVE.get(player.getUUID()))) {
            return;
        }
        if (!holds(player, remoteId)) {
            clear(player.getUUID());
            return;
        }
        Set<Integer> keys = PRESSED.computeIfAbsent(remoteId, id -> ConcurrentHashMap.newKeySet());
        if (down) {
            keys.add(key);
        } else {
            keys.remove(key);
        }
    }

    /** 某个遥控器当前按下的鼠标键；没有记录时返回空列表。 */
    public static List<Integer> pressed(String remoteId) {
        Set<Integer> keys = PRESSED.get(remoteId);
        return keys == null ? List.of() : List.copyOf(keys);
    }

    /** 玩家是否正持有这件遥控器（主手或副手）。 */
    public static boolean holds(Player player, String remoteId) {
        for (InteractionHand hand : InteractionHand.values()) {
            ItemStack stack = player.getItemInHand(hand);
            if (stack.getItem() instanceof GunRemoteItem && remoteId.equals(GunRemoteItem.id(stack))) {
                return true;
            }
        }
        return false;
    }

    /** 清掉某个玩家的使用状态与残留按键。 */
    public static void clear(UUID playerId) {
        String remoteId = ACTIVE.remove(playerId);
        if (remoteId != null) {
            PRESSED.remove(remoteId);
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        clear(event.getEntity().getUUID());
    }
}
