package com.mcl.cbtc.lan;

import java.util.HashMap;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * 局域网注册表（世界存档）。
 * <p>
 * 规则：某个 ID 第一次被使用时，用当次输入的密码注册；之后同 ID 必须密码一致才能加入。
 * 这里只保存 ID → 密码，成员关系与运行状态在 {@link LanBus}。
 */
public final class LanRegistry extends SavedData {
    private static final String DATA_NAME = "cbtc_lan";
    /** 局域网 ID → 密码。 */
    private final Map<String, String> passwords = new HashMap<>();

    public static LanRegistry get(MinecraftServer server) {
        return server.overworld().getDataStorage()
                .computeIfAbsent(new Factory<>(LanRegistry::new, LanRegistry::load), DATA_NAME);
    }

    private static LanRegistry load(CompoundTag tag, HolderLookup.Provider registries) {
        LanRegistry registry = new LanRegistry();
        CompoundTag entries = tag.getCompound("Networks");
        for (String id : entries.getAllKeys()) {
            registry.passwords.put(id, entries.getString(id));
        }
        return registry;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        CompoundTag entries = new CompoundTag();
        passwords.forEach(entries::putString);
        tag.put("Networks", entries);
        return tag;
    }

    /** 加入结果。 */
    public enum Result {
        /** 新建并注册成功。 */
        CREATED,
        /** 密码匹配，加入成功。 */
        JOINED,
        /** 密码不匹配。 */
        WRONG_PASSWORD
    }

    /** 按 1A 规则尝试加入：ID 首次使用则用该密码注册，否则校验。 */
    public Result join(String lanId, String password) {
        String id = normalize(lanId);
        String stored = passwords.get(id);
        if (stored == null) {
            passwords.put(id, password);
            setDirty();
            return Result.CREATED;
        }
        return stored.equals(password) ? Result.JOINED : Result.WRONG_PASSWORD;
    }

    /** 该 ID 是否已被注册。 */
    public boolean exists(String lanId) {
        return passwords.containsKey(normalize(lanId));
    }

    @Nullable
    public String passwordOf(String lanId) {
        return passwords.get(normalize(lanId));
    }

    /** 保留可见字符、去掉首尾空白并转小写，避免大小写与空格导致同网不同 ID。 */
    public static String normalize(String raw) {
        return raw == null ? "" : raw.trim().toLowerCase(java.util.Locale.ROOT);
    }
}
