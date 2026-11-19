package com.mcl.cbtc.compat;

import com.mcl.cbtc.registry.CbcItems;
import com.simibubi.create.foundation.item.ItemDescription;
import com.simibubi.create.foundation.item.KineticStats;
import com.simibubi.create.foundation.item.TooltipModifier;
import java.util.HashMap;
import java.util.Map;
import net.createmod.catnip.lang.FontHelper;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

/**
 * 把本模组所有物品的描述交给机械动力原生的 {@link ItemDescription.Modifier} 渲染。
 * <p>
 * 官方取键规则是 {@code getDescriptionId() + ".tooltip.summary"}，
 * 没有对应语言键的物品会返回 null，自动跳过。
 */
public final class CreateTooltips {
    private static final Map<Item, ItemDescription.Modifier> MODIFIERS = new HashMap<>();

    private CreateTooltips() {
    }

    /** 仅在客户端初始化时调用。 */
    public static void register() {
        for (var holder : CbcItems.ITEMS.getEntries()) {
            Item item = holder.get();
            MODIFIERS.put(item, new ItemDescription.Modifier(item, FontHelper.Palette.STANDARD_CREATE));
        }
        // 动力学方块（目前是瓦列里中士）的「应力影响」提示：Create 只为自家物品在
        // 注册时挂了 KineticStats，这里是官方给单个物品补挂同一提示器的入口
        TooltipModifier kineticStats = KineticStats.create(CbcItems.SERGEANT_VALERY.get());
        if (kineticStats != null) {
            TooltipModifier.REGISTRY.register(CbcItems.SERGEANT_VALERY.get(), kineticStats);
        }
        NeoForge.EVENT_BUS.addListener(CreateTooltips::onTooltip);
    }

    private static void onTooltip(ItemTooltipEvent event) {
        ItemDescription.Modifier modifier = MODIFIERS.get(event.getItemStack().getItem());
        if (modifier != null) {
            modifier.modify(event);
        }
    }
}
