package com.mcl.cbtc.item;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/**
 * 互联网模块卡：装入 CB 拓展器后，让该电脑接入唯一的那张"互联网网络"。
 * <p>
 * 与局域网模块卡不同，它不需要任何配置——插上即用。
 */
public class InternetModuleItem extends UpgradeModuleItem {
    public InternetModuleItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.cbtc.internet_module.tooltip").withStyle(ChatFormatting.DARK_GRAY));
    }
}
