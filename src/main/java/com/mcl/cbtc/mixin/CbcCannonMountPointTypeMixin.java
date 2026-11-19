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
 * 让动力臂（原版动力臂与瓦列里中士）能把智能炮座当作装填目标。
 * <p>
 * CBC 的交互点类型 {@code CannonMountType#canCreatePoint} 用
 * {@code CBCBlocks.CANNON_MOUNT.has(state)} / {@code FIXED_CANNON_MOUNT.has(state)}
 * 判断方块是不是炮座；智能炮座是另外注册的方块，这里把它们补进去。
 */
@Pseudo
@Mixin(targets = "rbasamoyai.createbigcannons.index.CBCArmInteractionPointTypes$CannonMountType", remap = false)
public abstract class CbcCannonMountPointTypeMixin {
    @Redirect(method = "canCreatePoint",
            at = @At(value = "INVOKE",
                    target = "Lcom/tterrag/registrate/util/entry/BlockEntry;has(Lnet/minecraft/world/level/block/state/BlockState;)Z"),
            remap = false)
    private boolean cbtc$treatSmartMountAsMount(BlockEntry entry, BlockState state) {
        return entry.has(state)
                || (entry == CBCBlocks.CANNON_MOUNT && CbcCannons.isSmartMount(state))
                || (entry == CBCBlocks.FIXED_CANNON_MOUNT && CbcCannons.isSmartFixedMount(state));
    }
}
