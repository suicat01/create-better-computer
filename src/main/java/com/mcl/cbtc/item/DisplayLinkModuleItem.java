package com.mcl.cbtc.item;

import com.simibubi.create.api.behaviour.display.DisplayTarget;
import com.simibubi.create.content.redstone.displayLink.DisplayLinkBlockEntity;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
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
 * 可命名、可寻址的无线显示设备绑定卡。一张卡可保存多个独立设备。
 * <p>
 * 与外设绑定卡使用同一套定位方案：结构 UUID + 结构内坐标（显示设备在物理结构里）
 * 或主世界坐标；另外保存最近一次的世界坐标，结构被拆掉且缓存丢失时用它把绑定找回来。
 */
public class DisplayLinkModuleItem extends UpgradeModuleItem {
    private static final String DEVICES = "DisplayDevices";
    private static final String SELECTED = "SelectedDisplay";

    public DisplayLinkModuleItem(Properties properties) {
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
        if (player.isShiftKeyDown()) {
            if (!level.isClientSide) {
                unbind(stack, level, target);
                player.displayClientMessage(Component.translatable("item.cbtc.display_link_module.unbound_msg",
                        target.name()), true);
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        CompoundTag tag = tag(stack);
        if (!isSelected(tag, level, target)) {
            select(stack, level, target);
            player.displayClientMessage(Component.translatable("item.cbtc.display_link_module.selected_msg",
                    target.name()), true);
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        if (level.isClientSide) {
            Device existing = findDevice(stack, level, target.loc());
            com.mcl.cbtc.client.DisplayNameScreen.open(context.getHand(), clicked, target.name(),
                    existing == null ? "" : existing.name());
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.CONSUME;
    }

    @Nullable
    private static Binding target(Level level, BlockPos clicked) {
        // 显示链接本身也可能在物理结构里，先穿透取出方块实体
        BlockEntity be = com.mcl.cbtc.device.Devices.blockEntityAt(level,
                new com.mcl.cbtc.device.Devices.DeviceLocation(null, clicked));
        BlockPos pos = clicked;
        if (be instanceof DisplayLinkBlockEntity link) {
            pos = link.getTargetPosition();
        }
        com.mcl.cbtc.device.Devices.DeviceLocation loc =
                com.mcl.cbtc.device.Devices.normalizeLocation(level,
                        com.mcl.cbtc.device.Devices.locate(level, pos));
        if (loc == null) {
            loc = new com.mcl.cbtc.device.Devices.DeviceLocation(null, pos);
        }
        if (!isDisplay(level, loc.pos()) && !isDisplay(level, pos)) {
            return null;
        }
        String name = level.getBlockState(loc.pos()).getBlock().getName().getString();
        return new Binding(loc, name);
    }

    /** 坐标上是不是可接收文本的显示设备。 */
    private static boolean isDisplay(Level level, BlockPos pos) {
        return DisplayTarget.get(level, pos) != null;
    }

    private static boolean isSelected(CompoundTag tag, Level level, Binding target) {
        if (!tag.contains(SELECTED, Tag.TAG_COMPOUND)) {
            return false;
        }
        CompoundTag selected = tag.getCompound(SELECTED);
        if (!level.dimension().location().toString().equals(selected.getString("Dim"))) {
            return false;
        }
        BlockPos storedPos = BlockPos.of(selected.getLong("Pos"));
        if (storedPos.equals(target.loc().pos())) {
            return true;
        }
        com.mcl.cbtc.device.Devices.DeviceLocation stored =
                com.mcl.cbtc.device.Devices.normalizeLocation(level,
                        new com.mcl.cbtc.device.Devices.DeviceLocation(readSubLevel(selected), storedPos));
        return stored != null && stored.equals(target.loc());
    }

    private static void select(ItemStack stack, Level level, Binding target) {
        CompoundTag tag = tag(stack);
        CompoundTag selected = new CompoundTag();
        selected.putLong("Pos", target.loc().pos().asLong());
        writeSubLevel(selected, target.loc().subLevelId());
        selected.putString("Dim", level.dimension().location().toString());
        selected.putString("Name", target.name());
        tag.put(SELECTED, selected);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    public static void bind(ItemStack stack, Level level, BlockPos pos, String name) {
        Binding target = target(level, pos);
        if (target == null) {
            return;
        }
        BlockPos storePos = target.loc().pos();
        UUID subLevelId = target.loc().subLevelId();
        BlockPos worldPos = currentWorldPos(level, target.loc(), storePos);

        CompoundTag tag = tag(stack);
        ListTag entries = tag.getList(DEVICES, Tag.TAG_COMPOUND);
        int deviceIndex = -1;
        int nextNumber = 1;
        String dim = level.dimension().location().toString();
        for (int i = 0; i < entries.size(); i++) {
            CompoundTag entry = entries.getCompound(i);
            if (dim.equals(entry.getString("Dim")) && sameDisplay(level, entry, target.loc(), storePos)) {
                deviceIndex = i;
            }
            if (entry.getInt("Number") >= nextNumber) {
                nextNumber = entry.getInt("Number") + 1;
            }
        }
        String defaultName = "未命名的" + target.name() + String.format("%03d", nextNumber);
        CompoundTag entry = deviceIndex >= 0 ? entries.getCompound(deviceIndex) : new CompoundTag();
        if (entry.getString("Id").isBlank()) {
            entry.putString("Id", UUID.randomUUID().toString());
            entry.putInt("Number", nextNumber);
        }
        entry.putLong("Pos", storePos.asLong());
        writeSubLevel(entry, subLevelId);
        entry.putString("Dim", dim);
        entry.putLong("World", worldPos.asLong());
        entry.putString("Name", name == null || name.isBlank() ? defaultName : name.trim());
        entry.putString("Kind", target.name());
        if (deviceIndex >= 0) {
            entries.set(deviceIndex, entry);
        } else {
            entries.add(entry);
        }
        tag.put(DEVICES, entries);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    private static void writeSubLevel(CompoundTag entry, @Nullable UUID subLevelId) {
        if (subLevelId == null) {
            entry.remove("SubLevel");
        } else {
            entry.putString("SubLevel", subLevelId.toString());
        }
    }

    /** 旧记录是否指向玩家点中的同一台显示设备；结构内坐标全局唯一，坐标相等即可认定。 */
    private static boolean sameDisplay(Level level, CompoundTag entry, com.mcl.cbtc.device.Devices.DeviceLocation target,
                                       BlockPos storePos) {
        BlockPos oldPos = BlockPos.of(entry.getLong("Pos"));
        if (oldPos.equals(storePos)) {
            return true;
        }
        com.mcl.cbtc.device.Devices.DeviceLocation oldLoc =
                com.mcl.cbtc.device.Devices.normalizeLocation(level,
                        new com.mcl.cbtc.device.Devices.DeviceLocation(readSubLevel(entry), oldPos));
        if (oldLoc != null && oldLoc.equals(target)) {
            return true;
        }
        Vec3 targetWorld = com.mcl.cbtc.device.Devices.worldPos(level, target);
        Vec3 oldWorld = com.mcl.cbtc.device.Devices.resolveWorldPos(level,
                new com.mcl.cbtc.device.Devices.DeviceLocation(readSubLevel(entry), oldPos), targetWorld);
        return targetWorld != null && oldWorld != null && oldWorld.distanceTo(targetWorld) <= 2.0;
    }

    @Nullable
    private static UUID readSubLevel(CompoundTag entry) {
        if (!entry.contains("SubLevel")) {
            return null;
        }
        String raw = entry.getString("SubLevel");
        if (raw.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static void unbind(ItemStack stack, Level level, Binding target) {
        CompoundTag tag = tag(stack);
        ListTag entries = tag.getList(DEVICES, Tag.TAG_COMPOUND);
        String dim = level.dimension().location().toString();
        BlockPos storePos = target.loc().pos();
        entries.removeIf(entry -> entry instanceof CompoundTag compound
                && dim.equals(compound.getString("Dim"))
                && sameDisplay(level, compound, target.loc(), storePos));
        tag.put(DEVICES, entries);
        tag.remove(SELECTED);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    /** 读取卡内全部设备；旧版单设备数据会在首次读取时迁移。 */
    public static List<Device> devices(ItemStack stack) {
        return devices(stack, null);
    }

    /**
     * 读取卡内全部设备，并在服务端把每条记录校正成当前有效的定位（顺手写回 NBT）。
     * 纠错顺序与绑定卡一致：记录坐标 → 最近一次的世界坐标 → 坐标附近的显示设备。
     */
    public static List<Device> devices(ItemStack stack, @Nullable Level level) {
        CompoundTag tag = tag(stack);
        ListTag entries = tag.getList(DEVICES, Tag.TAG_COMPOUND);
        if (entries.isEmpty() && tag.contains("DisplayPos")) {
            CompoundTag legacy = new CompoundTag();
            legacy.putString("Id", tag.getString("DisplayId"));
            legacy.putString("Name", tag.getString("DisplayName"));
            legacy.putString("Kind", tag.getString("DisplayKind"));
            legacy.putString("Dim", tag.getString("DisplayDim"));
            legacy.putLong("Pos", tag.getLong("DisplayPos"));
            legacy.putInt("Number", 1);
            entries.add(legacy);
            tag.put(DEVICES, entries);
            for (String key : List.of("DisplayId", "DisplayName", "DisplayKind", "DisplayDim", "DisplayPos")) {
                tag.remove(key);
            }
            stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        }
        List<Device> result = new ArrayList<>();
        boolean server = level != null && !level.isClientSide;
        boolean changed = false;
        for (int i = 0; i < entries.size(); i++) {
            CompoundTag entry = entries.getCompound(i);
            ResourceLocation dim = ResourceLocation.tryParse(entry.getString("Dim"));
            String id = entry.getString("Id");
            if (dim == null || id.isBlank() || !entry.contains("Pos")) {
                continue;
            }
            BlockPos storedPos = BlockPos.of(entry.getLong("Pos"));
            UUID storedSub = readSubLevel(entry);
            BlockPos worldFallback = entry.contains("World") ? BlockPos.of(entry.getLong("World")) : null;
            com.mcl.cbtc.device.Devices.DeviceLocation canonical =
                    new com.mcl.cbtc.device.Devices.DeviceLocation(storedSub, storedPos);
            Level recordLevel = com.mcl.cbtc.device.Devices.dimensionLevel(level, dim);
            if (server && recordLevel != null) {
                com.mcl.cbtc.device.Devices.DeviceLocation resolved =
                        resolveDisplay(recordLevel, canonical, worldFallback);
                if (resolved != null) {
                    canonical = resolved;
                }
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
            result.add(new Device(id, entry.getString("Name"), entry.getString("Kind"), dim,
                    canonical.pos(), canonical.subLevelId()));
        }
        if (changed) {
            stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        }
        return result;
    }

    /** 把一条记录解析成当前有效的显示设备定位；找不到时返回最接近的猜测。 */
    @Nullable
    private static com.mcl.cbtc.device.Devices.DeviceLocation resolveDisplay(Level level,
            com.mcl.cbtc.device.Devices.DeviceLocation base, @Nullable BlockPos worldFallback) {
        com.mcl.cbtc.device.Devices.DeviceLocation normalized =
                com.mcl.cbtc.device.Devices.normalizeLocation(level, base);
        if (validDisplay(level, normalized)) {
            return normalized;
        }
        if (worldFallback != null) {
            com.mcl.cbtc.device.Devices.DeviceLocation fromWorld =
                    com.mcl.cbtc.device.Devices.normalizeLocation(
                            level, new com.mcl.cbtc.device.Devices.DeviceLocation(null, worldFallback));
            if (validDisplay(level, fromWorld)) {
                return fromWorld;
            }
            BlockPos near = findDisplayNear(level, worldFallback, 2);
            if (near != null) {
                return com.mcl.cbtc.device.Devices.normalizeLocation(
                        level, new com.mcl.cbtc.device.Devices.DeviceLocation(null, near));
            }
        }
        if (normalized != null) {
            BlockPos near = findDisplayNear(level, normalized.pos(), 2);
            if (near != null) {
                return com.mcl.cbtc.device.Devices.normalizeLocation(
                        level, new com.mcl.cbtc.device.Devices.DeviceLocation(null, near));
            }
        }
        return normalized;
    }

    private static boolean validDisplay(Level level,
                                        @Nullable com.mcl.cbtc.device.Devices.DeviceLocation loc) {
        return loc != null && isDisplay(level, loc.pos());
    }

    /** 在 pos 附近找一台显示设备，返回离 pos 最近的位置。 */
    @Nullable
    private static BlockPos findDisplayNear(Level level, BlockPos pos, int radius) {
        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    BlockPos candidate = pos.offset(dx, dy, dz);
                    if (!isDisplay(level, candidate)) {
                        continue;
                    }
                    double dist = candidate.distSqr(pos);
                    if (dist < bestDist) {
                        bestDist = dist;
                        best = candidate;
                    }
                }
            }
        }
        return best;
    }

    /** 记录一个当前世界坐标，供结构被拆掉且缓存丢失时找回绑定。 */
    private static BlockPos currentWorldPos(Level level,
                                            com.mcl.cbtc.device.Devices.DeviceLocation loc, BlockPos fallback) {
        Vec3 world = com.mcl.cbtc.device.Devices.worldPos(level, loc);
        return world == null ? fallback : BlockPos.containing(world);
    }

    @Nullable
    private static Device findDevice(ItemStack stack, Level level,
                                     com.mcl.cbtc.device.Devices.DeviceLocation loc) {
        for (Device device : devices(stack)) {
            if (!level.dimension().location().equals(device.dimension())) {
                continue;
            }
            if (device.pos().equals(loc.pos())
                    || Objects.equals(device.subLevelId(), loc.subLevelId())) {
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
            tooltip.add(Component.translatable("item.cbtc.display_link_module.empty").withStyle(ChatFormatting.GRAY));
        } else {
            tooltip.add(Component.translatable("item.cbtc.display_link_module.count", devices.size())
                    .withStyle(ChatFormatting.AQUA));
            for (Device device : devices) {
                tooltip.add(Component.literal("· " + device.name()).withStyle(ChatFormatting.GRAY));
            }
        }
        tooltip.add(Component.translatable("item.cbtc.display_link_module.tooltip").withStyle(ChatFormatting.DARK_GRAY));
    }

    private static CompoundTag tag(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data == null ? new CompoundTag() : data.copyTag();
    }

    private record Binding(com.mcl.cbtc.device.Devices.DeviceLocation loc, String name) {
    }

    public record Device(String id, String name, String kind, ResourceLocation dimension, BlockPos pos,
                        @Nullable UUID subLevelId) {
    }
}
