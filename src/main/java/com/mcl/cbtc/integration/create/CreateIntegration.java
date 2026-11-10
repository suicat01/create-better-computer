package com.mcl.cbtc.integration.create;

import com.mcl.cbtc.CreateBetterComputer;
import com.simibubi.create.api.behaviour.display.DisplaySource;
import com.simibubi.create.api.behaviour.display.DisplayTarget;
import com.simibubi.create.api.registry.CreateRegistries;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * 把 CB电脑 的显示数据源、信息转发器的显示目标注册进 Create 的对应注册表。
 */
public final class CreateIntegration {
    private static final DeferredRegister<DisplaySource> DISPLAY_SOURCES =
            DeferredRegister.create(CreateRegistries.DISPLAY_SOURCE, CreateBetterComputer.MODID);
    private static final DeferredRegister<DisplayTarget> DISPLAY_TARGETS =
            DeferredRegister.create(CreateRegistries.DISPLAY_TARGET, CreateBetterComputer.MODID);

    public static final DeferredHolder<DisplaySource, ComputerDisplaySource> COMPUTER_SOURCE =
            DISPLAY_SOURCES.register("computer", ComputerDisplaySource::new);

    public static final DeferredHolder<DisplayTarget, InfoTransmitterDisplayTarget> INFO_TRANSMITTER_TARGET =
            DISPLAY_TARGETS.register("info_transmitter", InfoTransmitterDisplayTarget::new);

    private CreateIntegration() {
    }

    public static void register(IEventBus bus) {
        DISPLAY_SOURCES.register(bus);
        DISPLAY_TARGETS.register(bus);
        CreateBetterComputer.LOGGER.info("已接入机械动力：红石链接频道 / 显示链接数据源 / 信息转发器显示目标");
    }

    /**
     * 注册阶段结束后，把 CB电脑 方块登记为显示数据源、信息转发器登记为显示目标
     * （Create 用静态表按方块 / 方块实体查找）。
     */
    public static void bindBlocks() {
        DisplaySource.BY_BLOCK.add(com.mcl.cbtc.registry.CbcBlocks.COMPUTER.get(), COMPUTER_SOURCE.get());
        DisplayTarget.BY_BLOCK_ENTITY.register(com.mcl.cbtc.registry.CbcBlockEntities.INFO_TRANSMITTER.get(),
                INFO_TRANSMITTER_TARGET.get());
    }
}
