package com.mcl.cbtc.client;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;

/**
 * 默念浮字的客户端状态：记录每个正在显示的文字及其剩余时间。
 * <p>
 * 只存客户端内存，不进存档。每条记录绑定一个设备的方块坐标，
 * 由 {@link MurmurRenderer} 在方块实体渲染阶段画在方块上方。
 * 坐标用设备自身的方块坐标（结构移动时它不变），这样文字才能跟着结构一起动。
 */
public final class MurmurTexts {
    /**
     * 一条正在显示的默念文字。
     * <p>
     * {@code key} 是发声设备的稳定身份：同一设备重复默念只更新文字与时限，
     * 不会叠出多条浮字。
     */
    public record Entry(String key, BlockPos pos, String text, long expireAt) {
    }

    /** 显示时长（毫秒）。 */
    private static final long LIFETIME_MS = 4000;

    private static final List<Entry> ACTIVE = new ArrayList<>();

    private MurmurTexts() {
    }

    /** 登记一条默念文字：同一发声设备只保留最新一条。 */
    public static void add(String key, BlockPos pos, String text) {
        long expire = System.currentTimeMillis() + LIFETIME_MS;
        String id = key == null || key.isBlank() ? pos.asLong() + "" : key;
        synchronized (ACTIVE) {
            ACTIVE.removeIf(e -> e.key().equals(id));
            ACTIVE.add(new Entry(id, pos, text, expire));
        }
    }

    /** 取该方块上当前有效的文字，顺带清掉过期的。 */
    public static List<Entry> at(BlockPos pos) {
        long now = System.currentTimeMillis();
        synchronized (ACTIVE) {
            ACTIVE.removeIf(e -> e.expireAt() <= now);
            List<Entry> result = new ArrayList<>();
            for (Entry entry : ACTIVE) {
                if (entry.pos().equals(pos)) {
                    result.add(entry);
                }
            }
            return result;
        }
    }
}
