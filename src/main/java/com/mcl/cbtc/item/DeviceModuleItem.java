package com.mcl.cbtc.item;

import com.mcl.cbtc.device.DeviceKind;
import com.mcl.cbtc.network.DeviceSelectPayload;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * 外设绑定模块卡：可远程绑定任意数量的外设并命名（可中文），供积木按名称读取。
 * <p>
 * 交互：右键外设选中 → 再次右键弹出命名框 → 命名完成绑定；Shift+右键解绑。
 * <p>
 * 绑定记录保存两种坐标：结构 UUID + 结构内坐标（方块在物理结构里）或主世界坐标。
 * 另外记一个"最近一次的世界坐标"，结构被拆掉且服务端缓存丢失时用它把绑定找回来。
 */
public class DeviceModuleItem extends UpgradeModuleItem {
    private static final String DEVICES = "PeripheralDevices";

    /** 服务端权威的"已选中"记录：玩家 UUID → 选中的外设。 */
    private static final java.util.Map<java.util.UUID, Selection> SELECTIONS = new java.util.concurrent.ConcurrentHashMap<>();

    public DeviceModuleItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        if (player == null) {
            return InteractionResult.PASS;
        }
        ItemStack stack = context.getItemInHand();
        Level level = context.getLevel();
        BlockPos clicked = context.getClickedPos();
        Binding target = target(level, clicked);
        if (target == null) {
            return InteractionResult.FAIL;
        }
        // 绑定是服务端权威的行为，选中状态也只存服务端，避免客户端状态被同步覆盖
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (player.isShiftKeyDown()) {
            unbind(stack, level, target);
            SELECTIONS.remove(player.getUUID());
            notifyClient(player, DeviceSelectPayload.CLEARED, target, "");
            player.displayClientMessage(Component.translatable("item.cbtc.device_module.unbound_msg",
                    target.name()), true);
            return InteractionResult.CONSUME;
        }
        Selection selection = SELECTIONS.get(player.getUUID());
        if (selection == null || !selection.matches(level, clicked)) {
            // 第一次右键：记录选中，并让客户端画出描边
            SELECTIONS.put(player.getUUID(), new Selection(level.dimension().location(), clicked));
            notifyClient(player, DeviceSelectPayload.SELECTED, target, "");
            player.displayClientMessage(Component.translatable("item.cbtc.device_module.selected_msg",
                    target.name()), true);
            return InteractionResult.CONSUME;
        }
        // 第二次右键：清掉选中，通知客户端打开命名框
        SELECTIONS.remove(player.getUUID());
        Device existing = findDevice(stack, level, clicked);
        notifyClient(player, DeviceSelectPayload.OPEN_NAME, target, existing == null ? "" : existing.name());
        return InteractionResult.CONSUME;
    }

    /** 把选中状态发给该玩家自己的客户端。 */
    private static void notifyClient(Player player, int kind, Binding target, String currentName) {
        if (player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
            net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(serverPlayer,
                    new com.mcl.cbtc.network.DeviceSelectPayload(kind, target.pos(), target.name(),
                            target.kind().displayName(), currentName == null ? "" : currentName));
        }
    }

    /** 玩家当前选中的外设：维度 + 坐标。 */
    private record Selection(net.minecraft.resources.ResourceLocation dimension, BlockPos pos) {
        boolean matches(Level level, BlockPos other) {
            return pos.equals(other) && dimension.equals(level.dimension().location());
        }
    }

    /** 玩家登出或换维度时清掉残留的选中记录。 */
    public static void clearSelection(java.util.UUID playerId) {
        SELECTIONS.remove(playerId);
    }

    @Nullable
    private static Binding target(Level level, BlockPos clicked) {
        DeviceKind kind = com.mcl.cbtc.device.Devices.kindAt(level, clicked);
        if (kind == null || !bindable(kind)) {
            com.mcl.cbtc.CreateBetterComputer.LOGGER.info("外设绑定：{} 不是可绑定外设，方块实体={}",
                    clicked, level.getBlockEntity(clicked));
            return null;
        }
        // 方块可能在物理结构里，用穿透后的方块实体取名字，避免拿到空气
        BlockEntity be = com.mcl.cbtc.device.Devices.blockEntityAt(level, clicked);
        String name = be == null ? kind.displayName() : be.getBlockState().getBlock().getName().getString();
        return new Binding(clicked, kind, name);
    }

    /**
     * 目前允许绑定的外设种类。
     * <p>
     * 应力表、转速表、高度/速度/姿态传感器、结构图解传感器暂时移除绑定，
     * 之后由统一读取接口接管；这里保留的是有各自专用积木的外设。
     */
    private static boolean bindable(DeviceKind kind) {
        return switch (kind) {
            case GEARBOX, SPEED_CONTROLLER, TYPEWRITER, SPEAKER, LECTURE_STONE, NAV_TABLE,
                    INFO_TRANSMITTER -> true;
            default -> false;
        };
    }

    /** 写入或更新一台外设的绑定记录。 */
    public static void bind(ItemStack stack, Level level, BlockPos pos, String name) {
        Binding target = target(level, pos);
        if (target == null) {
            return;
        }
        // 定位：结构内坐标直接记结构 UUID；主世界坐标直接记世界坐标，绝不允许"结构内坐标 + 空 UUID"
        com.mcl.cbtc.device.Devices.DeviceLocation loc =
                com.mcl.cbtc.device.Devices.locate(level, target.pos());
        com.mcl.cbtc.device.Devices.DeviceLocation canonical =
                com.mcl.cbtc.device.Devices.normalizeLocation(level, loc);
        if (canonical == null) {
            canonical = new com.mcl.cbtc.device.Devices.DeviceLocation(null, target.pos());
        }
        BlockPos storePos = canonical.pos();
        UUID subLevelId = canonical.subLevelId();
        BlockPos worldPos = currentWorldPos(level, canonical, target.pos());

        CompoundTag tag = tag(stack);
        ListTag entries = tag.getList(DEVICES, Tag.TAG_COMPOUND);
        int index = -1;
        int nextNumber = 1;
        String dim = level.dimension().location().toString();
        for (int i = 0; i < entries.size(); i++) {
            CompoundTag entry = entries.getCompound(i);
            if (dim.equals(entry.getString("Dim"))
                    && sameDevice(level, entry, target.pos(), storePos, subLevelId)) {
                index = i;
            }
            if (entry.getInt("Number") >= nextNumber) {
                nextNumber = entry.getInt("Number") + 1;
            }
        }
        String defaultName = "未命名的" + target.kind().displayName() + String.format("%03d", nextNumber);
        CompoundTag entry = index >= 0 ? entries.getCompound(index) : new CompoundTag();
        if (entry.getString("Id").isBlank()) {
            entry.putString("Id", UUID.randomUUID().toString());
            entry.putInt("Number", nextNumber);
        }
        entry.putLong("Pos", storePos.asLong());
        entry.putString("Dim", level.dimension().location().toString());
        writeSubLevel(entry, subLevelId);
        entry.putLong("World", worldPos.asLong());
        entry.putString("Name", name == null || name.isBlank() ? defaultName : name.trim());
        entry.putString("Kind", target.kind().id());
        if (index >= 0) {
            entries.set(index, entry);
        } else {
            entries.add(entry);
        }
        tag.put(DEVICES, entries);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        // 注册/刷新定位缓存，物理结构组装/拆卸时据此换算坐标
        com.mcl.cbtc.device.Devices.registerLocation(level, entry.getString("Id"), canonical, target.kind());
    }

    /**
     * 工作台绑定：把定位模块登记到外设模块卡上。
     * <p>
     * 只记录模块 ID（写进卡的"Id"字段），坐标始终保存在模块物品自己的 NBT 里，
     * 积木读写时经由模块所在的导航台找到那个物品。
     */
    public static boolean bindTargetLocator(ItemStack card, ItemStack module) {
        String moduleId = TargetLocatorItem.ensureId(module);
        if (moduleId.isBlank()) {
            return false;
        }
        CompoundTag tag = tag(card);
        ListTag entries = tag.getList(DEVICES, Tag.TAG_COMPOUND);
        int nextNumber = 1;
        for (int i = 0; i < entries.size(); i++) {
            CompoundTag entry = entries.getCompound(i);
            if (DeviceKind.TARGET_LOCATOR.id().equals(entry.getString("Kind"))
                    && moduleId.equals(entry.getString("Id"))) {
                // 已经绑定过这个模块
                return true;
            }
            if (entry.getInt("Number") >= nextNumber) {
                nextNumber = entry.getInt("Number") + 1;
            }
        }
        CompoundTag entry = new CompoundTag();
        entry.putString("Id", moduleId);
        entry.putInt("Number", nextNumber);
        entry.putString("Kind", DeviceKind.TARGET_LOCATOR.id());
        entry.putString("Name", "未命名的" + DeviceKind.TARGET_LOCATOR.displayName()
                + String.format("%03d", nextNumber));
        entries.add(entry);
        tag.put(DEVICES, entries);
        card.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        return true;
    }

    private static void writeSubLevel(CompoundTag entry, java.util.UUID subLevelId) {
        if (subLevelId == null) {
            entry.remove("SubLevel");
        } else {
            entry.putString("SubLevel", subLevelId.toString());
        }
    }

    /**
     * 旧记录是否指向玩家点中的同一块外设。
     * <p>
     * 结构内坐标在存储区里是全局唯一的，所以坐标相等就是同一块（UUID 因重新组装而变化也不影响）。
     * 换算出错时再退一步，把旧记录投影回主世界按距离比对。
     */
    private static boolean sameDevice(Level level, CompoundTag entry, BlockPos clicked, BlockPos storePos,
                                      java.util.UUID subLevelId) {
        return sameDevice(level, clicked, storePos, subLevelId,
                BlockPos.of(entry.getLong("Pos")), readSubLevel(entry));
    }

    private static boolean sameDevice(Level level, BlockPos clicked, BlockPos storePos,
                                      java.util.UUID subLevelId, BlockPos oldPos, java.util.UUID oldSub) {
        if (oldPos.equals(storePos)) {
            return true;
        }
        com.mcl.cbtc.device.Devices.DeviceLocation oldLoc = com.mcl.cbtc.device.Devices.normalizeLocation(
                level, new com.mcl.cbtc.device.Devices.DeviceLocation(oldSub, oldPos));
        com.mcl.cbtc.device.Devices.DeviceLocation newLoc = com.mcl.cbtc.device.Devices.normalizeLocation(
                level, new com.mcl.cbtc.device.Devices.DeviceLocation(subLevelId, storePos));
        if (oldLoc != null && oldLoc.equals(newLoc)) {
            return true;
        }
        Vec3 center = Vec3.atCenterOf(clicked);
        Vec3 oldWorld = com.mcl.cbtc.device.Devices.resolveWorldPos(level,
                new com.mcl.cbtc.device.Devices.DeviceLocation(oldSub, oldPos), center);
        return oldWorld != null && oldWorld.distanceTo(center) <= 2.0;
    }

    @Nullable
    private static java.util.UUID readSubLevel(CompoundTag entry) {
        if (!entry.contains("SubLevel")) {
            return null;
        }
        String raw = entry.getString("SubLevel");
        if (raw.isBlank()) {
            return null;
        }
        try {
            return java.util.UUID.fromString(raw);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static void unbind(ItemStack stack, Level level, Binding target) {
        com.mcl.cbtc.device.Devices.DeviceLocation loc =
                com.mcl.cbtc.device.Devices.normalizeLocation(level,
                        com.mcl.cbtc.device.Devices.locate(level, target.pos()));
        BlockPos storePos = loc == null ? target.pos() : loc.pos();
        java.util.UUID subLevelId = loc == null ? null : loc.subLevelId();
        CompoundTag tag = tag(stack);
        ListTag entries = tag.getList(DEVICES, Tag.TAG_COMPOUND);
        ListTag kept = new ListTag();
        String dim = level.dimension().location().toString();
        for (int i = 0; i < entries.size(); i++) {
            CompoundTag compound = entries.getCompound(i);
            boolean matches = dim.equals(compound.getString("Dim"))
                    && sameDevice(level, compound, target.pos(), storePos, subLevelId);
            if (matches) {
                com.mcl.cbtc.device.Devices.removeLocation(compound.getString("Id"));
            } else {
                kept.add(compound);
            }
        }
        tag.put(DEVICES, kept);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    /** 读取卡内全部外设（纯读取，不做纠错）。 */
    public static List<Device> devices(ItemStack stack) {
        return devices(stack, null);
    }

    /**
     * 读取卡内全部外设，并在服务端把每条记录校正成当前有效的定位（顺手写回 NBT）。
     * <p>
     * 纠错顺序：缓存定位 → 记录坐标（结构内坐标或主世界坐标）→ 记录里最近一次的世界坐标 →
     * 坐标附近的同类外设。这样无论绑定是在结构组装前还是组装后写的、结构是否重新组装过，
     * 都能重新找到同一台外设。
     */
    public static List<Device> devices(ItemStack stack, @Nullable Level level) {
        CompoundTag tag = tag(stack);
        ListTag entries = tag.getList(DEVICES, Tag.TAG_COMPOUND);
        List<Device> result = new ArrayList<>();
        boolean server = level != null && !level.isClientSide;
        boolean changed = false;
        for (int i = 0; i < entries.size(); i++) {
            CompoundTag entry = entries.getCompound(i);
            String id = entry.getString("Id");
            DeviceKind kind = DeviceKind.byId(entry.getString("Kind"));
            if (kind == DeviceKind.TARGET_LOCATOR) {
                // 目标定位模块是物品而不是方块，只记模块 ID，坐标保存在模块自己的 NBT 里
                if (!id.isBlank()) {
                    result.add(new Device(id, entry.getString("Name"), kind,
                            level == null ? null : level.dimension().location(), BlockPos.ZERO, null));
                }
                continue;
            }
            ResourceLocation dim = ResourceLocation.tryParse(entry.getString("Dim"));
            if (dim == null || id.isBlank() || !entry.contains("Pos")) {
                continue;
            }
            BlockPos storedPos = BlockPos.of(entry.getLong("Pos"));
            Level recordLevel = dimensionLevel(level, dim);
            if (kind == null) {
                // 旧记录缺 Kind / Kind 已改名：按坐标上的方块补回来，不能因为缺字段丢掉整条绑定
                if (recordLevel != null && !recordLevel.isClientSide) {
                    kind = com.mcl.cbtc.device.Devices.kindAt(recordLevel, storedPos);
                    if (kind != null) {
                        entry.putString("Kind", kind.id());
                        changed = true;
                    }
                }
                if (kind == null) {
                    continue;
                }
            }
            java.util.UUID storedSub = readSubLevel(entry);
            BlockPos worldFallback = entry.contains("World") ? BlockPos.of(entry.getLong("World")) : null;
            com.mcl.cbtc.device.Devices.DeviceLocation canonical =
                    new com.mcl.cbtc.device.Devices.DeviceLocation(storedSub, storedPos);
            if (server && recordLevel != null) {
                com.mcl.cbtc.device.Devices.DeviceLocation cached =
                        com.mcl.cbtc.device.Devices.currentLocation(recordLevel, id);
                com.mcl.cbtc.device.Devices.DeviceLocation resolved = resolveBinding(
                        recordLevel, kind, cached != null ? cached : canonical, worldFallback);
                if (resolved != null) {
                    canonical = resolved;
                }
                com.mcl.cbtc.device.Devices.registerLocation(recordLevel, id, canonical, kind);
                Vec3 currentWorld = com.mcl.cbtc.device.Devices.worldPos(recordLevel, canonical);
                BlockPos world = currentWorld == null ? worldFallback : BlockPos.containing(currentWorld);
                boolean locChanged = !canonical.equals(new com.mcl.cbtc.device.Devices.DeviceLocation(
                        storedSub, storedPos));
                // 世界坐标只是拆卸后的找回线索：限流写回，避免飞行器移动时每 tick 改物品数据
                boolean worldChanged = false;
                if (world != null) {
                    long now = recordLevel.getGameTime();
                    if (!entry.contains("World")) {
                        worldChanged = true;
                    } else {
                        BlockPos recorded = BlockPos.of(entry.getLong("World"));
                        worldChanged = recorded.distSqr(world) >= 1
                                && now - entry.getLong("WorldT") >= 20;
                    }
                    if (worldChanged) {
                        entry.putLong("WorldT", now);
                    }
                }
                if (locChanged) {
                    entry.putLong("Pos", canonical.pos().asLong());
                    writeSubLevel(entry, canonical.subLevelId());
                }
                if (worldChanged) {
                    entry.putLong("World", world.asLong());
                }
                changed |= locChanged || worldChanged;
            }
            result.add(new Device(id, entry.getString("Name"), kind, dim,
                    canonical.pos(), canonical.subLevelId()));
        }
        if (changed) {
            stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        }
        return result;
    }

    /** 记录所属维度对应的服务端 Level；跨维度绑定要在那个维度里解析坐标。 */
    @Nullable
    private static Level dimensionLevel(Level level, ResourceLocation dim) {
        return com.mcl.cbtc.device.Devices.dimensionLevel(level, dim);
    }

    /**
     * 把一条记录解析成当前有效的定位；找不到时返回最接近的猜测（绝不返回 null 销毁信息）。
     */
    @Nullable
    private static com.mcl.cbtc.device.Devices.DeviceLocation resolveBinding(Level level, DeviceKind kind,
            @Nullable com.mcl.cbtc.device.Devices.DeviceLocation base, @Nullable BlockPos worldFallback) {
        com.mcl.cbtc.device.Devices.DeviceLocation normalized =
                com.mcl.cbtc.device.Devices.normalizeLocation(level, base);
        if (validDevice(level, normalized, kind)) {
            return normalized;
        }
        if (worldFallback != null) {
            com.mcl.cbtc.device.Devices.DeviceLocation fromWorld =
                    com.mcl.cbtc.device.Devices.normalizeLocation(
                            level, new com.mcl.cbtc.device.Devices.DeviceLocation(null, worldFallback));
            if (validDevice(level, fromWorld, kind)) {
                return fromWorld;
            }
            BlockPos near = com.mcl.cbtc.device.Devices.findKindNear(level, worldFallback, kind, 2);
            if (near != null) {
                return com.mcl.cbtc.device.Devices.normalizeLocation(
                        level, new com.mcl.cbtc.device.Devices.DeviceLocation(null, near));
            }
        }
        if (normalized != null) {
            BlockPos near = com.mcl.cbtc.device.Devices.findKindNear(level, normalized.pos(), kind, 2);
            if (near != null) {
                return com.mcl.cbtc.device.Devices.normalizeLocation(
                        level, new com.mcl.cbtc.device.Devices.DeviceLocation(null, near));
            }
        }
        return normalized;
    }

    private static boolean validDevice(Level level,
                                       @Nullable com.mcl.cbtc.device.Devices.DeviceLocation loc,
                                       DeviceKind kind) {
        if (loc == null) {
            return false;
        }
        return com.mcl.cbtc.device.Devices.kindAt(level, loc.pos()) == kind;
    }

    /** 记录一个当前世界坐标，供结构被拆掉且缓存丢失时找回绑定。 */
    private static BlockPos currentWorldPos(Level level,
                                            com.mcl.cbtc.device.Devices.DeviceLocation loc,
                                            BlockPos fallback) {
        Vec3 world = com.mcl.cbtc.device.Devices.worldPos(level, loc);
        return world == null ? fallback : BlockPos.containing(world);
    }

    @Nullable
    private static Device findDevice(ItemStack stack, Level level, BlockPos pos) {
        com.mcl.cbtc.device.Devices.DeviceLocation loc = com.mcl.cbtc.device.Devices.normalizeLocation(
                level, com.mcl.cbtc.device.Devices.locate(level, pos));
        BlockPos storePos = loc == null ? pos : loc.pos();
        java.util.UUID subLevelId = loc == null ? null : loc.subLevelId();
        for (Device device : devices(stack)) {
            if (device.dimension().equals(level.dimension().location())
                    && sameDevice(level, pos, storePos, subLevelId, device.pos(), device.subLevelId())) {
                return device;
            }
        }
        return null;
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return !devices(stack).isEmpty();
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        List<Device> devices = devices(stack);
        if (devices.isEmpty()) {
            tooltip.add(Component.translatable("item.cbtc.device_module.empty").withStyle(ChatFormatting.GRAY));
        } else {
            tooltip.add(Component.translatable("item.cbtc.device_module.count", devices.size())
                    .withStyle(ChatFormatting.AQUA));
            for (Device device : devices) {
                tooltip.add(Component.literal("· " + device.name() + "（" + device.kind().displayName() + "）")
                        .withStyle(ChatFormatting.GRAY));
            }
        }
        tooltip.add(Component.translatable("item.cbtc.device_module.tooltip").withStyle(ChatFormatting.DARK_GRAY));
    }

    private static CompoundTag tag(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data == null ? new CompoundTag() : data.copyTag();
    }

    private record Binding(BlockPos pos, DeviceKind kind, String name) {
    }

    public record Device(String id, String name, DeviceKind kind, ResourceLocation dimension, BlockPos pos,
                        java.util.UUID subLevelId) {
    }
}
