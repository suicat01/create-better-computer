package com.mcl.cbtc.compat;

import net.neoforged.fml.ModList;

/**
 * 饰品栏（Curios）软依赖闸门。
 * <p>
 * 本类不引用任何 Curios 类型，因此未安装该模组时也能安全加载；
 * 真正访问它的方法放在 {@link CuriosCompat}，只有确认加载后才调用。
 */
public final class Curios {
    /** 饰品栏的 modid。 */
    public static final String MOD_ID = "curios";

    private Curios() {
    }

    /** 是否装了饰品栏模组。 */
    public static boolean loaded() {
        return ModList.get().isLoaded(MOD_ID);
    }
}
