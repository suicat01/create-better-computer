package com.mcl.cbtc.registry;

import com.mcl.cbtc.CreateBetterComputer;
import com.mcl.cbtc.blockentity.CBComputerBlockEntity;
import com.mcl.cbtc.blockentity.ExpanderBlockEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class CbcBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> TYPES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, CreateBetterComputer.MODID);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CBComputerBlockEntity>> COMPUTER =
            TYPES.register("computer", () -> BlockEntityType.Builder
                    .of(CBComputerBlockEntity::new, CbcBlocks.COMPUTER.get())
                    .build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ExpanderBlockEntity>> EXPANDER =
            TYPES.register("expander", () -> BlockEntityType.Builder
                    .of(ExpanderBlockEntity::new, CbcBlocks.EXPANDER.get())
                    .build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<com.mcl.cbtc.blockentity.CBTypewriterBlockEntity>> TYPEWRITER =
            TYPES.register("typewriter", () -> BlockEntityType.Builder
                    .of(com.mcl.cbtc.blockentity.CBTypewriterBlockEntity::new, CbcBlocks.TYPEWRITER.get())
                    .build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<com.mcl.cbtc.blockentity.CBGearboxBlockEntity>> GEARBOX =
            TYPES.register("gearbox", () -> BlockEntityType.Builder
                    .of(com.mcl.cbtc.blockentity.CBGearboxBlockEntity::new, CbcBlocks.GEARBOX.get())
                    .build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<com.mcl.cbtc.blockentity.DiagramSensorBlockEntity>> DIAGRAM_SENSOR =
            TYPES.register("diagram_sensor", () -> BlockEntityType.Builder
                    .of(com.mcl.cbtc.blockentity.DiagramSensorBlockEntity::new, CbcBlocks.DIAGRAM_SENSOR.get())
                    .build(null));

    /** 原版可编程齿轮箱的复刻方块实体：完全走原版计时逻辑。 */
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<com.mcl.cbtc.blockentity.OriginalGearshiftBlockEntity>> ORIGINAL_GEARSHIFT =
            TYPES.register("original_gearshift", () -> BlockEntityType.Builder
                    .of(com.mcl.cbtc.blockentity.OriginalGearshiftBlockEntity::new,
                            CbcBlocks.ORIGINAL_GEARSHIFT.get())
                    .build(null));

    /** 信息转发器：缓存各显示链接数据源送来的文本。 */
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<com.mcl.cbtc.blockentity.InfoTransmitterBlockEntity>> INFO_TRANSMITTER =
            TYPES.register("info_transmitter", () -> BlockEntityType.Builder
                    .of(com.mcl.cbtc.blockentity.InfoTransmitterBlockEntity::new,
                            CbcBlocks.INFO_TRANSMITTER.get())
                    .build(null));

    /** CB音响：一个扬声器升级槽。 */
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<com.mcl.cbtc.blockentity.CBSpeakerBlockEntity>> SPEAKER =
            TYPES.register("speaker", () -> BlockEntityType.Builder
                    .of(com.mcl.cbtc.blockentity.CBSpeakerBlockEntity::new, CbcBlocks.SPEAKER.get())
                    .build(null));

    /** 讲解石及其全部石质变种共用同一个方块实体类型。 */
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<com.mcl.cbtc.blockentity.LectureStoneBlockEntity>> LECTURE_STONE =
            TYPES.register("lecture_stone", () -> BlockEntityType.Builder
                    .of(com.mcl.cbtc.blockentity.LectureStoneBlockEntity::new,
                            CbcBlocks.LECTURE_STONE.get(),
                            CbcBlocks.LECTURE_STONE_COBBLESTONE.get(),
                            CbcBlocks.LECTURE_STONE_ANDESITE.get(),
                            CbcBlocks.LECTURE_STONE_DEEPSLATE.get(),
                            CbcBlocks.LECTURE_STONE_STONE_BRICKS.get(),
                            CbcBlocks.LECTURE_STONE_GRANITE.get(),
                            CbcBlocks.LECTURE_STONE_MOSSY_COBBLESTONE.get(),
                            CbcBlocks.LECTURE_STONE_MOSSY_STONE_BRICKS.get(),
                            CbcBlocks.LECTURE_STONE_DIORITE.get())
                    .build(null));

    /** 智能转速控制器：继承 Create 的双端传动轴方块实体。 */
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<com.mcl.cbtc.blockentity.SmartSpeedControllerBlockEntity>> SPEED_CONTROLLER =
            TYPES.register("speed_controller", () -> BlockEntityType.Builder
                    .of(com.mcl.cbtc.blockentity.SmartSpeedControllerBlockEntity::new,
                            CbcBlocks.SPEED_CONTROLLER.get())
                    .build(null));

    private CbcBlockEntities() {
    }

    public static void register(IEventBus bus) {
        TYPES.register(bus);
    }
}
