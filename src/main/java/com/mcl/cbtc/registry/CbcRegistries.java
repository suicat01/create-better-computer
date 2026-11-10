package com.mcl.cbtc.registry;

import com.mcl.cbtc.CreateBetterComputer;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * 全局注册入口。各子系统（方块、物品、菜单、数据源等）在这里挂载。
 */
public final class CbcRegistries {
    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, CreateBetterComputer.MODID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> MAIN_TAB = TABS.register("main",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.cbtc"))
                    .withTabsBefore(CreativeModeTabs.REDSTONE_BLOCKS)
                    .icon(() -> CbcBlocks.COMPUTER.get().asItem().getDefaultInstance())
                    .displayItems((params, output) -> {
                        output.accept(CbcBlocks.COMPUTER.get());
                        output.accept(CbcBlocks.EXPANDER.get());
                        output.accept(CbcBlocks.TYPEWRITER.get());
                        output.accept(CbcBlocks.GEARBOX.get());
                        output.accept(CbcBlocks.SPEAKER.get());
                        output.accept(CbcBlocks.LECTURE_STONE.get());
                        output.accept(CbcBlocks.LECTURE_STONE_COBBLESTONE.get());
                        output.accept(CbcBlocks.LECTURE_STONE_ANDESITE.get());
                        output.accept(CbcBlocks.LECTURE_STONE_DEEPSLATE.get());
                        output.accept(CbcBlocks.LECTURE_STONE_STONE_BRICKS.get());
                        output.accept(CbcBlocks.LECTURE_STONE_GRANITE.get());
                        output.accept(CbcBlocks.LECTURE_STONE_MOSSY_COBBLESTONE.get());
                        output.accept(CbcBlocks.LECTURE_STONE_MOSSY_STONE_BRICKS.get());
                        output.accept(CbcBlocks.LECTURE_STONE_DIORITE.get());
                        output.accept(CbcBlocks.INFO_TRANSMITTER.get());
                        output.accept(CbcBlocks.SPEED_CONTROLLER.get());
                        output.accept(CbcItems.STORAGE_LINK_MODULE.get());
                        output.accept(CbcItems.DISPLAY_LINK_MODULE.get());
                        output.accept(CbcItems.WIRELESS_REDSTONE_MODULE.get());
                        output.accept(CbcItems.LAN_MODULE.get());
                        output.accept(CbcItems.DEVICE_MODULE.get());
                        output.accept(CbcItems.INTERNET_MODULE.get());
                        output.accept(CbcItems.SPEAKER_MODULE.get());
                        output.accept(CbcItems.HARD_DRIVE.get());
                        output.accept(CbcItems.TARGET_LOCATOR.get());
                        output.accept(CbcItems.LODESTONE_CHARM.get());
                        output.accept(CbcItems.BOUND_COMPASS.get());
                        output.accept(CbcItems.MUSIC_MODULE.get());
                        output.accept(CbcItems.GEARBOX_ANGLE_TOOL.get());
                        output.accept(CbcItems.BREEZE_RADIATOR.get());
                        output.accept(CbcItems.UNCHARGED_SCULPTING_HAND.get());
                        output.accept(CbcItems.SCULPTING_HAND.get());
                        output.accept(CbcItems.ALLAY_SOUND_CARD.get());
                        output.accept(CbcItems.LAPIS_SHEET.get());
                        output.accept(CbcItems.INTEGRATED_CIRCUIT.get());
                        output.accept(CbcItems.PROTOTYPE_MODULE_CARD.get());
                        output.accept(CbcItems.BLAZE_COMPUTE_CHIP.get());
                        output.accept(CbcItems.SUPER_BLAZE_SPAWN_EGG.get());
                        output.accept(CbcItems.SUPER_BLAZE_ROD.get());
                        output.accept(CbcItems.SUPER_BLAZE_POWDER.get());
                        output.accept(CbcItems.BLAZE_SUPERCOMPUTER_UNIT.get());
                        output.accept(CbcItems.SHULKER_CHIP.get());
                    })
                    .build());

    private CbcRegistries() {
    }

    public static void register(IEventBus modEventBus) {
        CbcBlocks.register(modEventBus);
        CbcItems.register(modEventBus);
        CbcEntities.register(modEventBus);
        CbcPotions.register(modEventBus);
        CbcBlockEntities.register(modEventBus);
        CbcMenus.register(modEventBus);
        CbcRecipes.register(modEventBus);
        TABS.register(modEventBus);
        // 目标定位模块的导航目标注册到航空学的注册表里；没装航空学时跳过
        if (com.mcl.cbtc.device.DeviceAccessBridge.hasSimulated()) {
            com.mcl.cbtc.compat.TargetLocatorCompat.register(modEventBus);
        }
        // 超级烈焰粉 → 力量 III 药水的酿造配方（该事件在游戏总线触发，不在 mod 总线）
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(CbcRegistries::registerBrewingRecipes);
    }

    private static void registerBrewingRecipes(net.neoforged.neoforge.event.brewing.RegisterBrewingRecipesEvent event) {
        net.minecraft.world.item.alchemy.PotionBrewing.Builder builder = event.getBuilder();
        builder.addMix(net.minecraft.world.item.alchemy.Potions.AWKWARD, CbcItems.SUPER_BLAZE_POWDER.get(),
                CbcPotions.STRENGTH_3);
        builder.addMix(CbcPotions.STRENGTH_3, net.minecraft.world.item.Items.REDSTONE,
                CbcPotions.LONG_STRENGTH_3);
        builder.addMix(CbcPotions.STRENGTH_3, net.minecraft.world.item.Items.GLOWSTONE_DUST,
                CbcPotions.STRONG_STRENGTH_3);
    }
}
