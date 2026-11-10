package com.mcl.cbtc.compat;

import com.mcl.cbtc.CreateBetterComputer;
import com.mcl.cbtc.item.BoundCompassItem;
import com.mcl.cbtc.item.TargetLocatorItem;
import dev.simulated_team.simulated.content.blocks.nav_table.NavTableBlockEntity;
import dev.simulated_team.simulated.content.blocks.nav_table.navigation_target.NavigationTarget;
import dev.simulated_team.simulated.index.SimDataComponents;
import java.lang.ref.WeakReference;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * 目标定位模块与航空学（Simulated）的对接层。
 * <p>
 * 负责三件事：
 * <ol>
 *   <li>注册一个导航目标类型，给模块物品挂上 TARGET 组件后导航台就会指向它；</li>
 *   <li>导航台每刻调用本类时记录"模块 ID → 它所在导航台"，供积木读写；</li>
 *   <li>提供按模块 ID 读取 / 改写坐标的入口。</li>
 * </ol>
 * 只有确认装了 Simulated 才会加载本类。
 */
public final class TargetLocatorCompat {
    /** Simulated 的导航目标注册表（由航空学创建）。 */
    public static final ResourceKey<Registry<NavigationTarget>> NAV_TARGET_REGISTRY =
            ResourceKey.createRegistryKey(ResourceLocation.fromNamespaceAndPath("simulated",
                    "navigation_target"));

    private static final DeferredRegister<NavigationTarget> NAV_TARGETS =
            DeferredRegister.create(NAV_TARGET_REGISTRY, CreateBetterComputer.MODID);

    private static final DeferredHolder<NavigationTarget, NavigationTarget> NAV_TARGET =
            NAV_TARGETS.register("target_locator", TargetLocatorNavTarget::new);

    /** 绑定指南针的导航目标：指向随身携带对应挂件的玩家。 */
    private static final DeferredHolder<NavigationTarget, NavigationTarget> BOUND_COMPASS_TARGET =
            NAV_TARGETS.register("bound_compass", BoundCompassNavTarget::new);

    /** 模块 ID → 它当前所在的导航台（弱引用，区块卸载后自动失效）。 */
    private static final Map<String, WeakReference<NavTableBlockEntity>> LINKS = new ConcurrentHashMap<>();

    private TargetLocatorCompat() {
    }

    public static void register(IEventBus bus) {
        NAV_TARGETS.register(bus);
    }

    /** 补上导航目标组件；已经有就不再重复设置。 */
    public static void ensureTarget(ItemStack stack) {
        if (stack.isEmpty() || stack.has(SimDataComponents.TARGET)) {
            return;
        }
        stack.set(SimDataComponents.TARGET, NAV_TARGET.get());
    }

    /** 给绑定指南针补上导航目标组件；已经有就不再重复设置。 */
    public static void ensureBoundCompassTarget(ItemStack stack) {
        if (stack.isEmpty() || stack.has(SimDataComponents.TARGET)) {
            return;
        }
        stack.set(SimDataComponents.TARGET, BOUND_COMPASS_TARGET.get());
    }

    /** 读取模块的 X / Z 坐标；模块不在导航台里时返回 0。 */
    public static double read(String moduleId, String axis) {
        NavTableBlockEntity nav = find(moduleId);
        if (nav == null) {
            return 0;
        }
        ItemStack held = nav.getHeldItem();
        if (!isModule(held, moduleId)) {
            return 0;
        }
        return "z".equalsIgnoreCase(axis) ? TargetLocatorItem.z(held) : TargetLocatorItem.x(held);
    }

    /** 改写模块某一轴的坐标；模块不在导航台里时返回 false。 */
    public static boolean write(String moduleId, String axis, double value) {
        NavTableBlockEntity nav = find(moduleId);
        if (nav == null) {
            return false;
        }
        ItemStack held = nav.getHeldItem();
        if (!isModule(held, moduleId)) {
            return false;
        }
        ItemStack updated = held.copy();
        boolean zAxis = "z".equalsIgnoreCase(axis);
        double x = zAxis ? TargetLocatorItem.x(updated) : value;
        double z = zAxis ? value : TargetLocatorItem.z(updated);
        TargetLocatorItem.setPosition(updated, x, z);
        nav.setHeldItem(updated);
        nav.setChanged();
        return true;
    }

    private static boolean isModule(ItemStack stack, String moduleId) {
        return stack.getItem() instanceof TargetLocatorItem
                && moduleId.equals(TargetLocatorItem.moduleId(stack));
    }

    private static NavTableBlockEntity find(String moduleId) {
        if (moduleId == null || moduleId.isBlank()) {
            return null;
        }
        WeakReference<NavTableBlockEntity> ref = LINKS.get(moduleId);
        NavTableBlockEntity nav = ref == null ? null : ref.get();
        if (nav == null || nav.isRemoved() || nav.getLevel() == null || nav.getLevel().isClientSide) {
            LINKS.remove(moduleId);
            return null;
        }
        return nav;
    }

    /** 导航目标：指向模块写入的世界 X / Z。 */
    private static final class TargetLocatorNavTarget implements NavigationTarget {
        @Override
        public Vec3 getTarget(NavTableBlockEntity be, ItemStack stack) {
            // 优先读导航台物品栏里的那一份，保证 ID / 坐标写回的是真正的物品
            ItemStack held = be.getHeldItem();
            ItemStack module = held.getItem() instanceof TargetLocatorItem ? held : stack;
            if (!(module.getItem() instanceof TargetLocatorItem)) {
                return null;
            }
            boolean newlyIdentified = TargetLocatorItem.moduleId(module).isBlank();
            String moduleId = TargetLocatorItem.ensureId(module);
            if (newlyIdentified) {
                // ID 是刚写进导航台物品栏的，需要让方块实体标记为待保存
                be.setChanged();
            }
            if (be.getLevel() != null && !be.getLevel().isClientSide) {
                LINKS.put(moduleId, new WeakReference<>(be));
            }
            Vec3 self = be.getProjectedSelfPos();
            double y = self == null ? 0 : self.y;
            // 与地图/指南针导航目标一致：返回的是世界坐标，X/Z 取自模块
            return new Vec3(TargetLocatorItem.x(module), y, TargetLocatorItem.z(module));
        }
    }

    /** 导航目标：指向随身携带绑定挂件的那名玩家。 */
    private static final class BoundCompassNavTarget implements NavigationTarget {
        @Override
        public Vec3 getTarget(NavTableBlockEntity be, ItemStack stack) {
            Level level = be.getLevel();
            if (level == null || level.isClientSide) {
                return null;
            }
            MinecraftServer server = level.getServer();
            if (server == null) {
                return null;
            }
            ServerPlayer holder = BoundCompassItem.holderOf(server, BoundCompassItem.boundCharmId(stack));
            return holder == null ? null : new Vec3(holder.getX(), holder.getY(), holder.getZ());
        }
    }
}
