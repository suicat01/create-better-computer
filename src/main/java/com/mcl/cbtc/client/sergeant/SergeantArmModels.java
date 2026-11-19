package com.mcl.cbtc.client.sergeant;

import com.mcl.cbtc.CreateBetterComputer;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;

/**
 * 瓦列里中士使用的局部模型：与原版动力臂同名同结构，只是贴图换成美术重绘的那一套。
 * <p>
 * 必须在第一次资源重载之前创建，{@link com.mcl.cbtc.client.CbcClient} 的构造器会调用 {@link #init()}。
 */
public final class SergeantArmModels {
    public static final PartialModel BASE = PartialModel.of(
            CreateBetterComputer.asResource("block/sergeant_valery/base"));
    public static final PartialModel COG = PartialModel.of(
            CreateBetterComputer.asResource("block/sergeant_valery/cog"));
    public static final PartialModel LOWER_BODY = PartialModel.of(
            CreateBetterComputer.asResource("block/sergeant_valery/lower_body"));
    public static final PartialModel UPPER_BODY = PartialModel.of(
            CreateBetterComputer.asResource("block/sergeant_valery/upper_body"));
    public static final PartialModel CLAW_BASE = PartialModel.of(
            CreateBetterComputer.asResource("block/sergeant_valery/claw_base"));
    public static final PartialModel CLAW_BASE_GOGGLES = PartialModel.of(
            CreateBetterComputer.asResource("block/sergeant_valery/claw_base_goggles"));
    public static final PartialModel CLAW_GRIP_UPPER = PartialModel.of(
            CreateBetterComputer.asResource("block/sergeant_valery/upper_claw_grip"));
    public static final PartialModel CLAW_GRIP_LOWER = PartialModel.of(
            CreateBetterComputer.asResource("block/sergeant_valery/lower_claw_grip"));

    private SergeantArmModels() {
    }

    /** 空方法，仅用于触发类初始化。 */
    public static void init() {
    }
}
