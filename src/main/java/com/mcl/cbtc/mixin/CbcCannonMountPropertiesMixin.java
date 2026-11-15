package com.mcl.cbtc.mixin;

import com.mcl.cbtc.compat.cbc.CbcCannons;
import net.minecraft.world.level.block.entity.BlockEntityType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import rbasamoyai.createbigcannons.cannon_control.cannon_types.ICannonContraptionType;
import rbasamoyai.createbigcannons.cannon_control.config.CannonMountBlockPropertiesProvider;
import rbasamoyai.createbigcannons.cannon_control.config.CannonMountPropertiesHandler;
import rbasamoyai.createbigcannons.index.CBCBlockEntities;

/**
 * 让智能炮座直接复用 CBC 原版炮座的数据驱动属性（俯仰范围等）。
 * <p>
 * CBC 的 {@code cannon_mounts/block_entities/...} 数据以方块实体类型为键，新方块实体类型
 * 查不到数据会落到 0/0 的兜底属性，炮管就完全抬不起来。这里把智能炮座的类型映射回
 * CBC 对应的类型，属性、网络同步都与原版完全一致。
 */
@Pseudo
@Mixin(targets = "rbasamoyai.createbigcannons.cannon_control.config.CannonMountPropertiesHandler", remap = false)
public abstract class CbcCannonMountPropertiesMixin {
    @Inject(method = "getProperties(Lnet/minecraft/world/level/block/entity/BlockEntityType;Lrbasamoyai/createbigcannons/cannon_control/cannon_types/ICannonContraptionType;)Lrbasamoyai/createbigcannons/cannon_control/config/CannonMountBlockPropertiesProvider;",
            at = @At("HEAD"), cancellable = true, remap = false)
    private static void cbtc$useCbcProperties(BlockEntityType<?> type, ICannonContraptionType cannonType,
                                              CallbackInfoReturnable<CannonMountBlockPropertiesProvider> cir) {
        if (CbcCannons.SMART_CANNON_MOUNT_BE != null && type == CbcCannons.SMART_CANNON_MOUNT_BE.get()) {
            cir.setReturnValue(new com.mcl.cbtc.compat.cbc.SmartMountProperties(
                    CannonMountPropertiesHandler.getProperties(
                            CBCBlockEntities.CANNON_MOUNT.get(), cannonType),
                    false));
        } else if (CbcCannons.SMART_FIXED_CANNON_MOUNT_BE != null
                && type == CbcCannons.SMART_FIXED_CANNON_MOUNT_BE.get()) {
            cir.setReturnValue(new com.mcl.cbtc.compat.cbc.SmartMountProperties(
                    CannonMountPropertiesHandler.getProperties(
                            CBCBlockEntities.FIXED_CANNON_MOUNT.get(), cannonType),
                    true));
        }
    }
}
