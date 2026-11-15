package com.mcl.cbtc.compat;

import com.tacz.guns.api.item.IGun;
import euphy.upo.sentrymechanicalarm.content.SentryArmBlockEntity.SentryStatus;
import euphy.upo.sentrymechanicalarm.network.SentryShootPacket.ActionType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;

/**
 * 由 Mixin 注入到哨戒动力臂上的控制接口。
 * <p>
 * 只有装了 Create: Sentry Mechanical Arm 时，它的方块实体才会实现本接口；
 * 未安装时所有调用方都会先经过模组加载检查，不会触碰这里的实现。
 */
public interface SentryControl {
    /** 进入/退出 CB 电脑接管。接管期间动力臂不再自动索敌与自动开火。 */
    void cbtc$setControlled(boolean controlled);

    boolean cbtc$isControlled();

    /** 瞄准世界坐标中的一点（会同时把动力臂标记为已接管）。 */
    void cbtc$aimAt(Vec3 target);

    /** 按当前朝向扣动一次扳机；弹匣空时自动改为换弹。 */
    void cbtc$fireOnce();

    /** 立即换弹：从动力臂的弹药箱里补满当前武器的弹匣。 */
    void cbtc$reload();

    /** 动力臂内的备用弹药总数（与当前武器弹药类型一致，不含弹匣内已有的子弹）。 */
    int cbtc$reserveAmmo();

    /** 动力臂当前武器弹匣内的子弹数。 */
    int cbtc$magazineAmmo();

    // 原版私有/受保护方法：由 Mixin 的 @Invoker 暴露，供上面的逻辑复用
    void cbtc$aimAtJoints(float yaw, float pitch);

    void cbtc$fireGun(float yaw, float pitch);

    boolean cbtc$performInstantReload(FakePlayer player, IGun gun, ItemStack stack);

    void cbtc$sendActionPacket(ActionType type);

    void cbtc$setStatus(SentryStatus status);
}
