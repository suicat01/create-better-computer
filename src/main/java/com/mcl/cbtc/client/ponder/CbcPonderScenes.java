package com.mcl.cbtc.client.ponder;

import com.mcl.cbtc.CreateBetterComputer;
import com.mcl.cbtc.entity.SuperBlaze;
import com.mcl.cbtc.registry.CbcBlocks;
import com.mcl.cbtc.registry.CbcEntities;
import com.mcl.cbtc.registry.CbcItems;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.kinetics.deployer.DeployerBlockEntity;
import com.simibubi.create.foundation.ponder.CreateSceneBuilder;
import net.createmod.catnip.math.Pointing;
import net.createmod.ponder.api.ParticleEmitter;
import net.createmod.ponder.api.PonderPalette;
import net.createmod.ponder.api.element.ElementLink;
import net.createmod.ponder.api.element.EntityElement;
import net.createmod.ponder.api.registration.PonderSceneRegistrationHelper;
import net.createmod.ponder.api.registration.PonderTagRegistrationHelper;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.createmod.ponder.api.scene.Selection;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * CB电脑、CB拓展器与捏塑创生之手的思索场景。结构文件位于 assets/cbtc/ponder/，
 * 场景文本键为 cbtc.ponder.&lt;场景名&gt;.*。
 */
public final class CbcPonderScenes {
    public static final ResourceLocation COMPUTER_TAG = CreateBetterComputer.asResource("computer");
    public static final ResourceLocation SCULPTING_HAND_TAG = CreateBetterComputer.asResource("sculpting_hand");
    public static final ResourceLocation SUPER_BLAZE_TAG = CreateBetterComputer.asResource("super_blaze");

    /** 九种讲解石变种，共用同一个思索场景。 */
    public static final List<ResourceLocation> LECTURE_STONES = List.of(
            CreateBetterComputer.asResource("lecture_stone"),
            CreateBetterComputer.asResource("lecture_stone_cobblestone"),
            CreateBetterComputer.asResource("lecture_stone_andesite"),
            CreateBetterComputer.asResource("lecture_stone_deepslate"),
            CreateBetterComputer.asResource("lecture_stone_stone_bricks"),
            CreateBetterComputer.asResource("lecture_stone_granite"),
            CreateBetterComputer.asResource("lecture_stone_mossy_cobblestone"),
            CreateBetterComputer.asResource("lecture_stone_mossy_stone_bricks"),
            CreateBetterComputer.asResource("lecture_stone_diorite"));

    private CbcPonderScenes() {
    }

    public static void register(PonderSceneRegistrationHelper<ResourceLocation> helper) {
        helper.addStoryBoard(CreateBetterComputer.asResource("computer"), "computer",
                CbcPonderScenes::computer, COMPUTER_TAG);
        helper.addStoryBoard(CreateBetterComputer.asResource("expander"), "expander",
                CbcPonderScenes::expander, COMPUTER_TAG);
        helper.addStoryBoard(CreateBetterComputer.asResource("info_transmitter"), "info_transmitter",
                CbcPonderScenes::infoTransmitter, COMPUTER_TAG);
        helper.addStoryBoard(CreateBetterComputer.asResource("sculpting_hand"), "sculpting_hand",
                CbcPonderScenes::sculptingHand, SCULPTING_HAND_TAG);
        helper.addStoryBoard(CreateBetterComputer.asResource("uncharged_sculpting_hand"), "uncharged_sculpting_hand",
                CbcPonderScenes::unchargedSculptingHand, SCULPTING_HAND_TAG);
        helper.addStoryBoard(CreateBetterComputer.asResource("speaker"), "speaker",
                CbcPonderScenes::speaker, COMPUTER_TAG);
        helper.addStoryBoard(CreateBetterComputer.asResource("typewriter"), "typewriter",
                CbcPonderScenes::typewriter, COMPUTER_TAG);
        helper.addStoryBoard(CreateBetterComputer.asResource("gearbox"), "gearbox",
                CbcPonderScenes::gearbox, COMPUTER_TAG);
        helper.addStoryBoard(CreateBetterComputer.asResource("gearbox_angle_tool"), "gearbox_angle_tool",
                CbcPonderScenes::gearboxAngleTool, COMPUTER_TAG);
        registerModule(helper, "storage_link_module", CbcItems.STORAGE_LINK_MODULE.get(),
                "Connects the computer to a Create package logistics network",
                "Right-click a Stock Link with the card to join the network it belongs to",
                "Insert it into the expander, then use blocks to read and write that network; sneak-right-click a Stock Link to disconnect");
        registerModule(helper, "wireless_redstone_module", CbcItems.WIRELESS_REDSTONE_MODULE.get(),
                "Gives the computer Create wireless redstone send and receive blocks",
                "Insert it into the expander, then pick a channel inside the blocks",
                "Channel #1 is red and #2 is blue; both together make one exact frequency");
        registerModule(helper, "lan_module", CbcItems.LAN_MODULE.get(),
                "Joins the computer to a local network shared by other computers",
                "Right-click in the air with the card to open the LAN setup screen and set its ID and password",
                "The first password used for an ID registers it, and later users must match it");
        registerModule(helper, "internet_module", CbcItems.INTERNET_MODULE.get(),
                "Connects the computer to the server-wide internet",
                "No binding needed: just insert it into one of the expander's module slots",
                "Broadcast and chat blocks become available right away");
        registerModule(helper, "device_module", CbcItems.DEVICE_MODULE.get(),
                "Binds peripherals to the computer so blocks can reach them by name",
                "Right-click a peripheral to select it, then right-click again to name it",
                "Sneak-right-click to unbind; one card can hold any number of peripherals");
        registerModule(helper, "display_link_module", CbcItems.DISPLAY_LINK_MODULE.get(),
                "Binds wireless displays to the computer by name",
                "Right-click a display to select it, then right-click again to name it",
                "One card can hold several displays, and blocks pick one of them by name");
        registerModule(helper, "speaker_module", CbcItems.SPEAKER_MODULE.get(),
                "Raises the computer's sound range from 12 to 50 blocks",
                "Insert it into the expander to boost the computer",
                "Put it in the CB Speaker's own slot and the speaker's range goes from 20 to 80 blocks");
        registerModule(helper, "music_module", CbcItems.MUSIC_MODULE.get(),
                "Carries a 54 slot playlist and lets the computer play music",
                "Right-click in the air with the card to open the playlist editor",
                "The card's own id names the playlist, and blocks pick songs by that id");
        registerModule(helper, "hard_drive", CbcItems.HARD_DRIVE.get(),
                "Stores the program's variables inside the card itself",
                "Insert it into the expander, then use the drive variable blocks to write and read",
                "Move the card to another computer and the stored variables come with it");
        helper.addStoryBoard(CreateBetterComputer.asResource("target_locator"), "target_locator",
                CbcPonderScenes::targetLocator, COMPUTER_TAG);
        helper.addStoryBoard(CreateBetterComputer.asResource("lodestone_charm"), "lodestone_charm",
                CbcPonderScenes::lodestoneCharm, COMPUTER_TAG);
        // 超级烈焰人的转换：烈焰蛋糕喂烈焰人 / 喂烈焰人刷怪笼
        helper.forComponents(java.util.List.of(CreateBetterComputer.asResource("super_blaze_spawn_egg"),
                        net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("create", "blaze_cake")))
                .addStoryBoard("super_blaze_conversion", CbcPonderScenes::superBlazeConversion,
                        SUPER_BLAZE_TAG);
        // 九种讲解石变种共用同一个思索场景
        helper.forComponents(LECTURE_STONES)
                .addStoryBoard("lecture_stone", CbcPonderScenes::lectureStone, COMPUTER_TAG);
    }

    /** 模块卡场景结构相同，只是展示的物品与文案不同。 */
    private static void registerModule(PonderSceneRegistrationHelper<ResourceLocation> helper, String sceneId,
                                       Item card, String line1, String line2, String line3) {
        helper.addStoryBoard(CreateBetterComputer.asResource(sceneId), sceneId,
                (scene, util) -> moduleCard(scene, util, sceneId, card, line1, line2, line3), COMPUTER_TAG);
    }

    public static void registerTags(PonderTagRegistrationHelper<ResourceLocation> helper) {
        helper.registerTag(COMPUTER_TAG)
                .title("CB Computer")
                .description("A computer that runs programs made of blocks")
                .item(CbcBlocks.COMPUTER.get().asItem())
                .addToIndex()
                .register();
        helper.addToTag(COMPUTER_TAG)
                .add(CreateBetterComputer.asResource("computer"))
                .add(CreateBetterComputer.asResource("expander"))
                .add(CreateBetterComputer.asResource("info_transmitter"))
                .add(CreateBetterComputer.asResource("speaker"))
                .add(CreateBetterComputer.asResource("typewriter"))
                .add(CreateBetterComputer.asResource("gearbox"))
                .add(CreateBetterComputer.asResource("gearbox_angle_tool"))
                .add(CreateBetterComputer.asResource("storage_link_module"))
                .add(CreateBetterComputer.asResource("wireless_redstone_module"))
                .add(CreateBetterComputer.asResource("lan_module"))
                .add(CreateBetterComputer.asResource("internet_module"))
                .add(CreateBetterComputer.asResource("device_module"))
                .add(CreateBetterComputer.asResource("display_link_module"))
                .add(CreateBetterComputer.asResource("speaker_module"))
                .add(CreateBetterComputer.asResource("music_module"))
                .add(CreateBetterComputer.asResource("hard_drive"))
                .add(CreateBetterComputer.asResource("target_locator"))
                .add(CreateBetterComputer.asResource("lodestone_charm"));
        for (ResourceLocation stone : LECTURE_STONES) {
            helper.addToTag(COMPUTER_TAG).add(stone);
        }
        helper.registerTag(SCULPTING_HAND_TAG)
                .title("Sculpting Hand")
                .description("Sculpts living creatures into machine parts")
                .item(CbcItems.SCULPTING_HAND.get())
                .addToIndex()
                .register();
        helper.addToTag(SCULPTING_HAND_TAG)
                .add(CreateBetterComputer.asResource("uncharged_sculpting_hand"))
                .add(CreateBetterComputer.asResource("sculpting_hand"));
        helper.registerTag(SUPER_BLAZE_TAG)
                .title("Super Blaze")
                .description("Turning blazes into super blazes with blaze cakes")
                .item(CbcItems.SUPER_BLAZE_SPAWN_EGG.get())
                .addToIndex()
                .register();
        helper.addToTag(SUPER_BLAZE_TAG)
                .add(CreateBetterComputer.asResource("super_blaze_spawn_egg"))
                .add(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("create", "blaze_cake"));
    }

    /**
     * 超级烈焰人的转换：烈焰蛋糕把烈焰人和烈焰人刷怪笼都转化成超级烈焰人。
     * <p>
     * 结构文件与 CB音响 场景结构一致：y=0 铺满一层下界砖地板，另有一个方块在 (2,1,2)。
     */
    private static void superBlazeConversion(SceneBuilder scene, SceneBuildingUtil util) {
        scene.title("super_blaze_conversion", "Converting Super Blazes");
        scene.configureBasePlate(0, 0, 5);
        scene.scaleSceneView(0.9F);
        scene.showBasePlate();
        scene.idle(10);

        BlockPos spawner = util.grid().at(2, 1, 2);
        Vec3 blazePos = util.vector().centerOf(1, 1, 2);

        // 整块下界砖地板随之后的结构一起显示
        scene.world().showSection(util.select().fromTo(0, 0, 0, 4, 0, 4), Direction.DOWN);
        scene.world().showSection(util.select().position(spawner), Direction.DOWN);
        scene.idle(15);

        scene.overlay().showText(80)
                .text("A Blaze Cake is a very special treat for blazes")
                .pointAt(util.vector().topOf(spawner))
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(90);

        // 第一段：烈焰蛋糕喂普通的烈焰人
        ElementLink<EntityElement> blaze = scene.world().createEntity(level -> {
            net.minecraft.world.entity.monster.Blaze entity =
                    net.minecraft.world.entity.EntityType.BLAZE.create(level);
            if (entity != null) {
                entity.setPos(blazePos.x, blazePos.y, blazePos.z);
                entity.setNoGravity(true);
                entity.setNoAi(true);
            }
            return entity;
        });
        scene.idle(20);

        scene.overlay().showControls(blazePos.add(0, 1.2, 0), Pointing.DOWN, 50)
                .rightClick()
                .withItem(new ItemStack(blazeCake()));
        scene.overlay().showText(80)
                .text("Right-click a Blaze with a Blaze Cake and it becomes a Super Blaze")
                .colored(PonderPalette.BLUE)
                .pointAt(blazePos)
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(70);

        // 转换瞬间：烈焰人换成超级烈焰人，并冒出灵魂火与火焰
        scene.world().modifyEntity(blaze, Entity::discard);
        scene.world().createEntity(level -> {
            SuperBlaze entity = CbcEntities.SUPER_BLAZE.get().create(level);
            if (entity != null) {
                entity.setPos(blazePos.x, blazePos.y, blazePos.z);
                entity.setNoGravity(true);
                entity.setNoAi(true);
            }
            return entity;
        });
        ParticleEmitter soulFire = scene.effects()
                .particleEmitterWithinBlockSpace(ParticleTypes.SOUL_FIRE_FLAME, new Vec3(0, 0.04, 0));
        ParticleEmitter flame = scene.effects()
                .particleEmitterWithinBlockSpace(ParticleTypes.FLAME, new Vec3(0, 0.04, 0));
        scene.effects().emitParticles(blazePos, soulFire, 8, 20);
        scene.effects().emitParticles(blazePos, flame, 6, 20);
        scene.idle(20);

        scene.overlay().showText(80)
                .text("The Super Blaze is a far stronger variant of the Blaze")
                .colored(PonderPalette.GREEN)
                .pointAt(blazePos)
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(90);

        // 第二段：同样的蛋糕也能喂给烈焰人刷怪笼
        scene.overlay().showControls(util.vector().topOf(spawner), Pointing.DOWN, 50)
                .rightClick()
                .withItem(new ItemStack(blazeCake()));
        scene.overlay().showText(80)
                .text("Fed to a Blaze Spawner, the cake makes it spawn Super Blazes")
                .colored(PonderPalette.BLUE)
                .pointAt(util.vector().topOf(spawner))
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(70);

        scene.effects().emitParticles(util.vector().centerOf(spawner), soulFire, 10, 20);
        scene.effects().emitParticles(util.vector().centerOf(spawner), flame, 8, 20);
        scene.idle(20);

        scene.overlay().showText(80)
                .text("The effect lasts 30 seconds, then the spawner turns back into a Blaze Spawner")
                .colored(PonderPalette.RED)
                .pointAt(util.vector().topOf(spawner))
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(90);

        scene.markAsFinished();
    }

    /** 烈焰蛋糕物品；机械动力没装时返回空栈，场景仍然照常播放。 */
    private static Item blazeCake() {
        return net.minecraft.core.registries.BuiltInRegistries.ITEM
                .getOptional(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("create", "blaze_cake"))
                .orElse(net.minecraft.world.item.Items.CAKE);
    }

    private static void speaker(SceneBuilder scene, SceneBuildingUtil util) {
        scene.title("speaker", "CB Speaker");
        scene.configureBasePlate(0, 0, 5);
        scene.scaleSceneView(0.9F);
        scene.showBasePlate();
        scene.idle(10);

        BlockPos speaker = util.grid().at(2, 1, 2);
        scene.world().showSection(util.select().position(speaker), Direction.DOWN);
        scene.idle(15);

        scene.overlay().showText(70)
                .text("The CB Speaker sets where the computer's sounds are played")
                .pointAt(util.vector().topOf(speaker))
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(80);

        scene.overlay().showControls(util.vector().topOf(speaker), Pointing.RIGHT, 50)
                .rightClick();
        scene.overlay().showText(70)
                .text("Right-click it to open its single upgrade slot")
                .colored(PonderPalette.BLUE)
                .pointAt(util.vector().topOf(speaker))
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(80);

        scene.overlay().showControls(util.vector().topOf(speaker), Pointing.RIGHT, 50)
                .rightClick()
                .withItem(new ItemStack(CbcItems.SPEAKER_MODULE.get()));
        scene.overlay().showText(70)
                .text("Insert a Speaker Module to raise the range from 20 to 80 blocks")
                .colored(PonderPalette.GREEN)
                .pointAt(util.vector().topOf(speaker))
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(80);

        scene.overlay().showControls(util.vector().topOf(speaker), Pointing.RIGHT, 50)
                .rightClick()
                .withItem(new ItemStack(CbcItems.DEVICE_MODULE.get()));
        scene.overlay().showText(70)
                .text("Bind it with the Peripheral Binding Module, then the Play Sound block uses it")
                .colored(PonderPalette.RED)
                .pointAt(util.vector().topOf(speaker))
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(80);

        scene.markAsFinished();
    }

    private static void lectureStone(SceneBuilder scene, SceneBuildingUtil util) {
        scene.title("lecture_stone", "Lecture Stone");
        scene.configureBasePlate(0, 0, 5);
        scene.scaleSceneView(0.85F);
        scene.showBasePlate();
        scene.idle(10);

        // 九种变种铺成 3x3，展示全部外观
        Selection stones = util.select().fromTo(1, 1, 1, 3, 1, 3);
        scene.world().showSection(stones, Direction.DOWN);
        scene.idle(15);

        scene.overlay().showText(70)
                .text("All nine Lecture Stone variants work exactly the same way")
                .pointAt(util.vector().topOf(util.grid().at(2, 1, 2)))
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(80);

        scene.overlay().showText(70)
                .text("It sets where the computer plays its sounds, with a fixed range of 6 blocks")
                .colored(PonderPalette.GREEN)
                .pointAt(util.vector().topOf(util.grid().at(2, 1, 2)))
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(80);

        scene.overlay().showText(70)
                .text("There is no upgrade slot: right-click does nothing and the range cannot be raised")
                .colored(PonderPalette.RED)
                .pointAt(util.vector().topOf(util.grid().at(2, 1, 2)))
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(80);

        scene.overlay().showControls(util.vector().topOf(util.grid().at(2, 1, 2)), Pointing.RIGHT, 50)
                .rightClick()
                .withItem(new ItemStack(CbcItems.DEVICE_MODULE.get()));
        scene.overlay().showText(70)
                .text("Bind it with the Peripheral Binding Module, then the Play Sound block uses it")
                .colored(PonderPalette.BLUE)
                .pointAt(util.vector().topOf(util.grid().at(2, 1, 2)))
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(80);

        scene.markAsFinished();
    }

    private static void typewriter(SceneBuilder scene, SceneBuildingUtil util) {
        scene.title("typewriter", "CB Typewriter");
        scene.configureBasePlate(0, 0, 5);
        scene.scaleSceneView(0.9F);
        scene.showBasePlate();
        scene.idle(10);

        BlockPos typewriter = util.grid().at(2, 1, 2);
        Vec3 top = util.vector().topOf(typewriter);

        scene.world().showSection(util.select().position(typewriter), Direction.DOWN);
        scene.idle(15);

        scene.overlay().showText(70)
                .text("The CB Typewriter lets the computer read the keys you press")
                .pointAt(top)
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(80);

        scene.overlay().showControls(top, Pointing.RIGHT, 50)
                .rightClick();
        scene.overlay().showText(70)
                .text("Right-click it bare-handed to start typing, right-click again to stop")
                .colored(PonderPalette.BLUE)
                .pointAt(top)
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(80);

        scene.overlay().showControls(top, Pointing.RIGHT, 50)
                .rightClick()
                .withItem(new ItemStack(CbcItems.DEVICE_MODULE.get()));
        scene.overlay().showText(70)
                .text("Bind it with the Peripheral Binding Module so the computer can read it")
                .colored(PonderPalette.GREEN)
                .pointAt(top)
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(80);

        scene.overlay().showText(70)
                .text("Key presses trigger the When CB Typewriter key pressed event, along with held and long-press blocks")
                .colored(PonderPalette.RED)
                .pointAt(top)
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(80);

        scene.markAsFinished();
    }

    private static void gearbox(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = new CreateSceneBuilder(builder);
        scene.title("gearbox", "CB Programmable Gearbox");
        scene.configureBasePlate(0, 0, 6);
        scene.scaleSceneView(0.8F);
        scene.showBasePlate();
        scene.idle(10);

        BlockPos inputShaft = util.grid().at(2, 1, 2);
        BlockPos gearbox = util.grid().at(3, 1, 2);
        BlockPos outputShaft = util.grid().at(4, 1, 2);
        Selection input = util.select().fromTo(1, 1, 2, 2, 1, 2);
        Selection output = util.select().fromTo(3, 1, 2, 4, 1, 2);

        // 输入端接上动力：马达 + 传动轴转动
        scene.world().showSection(input, Direction.WEST);
        scene.idle(15);
        scene.world().setKineticSpeed(input, 32);
        scene.overlay().showText(70)
                .text("Power enters through the shaft: the input side always turns with the incoming rotation")
                .pointAt(util.vector().topOf(inputShaft))
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(80);

        scene.world().showSection(util.select().position(gearbox), Direction.UP);
        scene.idle(15);
        scene.overlay().showText(70)
                .text("It looks like a Sequenced Gearshift, but only program blocks control its speed")
                .colored(PonderPalette.BLUE)
                .pointAt(util.vector().topOf(gearbox))
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(80);

        scene.world().showSection(util.select().position(outputShaft), Direction.EAST);
        scene.idle(15);
        scene.overlay().showControls(util.vector().topOf(gearbox), Pointing.RIGHT, 50)
                .rightClick()
                .withItem(new ItemStack(CbcItems.DEVICE_MODULE.get()));
        scene.overlay().showText(70)
                .text("Bind it with the Peripheral Binding Module, then Control Gearbox sets speed, duration or angle")
                .colored(PonderPalette.GREEN)
                .pointAt(util.vector().topOf(gearbox))
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(50);

        // 积木下发转速后输出轴开始转动
        scene.world().setKineticSpeed(output, 32);
        scene.idle(40);

        scene.overlay().showText(70)
                .text("Stop Gearbox halts the output shaft at once")
                .colored(PonderPalette.RED)
                .pointAt(util.vector().topOf(outputShaft))
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(40);
        scene.world().setKineticSpeed(output, 0);
        scene.idle(40);

        scene.markAsFinished();
    }

    private static void targetLocator(SceneBuilder scene, SceneBuildingUtil util) {
        scene.title("target_locator", "Target Locator Module");
        scene.configureBasePlate(0, 0, 5);
        scene.scaleSceneView(0.9F);
        scene.showBasePlate();
        scene.idle(10);

        BlockPos table = util.grid().at(2, 1, 2);
        scene.world().showSection(util.select().position(table), Direction.UP);
        scene.idle(15);

        // 模块是物品，直接摆在工作台台面上
        Vec3 onTable = util.vector().of(2.5, 2.05, 2.5);
        scene.world().createItemEntity(onTable, Vec3.ZERO, new ItemStack(CbcItems.TARGET_LOCATOR.get()));
        scene.idle(15);

        scene.overlay().showText(70)
                .text("The Target Locator Module stores its target X / Z inside the item itself")
                .pointAt(util.vector().topOf(table))
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(80);

        scene.overlay().showControls(onTable.add(0, 0.35, 0), Pointing.RIGHT, 50)
                .rightClick()
                .withItem(new ItemStack(CbcItems.TARGET_LOCATOR.get()));
        scene.overlay().showText(70)
                .text("Right-click in the air to open the coordinate screen and write the X and Z")
                .colored(PonderPalette.BLUE)
                .pointAt(onTable)
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(80);

        scene.overlay().showText(70)
                .text("Put it into an Aeronautics Navigation Table and the table points to that X / Z")
                .colored(PonderPalette.GREEN)
                .pointAt(onTable)
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(80);

        // 外设绑定模块卡放在工作台旁边，演示合成绑定
        Vec3 cardPos = util.vector().of(3.5, 1.05, 2.5);
        scene.world().createItemEntity(cardPos, Vec3.ZERO, new ItemStack(CbcItems.DEVICE_MODULE.get()));
        scene.idle(15);
        scene.overlay().showText(70)
                .text("Craft it together with a Peripheral Binding Module card; the module stays in the grid")
                .colored(PonderPalette.RED)
                .pointAt(cardPos)
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(80);

        scene.overlay().showText(70)
                .text("The card records the module, and the position blocks read and write its X / Z")
                .colored(PonderPalette.BLUE)
                .pointAt(onTable)
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(80);

        scene.markAsFinished();
    }

    private static void lodestoneCharm(SceneBuilder scene, SceneBuildingUtil util) {
        scene.title("lodestone_charm", "Lodestone Charm");
        scene.configureBasePlate(0, 0, 5);
        scene.scaleSceneView(0.9F);
        scene.showBasePlate();
        scene.idle(10);

        // 挂件是饰品，直接摆在场景中央
        Vec3 charmPos = util.vector().of(2.5, 1.05, 2.5);
        scene.world().createItemEntity(charmPos, Vec3.ZERO, new ItemStack(CbcItems.LODESTONE_CHARM.get()));
        scene.idle(15);

        scene.overlay().showText(70)
                .text("The Lodestone Charm is a trinket that carries its own id")
                .pointAt(charmPos)
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(80);

        scene.overlay().showControls(charmPos.add(0, 0.35, 0), Pointing.RIGHT, 50)
                .rightClick()
                .withItem(new ItemStack(CbcItems.LODESTONE_CHARM.get()));
        scene.overlay().showText(70)
                .text("Right-click to wear it in the leggings slot, or in any curio slot when Curios is installed")
                .colored(PonderPalette.BLUE)
                .pointAt(charmPos)
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(80);

        // 工作台演示：指南针 + 挂件
        BlockPos table = util.grid().at(2, 1, 2);
        scene.world().showSection(util.select().position(table), Direction.DOWN);
        scene.idle(15);
        Vec3 compassPos = util.vector().of(2.5, 2.05, 2.0);
        scene.world().createItemEntity(compassPos, Vec3.ZERO, new ItemStack(net.minecraft.world.item.Items.COMPASS));
        scene.idle(10);

        scene.overlay().showText(70)
                .text("Craft it with a compass: the charm stays in the grid")
                .pointAt(util.vector().topOf(table))
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(80);

        Vec3 boundPos = util.vector().of(2.5, 2.05, 3.0);
        scene.world().createItemEntity(boundPos, new Vec3(0, 0.2, 0),
                new ItemStack(CbcItems.BOUND_COMPASS.get()));
        scene.idle(15);
        scene.overlay().showText(70)
                .text("The compass becomes a Bound Compass that always points to the charm's bearer")
                .colored(PonderPalette.GREEN)
                .pointAt(boundPos)
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(80);

        scene.overlay().showText(70)
                .text("It works while the charm sits in the inventory, armor, offhand or a curio slot")
                .colored(PonderPalette.BLUE)
                .pointAt(boundPos)
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(80);

        scene.overlay().showText(70)
                .text("Place the Bound Compass in an Aeronautics Navigation Table and the table points to the bearer too")
                .colored(PonderPalette.RED)
                .pointAt(boundPos)
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(80);

        scene.markAsFinished();
    }

    private static void moduleCard(SceneBuilder scene, SceneBuildingUtil util, String sceneId, Item card,
                                   String line1, String line2, String line3) {
        scene.title(sceneId, sceneId);
        scene.configureBasePlate(0, 0, 5);
        scene.scaleSceneView(0.9F);
        scene.showBasePlate();
        scene.idle(10);

        BlockPos expander = util.grid().at(2, 1, 2);
        Vec3 top = util.vector().topOf(expander);

        scene.world().showSection(util.select().position(expander), Direction.DOWN);
        scene.idle(15);

        scene.overlay().showText(70)
                .text(line1)
                .pointAt(top)
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(80);

        scene.overlay().showControls(top, Pointing.RIGHT, 50)
                .rightClick()
                .withItem(new ItemStack(card));
        scene.overlay().showText(70)
                .text(line2)
                .colored(PonderPalette.BLUE)
                .pointAt(top)
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(80);

        scene.overlay().showText(70)
                .text(line3)
                .colored(PonderPalette.GREEN)
                .pointAt(top)
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(80);

        scene.markAsFinished();
    }

    private static void gearboxAngleTool(SceneBuilder scene, SceneBuildingUtil util) {
        scene.title("gearbox_angle_tool", "Gearbox Angle Setter");
        scene.configureBasePlate(0, 0, 6);
        scene.scaleSceneView(0.8F);
        scene.showBasePlate();
        scene.idle(10);

        BlockPos gearbox = util.grid().at(2, 1, 2);
        BlockPos shaft = util.grid().at(3, 1, 2);

        scene.world().showSection(util.select().fromTo(2, 1, 2, 3, 1, 2), Direction.WEST);
        scene.idle(15);

        scene.overlay().showText(70)
                .text("The gearbox's angle is measured from the orientation of its output shaft")
                .pointAt(util.vector().topOf(gearbox))
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(80);

        scene.overlay().showControls(util.vector().topOf(gearbox), Pointing.RIGHT, 50)
                .rightClick()
                .withItem(new ItemStack(CbcItems.GEARBOX_ANGLE_TOOL.get()));
        scene.overlay().showText(70)
                .text("Right-click it with the Gearbox Angle Setter to record the current angle as 0 degrees")
                .colored(PonderPalette.BLUE)
                .pointAt(util.vector().topOf(gearbox))
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(80);

        scene.overlay().showText(70)
                .text("The zero point is stored in the gearbox, so angle blocks read values relative to it")
                .colored(PonderPalette.GREEN)
                .pointAt(util.vector().topOf(shaft))
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(80);

        scene.markAsFinished();
    }

    private static void computer(SceneBuilder scene, SceneBuildingUtil util) {
        scene.title("computer", "CB Computer");
        scene.configureBasePlate(0, 0, 5);
        scene.scaleSceneView(0.9F);
        scene.showBasePlate();
        scene.idle(10);

        BlockPos computer = util.grid().at(2, 1, 2);
        scene.world().showSection(util.select().position(computer), Direction.UP);
        scene.idle(15);

        scene.overlay().showText(80)
                .text("CB Computer is a console for programming with blocks")
                .pointAt(util.vector().topOf(computer))
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(90);

        scene.overlay().showControls(util.vector().blockSurface(computer, Direction.NORTH), Pointing.RIGHT, 60)
                .rightClick();
        scene.overlay().showText(60)
                .text("Right-click to open the programming interface")
                .colored(PonderPalette.BLUE)
                .pointAt(util.vector().topOf(computer))
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(70);

        scene.overlay().showControls(util.vector().blockSurface(computer, Direction.NORTH), Pointing.RIGHT, 60)
                .whileSneaking()
                .rightClick();
        scene.overlay().showText(60)
                .text("Sneak and right-click to toggle the computer's power")
                .colored(PonderPalette.GREEN)
                .pointAt(util.vector().topOf(computer))
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(70);

        scene.overlay().showText(70)
                .text("Programs only run while the computer is powered on")
                .colored(PonderPalette.RED)
                .pointAt(util.vector().topOf(computer))
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(80);

        scene.markAsFinished();
    }

    private static void expander(SceneBuilder scene, SceneBuildingUtil util) {
        scene.title("expander", "CB Expander");
        scene.configureBasePlate(0, 0, 5);
        scene.scaleSceneView(0.9F);
        scene.showBasePlate();
        scene.idle(10);

        BlockPos computer = util.grid().at(2, 1, 2);
        BlockPos expander = util.grid().at(1, 1, 2);

        scene.world().showSection(util.select().position(computer), Direction.UP);
        scene.idle(15);
        // 拓展器从西侧滑入，贴到电脑背部
        scene.world().showSection(util.select().position(expander), Direction.WEST);
        scene.idle(20);

        scene.overlay().showText(80)
                .text("The CB Expander attaches to the back of a CB Computer, its front facing the computer")
                .pointAt(util.vector().topOf(expander))
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(90);

        scene.overlay().showControls(util.vector().blockSurface(expander, Direction.NORTH), Pointing.RIGHT, 60)
                .rightClick();
        scene.overlay().showText(60)
                .text("Right-click the attached expander to open its upgrade module interface")
                .colored(PonderPalette.BLUE)
                .pointAt(util.vector().topOf(expander))
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(70);

        scene.overlay().showText(70)
                .text("It provides 6 upgrade module slots that extend the computer's features")
                .colored(PonderPalette.GREEN)
                .pointAt(util.vector().topOf(expander))
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(80);

        scene.markAsFinished();
    }

    private static void infoTransmitter(SceneBuilder scene, SceneBuildingUtil util) {
        scene.title("info_transmitter", "Info Transmitter");
        scene.configureBasePlate(0, 0, 6);
        scene.scaleSceneView(0.8F);
        scene.showBasePlate();
        scene.idle(10);

        BlockPos depot = util.grid().at(2, 1, 2);
        BlockPos link = util.grid().at(2, 1, 1);
        BlockPos transmitter = util.grid().at(4, 1, 2);

        scene.world().showSection(util.select().position(depot), Direction.DOWN);
        scene.world().showSection(util.select().position(transmitter), Direction.DOWN);
        scene.idle(15);

        // 先手持显示链接器右键信息转发器，把它设为输出目标
        scene.overlay().showControls(util.vector().topOf(transmitter), Pointing.RIGHT, 50)
                .rightClick()
                .withItem(new ItemStack(AllBlocks.DISPLAY_LINK.asItem()));
        scene.overlay().showText(70)
                .text("Right-click the Info Transmitter with a Display Link to set it as the output target")
                .colored(PonderPalette.BLUE)
                .pointAt(util.vector().topOf(transmitter))
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(85);

        // 设置好输出目标后，再把链接器贴到信息源上
        scene.world().showSection(util.select().position(link), Direction.NORTH);
        scene.idle(15);
        scene.overlay().showControls(util.vector().blockSurface(link, Direction.NORTH), Pointing.RIGHT, 50)
                .rightClick();
        scene.overlay().showText(70)
                .text("Then attach it to the data source and pick the line it writes to")
                .colored(PonderPalette.BLUE)
                .pointAt(util.vector().blockSurface(link, Direction.NORTH))
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(85);

        // 数据从链接流向转发器
        scene.overlay().showLine(PonderPalette.GREEN, util.vector().topOf(link), util.vector().topOf(transmitter), 70);
        scene.overlay().showText(70)
                .text("The link then writes its data into the transmitter, line by line")
                .colored(PonderPalette.GREEN)
                .pointAt(util.vector().topOf(transmitter))
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(80);

        // 外设绑定模块卡右键绑定后才能被电脑读取
        scene.overlay().showControls(util.vector().topOf(transmitter), Pointing.RIGHT, 50)
                .rightClick()
                .withItem(new ItemStack(CbcItems.DEVICE_MODULE.get()));
        scene.overlay().showText(70)
                .text("Bind it with the Peripheral Binding Module, then the Get Info block reads line N")
                .colored(PonderPalette.RED)
                .pointAt(util.vector().topOf(transmitter))
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(80);

        scene.markAsFinished();
    }

    private static void sculptingHand(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = new CreateSceneBuilder(builder);
        scene.title("sculpting_hand", "Sculpting Hand");
        scene.configureBasePlate(0, 0, 6);
        scene.scaleSceneView(0.8F);
        scene.showBasePlate();
        scene.idle(10);

        BlockPos hand = util.grid().at(3, 1, 2);
        BlockPos seat = util.grid().at(5, 1, 2);
        // 传动轴从机械手侧面（南侧）接入，创造马达接在传动轴末端，机械手与坐垫之间空出一格
        Selection driveTrain = util.select().fromTo(3, 1, 3, 3, 1, 4);
        // 坐垫的碰撞高度约 6/16 格，烈焰人正好站在坐垫顶面上
        Vec3 blazePos = new Vec3(seat.getX() + 0.5, seat.getY() + 0.375, seat.getZ() + 0.5);

        // 机械手先握住捏塑创生之手，再出现在场景中
        scene.world().modifyBlockEntityNBT(util.select().position(hand), DeployerBlockEntity.class, nbt -> {
            nbt.putString("Mode", "USE");
            nbt.put("HeldItem", new ItemStack(CbcItems.SCULPTING_HAND.get())
                    .save(scene.world().getHolderLookupProvider()));
        });
        scene.world().showSection(util.select().position(hand), Direction.WEST);
        scene.idle(10);
        scene.world().showSection(util.select().position(seat), Direction.DOWN);
        scene.idle(20);

        ElementLink<EntityElement> blaze = scene.world().createEntity(level -> {
            SuperBlaze entity = CbcEntities.SUPER_BLAZE.get().create(level);
            if (entity != null) {
                entity.setPos(blazePos.x, blazePos.y, blazePos.z);
                entity.setNoGravity(true);
                entity.setNoAi(true);
            }
            return entity;
        });
        scene.idle(20);

        scene.overlay().showText(60)
                .text("Right-click the Super Blaze")
                .pointAt(blazePos)
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(50);

        // 机械手代替玩家动手：侧面接着动力链，动力贯通后才会动作
        scene.world().showSection(driveTrain, Direction.SOUTH);
        scene.idle(10);
        scene.world().setKineticSpeed(driveTrain, 32);
        scene.idle(20);
        scene.world().setKineticSpeed(util.select().position(hand), 32);
        scene.idle(20);

        // 机械手伸出，探到坐垫上的超级烈焰人身上
        scene.world().moveDeployer(hand, 1F, 25);
        scene.idle(30);

        // 接触生效：烈焰人被捏塑掉，留下烈焰超级计算机单元
        scene.world().modifyEntity(blaze, Entity::discard);
        scene.world().createItemEntity(blazePos.add(0, 0.1, 0), new Vec3(0, 0.3, 0),
                new ItemStack(CbcItems.BLAZE_SUPERCOMPUTER_UNIT.get()));
        scene.idle(10);

        scene.overlay().showText(60)
                .text("It sculpts the Super Blaze into a Blaze Supercomputer Unit")
                .colored(PonderPalette.GREEN)
                .pointAt(blazePos)
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(40);

        // 机械手缩回原位
        scene.world().moveDeployer(hand, 0F, 25);
        scene.idle(20);

        // 补充说明：耐久条显示剩余使用次数
        scene.overlay().showText(70)
                .text("The durability bar shows the remaining uses (32 in total)")
                .colored(PonderPalette.BLUE)
                .pointAt(util.vector().blockSurface(hand, Direction.WEST))
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(60);

        scene.markAsFinished();
    }

    private static void unchargedSculptingHand(SceneBuilder scene, SceneBuildingUtil util) {
        scene.title("uncharged_sculpting_hand", "Uncharged Sculpting Hand");
        scene.configureBasePlate(0, 0, 5);
        scene.scaleSceneView(0.9F);
        scene.showBasePlate();
        scene.idle(10);

        BlockPos rod = util.grid().at(2, 1, 2);
        Vec3 rodTip = util.vector().topOf(rod);
        Vec3 handPos = util.vector().of(2.5, 1.05, 1.5);

        scene.world().showSection(util.select().position(rod), Direction.UP);
        scene.idle(15);

        // 雷雨天：整片场景下起雨
        startStorm(scene, util);
        scene.idle(10);

        scene.overlay().showText(70)
                .text("On a stormy day, place the Uncharged Sculpting Hand under a lightning rod")
                .pointAt(rodTip)
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(60);

        // 把未充能的手放在避雷针脚下
        ElementLink<EntityElement> hand = scene.world().createItemEntity(
                handPos, Vec3.ZERO, new ItemStack(CbcItems.UNCHARGED_SCULPTING_HAND.get()));
        scene.idle(30);

        scene.overlay().showText(60)
                .text("Lightning strikes the rod")
                .colored(PonderPalette.RED)
                .pointAt(rodTip)
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(12);

        // 真实的闪电实体劈在避雷针顶端
        ElementLink<EntityElement> bolt = scene.world().createEntity(level -> {
            LightningBolt lightning = EntityType.LIGHTNING_BOLT.create(level);
            if (lightning != null) {
                lightning.setPos(rodTip.x, rodTip.y, rodTip.z);
                lightning.setVisualOnly(true);
            }
            return lightning;
        });
        scene.idle(3);

        // 电火花顺着避雷针四溅
        ParticleEmitter sparks = scene.effects()
                .particleEmitterWithinBlockSpace(ParticleTypes.ELECTRIC_SPARK, new Vec3(0, 0.1, 0));
        scene.effects().emitParticles(rodTip, sparks, 4, 12);
        scene.idle(10);
        scene.world().modifyEntity(bolt, Entity::discard);

        // 雷电的能量灌入手中的未充能手，替换成充能完成的手
        scene.world().modifyEntity(hand, entity -> {
            if (entity instanceof ItemEntity item) {
                item.setItem(new ItemStack(CbcItems.SCULPTING_HAND.get()));
            }
        });
        scene.effects().emitParticles(handPos, sparks, 6, 12);
        scene.idle(12);

        scene.overlay().showText(60)
                .text("The lightning charges it into a Sculpting Hand")
                .colored(PonderPalette.GREEN)
                .pointAt(handPos)
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(50);

        scene.markAsFinished();
    }

    /** 在整个场景上方撒下雨粒子，营造雷雨天。 */
    private static void startStorm(SceneBuilder scene, SceneBuildingUtil util) {
        ParticleEmitter rain = scene.effects()
                .simpleParticleEmitter(ParticleTypes.RAIN, new Vec3(0, -0.6, 0));
        for (int x = 0; x < 5; x++) {
            for (int z = 0; z < 5; z++) {
                scene.effects().emitParticles(util.vector().of(x + 0.5, 4.5, z + 0.5), rain, 1, 120);
            }
        }
    }
}
