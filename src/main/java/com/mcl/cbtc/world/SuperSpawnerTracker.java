package com.mcl.cbtc.world;

import com.mcl.cbtc.event.SuperSpawnerEvents;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * 记录被喂过烈焰蛋糕的刷怪笼及其还原时刻，随存档保存，重启后仍能按时还原。
 */
public final class SuperSpawnerTracker extends SavedData {
    private static final String NAME = "cbtc_super_spawners";
    private static final String ENTRIES_TAG = "entries";

    private final Map<Entry, Long> deadlines = new HashMap<>();

    public static SuperSpawnerTracker get(MinecraftServer server) {
        return server.overworld()
                .getDataStorage()
                .computeIfAbsent(new Factory<>(SuperSpawnerTracker::new, SuperSpawnerTracker::load, null), NAME);
    }

    public void schedule(Level level, BlockPos pos, long until) {
        deadlines.put(new Entry(level.dimension().location(), pos.immutable()), until);
        setDirty();
    }

    /** 每 tick 检查一次，只处理已加载区块里的刷怪笼，未加载的等加载后再还原。 */
    public void tick(MinecraftServer server) {
        if (deadlines.isEmpty()) {
            return;
        }
        long gameTime = server.overworld().getGameTime();
        deadlines.entrySet().removeIf(entry -> {
            Entry key = entry.getKey();
            if (gameTime < entry.getValue()) {
                return false;
            }
            ServerLevel level = server.getLevel(ResourceKey.create(Registries.DIMENSION, key.dimension()));
            if (level == null || !level.hasChunkAt(key.pos())) {
                return false;
            }
            SuperSpawnerEvents.revert(level, key.pos());
            setDirty();
            return true;
        });
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (Map.Entry<Entry, Long> entry : deadlines.entrySet()) {
            CompoundTag item = new CompoundTag();
            item.putString("dimension", entry.getKey().dimension().toString());
            item.putLong("pos", entry.getKey().pos().asLong());
            item.putLong("until", entry.getValue());
            list.add(item);
        }
        tag.put(ENTRIES_TAG, list);
        return tag;
    }

    public static SuperSpawnerTracker load(CompoundTag tag, HolderLookup.Provider registries) {
        SuperSpawnerTracker data = new SuperSpawnerTracker();
        ListTag list = tag.getList(ENTRIES_TAG, Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag item = list.getCompound(i);
            ResourceLocation dimension = ResourceLocation.tryParse(item.getString("dimension"));
            if (dimension == null) {
                continue;
            }
            data.deadlines.put(new Entry(dimension, BlockPos.of(item.getLong("pos"))), item.getLong("until"));
        }
        return data;
    }

    private record Entry(ResourceLocation dimension, BlockPos pos) {
    }
}
