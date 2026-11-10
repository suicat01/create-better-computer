package com.mcl.cbtc.device;

import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;

/**
 * 唯一直接引用 Simulated（航空学家族）传感器类型的类。
 * 只有确认 mod 已加载后才会触碰这个类。
 */
final class SimulatedReader {
    private SimulatedReader() {
    }

    static double altitude(BlockEntity be) {
        if (be instanceof dev.simulated_team.simulated.content.blocks.altitude_sensor.AltitudeSensorBlockEntity sensor) {
            return sensor.getWorldHeight();
        }
        return 0;
    }

    static double velocity(BlockEntity be) {
        if (be instanceof dev.simulated_team.simulated.content.blocks.velocity_sensor.VelocitySensorBlockEntity sensor) {
            return sensor.getAdjustedVelocity();
        }
        return 0;
    }

    static double gimbal(BlockEntity be, DeviceKind.Data data) {
        if (be instanceof dev.simulated_team.simulated.content.blocks.gimbal_sensor.GimbalSensorBlockEntity sensor) {
            return switch (data) {
                case PITCH -> Math.toDegrees(sensor.getXAngle());
                case ROLL -> Math.toDegrees(sensor.getZAngle());
                default -> 0;
            };
        }
        return 0;
    }

    /**
     * 读取导航台的坐标与距离。
     * <p>
     * 坐标用的是投影到主世界后的位置：导航台装在飞行器（子世界）上时，
     * {@code getProjectedSelfPos} / {@code getTargetPosition(true)} 会换算成真实世界坐标，
     * 而不是子世界里的局部坐标。
     */
    static double navTable(BlockEntity be, String key) {
        if (!(be instanceof dev.simulated_team.simulated.content.blocks.nav_table.NavTableBlockEntity nav)) {
            return 0;
        }
        Vec3 self = nav.getProjectedSelfPos();
        Vec3 target = nav.getTargetPosition(true);
        return switch (key) {
            case "x" -> self.x;
            case "z" -> self.z;
            case "target_x" -> target == null ? 0 : target.x;
            case "target_z" -> target == null ? 0 : target.z;
            case "distance" -> target == null ? 0 : self.distanceTo(target);
            case "bearing" -> target == null ? 0 : bearing(self, target);
            default -> 0;
        };
    }

    /**
     * 目标相对导航台的方位角，正东为 0°，向北（-Z）为正，范围 -180° ~ +180°。
     */
    private static double bearing(Vec3 self, Vec3 target) {
        double east = target.x - self.x;
        double north = self.z - target.z;
        return Math.toDegrees(Math.atan2(north, east));
    }

    /**
     * 导航台投影到主世界的自身坐标；不是导航台时返回 null。
     */
    static Vec3 navTableSelf(BlockEntity be) {
        if (be instanceof dev.simulated_team.simulated.content.blocks.nav_table.NavTableBlockEntity nav) {
            return nav.getProjectedSelfPos();
        }
        return null;
    }

    /**
     * 导航台投影到主世界的目标坐标；不是导航台或未放导航物品时返回 null。
     */
    static Vec3 navTableTarget(BlockEntity be) {
        if (be instanceof dev.simulated_team.simulated.content.blocks.nav_table.NavTableBlockEntity nav) {
            return nav.getTargetPosition(true);
        }
        return null;
    }

    static boolean matches(BlockEntity be, DeviceKind kind) {
        return switch (kind) {
            case ALTITUDE_SENSOR ->
                    be instanceof dev.simulated_team.simulated.content.blocks.altitude_sensor.AltitudeSensorBlockEntity;
            case VELOCITY_SENSOR ->
                    be instanceof dev.simulated_team.simulated.content.blocks.velocity_sensor.VelocitySensorBlockEntity;
            case GIMBAL_SENSOR ->
                    be instanceof dev.simulated_team.simulated.content.blocks.gimbal_sensor.GimbalSensorBlockEntity;
            case NAV_TABLE ->
                    be instanceof dev.simulated_team.simulated.content.blocks.nav_table.NavTableBlockEntity;
            default -> false;
        };
    }
}
