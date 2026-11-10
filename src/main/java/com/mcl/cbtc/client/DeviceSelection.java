package com.mcl.cbtc.client;

import net.minecraft.core.BlockPos;

/**
 * 客户端记住"外设绑定模块卡当前选中的方块"，用于画方块描边。
 * <p>
 * 选中本身由服务端判定并通过 {@code DeviceSelectPayload} 下发，这里只存显示用的坐标。
 */
public final class DeviceSelection {
    private static BlockPos selected;
    private static net.minecraft.resources.ResourceLocation selectedDimension;

    private DeviceSelection() {
    }

    public static void set(BlockPos pos, net.minecraft.resources.ResourceLocation dimension) {
        selected = pos;
        selectedDimension = dimension;
    }

    public static void clear() {
        selected = null;
        selectedDimension = null;
    }

    /** 当前选中的方块；没有选中时为 null。 */
    public static BlockPos selected() {
        return selected;
    }

    /** 选中方块所在的维度；没有选中时为 null。 */
    public static net.minecraft.resources.ResourceLocation dimension() {
        return selectedDimension;
    }
}
