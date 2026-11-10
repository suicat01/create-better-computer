package com.mcl.cbtc.item;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/**
 * 扬声器升级模块卡：装入 CB 拓展器后，把播放声音与朗诵的可听范围上限从 12 格提升到 50 格。
 * <p>
 * 只管范围上限，不改动积木本身的写法——超出上限的填值会被夹到上限。
 */
public class SpeakerModuleItem extends UpgradeModuleItem {
    public SpeakerModuleItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.cbtc.speaker_module.tooltip").withStyle(ChatFormatting.DARK_GRAY));
    }
}
