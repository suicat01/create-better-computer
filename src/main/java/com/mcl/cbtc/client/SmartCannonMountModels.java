package com.mcl.cbtc.client;

import com.mcl.cbtc.CreateBetterComputer;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;

/**
 * 智能炮座使用的 Flywheel 局部模型。
 * <p>
 * 局部模型必须在第一次资源重载之前创建（也就是模组构造阶段）：Flywheel 只会在
 * {@code ModelEvent.RegisterAdditional} 时把已存在的 PartialModel 收集起来烘焙，
 * 晚于这个事件创建的局部模型永远拿不到烘焙结果，渲染时只会得到紫黑格子的 missing model。
 * 因此 {@link CbcClient} 的构造器里会显式调用 {@link #init()} 触发本类初始化。
 */
public final class SmartCannonMountModels {
    /** 旋转炮架：贴图换成了新配色，因此不能用 CBC 自己的 ROTATING_MOUNT。 */
    public static final PartialModel ROTATING_MOUNT = PartialModel.of(
            CreateBetterComputer.asResource("block/smart_cannon_mount/rotating_mount"));

    private SmartCannonMountModels() {
    }

    /** 空方法，仅用于触发类初始化。 */
    public static void init() {
    }
}
