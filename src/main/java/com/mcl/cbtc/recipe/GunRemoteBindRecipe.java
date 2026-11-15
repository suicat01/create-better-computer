package com.mcl.cbtc.recipe;

import com.mcl.cbtc.item.DeviceModuleItem;
import com.mcl.cbtc.item.GunRemoteItem;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;

/**
 * 工作台绑定：枪式遥控器 + 外设绑定模块卡 → 绑定了该遥控器的外设模块卡。
 * <p>
 * 遥控器作为"剩余物品"留在合成格里，卡里只记遥控器 ID；积木读写时靠这个 ID 找到它。
 */
public class GunRemoteBindRecipe extends CustomRecipe {
    public static final GunRemoteBindRecipe INSTANCE = new GunRemoteBindRecipe();

    public GunRemoteBindRecipe() {
        super(CraftingBookCategory.MISC);
    }

    @Override
    public boolean matches(CraftingInput input, Level level) {
        ItemStack card = ItemStack.EMPTY;
        ItemStack remote = ItemStack.EMPTY;
        for (int i = 0; i < input.size(); i++) {
            ItemStack stack = input.getItem(i);
            if (stack.isEmpty()) {
                continue;
            }
            if (stack.getItem() instanceof GunRemoteItem) {
                if (!remote.isEmpty()) {
                    return false;
                }
                remote = stack;
            } else if (stack.getItem() instanceof DeviceModuleItem) {
                if (!card.isEmpty()) {
                    return false;
                }
                card = stack;
            } else {
                return false;
            }
        }
        return !card.isEmpty() && !remote.isEmpty();
    }

    @Override
    public ItemStack assemble(CraftingInput input, HolderLookup.Provider registries) {
        ItemStack card = ItemStack.EMPTY;
        ItemStack remote = ItemStack.EMPTY;
        for (int i = 0; i < input.size(); i++) {
            ItemStack stack = input.getItem(i);
            if (stack.getItem() instanceof GunRemoteItem && remote.isEmpty()) {
                remote = stack;
            } else if (stack.getItem() instanceof DeviceModuleItem && card.isEmpty()) {
                card = stack;
            }
        }
        if (card.isEmpty() || remote.isEmpty()) {
            return ItemStack.EMPTY;
        }
        ItemStack result = card.copy();
        DeviceModuleItem.bindGunRemote(result, remote);
        return result;
    }

    @Override
    public NonNullList<ItemStack> getRemainingItems(CraftingInput input) {
        NonNullList<ItemStack> remaining = NonNullList.withSize(input.size(), ItemStack.EMPTY);
        for (int i = 0; i < input.size(); i++) {
            ItemStack stack = input.getItem(i);
            if (stack.getItem() instanceof GunRemoteItem) {
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
        return com.mcl.cbtc.registry.CbcRecipes.GUN_REMOTE_BIND.get();
    }
}
