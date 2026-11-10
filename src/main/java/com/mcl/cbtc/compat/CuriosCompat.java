package com.mcl.cbtc.compat;

import com.mcl.cbtc.item.LodestoneCharmItem;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.type.capability.ICurioItem;

/**
 * 饰品栏（Curios）软依赖对接层。
 * <p>
 * 只有确认装了 Curios 才会加载本类：把磁石挂件注册成饰品，并查询玩家身上是否戴着指定挂件。
 */
public final class CuriosCompat {
    private CuriosCompat() {
    }

    /** 把物品注册成 Curios 饰品；能放进哪些槽位由 {@code data/curios/tags/item/} 下的标签决定。 */
    public static void registerCurio(Item item) {
        CuriosApi.registerCurio(item, new ICurioItem() {
            /** 允许右键直接把挂件装进饰品栏（Curios 默认不允许）。 */
            @Override
            public boolean canEquipFromUse(SlotContext slotContext, ItemStack stack) {
                return true;
            }
        });
    }

    /** 玩家身上（任意饰品槽位）是否戴着指定 ID 的磁石挂件。 */
    public static boolean isWearing(Player player, String charmId) {
        return CuriosApi.getCuriosInventory(player)
                .map(handler -> handler.findFirstCurio(stack -> isCharm(stack, charmId)).isPresent())
                .orElse(false);
    }

    private static boolean isCharm(ItemStack stack, String charmId) {
        return stack.getItem() instanceof LodestoneCharmItem
                && charmId.equals(LodestoneCharmItem.id(stack));
    }
}
