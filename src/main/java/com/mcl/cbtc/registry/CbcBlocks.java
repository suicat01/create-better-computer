package com.mcl.cbtc.registry;

import com.mcl.cbtc.CreateBetterComputer;
import com.mcl.cbtc.block.CBComputerBlock;
import com.mcl.cbtc.block.CBExpanderBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class CbcBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(CreateBetterComputer.MODID);

    public static final DeferredBlock<CBComputerBlock> COMPUTER = BLOCKS.register("computer",
            () -> new CBComputerBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(3.0F, 6.0F)
                    .sound(SoundType.METAL)
                    .requiresCorrectToolForDrops()
                    .noOcclusion()));

    public static final DeferredBlock<CBExpanderBlock> EXPANDER = BLOCKS.register("expander",
            () -> new CBExpanderBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(3.0F, 6.0F)
                    .sound(SoundType.METAL)
                    .requiresCorrectToolForDrops()
                    .noOcclusion()));

    public static final DeferredBlock<com.mcl.cbtc.block.CBTypewriterBlock> TYPEWRITER = BLOCKS.register("typewriter",
            () -> new com.mcl.cbtc.block.CBTypewriterBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(2.0F, 6.0F)
                    .sound(SoundType.METAL)
                    .requiresCorrectToolForDrops()
                    .noOcclusion()));

    public static final DeferredBlock<com.mcl.cbtc.block.CBGearboxBlock> GEARBOX = BLOCKS.register("gearbox",
            () -> new com.mcl.cbtc.block.CBGearboxBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(3.0F, 6.0F)
                    .sound(SoundType.METAL)
                    .requiresCorrectToolForDrops()
                    .noOcclusion()));

    public static final DeferredBlock<com.mcl.cbtc.block.DiagramSensorBlock> DIAGRAM_SENSOR =
            BLOCKS.register("diagram_sensor",
                    () -> new com.mcl.cbtc.block.DiagramSensorBlock(BlockBehaviour.Properties.of()
                            .mapColor(MapColor.METAL)
                            .strength(2.0F, 6.0F)
                            .sound(SoundType.METAL)
                            .requiresCorrectToolForDrops()
                            .noOcclusion()));

    /** 原版可编程齿轮箱的复刻方块：保留原版序列配置界面，序列由玩家直接填写。 */
    public static final DeferredBlock<com.mcl.cbtc.block.OriginalGearshiftBlock> ORIGINAL_GEARSHIFT =
            BLOCKS.register("original_gearshift",
                    () -> new com.mcl.cbtc.block.OriginalGearshiftBlock(BlockBehaviour.Properties.of()
                            .mapColor(MapColor.METAL)
                            .strength(3.0F, 6.0F)
                            .sound(SoundType.METAL)
                            .requiresCorrectToolForDrops()
                            .noOcclusion()));

    /** 信息转发器：六个面都可贴 Create 的显示链接器，把数据转发给 CB 电脑。 */
    public static final DeferredBlock<com.mcl.cbtc.block.InfoTransmitterBlock> INFO_TRANSMITTER =
            BLOCKS.register("info_transmitter",
                    () -> new com.mcl.cbtc.block.InfoTransmitterBlock(BlockBehaviour.Properties.of()
                            .mapColor(MapColor.METAL)
                            .strength(2.0F, 6.0F)
                            .sound(SoundType.METAL)
                            .requiresCorrectToolForDrops()));

    /** CB音响：可被外设绑定，作为播放声音的发声设备。 */
    public static final DeferredBlock<com.mcl.cbtc.block.CBSpeakerBlock> SPEAKER =
            BLOCKS.register("speaker",
                    () -> new com.mcl.cbtc.block.CBSpeakerBlock(BlockBehaviour.Properties.of()
                            .mapColor(MapColor.WOOD)
                            .strength(2.0F, 6.0F)
                            .sound(SoundType.WOOD)
                            .requiresCorrectToolForDrops()
                            .noOcclusion()));

    /** 讲解石：没有升级槽的音响，发声范围固定 6 格。 */
    public static final DeferredBlock<com.mcl.cbtc.block.LectureStoneBlock> LECTURE_STONE =
            BLOCKS.register("lecture_stone",
                    () -> new com.mcl.cbtc.block.LectureStoneBlock(BlockBehaviour.Properties.of()
                            .mapColor(MapColor.STONE)
                            .strength(2.0F, 6.0F)
                            .sound(SoundType.STONE)
                            .requiresCorrectToolForDrops()
                            .noOcclusion()));

    /** 讲解石的石质变种：共用同一建模与方块实体，仅贴图不同，各自独立 id。 */
    public static final DeferredBlock<com.mcl.cbtc.block.LectureStoneBlock> LECTURE_STONE_COBBLESTONE =
            lectureStoneVariant("lecture_stone_cobblestone");
    public static final DeferredBlock<com.mcl.cbtc.block.LectureStoneBlock> LECTURE_STONE_ANDESITE =
            lectureStoneVariant("lecture_stone_andesite");
    public static final DeferredBlock<com.mcl.cbtc.block.LectureStoneBlock> LECTURE_STONE_DEEPSLATE =
            lectureStoneVariant("lecture_stone_deepslate");
    public static final DeferredBlock<com.mcl.cbtc.block.LectureStoneBlock> LECTURE_STONE_STONE_BRICKS =
            lectureStoneVariant("lecture_stone_stone_bricks");
    public static final DeferredBlock<com.mcl.cbtc.block.LectureStoneBlock> LECTURE_STONE_GRANITE =
            lectureStoneVariant("lecture_stone_granite");
    public static final DeferredBlock<com.mcl.cbtc.block.LectureStoneBlock> LECTURE_STONE_MOSSY_COBBLESTONE =
            lectureStoneVariant("lecture_stone_mossy_cobblestone");
    public static final DeferredBlock<com.mcl.cbtc.block.LectureStoneBlock> LECTURE_STONE_MOSSY_STONE_BRICKS =
            lectureStoneVariant("lecture_stone_mossy_stone_bricks");
    public static final DeferredBlock<com.mcl.cbtc.block.LectureStoneBlock> LECTURE_STONE_DIORITE =
            lectureStoneVariant("lecture_stone_diorite");

    /** 智能转速控制器：输入端接入应力后，按界面设定的转速与方向从输出端输出。 */
    public static final DeferredBlock<com.mcl.cbtc.block.SmartSpeedControllerBlock> SPEED_CONTROLLER =
            BLOCKS.register("speed_controller",
                    () -> new com.mcl.cbtc.block.SmartSpeedControllerBlock(BlockBehaviour.Properties.of()
                            .mapColor(MapColor.METAL)
                            .strength(3.0F, 6.0F)
                            .sound(SoundType.METAL)
                            .requiresCorrectToolForDrops()
                            .noOcclusion()));

    private static DeferredBlock<com.mcl.cbtc.block.LectureStoneBlock> lectureStoneVariant(String id) {
        return BLOCKS.register(id,
                () -> new com.mcl.cbtc.block.LectureStoneBlock(BlockBehaviour.Properties.of()
                        .mapColor(MapColor.STONE)
                        .strength(2.0F, 6.0F)
                        .sound(SoundType.STONE)
                        .requiresCorrectToolForDrops()
                        .noOcclusion()));
    }

    private CbcBlocks() {
    }

    public static void register(IEventBus bus) {
        BLOCKS.register(bus);
    }
}
