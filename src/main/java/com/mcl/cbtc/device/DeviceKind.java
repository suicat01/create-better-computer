package com.mcl.cbtc.device;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * 可被"外设绑定模块卡"绑定、并被积木读取的设备类型。
 * <p>
 * 每种外设支持若干数据项；积木里的"数据类型"下拉只列出该外设支持的那些。
 */
public enum DeviceKind {
    STRESS_GAUGE("stress_gauge", "应力表", List.of(Data.STRESS)),
    SPEED_GAUGE("speed_gauge", "转速表", List.of(Data.SPEED)),
    ALTITUDE_SENSOR("altitude_sensor", "高度传感器", List.of(Data.ALTITUDE)),
    VELOCITY_SENSOR("velocity_sensor", "速度传感器", List.of(Data.VELOCITY)),
    GIMBAL_SENSOR("gimbal_sensor", "姿态传感器", List.of(Data.PITCH, Data.YAW, Data.ROLL)),
    GEARBOX("gearbox", "CB编程齿轮箱", List.of(Data.SPEED)),
    SPEED_CONTROLLER("speed_controller", "智能转速控制器", List.of(Data.SPEED)),
    DIAGRAM_SENSOR("diagram_sensor", "CB结构图解传感器", List.of(
            Data.MASS,
            Data.GRAVITY, Data.GRAVITY_X, Data.GRAVITY_Y, Data.GRAVITY_Z,
            Data.DRAG, Data.DRAG_X, Data.DRAG_Y, Data.DRAG_Z,
            Data.BUOYANCY, Data.BUOYANCY_X, Data.BUOYANCY_Y, Data.BUOYANCY_Z,
            Data.BALLOON_LIFT, Data.BALLOON_LIFT_X, Data.BALLOON_LIFT_Y, Data.BALLOON_LIFT_Z,
            Data.THRUST, Data.THRUST_X, Data.THRUST_Y, Data.THRUST_Z,
            Data.WING_LIFT, Data.WING_LIFT_X, Data.WING_LIFT_Y, Data.WING_LIFT_Z,
            Data.MAGNETIC, Data.MAGNETIC_X, Data.MAGNETIC_Y, Data.MAGNETIC_Z)),
    TYPEWRITER("typewriter", "CB打字机", List.of()),
    SPEAKER("speaker", "CB音响", List.of()),
    LECTURE_STONE("lecture_stone", "讲解石", List.of()),
    NAV_TABLE("nav_table", "导航台", List.of()),
    TARGET_LOCATOR("target_locator", "目标定位模块", List.of()),
    INFO_TRANSMITTER("info_transmitter", "信息转发器", List.of());

    private final String id;
    private final String displayName;
    private final List<Data> supported;

    DeviceKind(String id, String displayName, List<Data> supported) {
        this.id = id;
        this.displayName = displayName;
        this.supported = supported;
    }

    public String id() {
        return id;
    }

    public String displayName() {
        return displayName;
    }

    public List<Data> supported() {
        return supported;
    }

    @Nullable
    public static DeviceKind byId(String id) {
        for (DeviceKind kind : values()) {
            if (kind.id.equals(id)) {
                return kind;
            }
        }
        return null;
    }

    // ---------------------------------------------------------------- 读取

    /**
     * 读取一次数值。外设不在线、区块没加载或类型不匹配时返回 0。
     * 这里只做派发，具体取值在 {@link DeviceReaders} 里，便于把软依赖隔离在一个类中。
     */
    public double read(Level level, BlockPos pos, Data data) {
        if (!supported.contains(data)) {
            return 0;
        }
        return DeviceReaders.read(this, level, pos, data);
    }

    /** 外设支持的数据项。 */
    public enum Data {
        STRESS("stress", "应力"),
        SPEED("speed", "转速"),
        ALTITUDE("altitude", "高度"),
        VELOCITY("velocity", "速度"),
        PITCH("pitch", "俯仰"),
        YAW("yaw", "偏航"),
        ROLL("roll", "翻滚"),
        MASS("mass", "质量"),
        GRAVITY("gravity", "重力大小"),
        GRAVITY_X("gravity_x", "重力 X"),
        GRAVITY_Y("gravity_y", "重力 Y"),
        GRAVITY_Z("gravity_z", "重力 Z"),
        DRAG("drag", "阻力大小"),
        DRAG_X("drag_x", "阻力 X"),
        DRAG_Y("drag_y", "阻力 Y"),
        DRAG_Z("drag_z", "阻力 Z"),
        BUOYANCY("buoyancy", "浮力大小"),
        BUOYANCY_X("buoyancy_x", "浮力 X"),
        BUOYANCY_Y("buoyancy_y", "浮力 Y"),
        BUOYANCY_Z("buoyancy_z", "浮力 Z"),
        BALLOON_LIFT("balloon_lift", "气球升力大小"),
        BALLOON_LIFT_X("balloon_lift_x", "气球升力 X"),
        BALLOON_LIFT_Y("balloon_lift_y", "气球升力 Y"),
        BALLOON_LIFT_Z("balloon_lift_z", "气球升力 Z"),
        THRUST("thrust", "推进力大小"),
        THRUST_X("thrust_x", "推进力 X"),
        THRUST_Y("thrust_y", "推进力 Y"),
        THRUST_Z("thrust_z", "推进力 Z"),
        WING_LIFT("wing_lift", "机翼推力大小"),
        WING_LIFT_X("wing_lift_x", "机翼推力 X"),
        WING_LIFT_Y("wing_lift_y", "机翼推力 Y"),
        WING_LIFT_Z("wing_lift_z", "机翼推力 Z"),
        MAGNETIC("magnetic", "磁力大小"),
        MAGNETIC_X("magnetic_x", "磁力 X"),
        MAGNETIC_Y("magnetic_y", "磁力 Y"),
        MAGNETIC_Z("magnetic_z", "磁力 Z");

        private final String id;
        private final String displayName;

        Data(String id, String displayName) {
            this.id = id;
            this.displayName = displayName;
        }

        public String id() {
            return id;
        }

        public String displayName() {
            return displayName;
        }

        @Nullable
        public static Data byId(String id) {
            for (Data data : values()) {
                if (data.id.equals(id)) {
                    return data;
                }
            }
            return null;
        }
    }
}
