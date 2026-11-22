package com.mcl.cbtc.compat.cbc;

import com.happysg.radar.compat.cbc.CBCMuzzleUtil;
import com.happysg.radar.compat.cbc.CannonMountContext;
import com.happysg.radar.compat.cbc.CannonUtil;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import rbasamoyai.createbigcannons.cannon_control.contraption.AbstractMountedCannonContraption;
import rbasamoyai.createbigcannons.cannon_control.contraption.PitchOrientedContraptionEntity;
import rbasamoyai.createbigcannons.munitions.config.DimensionMunitionProperties;
import rbasamoyai.createbigcannons.munitions.config.DimensionMunitionPropertiesHandler;
import rbasamoyai.createbigcannons.munitions.config.components.BallisticPropertiesComponent;

/**
 * 读取 CBC 火炮当前这一发的发射参数。
 * <p>
 * 只有同时装了 Create Big Cannons 与 Create: Radars 时才会加载本类；
 * 真正的弹道模拟与落点渲染交给客户端，服务端只负责同步参数。
 */
public final class CannonImpactPredictor {
    /**
     * 一发炮弹的发射参数：炮口位置、方向、运载工具速度与弹道属性。
     */
    public record Profile(Vec3 muzzle, Vec3 direction, Vec3 inheritedVelocity,
                          double speed, double gravity, double drag, boolean quadratic,
                          double dragDensity) {
    }

    private CannonImpactPredictor() {
    }

    /**
     * 读取一门已组装火炮当前炮弹的发射参数。
     *
     * @return 发射参数；未组装、没有装填炮弹或弹道参数缺失时返回 null
     */
    @Nullable
    public static Profile profile(ServerLevel level, BlockEntity mount) {
        CannonMountContext context = CannonMountContext.of(mount);
        if (context == null || !context.hasAssembledCannon()) {
            return null;
        }
        PitchOrientedContraptionEntity entity = context.getContraption();
        if (entity == null || !(entity.getContraption() instanceof AbstractMountedCannonContraption contraption)) {
            return null;
        }
        // 落点预测只针对大口径火炮：小口径机炮的弹道意义不大
        if (!CannonUtil.isBigCannon(contraption)) {
            return null;
        }
        CannonUtil.BigCannonShotState state = CannonUtil.resolveBigCannonShotState(contraption, level);
        if (state == null || !state.hasProjectile()) {
            return null;
        }
        BallisticPropertiesComponent ballistics = state.ballistics();
        double speed = state.speed();
        if (ballistics == null || !(speed > 0.0D)) {
            return null;
        }
        Vec3 muzzle = CBCMuzzleUtil.getCBCSpawnAnchorWorld(entity);
        Vec3 forward = CBCMuzzleUtil.getForwardWorld(entity);
        if (forward.lengthSqr() < 1.0E-8D) {
            return null;
        }
        // 与 CBC 自身一致的维度倍率
        DimensionMunitionProperties dimension = DimensionMunitionPropertiesHandler.getProperties(level);
        return new Profile(muzzle, forward.normalize(), entity.getDeltaMovement(), speed,
                ballistics.gravity() * dimension.gravityMultiplier(), ballistics.drag(),
                ballistics.isQuadraticDrag(), dimension.dragMultiplier());
    }
}
