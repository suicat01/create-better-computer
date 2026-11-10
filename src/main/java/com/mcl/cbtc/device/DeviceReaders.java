package com.mcl.cbtc.device;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * 按外设种类派发读取。机械动力的表直接用 Create 类型（硬依赖），
 * 航空学家族的传感器走 {@link DeviceAccess} 的软依赖闸门。
 */
final class DeviceReaders {
    private DeviceReaders() {
    }

    static double read(DeviceKind kind, Level level, BlockPos pos, DeviceKind.Data data) {
        if (!level.isLoaded(pos)) {
            return 0;
        }
        BlockEntity be = DeviceAccess.blockEntityAt(level, pos);
        if (be == null) {
            return 0;
        }
        return switch (kind) {
            case STRESS_GAUGE -> be instanceof com.simibubi.create.content.kinetics.gauge.StressGaugeBlockEntity gauge
                    ? gauge.getNetworkStress()
                    : 0;
            case SPEED_GAUGE -> be instanceof com.simibubi.create.content.kinetics.gauge.SpeedGaugeBlockEntity gauge
                    ? gauge.getSpeed()
                    : 0;
            case ALTITUDE_SENSOR -> DeviceAccess.altitude(be);
            case VELOCITY_SENSOR -> DeviceAccess.velocity(be);
            case GIMBAL_SENSOR -> DeviceAccess.gimbal(be, data);
            case GEARBOX -> be instanceof com.mcl.cbtc.blockentity.CBGearboxBlockEntity gearbox
                    ? gearbox.getSpeed()
                    : 0;
            case SPEED_CONTROLLER -> be instanceof com.mcl.cbtc.blockentity.SmartSpeedControllerBlockEntity controller
                    ? controller.outputSpeed()
                    : 0;
            case TYPEWRITER -> 0;
            case SPEAKER -> 0;
            case LECTURE_STONE -> 0;
            case NAV_TABLE -> 0;
            case TARGET_LOCATOR -> 0;
            case INFO_TRANSMITTER -> 0;
            case DIAGRAM_SENSOR -> DeviceAccess.diagram(level, pos, data);
        };
    }

    /** 绑定/查询时把方块实体识别成外设种类；不认识返回 null。 */
    static DeviceKind kindOf(BlockEntity be) {
        for (DeviceKind kind : DeviceKind.values()) {
            if (DeviceAccess.matches(be, kind)) {
                return kind;
            }
        }
        return null;
    }

    /** CB 打字机当前按下的 GLFW 键码。 */
    static java.util.List<Integer> pressedKeys(BlockEntity be) {
        return be instanceof com.mcl.cbtc.blockentity.CBTypewriterBlockEntity typewriter
                ? typewriter.getPressedKeys()
                : java.util.List.of();
    }
}
