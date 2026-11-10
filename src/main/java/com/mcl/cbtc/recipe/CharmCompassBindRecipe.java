package com.mcl.cbtc.recipe;

import com.mcl.cbtc.item.BoundCompassItem;
import com.mcl.cbtc.item.LodestoneCharmItem;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;

/**
 * 工作台绑定：指南针 + 磁石挂件 → 绑定了这块挂件的指南针。
 * <p>
 * 单向绑定：指南针记住挂件 ID（佩戴后指针指向佩戴者），挂件本身作为剩余物品留在合成格，
 * 之后仍可继续佩戴或再绑其他指南针。
 */
public class CharmCompassBindRecipe extends CustomRecipe {
    public static final CharmCompassBindRecipe INSTANCE = new CharmCompassBindRecipe();

    public CharmCompassBindRecipe() {
        super(CraftingBookCategory.MISC);
    }

    @Override
    public boolean matches(CraftingInput input, Level level) {
        ItemStack compass = ItemStack.EMPTY;
        ItemStack charm = ItemStack.EMPTY;
        for (int i = 0; i < input.size(); i++) {
            ItemStack stack = input.getItem(i);
            if (stack.isEmpty()) {
                continue;
            }
            if (stack.is(Items.COMPASS)) {
                if (!compass.isEmpty()) {
                    return false;
                }
                compass = stack;
            } else if (stack.getItem() instanceof LodestoneCharmItem) {
                if (!charm.isEmpty()) {
                    return false;
                }
                charm = stack;
            } else {
                return false;
            }
        }
        return !compass.isEmpty() && !charm.isEmpty();
    }

    @Override
    public ItemStack assemble(CraftingInput input, HolderLookup.Provider registries) {
        ItemStack charm = ItemStack.EMPTY;
        for (int i = 0; i < input.size(); i++) {
            ItemStack stack = input.getItem(i);
            if (stack.getItem() instanceof LodestoneCharmItem) {
                charm = stack;
                break;
            }
        }
        if (charm.isEmpty()) {
            return ItemStack.EMPTY;
        }
        // 直接在合成格里的挂件上补 ID，保证之后佩戴的是同一块挂件
        return BoundCompassItem.create(charm);
    }

    @Override
    public NonNullList<ItemStack> getRemainingItems(CraftingInput input) {
        NonNullList<ItemStack> remaining = NonNullList.withSize(input.size(), ItemStack.EMPTY);
        for (int i = 0; i < input.size(); i++) {
            ItemStack stack = input.getItem(i);
            if (stack.getItem() instanceof LodestoneCharmItem) {
                remaining.set(i, stack.copy());
            }
        }
        return remaining;
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return width * height >= 2;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return com.mcl.cbtc.registry.CbcRecipes.CHARM_COMPASS_BIND.get();
    }
}
