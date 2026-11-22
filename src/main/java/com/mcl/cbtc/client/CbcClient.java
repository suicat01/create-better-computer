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
        // 枪炮手的瞄准组件：戴上后绘制取景器式的屏幕效果
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(GunnerSightOverlay::onRenderGui);
        // 枪炮手的瞄准组件：Ctrl + 滚轮调整画面缩放
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(GunnerSightZoom::onMouseScroll);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(GunnerSightZoom::onComputeFov);
        // 预测炮弹落点：在世界里画圆环与入射角度线（含航空学物理结构附着）
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(ImpactMarkerRenderer::onRenderLevel);
        // 智能炮座的局部模型必须在第一次资源重载前创建，否则 Flywheel 收集不到，渲染成紫黑格
        if (com.mcl.cbtc.compat.cbc.CbcCannons.loaded()) {
            SmartCannonMountModels.init();
        }
        // 瓦列里中士的动力臂局部模型同样必须在资源重载前创建
        com.mcl.cbtc.client.sergeant.SergeantArmModels.init();
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
            // 高级显示链接器的贴图同样带透明边缘
            net.minecraft.client.renderer.ItemBlockRenderTypes.setRenderLayer(
                    com.mcl.cbtc.registry.CbcBlocks.ADVANCED_DISPLAY_LINK.get(),
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
        // 装了饰品栏模组时，让枪炮手的瞄准组件在头部饰品槽里真的画出来
        if (com.mcl.cbtc.compat.Curios.loaded()) {
            com.mcl.cbtc.compat.GunnerSightCurioRenderer.register();
        }
        // 编程界面是自绘全屏 UI，把 FTB 的侧栏按钮（任务 / 队伍 / 地图）关掉
        registerFtbSidebarBlacklist();
    }

    /**
     * FTB Library 默认会给所有容器界面挂一排侧栏按钮（任务、队伍、地图）。
     * 这里用它的官方黑名单 API 把 CB 编程界面排除掉；FTB 没装时整段跳过。
     */
    private static void registerFtbSidebarBlacklist() {
        try {
            Class<?> apiClass = Class.forName("dev.ftb.mods.ftblibrary.api.client.FTBLibraryClientApi");
            Object api = apiClass.getMethod("get").invoke(null);
            apiClass.getMethod("addSidebarScreenBlacklist", String[].class)
                    .invoke(api, (Object) new String[]{ComputerScreen.class.getName()});
        } catch (Throwable ignored) {
            // 没装 FTB Library 或版本没有这个 API：保持原样
        }
    }

    @SubscribeEvent
    static void registerReloadListeners(net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent event) {
        // 资源重载会重建着色器，滤镜的后处理链要跟着丢弃
        event.registerReloadListener(GunnerSightFilter.RELOAD_LISTENER);
    }

    @SubscribeEvent
    static void registerLayerDefinitions(net.neoforged.neoforge.client.event.EntityRenderersEvent.RegisterLayerDefinitions event) {
        if (!com.mcl.cbtc.compat.Curios.loaded()) {
            return;
        }
        // 瞄准组件的饰品渲染只需一个空的头部部件，用来跟随头部转动
        event.registerLayerDefinition(com.mcl.cbtc.compat.GunnerSightCurioRenderer.LAYER,
                () -> net.minecraft.client.model.geom.builders.LayerDefinition.create(
                        com.mcl.cbtc.compat.GunnerSightCurioRenderer.mesh(), 64, 64));
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
        // 瓦列里中士：沿用动力臂的四段动画，贴图与局部模型换成美术那一套
        event.registerBlockEntityRenderer(com.mcl.cbtc.registry.CbcBlockEntities.SERGEANT_VALERY.get(),
                com.mcl.cbtc.client.sergeant.SergeantArmRenderer::new);
        // 超级烈焰人：复用原版烈焰人模型与动画，只换贴图
        event.registerEntityRenderer(com.mcl.cbtc.registry.CbcEntities.SUPER_BLAZE.get(),
                SuperBlazeRenderer::new);
        // 智能炮座：旋转炮架由自定义渲染器绘制（去掉了 CBC 原版的传动轴）
        if (com.mcl.cbtc.compat.cbc.CbcCannons.SMART_CANNON_MOUNT_BE != null) {
            event.registerBlockEntityRenderer(
                    com.mcl.cbtc.compat.cbc.CbcCannons.SMART_CANNON_MOUNT_BE.get(),
                    SmartCannonMountRenderer::new);
        }
    }

    @SubscribeEvent
    static void registerScreens(net.neoforged.neoforge.client.event.RegisterMenuScreensEvent event) {
        event.register(com.mcl.cbtc.registry.CbcMenus.COMPUTER.get(), ComputerScreen::new);
        event.register(com.mcl.cbtc.registry.CbcMenus.EXPANDER.get(), ExpanderScreen::new);
        event.register(com.mcl.cbtc.registry.CbcMenus.SPEAKER.get(), SpeakerScreen::new);
        event.register(com.mcl.cbtc.registry.CbcMenus.MUSIC_MODULE.get(), MusicModuleScreen::new);
        event.register(com.mcl.cbtc.registry.CbcMenus.SERGEANT_VALERY.get(),
                com.mcl.cbtc.client.sergeant.SergeantScreen::new);
    }
}
