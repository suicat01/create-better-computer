package com.mcl.cbtc.compat;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.neoforged.fml.ModList;

/**
 * 航空学（Simulated）音效的软依赖入口。
 * <p>
 * 按键音与打字机同款：按下用 TAP、松开用 UNTAP。未安装 Simulated 时不触碰它的类型。
 */
public final class SimulatedSounds {
    private static final String SIMULATED = "simulated";

    private SimulatedSounds() {
    }

    public static boolean loaded() {
        return ModList.get().isLoaded(SIMULATED);
    }

    /** 播放一次打字机同款键音。 */
    public static void key(Level level, BlockPos pos, boolean down) {
        var sound = down
                ? dev.simulated_team.simulated.index.SimSoundEvents.LINKED_TYPEWRITER_TAP
                : dev.simulated_team.simulated.index.SimSoundEvents.LINKED_TYPEWRITER_UNTAP;
        sound.playAt(level, pos, 1.0F, 1.0F, true);
    }
}
