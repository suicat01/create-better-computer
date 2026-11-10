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

    /** 按当前定位取方块实体：优先缓存定位，其次绑定记录坐标。 */
    @Nullable
    public static BlockEntity blockEntityAt(Level level, String deviceId, DeviceLocation fallback) {
        DeviceLocation loc = currentLocation(level, deviceId);
        if (loc == null) {
            loc = fallback;
        }
        if (loc == null) {
            return null;
        }
        BlockEntity be = blockEntityAt(level, normalizeLocation(level, loc));
        if (be != null) {
            return be;
        }
        return blockEntityAt(level, loc);
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
        for (var e : CURRENT_LOCATIONS.entrySet()) {
            Cached cached = e.getValue();
            if (cached.location() == null) {
                continue;
            }
            DeviceLocation loc = cached.location();
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
                BlockPos actual = findKindNear(level, local, cached.kind(), 2);
                if (actual == null) {
                    com.mcl.cbtc.CreateBetterComputer.LOGGER.debug(
                            "外设绑定：结构 {} 内没找到 {}（预期 {}）",
                            added, e.getKey(), local);
                    continue;
                }
                canonical = new DeviceLocation(added, actual);
            }
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
                continue;
            }
            list.add(new PendingRemoval(e.getKey(), SubLevelBridge.toWorld(subLevel, loc.pos()),
                    cached.kind(), now));
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
        for (PendingRemoval removal : removals) {
            BlockPos actual = findKindNear(level, removal.worldPos(), removal.kind(), 2);
            if (actual == null) {
                if (now - removal.capturedAt() < 20) {
                    unresolved.add(removal);
                } else {
                    com.mcl.cbtc.CreateBetterComputer.LOGGER.warn(
                            "外设绑定：{} 拆卸后没在主世界找到对应方块（预期 {}），保留原定位",
                            removal.deviceId(), removal.worldPos());
                }
                continue;
            }
            if (!actual.equals(removal.worldPos())) {
                com.mcl.cbtc.CreateBetterComputer.LOGGER.debug(
                        "外设绑定：{} 拆卸位置从 {} 修正到 {}", removal.deviceId(),
                        removal.worldPos(), actual);
            }
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
        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    BlockPos candidate = pos.offset(dx, dy, dz);
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
            if (nav != null) {
                return nav;
            }
        }
        Vec3 resolved = resolveWorldPos(level, normalizeLocation(level, loc), anchor);
        if (resolved != null) {
            return resolved;
        }
        return be == null ? null : Vec3.atCenterOf(be.getBlockPos());
    }

    /** 一条已解析的定位对应的主世界坐标；无法解析时返回 null。 */
    @Nullable
    public static Vec3 worldPos(Level level, DeviceLocation location) {
        return resolveWorldPos(level, location, null);
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
        Vec3 selfA = DeviceAccess.navTableSelf(beA);
        Vec3 targetA = DeviceAccess.navTableTarget(beA);
        if (selfA == null || targetA == null) {
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
        BlockEntity beA = blockEntityAt(level, deviceA, fallbackA);
        if (beA == null) {
            return Double.NaN;
        }
        Vec3 selfA = DeviceAccess.navTableSelf(beA);
        Vec3 targetA = DeviceAccess.navTableTarget(beA);
        if (selfA == null || targetA == null) {
            return Double.NaN;
        }
        Vec3 selfB = projectedPos(level, deviceB, fallbackB, selfA);
        if (selfB == null) {
            return Double.NaN;
        }
        double dx = selfA.x - selfB.x;
        double dz = selfA.z - selfB.z;
        double tx = targetA.x - selfB.x;
        double tz = targetA.z - selfB.z;
        if (dx * dx + dz * dz < 1e-12 || tx * tx + tz * tz < 1e-12) {
            return Double.NaN;
        }
        double theta = Math.toDegrees(Math.atan2(dz, dx));
        double phi = Math.toDegrees(Math.atan2(tz, tx));
        double delta = com.mcl.cbtc.blockentity.CBGearboxBlockEntity.wrapAngle(theta - phi);
        BlockEntity beGear = blockEntityAt(level, gearboxDevice, fallbackGearbox);
        double current = beGear instanceof com.mcl.cbtc.blockentity.CBGearboxBlockEntity gearbox
                ? gearbox.relativeAngle() : 0;
        return com.mcl.cbtc.blockentity.CBGearboxBlockEntity.wrapAngle(current + delta);
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
