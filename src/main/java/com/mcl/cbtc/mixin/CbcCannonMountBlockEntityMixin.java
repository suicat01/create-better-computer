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
 * 让 CBC 的 {@code CannonMountBlockEntity} 把智能炮座也当成普通炮座。
 * <p>
 * CBC 在构造（初始化炮口朝向）、tick（未组装时复位朝向）与 assemble（组装守卫）
 * 三处用 {@code CBCBlocks.CANNON_MOUNT.has(state)} 判断方块类型，这里把智能炮座补进去，
 * 其余逻辑保持 CBC 原样。
 */
@Pseudo
@Mixin(targets = "rbasamoyai.createbigcannons.cannon_control.cannon_mount.CannonMountBlockEntity", remap = false)
public abstract class CbcCannonMountBlockEntityMixin {
    @Redirect(method = {"<init>", "tick", "assemble"},
            at = @At(value = "INVOKE",
                    target = "Lcom/tterrag/registrate/util/entry/BlockEntry;has(Lnet/minecraft/world/level/block/state/BlockState;)Z"),
            remap = false)
    private boolean cbtc$treatSmartMountAsMount(BlockEntry entry, BlockState state) {
        return entry.has(state) || (entry == CBCBlocks.CANNON_MOUNT && CbcCannons.isSmartMount(state));
    }
}
