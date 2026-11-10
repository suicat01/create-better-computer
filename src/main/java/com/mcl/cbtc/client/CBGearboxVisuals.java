package com.mcl.cbtc.client;

import com.mcl.cbtc.registry.CbcBlockEntities;
import com.simibubi.create.content.kinetics.transmission.SplitShaftVisual;
import dev.engine_room.flywheel.api.visualization.VisualizerRegistry;
import dev.engine_room.flywheel.lib.visualization.SimpleBlockEntityVisualizer;

/**
 * CB编程齿轮箱的 Flywheel 可视化注册。
 * <p>
 * Create 的动能方块在开启 Flywheel 可视化时不会走 {@code SplitShaftRenderer}
 * （它在第一行就检测可视化并直接返回），传动轴完全由可视化器绘制。
 * 我们新增的方块实体类型没有现成的可视化器，所以必须在这里手动注册，
 * 否则方块本体能显示，但两端的传动轴一根都看不到。
 */
public final class CBGearboxVisuals {
    private CBGearboxVisuals() {
    }

    /** 在客户端初始化时调用；注册一次即可。 */
    public static void register() {
        VisualizerRegistry.setVisualizer(CbcBlockEntities.GEARBOX.get(),
                SimpleBlockEntityVisualizer.<com.mcl.cbtc.blockentity.CBGearboxBlockEntity>builder(
                                CbcBlockEntities.GEARBOX.get())
                        .factory(SplitShaftVisual::new)
                        .skipVanillaRender(be -> true)
                        .apply());
    }
}
