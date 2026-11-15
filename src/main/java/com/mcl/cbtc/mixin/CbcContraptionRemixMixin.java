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
 * 组装动态结构时，让 CBC 把智能炮座识别为炮座本体。
 * <p>
 * CBC 在 {@code ContraptionRemix.customChecks} 里判断方块是不是炮座/固定炮座/炮车，
 * 用来避免把安装座一起卷入动态结构。新方块不补进去会被当成普通方块卷走。
 */
@Pseudo
@Mixin(targets = "rbasamoyai.createbigcannons.remix.ContraptionRemix", remap = false)
public abstract class CbcContraptionRemixMixin {
    @Redirect(method = "customChecks",
            at = @At(value = "INVOKE",
                    target = "Lcom/tterrag/registrate/util/entry/BlockEntry;has(Lnet/minecraft/world/level/block/state/BlockState;)Z"),
            remap = false)
    private static boolean cbtc$treatSmartMountAsMount(BlockEntry entry, BlockState state) {
        if (entry.has(state)) {
            return true;
        }
        if (entry == CBCBlocks.CANNON_MOUNT) {
            return CbcCannons.isSmartMount(state);
        }
        if (entry == CBCBlocks.FIXED_CANNON_MOUNT) {
            return CbcCannons.isSmartFixedMount(state);
        }
        return false;
    }
}
