package com.mcl.cbtc.compat;

import net.neoforged.fml.ModList;

/**
 * Create: Radars（create_radar）软依赖闸门。
 * <p>
 * 本类不引用任何 Radar 类型，未安装该模组时也能安全加载；
 * 真正的类型访问放在 {@link RadarCompat}，只有确认加载后才调用。
 */
public final class Radar {
    /** Create: Radars 的 modid。 */
    public static final String MOD_ID = "create_radar";

    private Radar() {
    }

    /** 是否装了雷达模组。 */
    public static boolean loaded() {
        return ModList.get().isLoaded(MOD_ID);
    }
}
