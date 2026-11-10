package com.mcl.cbtc.recipe;

import com.mcl.cbtc.item.DeviceModuleItem;
import com.mcl.cbtc.item.TargetLocatorItem;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;

/**
 * 工作台绑定：目标定位模块 + 外设绑定模块卡 → 绑定了该模块的外设模块卡。
 * <p>
 * 定位模块本身作为"剩余物品"留在合成格里，坐标仍然只保存在模块物品上；
 * 模块卡只记下模块 ID，积木读写时通过它找到模块。
 */
public class TargetLocatorBindRecipe extends CustomRecipe {
    public static final TargetLocatorBindRecipe INSTANCE = new TargetLocatorBindRecipe();

    public TargetLocatorBindRecipe() {
        super(CraftingBookCategory.MISC);
    }

    @Override
    public boolean matches(CraftingInput input, Level level) {
        ItemStack card = ItemStack.EMPTY;
        ItemStack module = ItemStack.EMPTY;
        for (int i = 0; i < input.size(); i++) {
            ItemStack stack = input.getItem(i);
            if (stack.isEmpty()) {
                continue;
            }
            if (stack.getItem() instanceof TargetLocatorItem) {
                if (!module.isEmpty()) {
                    return false;
                }
                module = stack;
            } else if (stack.getItem() instanceof DeviceModuleItem) {
                if (!card.isEmpty()) {
                    return false;
                }
                card = stack;
            } else {
                return false;
            }
        }
        return !card.isEmpty() && !module.isEmpty();
    }

    @Override
    public ItemStack assemble(CraftingInput input, HolderLookup.Provider registries) {
        ItemStack card = ItemStack.EMPTY;
        ItemStack module = ItemStack.EMPTY;
        for (int i = 0; i < input.size(); i++) {
            ItemStack stack = input.getItem(i);
            if (stack.getItem() instanceof TargetLocatorItem && module.isEmpty()) {
                module = stack;
            } else if (stack.getItem() instanceof DeviceModuleItem && card.isEmpty()) {
                card = stack;
            }
        }
        if (card.isEmpty() || module.isEmpty()) {
            return ItemStack.EMPTY;
        }
        ItemStack result = card.copy();
        DeviceModuleItem.bindTargetLocator(result, module);
        return result;
    }

    @Override
    public NonNullList<ItemStack> getRemainingItems(CraftingInput input) {
        NonNullList<ItemStack> remaining = NonNullList.withSize(input.size(), ItemStack.EMPTY);
        for (int i = 0; i < input.size(); i++) {
            ItemStack stack = input.getItem(i);
            if (stack.getItem() instanceof TargetLocatorItem) {
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
        return com.mcl.cbtc.registry.CbcRecipes.TARGET_LOCATOR_BIND.get();
    }
}
