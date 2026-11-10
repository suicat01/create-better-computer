package com.mcl.cbtc.device;

import net.minecraft.world.item.ItemStack;

/**
 * 目标定位模块与航空学导航台之间的软依赖闸门。
 * <p>
 * 本类不引用任何航空学类型，因此可以无条件加载；真正的类型访问在
 * {@link com.mcl.cbtc.compat.TargetLocatorCompat}，只有确认装了 Simulated 才调用它。
 */
public final class TargetLocatorBridge {
    private TargetLocatorBridge() {
    }

    /** 给模块物品补上"导航目标"数据组件，导航台才会接受它。 */
    public static void ensureTarget(ItemStack stack) {
        if (DeviceAccessBridge.hasSimulated()) {
            com.mcl.cbtc.compat.TargetLocatorCompat.ensureTarget(stack);
        }
    }

    /** 给绑定指南针补上"导航目标"数据组件，导航台才会指向它。 */
    public static void ensureBoundCompassTarget(ItemStack stack) {
        if (DeviceAccessBridge.hasSimulated()) {
            com.mcl.cbtc.compat.TargetLocatorCompat.ensureBoundCompassTarget(stack);
        }
    }

    /** 读取模块的 X / Z；模块不在任何导航台里时返回 0。 */
    public static double read(String moduleId, String axis) {
        return DeviceAccessBridge.hasSimulated()
                ? com.mcl.cbtc.compat.TargetLocatorCompat.read(moduleId, axis)
                : 0;
    }

    /** 改写模块某一轴的坐标；成功写回返回 true。 */
    public static boolean write(String moduleId, String axis, double value) {
        return DeviceAccessBridge.hasSimulated()
                && com.mcl.cbtc.compat.TargetLocatorCompat.write(moduleId, axis, value);
    }
}
