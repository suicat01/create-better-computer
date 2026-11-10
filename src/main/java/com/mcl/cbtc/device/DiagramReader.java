package com.mcl.cbtc.device;

import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import org.joml.Vector3d;

/**
 * 读取结构图解传感器的数据：按方块位置反查所属物理结构，再取结构质量与各项受力。
 * <p>
 * 本类刻意不引用任何 sable 类型。sable 的公开类签名里引用了它自己的 companion 子模块
 * （{@code SubLevelAccess} 等），而该子模块只存在于 sable 的自嵌套 jar 中，
 * 编译期无法直接访问，所以这里全部走运行期反射。
 * <p>
 * 力的取法与航空学「结构图解」实体一致（{@code DiagramEntity#makeDiagramDataPacket}）：
 * 每个 tick 的份量 = 该分组 {@code ForceTotal.localForce} 的累计量 / 物理子步数 × 0.05。
 * 不能直接读 localForce：物理步进后它会被原地清零，游戏线程读到的多半是 0。
 * <p>
 * 重力不在队列里，由维度重力 × 结构质量现算，方向取结构自身的局部坐标。
 * 力的大小取模长（恒为正），分量保留正负号。
 */
final class DiagramReader {
    /** 每 tick 的实际时长，与航空学换算一致。 */
    private static final double TICK_SECONDS = 0.05;

    /** 读取失败只记一次日志，避免刷屏。 */
    private static boolean DIAGNOSTIC = true;

    private DiagramReader() {
    }

    /**
     * 读取一项数据；结构不存在或数据项不受支持时返回 0。
     */
    static double read(Level level, BlockPos pos, DeviceKind.Data data) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return 0;
        }
        try {
            Object subLevel = subLevelAt(serverLevel, pos);
            if (subLevel == null) {
                return 0;
            }
            if (data == DeviceKind.Data.MASS) {
                return Math.abs(massOf(subLevel));
            }
            Vector3d force = forceOf(serverLevel, subLevel, data);
            return switch (component(data)) {
                case X -> Math.abs(force.x);
                case Y -> Math.abs(force.y);
                case Z -> Math.abs(force.z);
                case LENGTH -> force.length();
            };
        } catch (Throwable e) {
            if (DIAGNOSTIC) {
                com.mcl.cbtc.CreateBetterComputer.LOGGER.warn("结构图解读取失败", e);
                DIAGNOSTIC = false;
            }
            return 0;
        }
    }

    /** 取结构的质量。getMassTracker() 返回的 MassData 是 sable 公开 API。 */
    private static double massOf(Object subLevel) throws Throwable {
        Object tracker = invoke(subLevel, "getMassTracker");
        return tracker == null ? 0 : ((Number) invoke(tracker, "getMass")).doubleValue();
    }

    /** 反查方块所在位置属于哪个物理结构。 */
    private static Object subLevelAt(ServerLevel level, BlockPos pos) throws Throwable {
        Class<?> containerType = Class.forName("dev.ryanhcode.sable.api.sublevel.SubLevelContainer");
        Object container = invokeStatic(containerType, "getContainer",
                new Class<?>[]{Level.class}, level);
        if (container == null) {
            return null;
        }
        Object plot = invoke(container, "getPlot", new Class<?>[]{ChunkPos.class}, new ChunkPos(pos));
        return plot == null ? null : invoke(plot, "getSubLevel");
    }

    /** 数据项对应的当前受力；不支持的分组返回零向量。 */
    private static Vector3d forceOf(ServerLevel level, Object subLevel, DeviceKind.Data data)
            throws Throwable {
        String key = groupKeyOf(data);
        if (key == null) {
            return new Vector3d();
        }
        if ("gravity".equals(key)) {
            return gravityOf(level, subLevel);
        }
        return queuedOf(level, subLevel, key);
    }

    /**
     * 重力：维度重力按结构姿态转到局部坐标，再乘结构质量。
     * 空气浮力（压力梯度）已被并入重力，航空学在图解里也是这么显示的。
     */
    private static Vector3d gravityOf(ServerLevel level, Object subLevel) throws Throwable {
        Class<?> dataType = Class.forName(
                "dev.ryanhcode.sable.physics.config.dimension_physics.DimensionPhysicsData");
        Object gravity = dataType.getMethod("getGravity", Level.class).invoke(null, level);
        if (!(gravity instanceof Vector3d world)) {
            return new Vector3d();
        }
        Object pose = invoke(subLevel, "logicalPose");
        Object local = pose == null ? null
                : invoke(pose, "transformNormalInverse", new Class<?>[]{Vector3d.class},
                        new Vector3d(world));
        Vector3d direction = local instanceof Vector3d vector ? vector : new Vector3d(world);
        return direction.mul(massOf(subLevel));
    }

    /** 队列里某个分组的本 tick 份量。 */
    private static Vector3d queuedOf(ServerLevel level, Object subLevel, String keyName)
            throws Throwable {
        Class<?> forceGroupType = Class.forName("dev.ryanhcode.sable.api.physics.force.ForceGroup");
        Class<?> forceGroupsType = Class.forName("dev.ryanhcode.sable.api.physics.force.ForceGroups");
        Object registry = forceGroupsType.getField("REGISTRY").get(null);
        Object rawGroups = invoke(subLevel, "getQueuedForceGroups");
        if (!(rawGroups instanceof Map<?, ?> groups)) {
            return new Vector3d();
        }
        Vector3d total = new Vector3d();
        for (Map.Entry<?, ?> entry : groups.entrySet()) {
            if (!forceGroupType.isInstance(entry.getKey()) || entry.getValue() == null) {
                continue;
            }
            if (!keyName.equals(keyPath(registry, entry.getKey()))) {
                continue;
            }
            Object forceTotal = invoke(entry.getValue(), "getForceTotal");
            Object force = forceTotal == null ? null : invoke(forceTotal, "getLocalForce");
            if (force instanceof Vector3d vector) {
                total.add(vector);
            }
        }
        return total.div(substeps(level) * TICK_SECONDS);
    }

    /**
     * 物理子步数：队列里的力是每个子步累加一次，航空学换算时同样除这个数。
     * 取不到时按 1 处理。
     */
    private static double substeps(ServerLevel level) {
        try {
            Class<?> systemType = Class.forName(
                    "dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem");
            Object system = systemType.getMethod("get", Level.class).invoke(null, level);
            Object config = system == null ? null : invoke(system, "getConfig");
            Object value = config == null ? null : config.getClass().getField("substepsPerTick")
                    .get(config);
            return value instanceof Integer count && count > 0 ? count : 1;
        } catch (Throwable ignored) {
            return 1;
        }
    }

    /** 注册表键名，例如 {@code sable:gravity} 取 {@code gravity}。 */
    private static Object keyPath(Object registry, Object group) throws Throwable {
        Object id = invoke(registry, "getKey", new Class<?>[]{Object.class}, group);
        return id == null ? null : invoke(id, "getPath");
    }

    /** 沿类继承链找同参数类型的公有方法调用；找不到返回 null。 */
    private static Object invoke(Object target, String name, Class<?>[] types, Object... args)
            throws Throwable {
        Class<?> type = target.getClass();
        while (type != null) {
            try {
                java.lang.reflect.Method method = type.getMethod(name, types);
                method.setAccessible(true);
                return method.invoke(target, args);
            } catch (NoSuchMethodException e) {
                type = type.getSuperclass();
            }
        }
        return null;
    }

    /** 调无参方法。 */
    private static Object invoke(Object target, String name) throws Throwable {
        return invoke(target, name, new Class<?>[0]);
    }

    /** 调静态方法。 */
    private static Object invokeStatic(Class<?> type, String name, Class<?>[] types, Object... args)
            throws Throwable {
        java.lang.reflect.Method method = type.getMethod(name, types);
        return method.invoke(null, args);
    }

    /**
     * 数据项对应的受力分组键。
     * <p>
     * 用注册表键名而不是 ForceGroups 常量：后者返回 veil 的 RegistryObject，
     * 而 veil 是 sable 的运行期前置，编译期不可见。
     */
    private static String groupKeyOf(DeviceKind.Data data) {
        return switch (data) {
            case GRAVITY, GRAVITY_X, GRAVITY_Y, GRAVITY_Z -> "gravity";
            case DRAG, DRAG_X, DRAG_Y, DRAG_Z -> "drag";
            case BUOYANCY, BUOYANCY_X, BUOYANCY_Y, BUOYANCY_Z -> "levitation";
            case BALLOON_LIFT, BALLOON_LIFT_X, BALLOON_LIFT_Y, BALLOON_LIFT_Z -> "balloon_lift";
            case THRUST, THRUST_X, THRUST_Y, THRUST_Z -> "propulsion";
            case WING_LIFT, WING_LIFT_X, WING_LIFT_Y, WING_LIFT_Z -> "lift";
            case MAGNETIC, MAGNETIC_X, MAGNETIC_Y, MAGNETIC_Z -> "magnetic_force";
            default -> null;
        };
    }

    /** 数据项取哪个分量。 */
    private static Component component(DeviceKind.Data data) {
        String id = data.id();
        if (id.endsWith("_x")) {
            return Component.X;
        }
        if (id.endsWith("_y")) {
            return Component.Y;
        }
        if (id.endsWith("_z")) {
            return Component.Z;
        }
        return Component.LENGTH;
    }

    private enum Component {
        X, Y, Z, LENGTH
    }
}
