package com.mcl.cbtc.device;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * 穿透 Sable 子世界（sublevel）定位方块实体。
 * <p>
 * Sable 把结构块存放在主世界同一 Level 的"结构存储区"（远离主世界的大坐标区），
 * 所以结构内坐标可以直接用 {@code level.getBlockEntity(localPos)} 取到，而主世界坐标取不到。
 * 一条绑定记录必须明确是哪种坐标：
 * <ul>
 *   <li>结构 UUID + 结构内坐标：物理化/移动后依然有效；</li>
 *   <li>主世界坐标：结构被组装后失效，需要换算。</li>
 * </ul>
 * 旧存档里出现过"结构内坐标 + 没有结构 UUID"的记录，这里统一按存储区归属把 UUID 补回来。
 * 只有确认 Sable 已加载后才允许触碰本类（见 {@link DeviceAccess#hasSable()}）。
 */
final class SubLevelBridge {
    private SubLevelBridge() {
    }

    @Nullable
    static dev.ryanhcode.sable.api.sublevel.SubLevelContainer container(Level level) {
        return dev.ryanhcode.sable.api.sublevel.SubLevelContainer.getContainer(level);
    }

    /** 坐标是否落在结构存储区（Sable 给结构预留的大坐标区）。 */
    static boolean inStorageBounds(Level level, BlockPos pos) {
        dev.ryanhcode.sable.api.sublevel.SubLevelContainer container = container(level);
        return container != null && container.inBounds(pos);
    }

    /**
     * 结构内坐标 → 所属结构。存储区里同一个坐标只会属于一个结构，
     * 所以这是结构内坐标最可靠的归属方式（不依赖可能已经过期的 UUID）。
     */
    @Nullable
    static dev.ryanhcode.sable.sublevel.SubLevel ownerOf(Level level, BlockPos localPos) {
        dev.ryanhcode.sable.api.sublevel.SubLevelContainer container = container(level);
        if (container == null || !container.inBounds(localPos)) {
            return null;
        }
        dev.ryanhcode.sable.sublevel.plot.LevelPlot plot = container.getPlot(new ChunkPos(localPos));
        return plot == null ? null : plot.getSubLevel();
    }

    /** 结构内坐标 → 定位结果；该坐标不属于任何结构时返回 null。 */
    @Nullable
    static Devices.DeviceLocation locateLocal(Level level, BlockPos localPos) {
        dev.ryanhcode.sable.sublevel.SubLevel owner = ownerOf(level, localPos);
        return owner == null ? null : new Devices.DeviceLocation(owner.getUniqueId(), localPos);
    }

    /** 主世界坐标 → 定位结果；坐标不在任何结构范围内时返回 null。 */
    @Nullable
    static Devices.DeviceLocation locate(Level level, BlockPos worldPos) {
        dev.ryanhcode.sable.api.sublevel.SubLevelContainer container = container(level);
        if (container == null) {
            return null;
        }
        // 先处理"传进来的其实已经是结构内坐标"的情况，避免把它当世界坐标做逆变换
        Devices.DeviceLocation local = locateLocal(level, worldPos);
        if (local != null) {
            return local;
        }
        Vec3 center = Vec3.atCenterOf(worldPos);
        // 世界包围盒是结构姿态变换后的范围，用它先筛出候选结构最直接
        for (dev.ryanhcode.sable.sublevel.SubLevel sub : container.getAllSubLevels()) {
            if (!sub.boundingBox().contains(center.x, center.y, center.z)) {
                continue;
            }
            BlockPos localPos = toLocal(sub, worldPos);
            if (level.getBlockEntity(localPos) != null) {
                return new Devices.DeviceLocation(sub.getUniqueId(), localPos);
            }
        }
        // 包围盒可能滞后一 tick（刚移动完），再用逆变换兜底
        for (dev.ryanhcode.sable.sublevel.SubLevel sub : container.getAllSubLevels()) {
            BlockPos localPos = toLocal(sub, worldPos);
            if (localPos.equals(worldPos) || !container.inBounds(localPos)) {
                continue;
            }
            if (level.getBlockEntity(localPos) != null) {
                return new Devices.DeviceLocation(sub.getUniqueId(), localPos);
            }
        }
        return null;
    }

    /**
     * 规范化一条存储定位：
     * <ol>
     *   <li>结构 UUID 还在 → 原样返回；</li>
     *   <li>结构 UUID 已失效（重新组装会换 UUID）→ 按结构内坐标重新归属；</li>
     *   <li>没有 UUID 且坐标在存储区 → 按存储区找到所属结构；</li>
     *   <li>没有 UUID 且是主世界坐标 → 如果方块已被搬进结构，换算成结构内定位。</li>
     * </ol>
     * 实在认不出时原样返回，交给上层决定是否失效。
     */
    static Devices.DeviceLocation normalize(Level level, Devices.DeviceLocation loc) {
        if (loc == null) {
            return null;
        }
        dev.ryanhcode.sable.api.sublevel.SubLevelContainer container = container(level);
        if (container == null) {
            return loc;
        }
        if (loc.subLevelId() != null) {
            if (container.getSubLevel(loc.subLevelId()) != null) {
                return loc;
            }
            Devices.DeviceLocation relocated = locate(level, loc.pos());
            return relocated != null ? relocated : loc;
        }
        Devices.DeviceLocation local = locateLocal(level, loc.pos());
        if (local != null) {
            return local;
        }
        Devices.DeviceLocation located = locate(level, loc.pos());
        return located != null ? located : loc;
    }

    /**
     * 一条定位对应的方块实体。结构内坐标直接取；主世界坐标取不到时，
     * 说明方块已经被搬进结构，按世界坐标穿透后再取。
     */
    @Nullable
    static BlockEntity blockEntity(Level level, Devices.DeviceLocation loc) {
        if (loc == null) {
            return null;
        }
        BlockEntity direct = level.getBlockEntity(loc.pos());
        if (direct != null) {
            return direct;
        }
        Devices.DeviceLocation located = locate(level, loc.pos());
        return located == null ? null : level.getBlockEntity(located.pos());
    }

    /** 主世界坐标 → 结构内存储坐标。 */
    static BlockPos toLocal(dev.ryanhcode.sable.sublevel.SubLevel sub, BlockPos worldPos) {
        Vec3 local = sub.logicalPose().transformPositionInverse(
                new Vec3(worldPos.getX() + 0.5, worldPos.getY() + 0.5, worldPos.getZ() + 0.5));
        return BlockPos.containing(local);
    }

    /** 结构内存储坐标 → 主世界坐标。 */
    static BlockPos toWorld(dev.ryanhcode.sable.sublevel.SubLevel sub, BlockPos localPos) {
        Vec3 world = sub.logicalPose().transformPosition(
                new Vec3(localPos.getX() + 0.5, localPos.getY() + 0.5, localPos.getZ() + 0.5));
        return BlockPos.containing(world);
    }

    /** 按结构 UUID 把结构内存储坐标投影到主世界坐标；结构不存在时返回 null。 */
    @Nullable
    static Vec3 projectToWorld(Level level, java.util.UUID subLevelId, BlockPos localPos) {
        dev.ryanhcode.sable.api.sublevel.SubLevelContainer container = container(level);
        if (container == null) {
            return null;
        }
        dev.ryanhcode.sable.sublevel.SubLevel sub = container.getSubLevel(subLevelId);
        return sub == null ? null : sub.logicalPose().transformPosition(
                new Vec3(localPos.getX() + 0.5, localPos.getY() + 0.5, localPos.getZ() + 0.5));
    }
}
