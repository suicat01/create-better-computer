package com.mcl.cbtc.compat;

import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Create: Sentry Mechanical Arm 软依赖对接层。
 * <p>
 * 只有确认装了 sentrymechanicalarm 才会加载本类，未安装时不触碰它的任何类型。
 */
public final class SentryArmCompat {
    private SentryArmCompat() {
    }

    /** 方块实体是不是哨戒动力臂。 */
    public static boolean isSentryArm(BlockEntity blockEntity) {
        return blockEntity instanceof euphy.upo.sentrymechanicalarm.content.SentryArmBlockEntity;
    }

    /** 让动力臂进入/退出 CB 电脑接管。 */
    public static void setControlled(BlockEntity blockEntity, boolean controlled) {
        if (blockEntity instanceof SentryControl control) {
            control.cbtc$setControlled(controlled);
        }
    }

    /** 让动力臂瞄准世界坐标中的一点。 */
    public static void aim(BlockEntity blockEntity, Vec3 target) {
        if (blockEntity instanceof SentryControl control) {
            control.cbtc$aimAt(target);
        }
    }

    /** 让动力臂扣动一次扳机。 */
    public static void fire(BlockEntity blockEntity) {
        if (blockEntity instanceof SentryControl control) {
            control.cbtc$fireOnce();
        }
    }

    /** 让动力臂立即换弹。 */
    public static void reload(BlockEntity blockEntity) {
        if (blockEntity instanceof SentryControl control) {
            control.cbtc$reload();
        }
    }

    /** 动力臂内的备用弹药总数。 */
    public static int reserveAmmo(BlockEntity blockEntity) {
        return blockEntity instanceof SentryControl control ? control.cbtc$reserveAmmo() : 0;
    }

    /** 动力臂武器弹匣内的子弹数。 */
    public static int magazineAmmo(BlockEntity blockEntity) {
        return blockEntity instanceof SentryControl control ? control.cbtc$magazineAmmo() : 0;
    }
}
