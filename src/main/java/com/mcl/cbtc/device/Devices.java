package com.mcl.cbtc.device;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * 外设读取的公开入口。积木与模块卡都通过这里访问外设，避免直接接触软依赖类型。
 * <p>
 * 绑定定位只有两种合法形态：结构 UUID + 结构内坐标（方块在物理结构里）、
 * 或主世界坐标（方块在主世界）。缓存只是加速，任何时候丢了都必须能从绑定记录本身重新解析。
 */
public final class Devices {
    private Devices() {
    }

    /** 注册物理结构组装/拆卸监听（Sable 已加载时由主类调用一次）。 */
    public static void registerSubLevelBindingTracker() {
        SubLevelBindingTracker.register();
    }

    /** 把坐标上的方块实体识别成外设；不是支持的外设返回 null。 */
    @Nullable
    public static DeviceKind kindAt(Level level, BlockPos pos) {
        if (!level.isLoaded(pos)) {
            return null;
        }
        BlockEntity be = DeviceAccess.blockEntityAt(level, pos);
        return be == null ? null : DeviceReaders.kindOf(be);
    }

    /** 取坐标上的方块实体；方块被物理化（组装进飞行器）后自动穿透子世界查找。 */
    @Nullable
    public static BlockEntity blockEntityAt(Level level, BlockPos pos) {
        if (!level.isLoaded(pos)) {
            return null;
        }
        return DeviceAccess.blockEntityAt(level, pos);
    }

    /** 定位结果：subLevelId 为物理结构 UUID（null 表示主世界），pos 为结构内存储坐标或主世界坐标。 */
    public record DeviceLocation(java.util.UUID subLevelId, BlockPos pos) {
    }

    /**
     * 结构移除时捕获的定位：拆卸那一刻方块可能还没放回主世界、或换算后落在相邻格，
     * 所以先记下预期坐标，下一 tick 再到主世界核对真实方块。
     */
    public record PendingRemoval(String deviceId, BlockPos worldPos, DeviceKind kind, long capturedAt) {
    }

    /** 缓存条目：定位 + 外设类型 + 所属维度（维度用于避免跨维度串用缓存）。 */
    private record Cached(DeviceLocation location, @Nullable DeviceKind kind,
                          @Nullable ResourceLocation dimension) {
    }

    /** 服务端定位缓存：deviceId → 当前定位。丢失不影响正确性，只影响性能。 */
    private static final java.util.Map<String, Cached> CURRENT_LOCATIONS =
            new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * 各外设最近一次解析出的主世界坐标。
     * <p>
     * 结构拆卸时方块会被放回主世界，靠结构姿态换算出的坐标在姿态尚未更新时会偏几格；
     * 而"上次还在这个坐标"是直接证据，用它当地找回的起点，比姿态可靠得多。
     */
    private static final java.util.Map<String, Vec3> LAST_WORLD =
            new java.util.concurrent.ConcurrentHashMap<>();

    /** 记下一次成功解析的主世界坐标。 */
    private static Vec3 rememberWorld(String deviceId, Vec3 world) {
        if (deviceId != null && !deviceId.isBlank() && world != null) {
            LAST_WORLD.put(deviceId, world);
        }
        return world;
    }

    /** 该外设最近一次已知的主世界坐标；没有记录时返回 null。 */
    @Nullable
    public static Vec3 lastWorld(String deviceId) {
        return deviceId == null ? null : LAST_WORLD.get(deviceId);
    }

    /** 身份索引条目：方块自己登记的当前位置。 */
    private record IdentityEntry(ResourceLocation dimension, BlockPos pos) {
    }

    /**
     * 方块外设的持久身份索引：UUID → 方块自己登记的位置（只记服务端）。
     * <p>
     * 方块实体每次加载都会登记一次，所以结构被搬动、重新组装后这里总是最新的；
     * 索引丢失（服务器重启、区块没加载）时退回绑定记录里的坐标，靠坐标把区块加载出来后
     * 读方块自己的 UUID 再补回索引，绑定本身不需要玩家重做。
     */
    private static final java.util.Map<java.util.UUID, IdentityEntry> IDENTITY_INDEX =
            new java.util.concurrent.ConcurrentHashMap<>();

    /** 绑定记录 ID → 方块外设身份 UUID。 */
    private static final java.util.Map<String, java.util.UUID> BINDING_IDS =
            new java.util.concurrent.ConcurrentHashMap<>();

    /** 方块实体加载时登记自己的身份（由各方块实体的 onLoad 调用）。 */
    public static void registerIdentity(Level level, BlockEntity be) {
        if (level == null || level.isClientSide || !(be instanceof DeviceIdentity identity)) {
            return;
        }
        java.util.UUID id = identity.deviceId();
        if (id == null) {
            return;
        }
        IDENTITY_INDEX.put(id, new IdentityEntry(level.dimension().location(), be.getBlockPos().immutable()));
        DeviceLocation loc = locationOf(level, be);
        if (loc == null) {
            return;
        }
        if (loc.subLevelId() == null && looksLikeSubLevelCoords(level, loc.pos())) {
            // 物理结构容器还没就绪，暂时认不出结构内坐标；索引已经记好，缓存交给组装回调修正
            return;
        }
        // 已经绑定过这台外设的卡立刻跟上，不必等下一次读取纠错
        for (var e : BINDING_IDS.entrySet()) {
            if (!id.equals(e.getValue())) {
                continue;
            }
            Cached old = CURRENT_LOCATIONS.get(e.getKey());
            CURRENT_LOCATIONS.put(e.getKey(), new Cached(loc,
                    old != null && old.kind() != null ? old.kind() : DeviceReaders.kindOf(be),
                    level.dimension().location()));
        }
    }

    /** 方块实体被移除时注销身份；位置已经被新方块登记过时不动索引。 */
    public static void forgetIdentity(Level level, BlockEntity be) {
        if (level == null || level.isClientSide || !(be instanceof DeviceIdentity identity)) {
            return;
        }
        java.util.UUID id = identity.deviceId();
        if (id == null) {
            return;
        }
        IdentityEntry entry = IDENTITY_INDEX.get(id);
        if (entry != null && entry.dimension() != null
                && entry.dimension().equals(level.dimension().location())
                && entry.pos().equals(be.getBlockPos())) {
            IDENTITY_INDEX.remove(id, entry);
        }
    }

    /** 按身份取方块实体；索引里没有、维度不符或位置已被别的方块占用时返回 null。 */
    @Nullable
    public static BlockEntity blockEntityByIdentity(Level level, @Nullable java.util.UUID id) {
        if (level == null || id == null) {
            return null;
        }
        IdentityEntry entry = IDENTITY_INDEX.get(id);
        if (entry == null || entry.dimension() == null
                || !entry.dimension().equals(level.dimension().location())) {
            return null;
        }
        if (!level.isLoaded(entry.pos())) {
            return null;
        }
        BlockEntity be = level.getBlockEntity(entry.pos());
        if (be instanceof DeviceIdentity identity && id.equals(identity.deviceId())) {
            return be;
        }
        if (be != null) {
            // 那个位置已经是别的方块了，索引过期
            IDENTITY_INDEX.remove(id, entry);
        }
        return null;
    }

    /** 绑定记录对应的方块外设当前定位；索引里没有时返回 null。 */
    @Nullable
    public static DeviceLocation identityLocation(Level level, @Nullable java.util.UUID id) {
        return locationOf(level, blockEntityByIdentity(level, id));
    }

    /** 方块实体当前的绑定定位（在结构里时带结构 UUID）。 */
    @Nullable
    public static DeviceLocation locationOf(Level level, @Nullable BlockEntity be) {
        if (level == null || be == null) {
            return null;
        }
        DeviceLocation loc = locate(level, be.getBlockPos());
        return loc != null ? loc : new DeviceLocation(null, be.getBlockPos().immutable());
    }

    /** 坐标上方块实体的身份；不是本模组外设或旧存档还没写入身份时返回 null。 */
    @Nullable
    public static java.util.UUID identityAt(Level level, BlockPos pos) {
        if (level == null || pos == null || !level.isLoaded(pos)) {
            return null;
        }
        BlockEntity be = DeviceAccess.blockEntityAt(level, pos);
        return be instanceof DeviceIdentity identity ? identity.deviceId() : null;
    }

    /** 取坐标上方块实体的身份，没有就生成并登记；不是本模组外设时返回 null。 */
    @Nullable
    public static java.util.UUID ensureIdentity(Level level, BlockPos pos) {
        if (level == null || pos == null || !level.isLoaded(pos)) {
            return null;
        }
        BlockEntity be = DeviceAccess.blockEntityAt(level, pos);
        if (!(be instanceof DeviceIdentity identity)) {
            return null;
        }
        java.util.UUID id = identity.ensureDeviceId();
        registerIdentity(level, be);
        return id;
    }

    /** 绑定记录对应的方块外设身份；旧存档或外部模组方块返回 null。 */
    @Nullable
    public static java.util.UUID identityOf(String deviceId) {
        return deviceId == null ? null : BINDING_IDS.get(deviceId);
    }

    /** 把绑定记录挂到一台方块外设的身份上。 */
    public static void bindIdentity(String deviceId, @Nullable java.util.UUID id) {
        if (deviceId == null || deviceId.isBlank()) {
            return;
        }
        if (id == null) {
            BINDING_IDS.remove(deviceId);
        } else {
            BINDING_IDS.put(deviceId, id);
        }
    }

    /** 解绑时断开身份关联。 */
    public static void unbindIdentity(String deviceId) {
        if (deviceId != null) {
            BINDING_IDS.remove(deviceId);
        }
    }

    /** 连线求角失败告警的节流表：key → 上次输出时的游戏刻。 */
    private static final java.util.Map<String, Long> BEARING_DIAG_COOLDOWN =
            new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * 定位主世界坐标：如果传入的坐标其实已经在结构存储区里（旧存档或穿透交互会这样），
     * 直接按存储区归属成"结构 UUID + 结构内坐标"，绝不能把它当世界坐标记下来。
     */
    @Nullable
    public static DeviceLocation locate(Level level, BlockPos pos) {
        if (DeviceAccess.hasSable()) {
            DeviceLocation local = SubLevelBridge.locateLocal(level, pos);
            if (local != null) {
                return local;
            }
        }
        BlockEntity be = level.getBlockEntity(pos);
        if (be != null) {
            return new DeviceLocation(null, pos);
        }
        return DeviceAccess.hasSable() ? SubLevelBridge.locate(level, pos) : null;
    }

    /** 规范化一条绑定记录：补全缺失或失效的结构 UUID；不依赖缓存。 */
    public static DeviceLocation normalizeLocation(Level level, DeviceLocation location) {
        if (location == null) {
            return null;
        }
        return DeviceAccess.hasSable() ? SubLevelBridge.normalize(level, location) : location;
    }

    /** 绑定记录所属维度对应的服务端 Level；跨维度绑定要在那个维度里解析坐标。 */
    @Nullable
    public static Level dimensionLevel(Level level, ResourceLocation dim) {
        if (level == null || dim == null) {
            return null;
        }
        if (dim.equals(level.dimension().location())) {
            return level;
        }
        if (level instanceof net.minecraft.server.level.ServerLevel serverLevel) {
            return serverLevel.getServer().getLevel(net.minecraft.resources.ResourceKey.create(
                    net.minecraft.core.registries.Registries.DIMENSION, dim));
        }
        return null;
    }

    /** 注册/刷新一台外设的当前定位（绑定时调用）。 */
    public static void registerLocation(String deviceId, DeviceLocation location, DeviceKind kind) {
        registerLocation(null, deviceId, location, kind);
    }

    /** 注册/刷新一台外设的当前定位，并记下它所属的维度。 */
    public static void registerLocation(Level level, String deviceId, DeviceLocation location, DeviceKind kind) {
        if (deviceId == null || deviceId.isBlank()) {
            return;
        }
        CURRENT_LOCATIONS.put(deviceId, new Cached(location, kind,
                level == null ? null : level.dimension().location()));
    }

    /** 移除一台外设的定位缓存（解绑时调用）。 */
    public static void removeLocation(String deviceId) {
        if (deviceId != null) {
            CURRENT_LOCATIONS.remove(deviceId);
            LAST_WORLD.remove(deviceId);
        }
    }

    /** 取一台外设的当前定位；缓存没有时返回 null（回落到绑定记录里的坐标）。 */
    @Nullable
    public static DeviceLocation currentLocation(String deviceId) {
        Cached cached = deviceId == null ? null : CURRENT_LOCATIONS.get(deviceId);
        return cached == null ? null : cached.location();
    }

    /** 取一台外设在该维度下的当前定位；缓存来自其它维度时视为没有。 */
    @Nullable
    public static DeviceLocation currentLocation(Level level, String deviceId) {
        Cached cached = deviceId == null ? null : CURRENT_LOCATIONS.get(deviceId);
        if (cached == null) {
            return null;
        }
        if (cached.dimension() != null && level != null
                && !cached.dimension().equals(level.dimension().location())) {
            return null;
        }
        return cached.location();
    }

    /** 取一台外设当前定位的坐标；缓存没有时用绑定记录里的坐标兜底。 */
    public static BlockPos currentPos(String deviceId, BlockPos fallback) {
        DeviceLocation loc = currentLocation(deviceId);
        return loc == null ? fallback : loc.pos();
    }

    /** 按定位取方块实体；结构内坐标与主世界坐标都能解析。 */
    @Nullable
    public static BlockEntity blockEntityAt(Level level, DeviceLocation location) {
        if (location == null) {
            return null;
        }
        if (DeviceAccess.hasSable()) {
            BlockEntity be = SubLevelBridge.blockEntity(level, location);
            if (be != null) {
                return be;
            }
        }
        return level.getBlockEntity(location.pos());
    }

    /** 按身份命中方块后，把缓存与最近世界坐标一起校正到它此刻的位置。 */
    private static void refreshFromIdentity(Level level, String deviceId, BlockEntity be) {
        DeviceLocation loc = locationOf(level, be);
        if (loc == null) {
            return;
        }
        Cached old = CURRENT_LOCATIONS.get(deviceId);
        CURRENT_LOCATIONS.put(deviceId, new Cached(loc,
                old != null && old.kind() != null ? old.kind() : DeviceReaders.kindOf(be),
                level.dimension().location()));
        Vec3 world = worldPos(level, loc);
        if (world != null && !looksLikeSubLevelCoords(level, BlockPos.containing(world))) {
            LAST_WORLD.put(deviceId, world);
        }
    }

    /** 按当前定位取方块实体：优先身份，其次缓存定位，最后绑定记录坐标。 */
    @Nullable
    public static BlockEntity blockEntityAt(Level level, String deviceId, DeviceLocation fallback) {
        // 方块自己带身份时直接问索引：结构搬过、坐标过期都不影响
        BlockEntity identified = blockEntityByIdentity(level, identityOf(deviceId));
        if (identified != null) {
            refreshFromIdentity(level, deviceId, identified);
            return identified;
        }
        DeviceLocation loc = currentLocation(level, deviceId);
        if (loc == null) {
            loc = fallback;
        }
        if (loc == null) {
            return null;
        }
        java.util.UUID expected = identityOf(deviceId);
        BlockEntity be = blockEntityAt(level, normalizeLocation(level, loc));
        if (be != null) {
            return acceptedByIdentity(level, deviceId, expected, be);
        }
        be = blockEntityAt(level, loc);
        if (be != null) {
            return acceptedByIdentity(level, deviceId, expected, be);
        }
        // 最后兜底：原地取不到方块时按类型就近找回一块并刷新缓存。
        // 结构化位置不能直接当主世界坐标搜（那是别的结构的地盘），
        // 这种记录改用"上次解析到的世界坐标"作为搜索起点。
        Cached cached = CURRENT_LOCATIONS.get(deviceId);
        if (cached == null || cached.kind() == null) {
            return null;
        }
        BlockPos searchFrom;
        int radius;
        if (loc.subLevelId() == null && !looksLikeSubLevelCoords(level, loc.pos())) {
            searchFrom = loc.pos();
            radius = 4;
        } else {
            Vec3 known = LAST_WORLD.get(deviceId);
            if (known == null) {
                return null;
            }
            searchFrom = BlockPos.containing(known);
            radius = 16;
        }
        java.util.Set<BlockPos> taken = new java.util.HashSet<>();
        for (var e : CURRENT_LOCATIONS.entrySet()) {
            if (!e.getKey().equals(deviceId) && e.getValue().location() != null) {
                taken.add(e.getValue().location().pos());
            }
        }
        BlockPos near = findKindNear(level, searchFrom, cached.kind(), radius, taken);
        if (near == null) {
            return null;
        }
        BlockEntity found = level.getBlockEntity(near);
        if (!sameIdentity(expected, found)) {
            // 附近这块不是绑定的那台外设，绝不把缓存改到它身上
            return null;
        }
        CURRENT_LOCATIONS.put(deviceId, new Cached(new DeviceLocation(null, near), cached.kind(), cached.dimension()));
        LAST_WORLD.put(deviceId, Vec3.atCenterOf(near));
        com.mcl.cbtc.CreateBetterComputer.LOGGER.debug("外设绑定：{} 读取时就近找回为 {}", deviceId, near);
        return found;
    }

    /** 方块实体与绑定记录里的身份是否一致；记录没有身份（外部模组方块）时不做校验。 */
    private static boolean sameIdentity(@Nullable java.util.UUID expected, @Nullable BlockEntity be) {
        if (expected == null) {
            return be != null;
        }
        return be instanceof DeviceIdentity identity && expected.equals(identity.deviceId());
    }

    /** 坐标解析出的方块要再过一遍身份校验；命中时顺手把缓存校正到它此刻的位置。 */
    @Nullable
    private static BlockEntity acceptedByIdentity(Level level, String deviceId,
                                                  @Nullable java.util.UUID expected, BlockEntity be) {
        if (!sameIdentity(expected, be)) {
            return null;
        }
        if (expected != null) {
            refreshFromIdentity(level, deviceId, be);
        }
        return be;
    }

    /**
     * 物理结构组装：刷新缓存里与本结构相关的定位。
     * <ul>
     *   <li>结构内坐标：按存储区重新归属（重新组装可能换了 UUID）；</li>
     *   <li>主世界坐标：换算成结构内坐标，并按类型核对一次。</li>
     * </ul>
     */
    static void onSubLevelAssembled(Level level, dev.ryanhcode.sable.sublevel.SubLevel subLevel) {
        java.util.UUID added = subLevel.getUniqueId();
        // 结构里已有一批外设，换算时不能把两台外设指到同一块上
        java.util.Set<BlockPos> used = new java.util.HashSet<>();
        for (Cached cached : CURRENT_LOCATIONS.values()) {
            if (cached.location() != null) {
                used.add(cached.location().pos());
            }
        }
        for (var e : CURRENT_LOCATIONS.entrySet()) {
            Cached cached = e.getValue();
            if (cached.location() == null) {
                continue;
            }
            DeviceLocation loc = cached.location();
            // 方块自己带身份时直接问索引，不必按坐标或类型去猜
            DeviceLocation owned = identityLocation(level, identityOf(e.getKey()));
            if (owned != null && added.equals(owned.subLevelId())) {
                used.add(owned.pos());
                if (!owned.equals(loc)) {
                    e.setValue(new Cached(owned, cached.kind(), cached.dimension()));
                    com.mcl.cbtc.CreateBetterComputer.LOGGER.debug("外设绑定：{} 按身份定位到结构内 {}",
                            e.getKey(), owned);
                }
                continue;
            }
            java.util.Set<BlockPos> occupiedByOthers = new java.util.HashSet<>(used);
            occupiedByOthers.remove(loc.pos());
            DeviceLocation canonical;
            if (SubLevelBridge.inStorageBounds(level, loc.pos())) {
                canonical = SubLevelBridge.locateLocal(level, loc.pos());
                if (canonical == null) {
                    continue;
                }
                if (loc.subLevelId() != null && loc.subLevelId().equals(canonical.subLevelId())) {
                    continue;
                }
                if (level.getBlockEntity(canonical.pos()) == null) {
                    continue;
                }
                if (occupiedByOthers.contains(canonical.pos())) {
                    continue;
                }
            } else {
                if (loc.subLevelId() != null) {
                    // 属于别的结构，不动
                    continue;
                }
                if (cached.kind() == null) {
                    // 不知道类型就无法安全核对，交给读取时的纠错流程处理
                    continue;
                }
                BlockPos local = SubLevelBridge.toLocal(subLevel, loc.pos());
                if (!SubLevelBridge.inStorageBounds(level, local)) {
                    continue;
                }
                BlockPos actual = findKindNear(level, local, cached.kind(), 2, occupiedByOthers);
                if (actual != null) {
                    // 存储区里相邻地址属于别的物理结构，就近找回必须确认是同一条结构，
                    // 否则一次"修复"就会把绑定指到别人家的方块上
                    DeviceLocation owner = SubLevelBridge.locateLocal(level, actual);
                    if (owner == null || !added.equals(owner.subLevelId())) {
                        actual = null;
                    }
                }
                if (actual == null) {
                    com.mcl.cbtc.CreateBetterComputer.LOGGER.debug(
                            "外设绑定：结构 {} 内没找到 {}（预期 {}）",
                            added, e.getKey(), local);
                    continue;
                }
                canonical = new DeviceLocation(added, actual);
            }
            used.add(canonical.pos());
            DeviceKind found = DeviceReaders.kindOf(level.getBlockEntity(canonical.pos()));
            e.setValue(new Cached(canonical, found != null ? found : cached.kind(), cached.dimension()));
            com.mcl.cbtc.CreateBetterComputer.LOGGER.debug("外设绑定：{} 结构内定位刷新为 {}",
                    e.getKey(), canonical);
        }
    }

    /**
     * 物理结构拆卸：先记下预期的主世界坐标（此刻方块可能还没放回去），
     * 由 {@link #applyRemovals} 在后续 tick 核对真实方块位置后再写回缓存。
     */
    static List<PendingRemoval> captureRemovals(Level level, dev.ryanhcode.sable.sublevel.SubLevel subLevel) {
        List<PendingRemoval> list = new ArrayList<>();
        java.util.UUID uniqueId = subLevel.getUniqueId();
        long now = level.getGameTime();
        for (var e : CURRENT_LOCATIONS.entrySet()) {
            Cached cached = e.getValue();
            if (cached.location() == null) {
                continue;
            }
            DeviceLocation loc = cached.location();
            boolean belongs = uniqueId.equals(loc.subLevelId());
            if (!belongs && loc.subLevelId() == null) {
                // 旧存档：只有结构内坐标、没有 UUID，靠存储区归属判断是否属于这个结构
                belongs = SubLevelBridge.locateLocal(level, loc.pos()) != null
                        && uniqueId.equals(SubLevelBridge.locateLocal(level, loc.pos()).subLevelId());
            }
            if (!belongs) {
                // 重新组装会换结构 UUID：用方块自己的身份确认它是不是在这个结构里
                DeviceLocation owned = identityLocation(level, identityOf(e.getKey()));
                belongs = owned != null && uniqueId.equals(owned.subLevelId());
            }
            if (!belongs) {
                continue;
            }
            // 优先用"上次解析到的世界坐标"：拆卸事件触发时结构姿态可能还没更新，
            // 姿态换算会偏几格，而方块实际就在它最后待过的那个位置附近。
            Vec3 known = LAST_WORLD.get(e.getKey());
            BlockPos expected = known != null
                    ? BlockPos.containing(known)
                    : SubLevelBridge.toWorld(subLevel, loc.pos());
            list.add(new PendingRemoval(e.getKey(), expected, cached.kind(), now));
        }
        return list;
    }

    /**
     * 拆卸后的核对：按类型在预期坐标附近找真实方块。
     * 找不到时不覆盖缓存（绝不把绑定写到一个猜出来的坐标上），返回给调用方继续重试。
     */
    static List<PendingRemoval> applyRemovals(Level level, List<PendingRemoval> removals) {
        List<PendingRemoval> unresolved = new ArrayList<>();
        long now = level.getGameTime();
        // 同一批拆卸里先找到的外设占住方块，后面的记录不会重复落到同一块上
        java.util.Set<BlockPos> claimed = new java.util.HashSet<>();
        for (PendingRemoval removal : removals) {
            long waited = now - removal.capturedAt();
            // 方块自己知道它是谁：身份索引里记的就是它拆卸后待的位置，不需要按类型猜
            java.util.UUID identity = identityOf(removal.deviceId());
            if (identity != null) {
                DeviceLocation owned = identityLocation(level, identity);
                if (owned != null && !looksLikeSubLevelCoords(level, owned.pos())) {
                    if (!owned.pos().equals(removal.worldPos())) {
                        com.mcl.cbtc.CreateBetterComputer.LOGGER.debug(
                                "外设绑定：{} 按身份定位到拆卸后位置 {}", removal.deviceId(), owned.pos());
                    }
                    claimed.add(owned.pos());
                    LAST_WORLD.put(removal.deviceId(), Vec3.atCenterOf(owned.pos()));
                    Cached old = CURRENT_LOCATIONS.get(removal.deviceId());
                    CURRENT_LOCATIONS.put(removal.deviceId(), new Cached(new DeviceLocation(null, owned.pos()),
                            removal.kind() != null ? removal.kind() : (old == null ? null : old.kind()),
                            level.dimension().location()));
                    continue;
                }
                if (waited < 200) {
                    // 方块还没被放回主世界，继续等它出现
                    unresolved.add(removal);
                    continue;
                }
            }
            // 方块放回主世界要几秒，且可能整块挪开一段（飞行器落地位置和记录点有差距），
            // 所以边等边扩大搜索半径，而不是等不到就放弃。
            int radius = waited < 20 ? 2 : waited < 60 ? 4 : waited < 140 ? 8 : 16;
            BlockPos actual = findKindNear(level, removal.worldPos(), removal.kind(), radius, claimed);
            if (actual == null) {
                if (waited < 200) {
                    unresolved.add(removal);
                    continue;
                }
                // 已经等够还没找到：至少把绑定改写成主世界坐标。
                // 继续保留结构存储区坐标会让后续每次读取都解析失败，
                // 连线方位角算出 NaN，齿轮箱就停在那里不动了。
                claimed.add(removal.worldPos());
                Cached stale = CURRENT_LOCATIONS.get(removal.deviceId());
                CURRENT_LOCATIONS.put(removal.deviceId(), new Cached(new DeviceLocation(null, removal.worldPos()),
                        stale == null ? null : stale.kind(), stale == null ? null : stale.dimension()));
                com.mcl.cbtc.CreateBetterComputer.LOGGER.warn(
                        "外设绑定：{} 拆卸后没在主世界找到对应方块（预期 {}），先按该坐标记录，读取时再就近找回",
                        removal.deviceId(), removal.worldPos());
                continue;
            }
            if (!actual.equals(removal.worldPos())) {
                com.mcl.cbtc.CreateBetterComputer.LOGGER.debug(
                        "外设绑定：{} 拆卸位置从 {} 修正到 {}", removal.deviceId(),
                        removal.worldPos(), actual);
            }
            claimed.add(actual);
            LAST_WORLD.put(removal.deviceId(), Vec3.atCenterOf(actual));
            Cached old = CURRENT_LOCATIONS.get(removal.deviceId());
            CURRENT_LOCATIONS.put(removal.deviceId(), new Cached(new DeviceLocation(null, actual),
                    removal.kind() != null ? removal.kind() : (old == null ? null : old.kind()),
                    old == null ? null : old.dimension()));
        }
        return unresolved;
    }

    /**
     * 在 pos 及其周围（半径 radius）找指定类型的外设，返回离 pos 最近的位置。
     * kind 为 null 时接受任意外设；找不到返回 null。
     */
    @Nullable
    public static BlockPos findKindNear(Level level, BlockPos pos, DeviceKind kind, int radius) {
        return findKindNear(level, pos, kind, radius, java.util.Set.of());
    }

    /**
     * 同 {@link #findKindNear(Level, BlockPos, DeviceKind, int)}，但跳过已经被别的绑定占用的位置。
     * <p>
     * 贴近摆放的同类外设（例如两台挨着的 CB音响）在按坐标就近找回时会一起塌到同一块上，
     * 两个绑定重合后，连线类的角度积木只能算出 NaN。这里用占用集合保证一台外设只归一条记录。
     */
    @Nullable
    public static BlockPos findKindNear(Level level, BlockPos pos, DeviceKind kind, int radius,
                                        java.util.Set<BlockPos> exclude) {
        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    BlockPos candidate = pos.offset(dx, dy, dz);
                    if (exclude.contains(candidate)) {
                        continue;
                    }
                    BlockEntity be = level.getBlockEntity(candidate);
                    if (be == null) {
                        continue;
                    }
                    DeviceKind found = DeviceReaders.kindOf(be);
                    if (found == null || (kind != null && found != kind)) {
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

    /** 是否装了哨戒动力臂模组。 */
    public static boolean hasSentry() {
        return DeviceAccess.hasSentry();
    }

    /** 是否有任何火控单位可用：哨戒动力臂或 CBC 智能炮座。 */
    public static boolean hasFireControl() {
        return DeviceAccess.hasSentry() || com.mcl.cbtc.compat.cbc.CbcCannons.loaded();
    }

    /**
     * 让哨戒动力臂进入/退出 CB 电脑接管。
     * <p>
     * 绑定到外设卡即接管：动力臂停止自动索敌，瞄准与射击只听从积木；
     * 解绑后恢复原版自动攻击。坐标上的方块不是哨戒动力臂时什么也不做。
     */
    public static void setSentryControlled(Level level, BlockPos pos, boolean controlled) {
        if (level == null || pos == null || !DeviceAccess.hasSentry() || !level.isLoaded(pos)) {
            return;
        }
        BlockEntity be = DeviceAccess.blockEntityAt(level, pos);
        if (be != null) {
            com.mcl.cbtc.compat.SentryArmCompat.setControlled(be, controlled);
        }
    }

    /** 读取外设数值；外设不在线、类型不匹配或数据项不受支持时返回 0。 */
    public static double read(Level level, BlockPos pos, DeviceKind kind, DeviceKind.Data data) {
        if (kind == null || data == null) {
            return 0;
        }
        return kind.read(level, pos, data);
    }

    /** 打字机当前按下的 GLFW 键码；其它方块或未安装 Simulated 时返回空列表。 */
    public static List<Integer> pressedKeys(Level level, BlockPos pos) {
        if (!level.isLoaded(pos)) {
            return List.of();
        }
        BlockEntity be = DeviceAccess.blockEntityAt(level, pos);
        if (be == null) {
            return List.of();
        }
        return new ArrayList<>(DeviceReaders.pressedKeys(be));
    }

    /**
     * 读取导航台的坐标或距离。key 取 x / z / target_x / target_z / distance。
     * 坐标是投影到主世界后的真实坐标（装在飞行器上时按飞行器位置换算）。
     */
    public static double navTable(Level level, BlockPos pos, String key) {
        if (!level.isLoaded(pos)) {
            return 0;
        }
        BlockEntity be = DeviceAccess.blockEntityAt(level, pos);
        return be == null ? 0 : DeviceAccess.navTable(be, key);
    }

    /** 读取信息转发器第 line 行（从 1 起）的内容；方块不在线或行号越界时返回空文本。 */
    public static String infoLine(Level level, BlockPos pos, int line) {
        if (level == null || pos == null || !level.isLoaded(pos)) {
            return "";
        }
        BlockEntity be = DeviceAccess.blockEntityAt(level, pos);
        return be instanceof com.mcl.cbtc.blockentity.InfoTransmitterBlockEntity transmitter
                ? transmitter.lineText(line)
                : "";
    }

    /** 外设投影到主世界的坐标（Vec3）；导航台用自身投影，其它外设物理化时按结构姿态投影。 */
    @Nullable
    public static Vec3 projectedPos(Level level, String deviceId, DeviceLocation fallback) {
        return projectedPos(level, deviceId, fallback, null);
    }

    /**
     * 把一个可能位于结构存储区的坐标投影成主世界坐标。
     * <p>
     * 方块物理化后，它的 {@code getBlockPos()} 仍是结构存储区里的地址，
     * 客户端拿这个地址画字/粒子只会落在没人看的存储区。默念、粒子这类
     * 必须出现在真实世界位置的效果都要先过这一步。
     */
    public static BlockPos projectPos(Level level, BlockPos pos) {
        if (!DeviceAccess.hasSable()) {
            return pos;
        }
        DeviceLocation local = SubLevelBridge.locateLocal(level, pos);
        if (local == null || local.subLevelId() == null) {
            return pos;
        }
        Vec3 world = SubLevelBridge.projectToWorld(level, local.subLevelId(), local.pos());
        return world == null ? pos : BlockPos.containing(world);
    }

    /**
     * 同上，额外传入锚点（通常是与 B 同一物理结构里的导航台世界坐标）。
     * 旧存档里的绑定记录可能只有结构内坐标、没有结构 UUID，这时靠存储区归属或锚点找回正确的子世界。
     */
    @Nullable
    public static Vec3 projectedPos(Level level, String deviceId, DeviceLocation fallback,
                                    @Nullable Vec3 anchor) {
        DeviceLocation loc = currentLocation(level, deviceId);
        if (loc == null) {
            loc = fallback;
        }
        BlockEntity be = blockEntityAt(level, deviceId, fallback);
        if (be != null) {
            Vec3 nav = DeviceAccess.navTableSelf(be);
            // 投影还没算好时导航台返回的是结构存储区坐标，这种值绝不能当世界坐标用
            if (nav != null && !looksLikeSubLevelCoords(level, BlockPos.containing(nav))) {
                return rememberWorld(deviceId, nav);
            }
        }
        Vec3 resolved = resolveWorldPos(level, normalizeLocation(level, loc), anchor);
        if (resolved != null) {
            return rememberWorld(deviceId, resolved);
        }
        if (be == null) {
            return null;
        }
        // 方块还在结构存储区里时 getBlockPos() 是局部坐标，直接返回会把角度算歪
        return looksLikeSubLevelCoords(level, be.getBlockPos())
                ? null : rememberWorld(deviceId, Vec3.atCenterOf(be.getBlockPos()));
    }

    /** 一条已解析的定位对应的主世界坐标；无法解析时返回 null。 */
    @Nullable
    public static Vec3 worldPos(Level level, DeviceLocation location) {
        return resolveWorldPos(level, location, null);
    }

    /**
     * 把主世界坐标换算成该坐标所在物理结构的局部坐标系。
     * <p>
     * 物理化后的方块实体仍以结构存储区坐标为准，瞄准这类方块时目标点必须一并换算，
     * 方向和角度才有意义。坐标不在任何结构里时原样返回。
     */
    public static Vec3 toLocalSpace(Level level, BlockPos pos, Vec3 worldPoint) {
        if (!DeviceAccess.hasSable() || level == null || worldPoint == null) {
            return worldPoint;
        }
        DeviceLocation local = SubLevelBridge.locateLocal(level, pos);
        if (local == null || local.subLevelId() == null) {
            return worldPoint;
        }
        return SubLevelBridge.toLocalSpace(level, local.subLevelId(), worldPoint);
    }

    /**
     * 把一条存储定位解析成主世界坐标：
     * 有结构 UUID 就直接投影；UUID 缺失（旧存档只写了结构内坐标）时先按存储区归属，
     * 再按锚点挑最近的子世界；坐标本来就是主世界坐标时直接返回方块中心。
     */
    @Nullable
    public static Vec3 resolveWorldPos(Level level, DeviceLocation location, @Nullable Vec3 anchor) {
        if (location == null) {
            return null;
        }
        DeviceLocation normalized = normalizeLocation(level, location);
        if (normalized == null) {
            return null;
        }
        if (normalized.subLevelId() != null && DeviceAccess.hasSable()) {
            Vec3 projected = SubLevelBridge.projectToWorld(level, normalized.subLevelId(), normalized.pos());
            if (projected != null) {
                return projected;
            }
        }
        if (looksLikeSubLevelCoords(level, normalized.pos())) {
            return anchor == null ? null : recoverProjection(level, normalized.pos(), anchor);
        }
        return Vec3.atCenterOf(normalized.pos());
    }

    /** 结构内坐标的特征：Sable 把结构块映射到远离主世界的超大坐标区。 */
    private static boolean looksLikeSubLevelCoords(Level level, BlockPos pos) {
        if (!DeviceAccess.hasSable()) {
            // 没有 Sable 时超大坐标就是真实世界坐标，不能当结构内坐标处理
            return false;
        }
        return SubLevelBridge.inStorageBounds(level, pos)
                || Math.abs((long) pos.getX()) > 1_000_000L || Math.abs((long) pos.getZ()) > 1_000_000L;
    }

    /**
     * 该坐标是否位于结构存储区。
     * <p>
     * 存储区坐标绝不能在周围"按类型就近找回"：相邻地址属于别的物理结构，
     * 找回来的可能是别人的方块，一次找回就把两条绑定绑到同一块上。
     */
    public static boolean isStoragePos(Level level, BlockPos pos) {
        return looksLikeSubLevelCoords(level, pos);
    }

    /**
     * 缺少结构 UUID 时的兜底：遍历所有子世界投影这个结构内坐标，取离锚点最近的一个。
     * 同一结构里的外设不可能离锚点太远，所以用距离上限把其它结构排除掉。
     */
    @Nullable
    private static Vec3 recoverProjection(Level level, BlockPos localPos, Vec3 anchor) {
        if (!DeviceAccess.hasSable()) {
            return null;
        }
        dev.ryanhcode.sable.api.sublevel.SubLevelContainer container =
                dev.ryanhcode.sable.api.sublevel.SubLevelContainer.getContainer(level);
        if (container == null) {
            return null;
        }
        Vec3 center = new Vec3(localPos.getX() + 0.5, localPos.getY() + 0.5, localPos.getZ() + 0.5);
        Vec3 best = null;
        double bestDist = 64.0;
        for (dev.ryanhcode.sable.sublevel.SubLevel sub : container.getAllSubLevels()) {
            Vec3 world = sub.logicalPose().transformPosition(center);
            double dist = world.distanceTo(anchor);
            if (dist < bestDist) {
                bestDist = dist;
                best = world;
            }
        }
        return best;
    }

    /** 临时诊断：基线方位角的中间量，每个设备对每 5 秒最多打印一次。 */
    private static final java.util.Map<String, Long> BASELINE_DIAG_TICKS =
            new java.util.concurrent.ConcurrentHashMap<>();

    /** 实测结论：齿轮箱账面角增大时，平台在世界里的方位角是否同向增大。 */
    private static final java.util.Map<String, Boolean> SPIN_FOLLOWS_BOOK =
            new java.util.concurrent.ConcurrentHashMap<>();

    /** 上一次采样：{账面角, 方位角}，用于实测账面角与平台转向的关系。 */
    private static final java.util.Map<String, double[]> SPIN_LAST_SAMPLE =
            new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * 实测齿轮箱账面角与平台方位角的关系。
     * <p>
     * 账面角增大时平台可能同向转，也可能因齿轮组反向装配而反向转。写死符号总有一半装置是反的，
     * 反了就成了正反馈：每转一次偏差翻倍，平台绕着整圈乱打转、永远不收敛。
     * 这里拿上一次采样的实际转动量判断方向；判断不出来时沿用上次结论，初始按同向处理。
     */
    private static boolean spinFollowsBook(String key, double book, double theta) {
        boolean same = SPIN_FOLLOWS_BOOK.getOrDefault(key, Boolean.TRUE);
        double[] last = SPIN_LAST_SAMPLE.get(key);
        if (last != null) {
            double db = com.mcl.cbtc.blockentity.CBGearboxBlockEntity.wrapAngle(book - last[0]);
            double dt = com.mcl.cbtc.blockentity.CBGearboxBlockEntity.wrapAngle(theta - last[1]);
            // 齿轮箱确实转过一段、平台也确实动过，才足以判断方向
            if (Math.abs(db) > 15 && Math.abs(dt) > 5) {
                same = db * dt > 0;
                SPIN_FOLLOWS_BOOK.put(key, same);
            }
        }
        SPIN_LAST_SAMPLE.put(key, new double[]{book, theta});
        return same;
    }

    /**
     * 基线方位角：以 A（前，导航台）的目标点为"目标"，结构正前方 = 由 B 指向 A，
     * 返回目标相对正前方的夹角。正对目标 = 0°，目标在左侧 = +90°，右侧 = -90°，范围 -180° ~ +180°。
     */
    public static double baselineBearing(Level level, String deviceA, DeviceLocation fallbackA,
                                         String deviceB, DeviceLocation fallbackB) {
        String diagKey = (deviceA == null ? "" : deviceA) + "|" + (deviceB == null ? "" : deviceB);
        long now = level.getGameTime();
        boolean diag = now - BASELINE_DIAG_TICKS.getOrDefault(diagKey, -1000L) >= 100;
        if (diag) {
            BASELINE_DIAG_TICKS.put(diagKey, now);
        }
        BlockEntity beA = blockEntityAt(level, deviceA, fallbackA);
        if (beA == null) {
            diagBearing(diag, diagKey, "A 的方块实体没找到 fallbackA=" + fallbackA
                    + " cacheA=" + currentLocation(deviceA));
            return Double.NaN;
        }
        // 走 projectedPos 而不是直取导航台自身坐标：后者在投影就绪前会返回存储区坐标
        Vec3 selfA = projectedPos(level, deviceA, fallbackA);
        Vec3 targetA = DeviceAccess.navTableTarget(beA);
        if (selfA == null || targetA == null
                || looksLikeSubLevelCoords(level, BlockPos.containing(targetA))) {
            diagBearing(diag, diagKey, "A 不是导航台或没有目标 self=" + selfA + " target=" + targetA);
            return Double.NaN;
        }
        // 以 A 的世界坐标作锚点：B 的记录缺结构 UUID 时靠它找回正确的子世界
        Vec3 selfB = projectedPos(level, deviceB, fallbackB, selfA);
        if (selfB == null) {
            diagBearing(diag, diagKey, "B 坐标解析失败 fallbackB=" + fallbackB
                    + " cacheB=" + currentLocation(deviceB) + " selfA=" + selfA + " targetA=" + targetA);
            return Double.NaN;
        }
        // 前方轴由 B 指向 A（A 是前端，B 在后），不能反过来，否则一切结果都差 180°
        double fx = selfA.x - selfB.x;
        double fz = selfA.z - selfB.z;
        double len = Math.sqrt(fx * fx + fz * fz);
        if (len < 1e-6) {
            diagBearing(diag, diagKey, "A 与 B 坐标重合 selfA=" + selfA + " selfB=" + selfB);
            return Double.NaN;
        }
        fx /= len;
        fz /= len;
        double tx = targetA.x - selfA.x;
        double tz = targetA.z - selfA.z;
        double cross = tx * fz - tz * fx;
        double dot = tx * fx + tz * fz;
        double result = Math.toDegrees(Math.atan2(cross, dot));
        String navInfo = "";
        if (beA instanceof dev.simulated_team.simulated.content.blocks.nav_table.NavTableBlockEntity nav) {
            navInfo = " item=" + nav.getHeldItem().getItem().getDescriptionId()
                    + " rawTarget=" + nav.getTargetPosition(false)
                    + " relativeAngle=" + nav.getRelativeAngle()
                    + " distance=" + nav.distanceToTarget();
        }
        diagBearing(diag, diagKey, String.format(java.util.Locale.ROOT,
                "selfA=%s targetA=%s selfB=%s cross=%.4f dot=%.4f -> %.3f°%s",
                selfA, targetA, selfB, cross, dot, result, navInfo));
        return result;
    }

    /**
     * 绝对目标角：把指定齿轮箱转到这个角度，导航台就正对目标。
     * <p>
     * 平台绕 B 的方位角 theta 由 A、B 的位置算出，目标的方位角 phi 由目标点相对 B 算出，
     * 还要转过的增量就是 theta - phi（本装置实测：齿轮箱正转 = theta 减小）。
     * 再叠加齿轮箱当前相对角，得到"旋转至"可以直接用的绝对值，零点不需要标定。
     * 转到之后下一轮读到的仍是同一个值，「旋转至」判到位后自然不再转。
     *
     * @return 相对齿轮箱零点的绝对角（-180~180）；取不到数据时返回 NaN
     */
    public static double baselineAbsoluteTarget(Level level, String deviceA, DeviceLocation fallbackA,
                                                String deviceB, DeviceLocation fallbackB,
                                                String gearboxDevice, DeviceLocation fallbackGearbox) {
        String diagKey = "abs|" + (deviceA == null ? "" : deviceA) + "|" + (deviceB == null ? "" : deviceB);
        long now = level.getGameTime();
        boolean diag = now - BASELINE_DIAG_TICKS.getOrDefault(diagKey, -1000L) >= 100;
        if (diag) {
            BASELINE_DIAG_TICKS.put(diagKey, now);
        }
        BlockEntity beA = blockEntityAt(level, deviceA, fallbackA);
        if (beA == null) {
            diagBearing(diag, diagKey, "A 的方块实体没找到 fallbackA=" + fallbackA
                    + " cacheA=" + currentLocation(deviceA));
            return Double.NaN;
        }
        // 走 projectedPos 而不是直取导航台自身坐标：后者在投影就绪前会返回存储区坐标
        Vec3 selfA = projectedPos(level, deviceA, fallbackA);
        Vec3 targetA = DeviceAccess.navTableTarget(beA);
        if (selfA == null || targetA == null
                || looksLikeSubLevelCoords(level, BlockPos.containing(targetA))) {
            diagBearing(diag, diagKey, "A 不是导航台或没有目标 self=" + selfA + " target=" + targetA);
            return Double.NaN;
        }
        Vec3 selfB = projectedPos(level, deviceB, fallbackB, selfA);
        if (selfB == null) {
            diagBearing(diag, diagKey, "B 坐标解析失败 fallbackB=" + fallbackB
                    + " cacheB=" + currentLocation(deviceB));
            return Double.NaN;
        }
        double dx = selfA.x - selfB.x;
        double dz = selfA.z - selfB.z;
        double tx = targetA.x - selfB.x;
        double tz = targetA.z - selfB.z;
        if (dx * dx + dz * dz < 1e-12 || tx * tx + tz * tz < 1e-12) {
            diagBearing(diag, diagKey, "两点重合 selfA=" + selfA + " targetA=" + targetA + " selfB=" + selfB);
            return Double.NaN;
        }
        double theta = Math.toDegrees(Math.atan2(dz, dx));
        double phi = Math.toDegrees(Math.atan2(tz, tx));
        double delta = com.mcl.cbtc.blockentity.CBGearboxBlockEntity.wrapAngle(theta - phi);
        BlockEntity beGear = blockEntityAt(level, gearboxDevice, fallbackGearbox);
        double current = beGear instanceof com.mcl.cbtc.blockentity.CBGearboxBlockEntity gearbox
                ? gearbox.relativeAngle() : 0;
        boolean sameSpin = spinFollowsBook(diagKey, current, theta);
        double target = com.mcl.cbtc.blockentity.CBGearboxBlockEntity.wrapAngle(
                current + (sameSpin ? -delta : delta));
        diagBearing(diag, diagKey, String.format(java.util.Locale.ROOT,
                "selfA=%s targetA=%s selfB=%s theta=%.3f phi=%.3f delta=%.3f same=%s current=%.3f -> %.3f",
                selfA, targetA, selfB, theta, phi, delta, sameSpin, current, target));
        return target;
    }

    /**
     * 某个方向相对 A→B 连线前方轴的偏差角：0° = 与连线同向，符号与「导航台连线」一致。
     * <p>
     * 只用到方向、不需要目标点，所以前后两个外设都只要坐标，不要求是导航台。
     *
     * @return 偏差角（-180~180）；外设缺失或两点重合成一条线时返回 NaN
     */
    public static double directionBearing(Level level, Vec3 direction,
                                          String deviceA, DeviceLocation fallbackA,
                                          String deviceB, DeviceLocation fallbackB) {
        Vec3 selfA = projectedPos(level, deviceA, fallbackA);
        if (selfA == null) {
            debugBearingOnce(level, "a:" + deviceA, "连线方位角：" + deviceA + " 没有解析到坐标");
            return Double.NaN;
        }
        Vec3 selfB = projectedPos(level, deviceB, fallbackB, selfA);
        if (selfB == null) {
            debugBearingOnce(level, "b:" + deviceB, "连线方位角：" + deviceB + " 没有解析到坐标");
            return Double.NaN;
        }
        double fx = selfA.x - selfB.x;
        double fz = selfA.z - selfB.z;
        double fl = Math.sqrt(fx * fx + fz * fz);
        double tl = Math.sqrt(direction.x * direction.x + direction.z * direction.z);
        if (fl < 1E-6 || tl < 1E-6) {
            debugBearingOnce(level, "same:" + deviceA + ":" + deviceB,
                    "连线方位角：" + deviceA + " 与 " + deviceB + " 的坐标重合（" + selfA + "），无法确定连线方向");
            return Double.NaN;
        }
        fx /= fl;
        fz /= fl;
        double tx = direction.x / tl;
        double tz = direction.z / tl;
        return Math.toDegrees(Math.atan2(tx * fz - tz * fx, tx * fx + tz * fz));
    }

    /** 对同一条告警做节流，避免积木每刻执行时刷屏。 */
    private static void debugBearingOnce(Level level, String key, String message) {
        long now = level.getGameTime();
        Long last = BEARING_DIAG_COOLDOWN.get(key);
        if (last != null && now - last < 100) {
            return;
        }
        BEARING_DIAG_COOLDOWN.put(key, now);
        com.mcl.cbtc.CreateBetterComputer.LOGGER.debug(message);
    }

    /**
     * 把某个方向换算成指定齿轮箱的绝对目标角，与 {@link #baselineAbsoluteTarget} 同一套公式，
     * 只是把"目标点相对 B 的方位"换成方向本身的方位。
     *
     * @return 相对齿轮箱零点的绝对角（-180~180）；取不到数据时返回 NaN
     */
    public static double directionAbsoluteTarget(Level level, Vec3 direction,
                                                 String deviceA, DeviceLocation fallbackA,
                                                 String deviceB, DeviceLocation fallbackB,
                                                 String gearboxDevice, DeviceLocation fallbackGearbox) {
        String diagKey = "dabs|" + (deviceA == null ? "" : deviceA) + "|" + (deviceB == null ? "" : deviceB);
        long now = level.getGameTime();
        boolean diag = now - BASELINE_DIAG_TICKS.getOrDefault(diagKey, -1000L) >= 100;
        if (diag) {
            BASELINE_DIAG_TICKS.put(diagKey, now);
        }
        Vec3 selfA = projectedPos(level, deviceA, fallbackA);
        if (selfA == null) {
            diagBearing(diag, diagKey, "A 坐标解析失败 fallbackA=" + fallbackA);
            return Double.NaN;
        }
        Vec3 selfB = projectedPos(level, deviceB, fallbackB, selfA);
        if (selfB == null) {
            diagBearing(diag, diagKey, "B 坐标解析失败 fallbackB=" + fallbackB + " selfA=" + selfA);
            return Double.NaN;
        }
        double dx = selfA.x - selfB.x;
        double dz = selfA.z - selfB.z;
        if (dx * dx + dz * dz < 1e-6 || direction.x * direction.x + direction.z * direction.z < 1e-12) {
            diagBearing(diag, diagKey, "两点重合 selfA=" + selfA + " selfB=" + selfB);
            return Double.NaN;
        }
        double theta = Math.toDegrees(Math.atan2(dz, dx));
        double phi = Math.toDegrees(Math.atan2(direction.z, direction.x));
        double delta = com.mcl.cbtc.blockentity.CBGearboxBlockEntity.wrapAngle(theta - phi);
        BlockEntity beGear = blockEntityAt(level, gearboxDevice, fallbackGearbox);
        double current = beGear instanceof com.mcl.cbtc.blockentity.CBGearboxBlockEntity gearbox
                ? gearbox.relativeAngle() : 0;
        boolean sameSpin = spinFollowsBook(diagKey, current, theta);
        double target = com.mcl.cbtc.blockentity.CBGearboxBlockEntity.wrapAngle(
                current + (sameSpin ? -delta : delta));
        diagBearing(diag, diagKey, String.format(java.util.Locale.ROOT,
                "selfA=%s selfB=%s dir=(%.3f,%.3f) theta=%.3f phi=%.3f delta=%.3f same=%s current=%.3f -> %.3f",
                selfA, selfB, direction.x, direction.z, theta, phi, delta, sameSpin, current, target));
        return target;
    }

    /** 打印一条基线方位角诊断；enabled 为 false 时直接跳过。 */
    private static void diagBearing(boolean enabled, String key, String message) {
        if (enabled) {
            com.mcl.cbtc.CreateBetterComputer.LOGGER.info("连线方位角 dbg [{}] {}", key, message);
        }
    }

    /** 向齿轮箱下发一次转速指令；坐标上不是编程齿轮箱时什么也不做。 */
    public static void controlGearbox(Level level, BlockPos pos, float speed, String mode, double value) {
        if (!level.isLoaded(pos)) {
            return;
        }
        if (DeviceAccess.blockEntityAt(level, pos) instanceof com.mcl.cbtc.blockentity.CBGearboxBlockEntity gearbox) {
            gearbox.applyCommand(speed, mode, value);
        }
    }

    /** 点动续期；坐标上不是编程齿轮箱时什么也不做。 */
    public static void keepJogging(Level level, BlockPos pos) {
        if (!level.isLoaded(pos)) {
            return;
        }
        if (DeviceAccess.blockEntityAt(level, pos) instanceof com.mcl.cbtc.blockentity.CBGearboxBlockEntity gearbox) {
            gearbox.keepJogging();
        }
    }

    /** 立即停掉齿轮箱的旋转；坐标上不是编程齿轮箱时什么也不做。 */
    public static void stopGearbox(Level level, BlockPos pos) {
        if (!level.isLoaded(pos)) {
            return;
        }
        if (DeviceAccess.blockEntityAt(level, pos) instanceof com.mcl.cbtc.blockentity.CBGearboxBlockEntity gearbox) {
            gearbox.stopRotation();
        }
    }

    /** 把齿轮箱当前朝向记作 0°；坐标上不是编程齿轮箱时什么也不做。 */
    public static void zeroGearboxAngle(Level level, BlockPos pos) {
        if (!level.isLoaded(pos)) {
            return;
        }
        if (DeviceAccess.blockEntityAt(level, pos) instanceof com.mcl.cbtc.blockentity.CBGearboxBlockEntity gearbox) {
            gearbox.setAngleZero();
        }
    }

    /** 齿轮箱相对零点的当前角度（-180~180）；坐标上不是编程齿轮箱时返回 0。 */
    public static double gearboxAngle(Level level, BlockPos pos) {
        if (!level.isLoaded(pos)) {
            return 0;
        }
        return DeviceAccess.blockEntityAt(level, pos) instanceof com.mcl.cbtc.blockentity.CBGearboxBlockEntity gearbox
                ? gearbox.relativeAngle()
                : 0;
    }

    /** 设置智能转速控制器的目标转速（RPM，正负表示方向）；坐标上不是该方块时什么也不做。 */
    public static void setSpeedController(Level level, BlockPos pos, float rpm) {
        if (!level.isLoaded(pos)) {
            return;
        }
        if (DeviceAccess.blockEntityAt(level, pos)
                instanceof com.mcl.cbtc.blockentity.SmartSpeedControllerBlockEntity controller) {
            controller.applySpeedSetting(rpm);
        }
    }

    /** 智能转速控制器当前的输出转速（RPM，带符号）；坐标上不是该方块时返回 0。 */
    public static double speedControllerSpeed(Level level, BlockPos pos) {
        if (!level.isLoaded(pos)) {
            return 0;
        }
        return DeviceAccess.blockEntityAt(level, pos)
                instanceof com.mcl.cbtc.blockentity.SmartSpeedControllerBlockEntity controller
                ? controller.outputSpeed()
                : 0;
    }
}
