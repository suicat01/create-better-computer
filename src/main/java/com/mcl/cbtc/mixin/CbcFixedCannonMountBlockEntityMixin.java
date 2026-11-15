package com.mcl.cbtc.mixin;

import com.mcl.cbtc.compat.cbc.CbcCannons;
import com.tterrag.registrate.util.entry.BlockEntry;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import rbasamoyai.createbigcannons.index.CBCBlocks;

/**
 * 让 CBC 的 {@code FixedCannonMountBlockEntity} 把智能固定炮座也当成固定炮座。
 * <p>
 * CBC 在构造（初始化炮口朝向）与 assemble（组装守卫）两处检查方块类型，
 * 这里把智能固定炮座补进去。
 */
@Pseudo
@Mixin(targets = "rbasamoyai.createbigcannons.cannon_control.fixed_cannon_mount.FixedCannonMountBlockEntity", remap = false)
public abstract class CbcFixedCannonMountBlockEntityMixin {
    @Redirect(method = {"<init>", "assemble"},
            at = @At(value = "INVOKE",
                    target = "Lcom/tterrag/registrate/util/entry/BlockEntry;has(Lnet/minecraft/world/level/block/state/BlockState;)Z"),
            remap = false)
    private boolean cbtc$treatSmartMountAsMount(BlockEntry entry, BlockState state) {
        return entry.has(state) || (entry == CBCBlocks.FIXED_CANNON_MOUNT && CbcCannons.isSmartFixedMount(state));
    }
}
