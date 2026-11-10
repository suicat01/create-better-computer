package com.mcl.cbtc.item;

import net.minecraft.world.item.Item;

/**
 * 升级模块的基类。目前只作为"可以放进 CB 拓展器槽位"的标记，
 * 具体模块在后续更新中继承本类添加。
 */
public class UpgradeModuleItem extends Item {
    public UpgradeModuleItem(Properties properties) {
        super(properties);
    }
}
