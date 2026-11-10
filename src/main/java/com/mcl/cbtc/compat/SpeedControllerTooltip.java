package com.mcl.cbtc.compat;

import com.mcl.cbtc.registry.CbcItems;
import com.simibubi.create.foundation.item.ItemDescription;
import net.createmod.catnip.lang.FontHelper;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

/**
 * 智能转速控制器的 Shift 概要。
 * <p>
 * 文案、Shift 判定与配色全部交给机械动力官方的 {@link ItemDescription.Modifier}，
 * 这里只负责在 tooltip 事件里把它调度到目标物品上。
 * 官方取键规则为 {@code getDescriptionId() + ".tooltip" + ".summary"}，
 * 即语言文件里的 {@code block.cbtc.speed_controller.tooltip.summary}。
 */
public final class SpeedControllerTooltip {
    private static ItemDescription.Modifier modifier;

    private SpeedControllerTooltip() {
    }

    /** 仅在客户端初始化时调用。 */
    public static void register() {
        Item item = CbcItems.SPEED_CONTROLLER.get();
        modifier = new ItemDescription.Modifier(item, FontHelper.Palette.STANDARD_CREATE);
        NeoForge.EVENT_BUS.addListener(SpeedControllerTooltip::onTooltip);
    }

    private static void onTooltip(ItemTooltipEvent event) {
        if (modifier == null) {
            return;
        }
        if (!event.getItemStack().is(CbcItems.SPEED_CONTROLLER.get())) {
            return;
        }
        modifier.modify(event);
    }
}
