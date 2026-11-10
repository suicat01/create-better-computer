package com.mcl.cbtc.client;

import com.mcl.cbtc.registry.CbcBlockEntities;
import com.simibubi.create.content.kinetics.transmission.SplitShaftVisual;
import dev.engine_room.flywheel.api.visualization.VisualizerRegistry;
import dev.engine_room.flywheel.lib.visualization.SimpleBlockEntityVisualizer;

/**
 * 智能转速控制器的 Flywheel 可视化注册。
 * <p>
 * Create 的双端传动轴方块在开启可视化时不走 {@code SplitShaftRenderer}
 * （渲染器第一行检测到可视化就返回），传动轴完全由可视化器绘制。
 * 新增方块实体类型必须在这里注册，否则两端看不到轴。
 */
public final class SmartSpeedControllerVisuals {
    private SmartSpeedControllerVisuals() {
    }

    public static void register() {
        VisualizerRegistry.setVisualizer(CbcBlockEntities.SPEED_CONTROLLER.get(),
                SimpleBlockEntityVisualizer
                        .<com.mcl.cbtc.blockentity.SmartSpeedControllerBlockEntity>builder(
                                CbcBlockEntities.SPEED_CONTROLLER.get())
                        .factory(SplitShaftVisual::new)
                        .skipVanillaRender(be -> true)
                        .apply());
    }
}
