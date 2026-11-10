package com.mcl.cbtc.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 程序里的一块积木（节点实例）。
 */
public final class Node {
    private final int id;
    private final String typeId;
    private final List<String> params = new ArrayList<>();
    private float x;
    private float y;

    public Node(int id, NodeType type, float x, float y) {
        this.id = id;
        this.typeId = type.id();
        this.params.addAll(type.defaultParams());
        this.x = x;
        this.y = y;
    }

    private Node(int id, String typeId, List<String> params, float x, float y) {
        this.id = id;
        this.typeId = typeId;
        this.params.addAll(params);
        this.x = x;
        this.y = y;
        normalizeParams();
    }

    /**
     * 存档里的参数列表可能来自旧版本积木定义（数量比现在少或结构不同）。
     * 这里按当前类型补齐缺失项、截断多余项，否则写回参数时下标越界会被静默丢弃。
     * <p>
     * 声音类积木在开头新增过 {@code device} 参数，旧存档的参数要整体右移一位，
     * 不能直接补在末尾，否则音效 id 会被当成设备名、音量被当成音效 id。
     */
    private void normalizeParams() {
        NodeType type = type();
        if (type == null) {
            return;
        }
        List<String> defaults = type.defaultParams();
        int leading = leadingParamCount(type);
        if (params.size() + leading == defaults.size()) {
            // 旧存档：新参数加在开头，把旧值整体右移，空出的前导位用默认值填
            List<String> shifted = new ArrayList<>(defaults.subList(0, leading));
            shifted.addAll(params);
            params.clear();
            params.addAll(shifted);
            return;
        }
        while (params.size() < defaults.size()) {
            params.add(defaults.get(params.size()));
        }
        while (params.size() > defaults.size()) {
            params.remove(params.size() - 1);
        }
    }

    /**
     * 该类型在历史版本里"加在参数开头"的个数。
     * <p>
     * 声音类积木原本没有设备参数，是后来插到最前面的，所以记为 1。
     */
    private static int leadingParamCount(NodeType type) {
        return switch (type.id()) {
            case "play_sound", "stop_sound" -> 1;
            default -> 0;
        };
    }

    public int id() {
        return id;
    }

    public NodeType type() {
        return NodeTypes.byId(typeId).orElse(null);
    }

    public String typeId() {
        return typeId;
    }

    public float x() {
        return x;
    }

    public float y() {
        return y;
    }

    public void moveTo(float x, float y) {
        this.x = x;
        this.y = y;
    }

    public String param(int index) {
        return index >= 0 && index < params.size() ? params.get(index) : "";
    }

    public String param(String key) {
        NodeType type = type();
        if (type == null) {
            return "";
        }
        return param(type.paramIndex(key));
    }

    public void setParam(int index, String value) {
        if (index >= 0 && index < params.size()) {
            params.set(index, value);
        }
    }

    public void setParam(String key, String value) {
        NodeType type = type();
        if (type != null) {
            setParam(type.paramIndex(key), value);
        }
    }

    public List<String> params() {
        return params;
    }

    public Node copy(int newId) {
        return new Node(newId, typeId, params, x, y);
    }

    public static Optional<Node> of(String typeId, int id, float x, float y) {
        return NodeTypes.byId(typeId).map(t -> new Node(id, t, x, y));
    }

    static Node restore(int id, String typeId, List<String> params, float x, float y) {
        return new Node(id, typeId, params, x, y);
    }
}
