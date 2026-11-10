package com.mcl.cbtc.client;

import com.mcl.cbtc.CreateBetterComputer;
import com.mcl.cbtc.menu.ComputerMenu;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.minecraft.client.gui.screens.MenuScreens;

@Mod(value = CreateBetterComputer.MODID, dist = Dist.CLIENT)
@EventBusSubscriber(modid = CreateBetterComputer.MODID, value = Dist.CLIENT)
public class CbcClient {
    public CbcClient(ModContainer container) {
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
        // 思索（Ponder）教程：CB电脑
        net.createmod.ponder.foundation.PonderIndex.addPlugin(new com.mcl.cbtc.client.ponder.CbcPonderPlugin());
        // 网络歌曲状态上报：歌曲只在客户端存在，需要定时推给服务端
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.register(
                com.mcl.cbtc.integration.netmusic.MusicReporter.class);
    }

    @SubscribeEvent
    static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            net.minecraft.client.renderer.ItemBlockRenderTypes.setRenderLayer(
                    com.mcl.cbtc.registry.CbcBlocks.TYPEWRITER.get(),
                    net.minecraft.client.renderer.RenderType.cutoutMipped());
            // 转速控制器的贴图带透明边缘，必须走 cutout，否则透明处会画成黑块
            net.minecraft.client.renderer.ItemBlockRenderTypes.setRenderLayer(
                    com.mcl.cbtc.registry.CbcBlocks.SPEED_CONTROLLER.get(),
                    net.minecraft.client.renderer.RenderType.cutout());
        });
        CreateBetterComputer.LOGGER.info("CB电脑客户端初始化");
        // 绑定指南针沿用原版指南针的指针逻辑：按 LODESTONE_TRACKER 组件算角度驱动模型 overrides
        net.minecraft.client.renderer.item.ItemProperties.register(
                com.mcl.cbtc.registry.CbcItems.BOUND_COMPASS.get(),
                net.minecraft.resources.ResourceLocation.withDefaultNamespace("angle"),
                new net.minecraft.client.renderer.item.CompassItemPropertyFunction((level, stack, entity) -> {
                    net.minecraft.world.item.component.LodestoneTracker tracker =
                            stack.get(net.minecraft.core.component.DataComponents.LODESTONE_TRACKER);
                    return tracker == null ? null : tracker.target().orElse(null);
                }));
        // 齿轮箱的传动轴由 Flywheel 可视化绘制，必须给新方块实体类型注册可视化器
        CBGearboxVisuals.register();
        // 智能转速控制器同样是双端传动轴方块，可视化注册方式一致
        SmartSpeedControllerVisuals.register();
    }

    @SubscribeEvent
    static void registerRenderers(net.neoforged.neoforge.client.event.EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(com.mcl.cbtc.registry.CbcBlockEntities.TYPEWRITER.get(),
                CBTypewriterRenderer::new);
        // 序列齿轮箱的传动轴由 SplitShaftRenderer 绘制，输入端与输出端转速不同时才能看到差异
        event.registerBlockEntityRenderer(com.mcl.cbtc.registry.CbcBlockEntities.GEARBOX.get(),
                com.simibubi.create.content.kinetics.transmission.SplitShaftRenderer::new);
        // 默念浮字必须由方块实体渲染器绘制，Sable 才会在结构上施加姿态（跟随 + 倾斜）
        event.registerBlockEntityRenderer(com.mcl.cbtc.registry.CbcBlockEntities.COMPUTER.get(),
                MurmurRenderer::new);
        event.registerBlockEntityRenderer(com.mcl.cbtc.registry.CbcBlockEntities.SPEAKER.get(),
                MurmurRenderer::new);
        // 智能转速控制器：两端传动轴由 SplitShaftRenderer 绘制
        event.registerBlockEntityRenderer(com.mcl.cbtc.registry.CbcBlockEntities.SPEED_CONTROLLER.get(),
                com.simibubi.create.content.kinetics.transmission.SplitShaftRenderer::new);
        // 讲解石及其石质变种同样可以发声，默念文字也要画在它上方
        event.registerBlockEntityRenderer(com.mcl.cbtc.registry.CbcBlockEntities.LECTURE_STONE.get(),
                MurmurRenderer::new);
        // 超级烈焰人：复用原版烈焰人模型与动画，只换贴图
        event.registerEntityRenderer(com.mcl.cbtc.registry.CbcEntities.SUPER_BLAZE.get(),
                SuperBlazeRenderer::new);
    }

    @SubscribeEvent
    static void registerScreens(net.neoforged.neoforge.client.event.RegisterMenuScreensEvent event) {
        event.register(com.mcl.cbtc.registry.CbcMenus.COMPUTER.get(), ComputerScreen::new);
        event.register(com.mcl.cbtc.registry.CbcMenus.EXPANDER.get(), ExpanderScreen::new);
        event.register(com.mcl.cbtc.registry.CbcMenus.SPEAKER.get(), SpeakerScreen::new);
        event.register(com.mcl.cbtc.registry.CbcMenus.MUSIC_MODULE.get(), MusicModuleScreen::new);
    }
}
