package com.mcl.cbtc.device;

import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.fml.ModList;

/**
 * 软依赖闸门：把"是否装了 Simulated"和"是否需要触碰 Simulated 类型"分开。
 * <p>
 * 本类不引用任何 Simulated 类型，因此可以安全地被无条件加载；
 * 真正的类型访问放在 {@link SimulatedReader}，只有确认加载后才调用它。
 */
final class DeviceAccess {
    private static final String SIMULATED = "simulated";
    private static final String SABLE = "sable";
    private static final String SENTRY = "sentrymechanicalarm";

    private DeviceAccess() {
    }

    static boolean hasSimulated() {
        return ModList.get().isLoaded(SIMULATED);
    }

    /** sable 是航空学家族的物理库，结构图解读取需要它。 */
    static boolean hasSable() {
        return ModList.get().isLoaded(SABLE);
    }

    /** create_radar（Create: Radars）提供雷达轴承。 */
    static boolean hasRadar() {
        return ModList.get().isLoaded("create_radar");
    }

    /** sentrymechanicalarm（Create: Sentry Mechanical Arm）提供哨戒动力臂。 */
    static boolean hasSentry() {
        return ModList.get().isLoaded(SENTRY);
    }

    static double diagram(net.minecraft.world.level.Level level, net.minecraft.core.BlockPos pos,
            DeviceKind.Data data) {
        return hasSable() ? DiagramReader.read(level, pos, data) : 0;
    }

    static double altitude(BlockEntity be) {
        return hasSimulated() ? SimulatedReader.altitude(be) : 0;
    }

    static double velocity(BlockEntity be) {
        return hasSimulated() ? SimulatedReader.velocity(be) : 0;
    }

    static double gimbal(BlockEntity be, DeviceKind.Data data) {
        return hasSimulated() ? SimulatedReader.gimbal(be, data) : 0;
    }

    static double navTable(BlockEntity be, String key) {
        return hasSimulated() ? SimulatedReader.navTable(be, key) : 0;
    }

    static net.minecraft.world.phys.Vec3 navTableSelf(BlockEntity be) {
        return hasSimulated() ? SimulatedReader.navTableSelf(be) : null;
    }

    static net.minecraft.world.phys.Vec3 navTableTarget(BlockEntity be) {
        return hasSimulated() ? SimulatedReader.navTableTarget(be) : null;
    }

    /**
     * 取坐标上的方块实体，先直接取主世界，取不到再穿透 Sable 子世界。
     * 方块被物理化（组装进飞行器）后，主世界原坐标上取不到，需要回落到子世界。
     */
    static BlockEntity blockEntityAt(net.minecraft.world.level.Level level, net.minecraft.core.BlockPos pos) {
        BlockEntity be = level.getBlockEntity(pos);
        if (be != null) {
            return be;
        }
        return hasSable()
                ? SubLevelBridge.blockEntity(level, new Devices.DeviceLocation(null, pos))
                : null;
    }

    /** 方块实体是否属于该外设种类；用于绑定时的合法性校验。 */
    static boolean matches(BlockEntity be, DeviceKind kind) {
        return switch (kind) {
            case STRESS_GAUGE -> be instanceof com.simibubi.create.content.kinetics.gauge.StressGaugeBlockEntity;
            case SPEED_GAUGE -> be instanceof com.simibubi.create.content.kinetics.gauge.SpeedGaugeBlockEntity;
            case TYPEWRITER -> hasSimulated() && be instanceof com.mcl.cbtc.blockentity.CBTypewriterBlockEntity;
            case GEARBOX -> be instanceof com.mcl.cbtc.blockentity.CBGearboxBlockEntity;
            case SPEED_CONTROLLER -> be instanceof com.mcl.cbtc.blockentity.SmartSpeedControllerBlockEntity;
            case SPEAKER -> be instanceof com.mcl.cbtc.blockentity.CBSpeakerBlockEntity;
            case LECTURE_STONE -> be instanceof com.mcl.cbtc.blockentity.LectureStoneBlockEntity;
            case INFO_TRANSMITTER -> be instanceof com.mcl.cbtc.blockentity.InfoTransmitterBlockEntity;
            case DIAGRAM_SENSOR -> be instanceof com.mcl.cbtc.blockentity.DiagramSensorBlockEntity && hasSable();
            // 网络控制器来自 Create: Radars，是软依赖，确认加载后才触碰它的类型
            case NETWORK_CONTROLLER -> hasRadar() && com.mcl.cbtc.compat.RadarCompat.isNetworkController(be);
            // 瞄准组件、枪式遥控器是物品，没有方块实体
            case GUNNER_SIGHT, GUN_REMOTE -> false;
            // 火控单位：哨戒动力臂（软依赖）或本模组的智能炮座（CBC 软依赖）
            case SENTRY_ARM -> (hasSentry() && com.mcl.cbtc.compat.SentryArmCompat.isSentryArm(be))
                    || (com.mcl.cbtc.compat.cbc.CbcCannons.loaded()
                            && com.mcl.cbtc.compat.cbc.CannonMountCompat.isSmartMount(be));
            default -> hasSimulated() && SimulatedReader.matches(be, kind);
        };
    }
}
