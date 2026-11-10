package com.mcl.cbtc.core;

import java.util.List;

/**
 * 一个积木节点的静态描述。节点类型用字符串 id 标识，实例只保存 id + 参数 + 位置。
 */
public record NodeType(
        String id,
        NodeCategory category,
        String nameKey,
        List<Port> inputs,
        List<Port> outputs,
        /** 在界面上可以编辑的文本参数（例如变量名、频道名、常量） */
        List<String> params,
        /** 节点默认参数 */
        List<String> defaultParams
) {
    public record Port(String key, PortKind kind) {
    }

    public String translationKey() {
        return "cbtc.node." + id;
    }

    public int paramIndex(String key) {
        return params.indexOf(key);
    }
}
