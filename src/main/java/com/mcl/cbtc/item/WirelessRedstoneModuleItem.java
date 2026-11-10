package com.mcl.cbtc.item;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/** 为 CB 电脑提供机械动力无线红石收发能力的升级卡。 */
public class WirelessRedstoneModuleItem extends UpgradeModuleItem {
    public WirelessRedstoneModuleItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.cbtc.wireless_redstone_module.tooltip")
                .withStyle(ChatFormatting.GRAY));
    }
}
