package com.mcl.cbtc.core;

/**
 * 一根连线：从某个节点的输出口接到另一个节点的输入口。
 */
public record Wire(int fromNode, String fromPort, int toNode, String toPort) {
    public String key() {
        return key(fromNode, fromPort, toNode, toPort);
    }

    public static String key(int fromNode, String fromPort, int toNode, String toPort) {
        return fromNode + ":" + fromPort + ">" + toNode + ":" + toPort;
    }
}
