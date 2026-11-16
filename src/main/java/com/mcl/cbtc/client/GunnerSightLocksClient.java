package com.mcl.cbtc.client;

import com.mcl.cbtc.network.GunnerSightLocksPayload;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 客户端保存每件瞄准组件的锁定表（实体网络 id → 锁定序号）。
 * <p>
 * 服务端每次变化都整表下发，这里直接替换；不在表里的目标就是未锁定。
 * 实体 id 只在同一维度内有效，因此同时记下维度，换维度后旧表不再生效。
 */
public final class GunnerSightLocksClient {
    /** 一件组件的锁定表：所属维度 + 实体 id 到序号的映射。 */
    private record Table(String dimension, Map<Integer, Integer> locks) {
    }

    private static final Map<String, Table> TABLES = new HashMap<>();

    private GunnerSightLocksClient() {
    }

    public static void accept(GunnerSightLocksPayload payload) {
        Map<Integer, Integer> map = new LinkedHashMap<>();
        for (GunnerSightLocksPayload.Entry entry : payload.entries()) {
            map.put(entry.entityId(), entry.seq());
        }
        TABLES.put(payload.sightId(), new Table(payload.dimension(), map));
    }

    /** 当前维度下某件组件的锁定表：实体 id → 序号。 */
    public static Map<Integer, Integer> locks(String sightId, String dimension) {
        Table table = TABLES.get(sightId);
        return table == null || !table.dimension().equals(dimension) ? Map.of() : table.locks();
    }

    /** 断开连接时清空。 */
    public static void clearAll() {
        TABLES.clear();
    }
}
