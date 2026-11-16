package com.mcl.cbtc.device;

import com.mcl.cbtc.item.GunnerSightItem;
import com.mcl.cbtc.registry.CbcItems;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * 瞄准组件的锁定目标表。
 * <p>
 * 每件瞄准组件（按物品 ID）维护一份有序锁定表，序号从 1 开始递增；
 * 数据只存在服务端内存里，随世界重启清空。
 */
public final class GunnerSightLocks {
    /** 一条锁定记录：目标 UUID、锁定序号与所在维度。 */
    public record Locked(UUID entity, int seq, ResourceKey<Level> dimension) {
    }

    private static final Map<String, List<Locked>> LOCKS = new HashMap<>();

    private GunnerSightLocks() {
    }

    /** 锁定一个目标；已经锁过时返回原序号。 */
    public static synchronized int lock(String sightId, ServerLevel level, Entity entity) {
        List<Locked> list = LOCKS.computeIfAbsent(sightId, key -> new ArrayList<>());
        for (Locked locked : list) {
            if (locked.entity().equals(entity.getUUID())) {
                return locked.seq();
            }
        }
        int seq = 0;
        for (Locked locked : list) {
            seq = Math.max(seq, locked.seq());
        }
        seq++;
        list.add(new Locked(entity.getUUID(), seq, level.dimension()));
        return seq;
    }

    /** 当前锁定表快照。 */
    public static synchronized List<Locked> all(String sightId) {
        return List.copyOf(LOCKS.getOrDefault(sightId, List.of()));
    }

    /** 清空某件组件的全部锁定。 */
    public static synchronized void clearAll(String sightId) {
        LOCKS.remove(sightId);
    }

    /** 清掉最后锁定的那个目标；没有锁定时返回 false。 */
    public static synchronized boolean clearLast(String sightId) {
        List<Locked> list = LOCKS.get(sightId);
        if (list == null || list.isEmpty()) {
            return false;
        }
        int best = -1;
        int bestSeq = Integer.MIN_VALUE;
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).seq() > bestSeq) {
                bestSeq = list.get(i).seq();
                best = i;
            }
        }
        list.remove(best);
        if (list.isEmpty()) {
            LOCKS.remove(sightId);
        }
        return true;
    }

    /** 移除单个目标（目标死亡时调用）。 */
    public static synchronized void remove(String sightId, UUID entity) {
        List<Locked> list = LOCKS.get(sightId);
        if (list == null) {
            return;
        }
        list.removeIf(locked -> locked.entity().equals(entity));
        if (list.isEmpty()) {
            LOCKS.remove(sightId);
        }
    }

    /** 玩家是否戴着指定 ID 的瞄准组件（头部槽位或饰品栏）。 */
    public static boolean wears(ServerPlayer player, String sightId) {
        ItemStack head = player.getItemBySlot(EquipmentSlot.HEAD);
        if (head.getItem() instanceof GunnerSightItem && sightId.equals(GunnerSightItem.id(head))) {
            return true;
        }
        if (com.mcl.cbtc.compat.Curios.loaded()) {
            ItemStack curio = com.mcl.cbtc.compat.CuriosCompat.findWearing(player, CbcItems.GUNNER_SIGHT.get());
            return !curio.isEmpty() && sightId.equals(GunnerSightItem.id(curio));
        }
        return false;
    }

    /** 把某件组件的锁定表同步给所有戴着它的玩家；每个玩家只收到自己维度的目标。 */
    public static void sync(ServerLevel anyLevel, String sightId) {
        List<Locked> locks = all(sightId);
        for (ServerLevel level : anyLevel.getServer().getAllLevels()) {
            List<com.mcl.cbtc.network.GunnerSightLocksPayload.Entry> entries = new ArrayList<>(locks.size());
            for (Locked locked : locks) {
                if (!locked.dimension().equals(level.dimension())) {
                    continue;
                }
                Entity entity = level.getEntity(locked.entity());
                if (entity != null) {
                    entries.add(new com.mcl.cbtc.network.GunnerSightLocksPayload.Entry(entity.getId(), locked.seq()));
                }
            }
            var payload = new com.mcl.cbtc.network.GunnerSightLocksPayload(sightId,
                    level.dimension().location().toString(), entries);
            for (ServerPlayer player : level.players()) {
                if (wears(player, sightId)) {
                    net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player, payload);
                }
            }
        }
    }
}
