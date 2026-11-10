package com.mcl.cbtc.core;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

/**
 * 一段积木程序：节点 + 连线 + 位置。这是 CB电脑 存盘的核心数据。
 * <p>
 * 同类程序既在服务端（执行）也在客户端（编辑）存在，客户端编辑完通过 {@code ProgramSyncPayload} 全量回传。
 */
public final class Program {
    private static final int CURRENT_VERSION = 1;

    private final List<Node> nodes = new ArrayList<>();
    private final List<Wire> wires = new ArrayList<>();
    private int nextNodeId = 1;

    public List<Node> nodes() {
        return nodes;
    }

    public List<Wire> wires() {
        return wires;
    }

    public int allocateId() {
        return nextNodeId++;
    }

    public Node addNode(NodeType type, float x, float y) {
        Node node = new Node(allocateId(), type, x, y);
        nodes.add(node);
        return node;
    }

    public Optional<Node> node(int id) {
        for (Node n : nodes) {
            if (n.id() == id) {
                return Optional.of(n);
            }
        }
        return Optional.empty();
    }

    public void removeNode(int id) {
        nodes.removeIf(n -> n.id() == id);
        wires.removeIf(w -> w.fromNode() == id || w.toNode() == id);
    }

    /**
     * 接一根线。同一目标输入口只保留最后一根；FLOW 输出口同样只允许一根。
     *
     * @return 是否真的新增了连线
     */
    public boolean connect(int fromNode, String fromPort, int toNode, String toPort) {
        return connect(fromNode, fromPort, toNode, toPort, null);
    }

    /**
     * 接一根线，并用 {@code targetKind} 覆盖目标输入口的静态类型。
     * <p>
     * 有些积木同一个端口在不同模式下类型不同（例如「控制齿轮箱」的 value：
     * 定时/定角是数字，旋转至是角度）。静态声明只能写一种，
     * 所以由调用方按当前模式把真实类型传进来。
     */
    public boolean connect(int fromNode, String fromPort, int toNode, String toPort,
            PortKind targetKind) {
        if (fromNode == toNode) {
            return false;
        }
        Node from = node(fromNode).orElse(null);
        Node to = node(toNode).orElse(null);
        if (from == null || to == null) {
            return false;
        }
        NodeType.Port outPort = port(from.type(), fromPort, true);
        NodeType.Port inPort = port(to.type(), toPort, false);
        if (outPort == null || inPort == null) {
            return false;
        }
        PortKind inKind = targetKind == null ? inPort.kind() : targetKind;
        if (!inKind.accepts(outPort.kind())) {
            return false;
        }
        wires.removeIf(w -> w.toNode() == toNode && w.toPort().equals(toPort));
        if (outPort.kind() == PortKind.FLOW) {
            wires.removeIf(w -> w.fromNode() == fromNode && w.fromPort().equals(fromPort));
        }
        return wires.add(new Wire(fromNode, fromPort, toNode, toPort));
    }

    public void disconnect(int fromNode, String fromPort, int toNode, String toPort) {
        wires.removeIf(w -> w.fromNode() == fromNode && w.fromPort().equals(fromPort)
                && w.toNode() == toNode && w.toPort().equals(toPort));
    }

    public List<Wire> wiresTo(int node, String port) {
        List<Wire> result = new ArrayList<>();
        for (Wire w : wires) {
            if (w.toNode() == node && w.toPort().equals(port)) {
                result.add(w);
            }
        }
        return result;
    }

    public Optional<Wire> wireFrom(int node, String port) {
        for (Wire w : wires) {
            if (w.fromNode() == node && w.fromPort().equals(port)) {
                return Optional.of(w);
            }
        }
        return Optional.empty();
    }

    public List<Node> nodesOfType(String typeId) {
        List<Node> result = new ArrayList<>();
        for (Node n : nodes) {
            if (n.typeId().equals(typeId)) {
                result.add(n);
            }
        }
        return result;
    }

    public Program copy() {
        Program p = new Program();
        for (Node n : nodes) {
            p.nodes.add(n.copy(n.id()));
        }
        p.wires.addAll(wires);
        p.nextNodeId = nextNodeId;
        return p;
    }

    public void replaceWith(Program other) {
        nodes.clear();
        wires.clear();
        for (Node n : other.nodes) {
            nodes.add(n.copy(n.id()));
        }
        wires.addAll(other.wires);
        nextNodeId = Math.max(other.nextNodeId, 1);
    }

    /**
     * 把一组节点整体复制到新位置，并保留它们之间的连线。
     * <p>
     * 只复制两端都在 {@code ids} 里的连线；连到集合外部的线不复制，
     * 否则副本会凭空接上原积木所在的那一串。
     *
     * @param ids 要复制的节点 id
     * @param dx  相对原位置的横向偏移
     * @param dy  相对原位置的纵向偏移
     * @return 新创建的节点，按原顺序排列
     */
    public List<Node> duplicate(Collection<Integer> ids, float dx, float dy) {
        List<Node> created = new ArrayList<>();
        java.util.Map<Integer, Node> mapping = new java.util.HashMap<>();
        for (Node n : nodes) {
            if (!ids.contains(n.id())) {
                continue;
            }
            Node copy = n.copy(allocateId());
            copy.moveTo(n.x() + dx, n.y() + dy);
            created.add(copy);
            mapping.put(n.id(), copy);
        }
        for (Node copy : created) {
            nodes.add(copy);
        }
        for (Wire w : new ArrayList<>(wires)) {
            Node from = mapping.get(w.fromNode());
            Node to = mapping.get(w.toNode());
            if (from != null && to != null) {
                connect(from.id(), w.fromPort(), to.id(), w.toPort());
            }
        }
        return created;
    }

    public void clear() {
        nodes.clear();
        wires.clear();
        nextNodeId = 1;
    }

    public boolean isEmpty() {
        return nodes.isEmpty();
    }

    private static NodeType.Port port(NodeType type, String key, boolean output) {
        if (type == null) {
            return null;
        }
        for (NodeType.Port p : output ? type.outputs() : type.inputs()) {
            if (p.key().equals(key)) {
                return p;
            }
        }
        // if_else 的"否则如果"分支端口是按分支数动态生成的（else1、else2…），这里按执行流放行
        if (output && key.matches("else\\d+") && type.params().contains("branches")) {
            return new NodeType.Port(key, PortKind.FLOW);
        }
        // 对应分支的条件口（cond1、cond2…）
        if (!output && key.matches("cond\\d+") && type.params().contains("branches")) {
            return new NodeType.Port(key, PortKind.BOOL);
        }
        // 拼接文本追加出来的数据口（t1、t2…），只接收文本
        if (!output && key.matches("t\\d+") && type.params().contains("extra")) {
            return new NodeType.Port(key, PortKind.TEXT);
        }
        return null;
    }

    // ------------------------------------------------------------------ 序列化

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("Version", CURRENT_VERSION);
        tag.putInt("NextId", nextNodeId);

        ListTag nodeList = new ListTag();
        for (Node n : nodes) {
            CompoundTag nt = new CompoundTag();
            nt.putInt("Id", n.id());
            nt.putString("Type", n.typeId());
            nt.putFloat("X", n.x());
            nt.putFloat("Y", n.y());
            ListTag params = new ListTag();
            for (String s : n.params()) {
                params.add(net.minecraft.nbt.StringTag.valueOf(s));
            }
            nt.put("Params", params);
            nodeList.add(nt);
        }
        tag.put("Nodes", nodeList);

        ListTag wireList = new ListTag();
        for (Wire w : wires) {
            CompoundTag wt = new CompoundTag();
            wt.putInt("From", w.fromNode());
            wt.putString("FromPort", w.fromPort());
            wt.putInt("To", w.toNode());
            wt.putString("ToPort", w.toPort());
            wireList.add(wt);
        }
        tag.put("Wires", wireList);
        return tag;
    }

    public static Program load(CompoundTag tag) {
        Program p = new Program();
        ListTag nodeList = tag.getList("Nodes", Tag.TAG_COMPOUND);
        for (int i = 0; i < nodeList.size(); i++) {
            CompoundTag nt = nodeList.getCompound(i);
            List<String> params = new ArrayList<>();
            ListTag pt = nt.getList("Params", Tag.TAG_STRING);
            for (int j = 0; j < pt.size(); j++) {
                params.add(pt.getString(j));
            }
            p.nodes.add(Node.restore(nt.getInt("Id"), nt.getString("Type"), params,
                    nt.getFloat("X"), nt.getFloat("Y")));
        }
        ListTag wireList = tag.getList("Wires", Tag.TAG_COMPOUND);
        Set<String> seen = new LinkedHashSet<>();
        for (int i = 0; i < wireList.size(); i++) {
            CompoundTag wt = wireList.getCompound(i);
            Wire w = new Wire(wt.getInt("From"), wt.getString("FromPort"),
                    wt.getInt("To"), wt.getString("ToPort"));
            if (seen.add(w.key())) {
                p.wires.add(w);
            }
        }
        int maxId = 0;
        for (Node n : p.nodes) {
            maxId = Math.max(maxId, n.id());
        }
        p.nextNodeId = Math.max(tag.getInt("NextId"), maxId + 1);
        return p;
    }

    /**
     * 用于日志/调试的简报。
     */
    public String summary() {
        Map<String, Integer> counts = new HashMap<>();
        Collection<Node> all = nodes;
        for (Node n : all) {
            counts.merge(n.typeId(), 1, Integer::sum);
        }
        return "nodes=" + nodes.size() + " wires=" + wires.size() + " " + counts;
    }
}
