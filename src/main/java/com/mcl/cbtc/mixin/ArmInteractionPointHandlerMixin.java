package com.mcl.cbtc.mixin;

import com.mcl.cbtc.block.SergeantValeryBlock;
import com.simibubi.create.content.kinetics.mechanicalArm.ArmInteractionPointHandler;
import com.tterrag.registrate.util.entry.BlockEntry;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 让 Create 的交互点选点流程识别"瓦列里中士"方块物品。
 * <p>
 * 原版 {@code ArmInteractionPointHandler.tick()} 只把机械臂物品当作选点工具；
 * 重定向后，手持瓦列里中士右键火炮底座也会出现原版选中框、左键可取消，
 * 放置方块时再沿原版流程把点写进方块实体。
 */
@Mixin(value = ArmInteractionPointHandler.class, remap = false)
public abstract class ArmInteractionPointHandlerMixin {
    @Redirect(method = "tick", at = @At(value = "INVOKE",
            target = "Lcom/tterrag/registrate/util/entry/BlockEntry;isIn(Lnet/minecraft/world/item/ItemStack;)Z"))
    private static boolean cbtc$sergeantCountsAsArm(BlockEntry<?> entry, ItemStack stack) {
        if (entry.isIn(stack)) {
            return true;
        }
        return stack.getItem() instanceof BlockItem blockItem
                && blockItem.getBlock() instanceof SergeantValeryBlock;
    }
}
