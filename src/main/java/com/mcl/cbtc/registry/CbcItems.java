package com.mcl.cbtc.registry;

import com.mcl.cbtc.CreateBetterComputer;
import com.mcl.cbtc.item.StorageLinkModuleItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class CbcItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(CreateBetterComputer.MODID);

    public static final DeferredItem<com.mcl.cbtc.item.BlockEntityCarrierItem> COMPUTER =
            ITEMS.register("computer", () -> new com.mcl.cbtc.item.BlockEntityCarrierItem(
                    CbcBlocks.COMPUTER.get(), CbcBlockEntities.COMPUTER::get,
                    new Item.Properties()));

    public static final DeferredItem<com.mcl.cbtc.item.BlockEntityCarrierItem> EXPANDER =
            ITEMS.register("expander", () -> new com.mcl.cbtc.item.BlockEntityCarrierItem(
                    CbcBlocks.EXPANDER.get(), CbcBlockEntities.EXPANDER::get,
                    new Item.Properties()));

    public static final DeferredItem<BlockItem> TYPEWRITER =
            ITEMS.registerSimpleBlockItem(CbcBlocks.TYPEWRITER);

    public static final DeferredItem<BlockItem> GEARBOX =
            ITEMS.registerSimpleBlockItem(CbcBlocks.GEARBOX);

    public static final DeferredItem<BlockItem> DIAGRAM_SENSOR =
            ITEMS.registerSimpleBlockItem(CbcBlocks.DIAGRAM_SENSOR);

    public static final DeferredItem<BlockItem> ORIGINAL_GEARSHIFT =
            ITEMS.registerSimpleBlockItem(CbcBlocks.ORIGINAL_GEARSHIFT);

    public static final DeferredItem<BlockItem> INFO_TRANSMITTER =
            ITEMS.registerSimpleBlockItem(CbcBlocks.INFO_TRANSMITTER);

    public static final DeferredItem<BlockItem> SPEED_CONTROLLER =
            ITEMS.registerSimpleBlockItem(CbcBlocks.SPEED_CONTROLLER);

    public static final DeferredItem<com.mcl.cbtc.item.BlockEntityCarrierItem> SPEAKER =
            ITEMS.register("speaker", () -> new com.mcl.cbtc.item.BlockEntityCarrierItem(
                    CbcBlocks.SPEAKER.get(), CbcBlockEntities.SPEAKER::get,
                    new Item.Properties()));

    public static final DeferredItem<BlockItem> LECTURE_STONE =
            ITEMS.registerSimpleBlockItem(CbcBlocks.LECTURE_STONE);

    public static final DeferredItem<BlockItem> LECTURE_STONE_COBBLESTONE =
            ITEMS.registerSimpleBlockItem(CbcBlocks.LECTURE_STONE_COBBLESTONE);
    public static final DeferredItem<BlockItem> LECTURE_STONE_ANDESITE =
            ITEMS.registerSimpleBlockItem(CbcBlocks.LECTURE_STONE_ANDESITE);
    public static final DeferredItem<BlockItem> LECTURE_STONE_DEEPSLATE =
            ITEMS.registerSimpleBlockItem(CbcBlocks.LECTURE_STONE_DEEPSLATE);
    public static final DeferredItem<BlockItem> LECTURE_STONE_STONE_BRICKS =
            ITEMS.registerSimpleBlockItem(CbcBlocks.LECTURE_STONE_STONE_BRICKS);
    public static final DeferredItem<BlockItem> LECTURE_STONE_GRANITE =
            ITEMS.registerSimpleBlockItem(CbcBlocks.LECTURE_STONE_GRANITE);
    public static final DeferredItem<BlockItem> LECTURE_STONE_MOSSY_COBBLESTONE =
            ITEMS.registerSimpleBlockItem(CbcBlocks.LECTURE_STONE_MOSSY_COBBLESTONE);
    public static final DeferredItem<BlockItem> LECTURE_STONE_MOSSY_STONE_BRICKS =
            ITEMS.registerSimpleBlockItem(CbcBlocks.LECTURE_STONE_MOSSY_STONE_BRICKS);
    public static final DeferredItem<BlockItem> LECTURE_STONE_DIORITE =
            ITEMS.registerSimpleBlockItem(CbcBlocks.LECTURE_STONE_DIORITE);

    public static final DeferredItem<StorageLinkModuleItem> STORAGE_LINK_MODULE =
            ITEMS.register("storage_link_module", () -> new StorageLinkModuleItem(
                    new Item.Properties().stacksTo(1)));

    public static final DeferredItem<com.mcl.cbtc.item.DisplayLinkModuleItem> DISPLAY_LINK_MODULE =
            ITEMS.register("display_link_module", () -> new com.mcl.cbtc.item.DisplayLinkModuleItem(
                    new Item.Properties().stacksTo(1)));

    public static final DeferredItem<com.mcl.cbtc.item.WirelessRedstoneModuleItem> WIRELESS_REDSTONE_MODULE =
            ITEMS.register("wireless_redstone_module", () -> new com.mcl.cbtc.item.WirelessRedstoneModuleItem(
                    new Item.Properties().stacksTo(1)));

    public static final DeferredItem<com.mcl.cbtc.item.LanModuleItem> LAN_MODULE =
            ITEMS.register("lan_module", () -> new com.mcl.cbtc.item.LanModuleItem(
                    new Item.Properties().stacksTo(1)));

    public static final DeferredItem<com.mcl.cbtc.item.DeviceModuleItem> DEVICE_MODULE =
            ITEMS.register("device_module", () -> new com.mcl.cbtc.item.DeviceModuleItem(
                    new Item.Properties().stacksTo(1)));

    public static final DeferredItem<com.mcl.cbtc.item.InternetModuleItem> INTERNET_MODULE =
            ITEMS.register("internet_module", () -> new com.mcl.cbtc.item.InternetModuleItem(
                    new Item.Properties().stacksTo(1)));

    public static final DeferredItem<com.mcl.cbtc.item.SpeakerModuleItem> SPEAKER_MODULE =
            ITEMS.register("speaker_module", () -> new com.mcl.cbtc.item.SpeakerModuleItem(
                    new Item.Properties().stacksTo(1)));

    public static final DeferredItem<com.mcl.cbtc.item.HardDriveItem> HARD_DRIVE =
            ITEMS.register("hard_drive", () -> new com.mcl.cbtc.item.HardDriveItem(
                    new Item.Properties().stacksTo(1)));

    public static final DeferredItem<com.mcl.cbtc.item.TargetLocatorItem> TARGET_LOCATOR =
            ITEMS.register("target_locator", () -> new com.mcl.cbtc.item.TargetLocatorItem(
                    new Item.Properties().stacksTo(1)));

    public static final DeferredItem<com.mcl.cbtc.item.LodestoneCharmItem> LODESTONE_CHARM =
            ITEMS.register("lodestone_charm", () -> new com.mcl.cbtc.item.LodestoneCharmItem(
                    new Item.Properties().stacksTo(1)));

    public static final DeferredItem<com.mcl.cbtc.item.BoundCompassItem> BOUND_COMPASS =
            ITEMS.register("bound_compass", () -> new com.mcl.cbtc.item.BoundCompassItem(
                    new Item.Properties().stacksTo(1)));

    public static final DeferredItem<com.mcl.cbtc.item.GearboxAngleToolItem> GEARBOX_ANGLE_TOOL =
            ITEMS.register("gearbox_angle_tool", () -> new com.mcl.cbtc.item.GearboxAngleToolItem(
                    new Item.Properties().stacksTo(1)));

    public static final DeferredItem<com.mcl.cbtc.item.MusicModuleItem> MUSIC_MODULE =
            ITEMS.register("music_module", () -> new com.mcl.cbtc.item.MusicModuleItem(
                    new Item.Properties().stacksTo(1)));

    public static final DeferredItem<Item> BREEZE_RADIATOR =
            ITEMS.register("breeze_radiator", () -> new Item(new Item.Properties()));

    public static final DeferredItem<com.mcl.cbtc.item.UnchargedSculptingHandItem> UNCHARGED_SCULPTING_HAND =
            ITEMS.register("uncharged_sculpting_hand", () -> new com.mcl.cbtc.item.UnchargedSculptingHandItem(
                    new Item.Properties().stacksTo(1).fireResistant().rarity(Rarity.UNCOMMON)));

    public static final DeferredItem<com.mcl.cbtc.item.SculptingHandItem> SCULPTING_HAND =
            ITEMS.register("sculpting_hand", () -> new com.mcl.cbtc.item.SculptingHandItem(
                    new Item.Properties().stacksTo(1).fireResistant().rarity(Rarity.EPIC)));

    public static final DeferredItem<Item> ALLAY_SOUND_CARD =
            ITEMS.register("allay_sound_card", () -> new Item(new Item.Properties()));

    public static final DeferredItem<Item> LAPIS_SHEET =
            ITEMS.register("lapis_sheet", () -> new Item(new Item.Properties()));

    public static final DeferredItem<Item> INTEGRATED_CIRCUIT =
            ITEMS.register("integrated_circuit", () -> new Item(new Item.Properties()));

    public static final DeferredItem<Item> PROTOTYPE_MODULE_CARD =
            ITEMS.register("prototype_module_card", () -> new Item(new Item.Properties()));

    public static final DeferredItem<Item> BLAZE_COMPUTE_CHIP =
            ITEMS.register("blaze_compute_chip", () -> new Item(new Item.Properties()));

    public static final DeferredItem<com.mcl.cbtc.item.SuperBlazeRodItem> SUPER_BLAZE_ROD =
            ITEMS.register("super_blaze_rod", () -> new com.mcl.cbtc.item.SuperBlazeRodItem(
                    new Item.Properties()));

    public static final DeferredItem<Item> SUPER_BLAZE_POWDER =
            ITEMS.register("super_blaze_powder", () -> new Item(new Item.Properties()));

    public static final DeferredItem<Item> BLAZE_SUPERCOMPUTER_UNIT =
            ITEMS.register("blaze_supercomputer_unit", () -> new Item(new Item.Properties()));

    public static final DeferredItem<Item> SHULKER_CHIP =
            ITEMS.register("shulker_chip", () -> new Item(new Item.Properties()));

    public static final DeferredItem<net.minecraft.world.item.SpawnEggItem> SUPER_BLAZE_SPAWN_EGG =
            ITEMS.register("super_blaze_spawn_egg", () -> new net.minecraft.world.item.SpawnEggItem(
                    CbcEntities.SUPER_BLAZE.get(), 0x3E316D, 0x93EBFF, new Item.Properties()));

    private CbcItems() {
    }

    public static void register(IEventBus bus) {
        ITEMS.register(bus);
    }
}
