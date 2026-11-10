package com.mcl.cbtc;

import com.mojang.logging.LogUtils;
import com.mcl.cbtc.integration.create.CreateIntegration;
import com.mcl.cbtc.registry.CbcRegistries;
import com.mcl.cbtc.signal.BroadcastBus;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;

/**
 * CB电脑（Create: Better Computer）主类。
 */
@Mod(CreateBetterComputer.MODID)
public class CreateBetterComputer {
    public static final String MODID = "cbtc";
    public static final Logger LOGGER = LogUtils.getLogger();

    public CreateBetterComputer(IEventBus modEventBus, ModContainer modContainer) {
        CbcRegistries.register(modEventBus);
        CreateIntegration.register(modEventBus);
        modEventBus.addListener(this::commonSetup);

        NeoForge.EVENT_BUS.addListener(this::onServerTick);
        NeoForge.EVENT_BUS.addListener(this::onServerStopped);
        // 聊天监听显式注册，保证仅依赖注解扫描的环境也能收到玩家发言
        NeoForge.EVENT_BUS.addListener(com.mcl.cbtc.net.ChatBus::onServerChat);
        // 捏塑创生之手的雷击充能与爆炸免疫，显式注册避免依赖注解扫描
        NeoForge.EVENT_BUS.addListener(com.mcl.cbtc.event.SculptingHandEvents::onStruckByLightning);
        NeoForge.EVENT_BUS.addListener(com.mcl.cbtc.event.SculptingHandEvents::onExplosionDetonate);
        // 悦灵会抢走手里的物品，捏塑创生之手需要提前拦下右键
        NeoForge.EVENT_BUS.addListener(com.mcl.cbtc.event.SculptingHandEvents::onEntityInteract);
        // 超级烈焰人的火球：伤害翻倍、着火 20 秒
        NeoForge.EVENT_BUS.addListener(com.mcl.cbtc.event.SuperBlazeEvents::onDamagePre);
        NeoForge.EVENT_BUS.addListener(com.mcl.cbtc.event.SuperBlazeEvents::onDamagePost);
        // 烈焰蛋糕喂食烈焰人 → 超级烈焰人
        NeoForge.EVENT_BUS.addListener(com.mcl.cbtc.event.SuperBlazeEvents::onEntityInteract);
        // 烈焰蛋糕喂给烈焰人刷怪笼 → 30 秒超级烈焰人刷怪笼
        NeoForge.EVENT_BUS.addListener(com.mcl.cbtc.event.SuperSpawnerEvents::onRightClickBlock);
        // 首次用超级烈焰棒/粉喂养燃烧室的进度
        NeoForge.EVENT_BUS.addListener(com.mcl.cbtc.event.SuperBlazeEvents::onRightClickBlock);
        // 玩家退出时清掉外设选中的中间状态，避免 UUID 记录长期堆积
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent event) -> {
            com.mcl.cbtc.item.DeviceModuleItem.clearSelection(event.getEntity().getUUID());
            com.mcl.cbtc.network.MusicStateStore.forget(event.getEntity().getUUID());
        });
        // 物理结构组装/拆卸时刷新外设绑定定位（Sable 已加载才生效）
        com.mcl.cbtc.device.Devices.registerSubLevelBindingTracker();
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        // 注册阶段结束后，把 CB电脑 方块挂到 Create 的显示数据源表上
        event.enqueueWork(CreateIntegration::bindBlocks);
        // 装了饰品栏模组时把磁石挂件注册成饰品
        event.enqueueWork(() -> {
            if (com.mcl.cbtc.compat.Curios.loaded()) {
                com.mcl.cbtc.compat.CuriosCompat.registerCurio(
                        com.mcl.cbtc.registry.CbcItems.LODESTONE_CHARM.get());
            }
        });
        // 智能转速控制器的 Shift 概要只在客户端生效，服务端不加载相关类
        event.enqueueWork(() -> {
            if (net.neoforged.fml.loading.FMLEnvironment.dist.isClient()) {
                com.mcl.cbtc.compat.SpeedControllerTooltip.register();
            }
        });
        LOGGER.info("CB电脑已装载：积木编程模块初始化");
    }

    private void onServerTick(ServerTickEvent.Post event) {
        // 所有广播在服务器 tick 末尾统一派发，执行器不会在写程序时被递归打断
        BroadcastBus.flush();
        com.mcl.cbtc.lan.LanBus.flushAll();
        com.mcl.cbtc.net.InternetBus.flush();
        com.mcl.cbtc.net.ChatBus.flush();
        com.mcl.cbtc.world.SuperSpawnerTracker.get(event.getServer()).tick(event.getServer());
    }

    private void onServerStopped(ServerStoppedEvent event) {
        BroadcastBus.reset(event.getServer());
        com.mcl.cbtc.lan.LanBus.reset(event.getServer());
        com.mcl.cbtc.net.InternetBus.reset(event.getServer());
        com.mcl.cbtc.net.ChatBus.reset(event.getServer());
    }

    public static ResourceLocation asResource(String path) {
        return ResourceLocation.fromNamespaceAndPath(MODID, path);
    }
}
