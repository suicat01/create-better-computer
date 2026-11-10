package com.mcl.cbtc.registry;

import com.mcl.cbtc.CreateBetterComputer;
import com.mcl.cbtc.menu.ComputerMenu;
import com.mcl.cbtc.menu.ExpanderMenu;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class CbcMenus {
    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(Registries.MENU, CreateBetterComputer.MODID);

    public static final DeferredHolder<MenuType<?>, MenuType<ComputerMenu>> COMPUTER =
            MENUS.register("computer", () -> IMenuTypeExtension.create(ComputerMenu::new));

    public static final DeferredHolder<MenuType<?>, MenuType<ExpanderMenu>> EXPANDER =
            MENUS.register("expander", () -> IMenuTypeExtension.create(ExpanderMenu::new));

    public static final DeferredHolder<MenuType<?>, MenuType<com.mcl.cbtc.menu.SpeakerMenu>> SPEAKER =
            MENUS.register("speaker", () -> IMenuTypeExtension.create(com.mcl.cbtc.menu.SpeakerMenu::new));

    public static final DeferredHolder<MenuType<?>, MenuType<com.mcl.cbtc.menu.MusicModuleMenu>> MUSIC_MODULE =
            MENUS.register("music_module",
                    () -> IMenuTypeExtension.create(com.mcl.cbtc.menu.MusicModuleMenu::new));

    private CbcMenus() {
    }

    public static void register(IEventBus bus) {
        MENUS.register(bus);
    }
}
