package com.mcl.cbtc.item;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeType;

/**
 * 超级烈焰棒：可作为熔炉燃料，燃烧 800 秒（16000 tick）。
 */
public class SuperBlazeRodItem extends Item {
    public static final int BURN_TIME = 16000;

    public SuperBlazeRodItem(Properties properties) {
        super(properties);
    }

    @Override
    public int getBurnTime(ItemStack stack, RecipeType<?> recipeType) {
        return BURN_TIME;
    }
}
