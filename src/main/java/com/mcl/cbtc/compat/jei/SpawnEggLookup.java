package com.mcl.cbtc.compat.jei;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SpawnEggItem;

/** 取生物对应的刷怪蛋物品，找不到时返回空栈。 */
final class SpawnEggLookup {
    private SpawnEggLookup() {
    }

    static ItemStack eggOf(EntityType<? extends LivingEntity> type) {
        SpawnEggItem egg = SpawnEggItem.byId(type);
        return egg == null ? ItemStack.EMPTY : new ItemStack(egg);
    }
}
