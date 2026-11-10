package com.mcl.cbtc.event;

import com.mcl.cbtc.CreateBetterComputer;
import com.mcl.cbtc.item.SculptingHandItem;
import com.mcl.cbtc.registry.CbcItems;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.animal.allay.Allay;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.entity.EntityStruckByLightningEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;

/** 捏塑创生之手在世界里的额外规则：闪电充能、爆炸免疫与悦灵豁免。 */
public final class SculptingHandEvents {
    private SculptingHandEvents() {
    }

    /**
     * 原版悦灵被右键时会先把玩家手里的物品抢走，导致捏塑创生之手还没生效就被夺走。
     * 这里在实体交互之前先拦下来，直接走捏塑逻辑。
     */
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (!(event.getTarget() instanceof Allay allay)) {
            return;
        }
        ItemStack stack = event.getItemStack();
        if (!(stack.getItem() instanceof SculptingHandItem)) {
            return;
        }
        event.setCanceled(true);
        event.setCancellationResult(event.getLevel().isClientSide()
                ? InteractionResult.SUCCESS
                : SculptingHandItem.trySculpt(stack, event.getEntity(), allay, event.getHand()));
    }

    /**
     * 掉落在地上的未充能捏塑创生之手被闪电击中后变成充能的捏塑创生之手。
     * 两种手都取消这次雷击：掉落物只有 5 点生命值，被雷劈中会被直接销毁。
     */
    public static void onStruckByLightning(EntityStruckByLightningEvent event) {
        if (!(event.getEntity() instanceof ItemEntity itemEntity)) {
            return;
        }
        ItemStack stack = itemEntity.getItem();
        if (!isSculptingHand(stack)) {
            return;
        }
        if (stack.is(CbcItems.UNCHARGED_SCULPTING_HAND.get())) {
            itemEntity.setItem(new ItemStack(CbcItems.SCULPTING_HAND.get(), stack.getCount()));
            CreateBetterComputer.LOGGER.info("未充能捏塑创生之手被闪电击中，已转化为捏塑创生之手（{}）",
                    itemEntity.blockPosition());
        }
        event.setCanceled(true);
    }

    /** 两只手都防火防爆：把掉落的物品实体移出爆炸的受影响列表。 */
    public static void onExplosionDetonate(ExplosionEvent.Detonate event) {
        event.getAffectedEntities().removeIf(entity ->
                entity instanceof ItemEntity itemEntity && isSculptingHand(itemEntity.getItem()));
    }

    private static boolean isSculptingHand(ItemStack stack) {
        return stack.is(CbcItems.UNCHARGED_SCULPTING_HAND.get()) || stack.is(CbcItems.SCULPTING_HAND.get());
    }
}
