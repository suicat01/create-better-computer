package com.mcl.cbtc.item;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/**
 * 未充能捏塑创生之手：与鞘翅同级别的材料，被闪电劈中后转化为充能的捏塑创生之手。
 */
public class UnchargedSculptingHandItem extends Item {
    public UnchargedSculptingHandItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.cbtc.sculpting_hand.desc").withStyle(ChatFormatting.GRAY));
    }

    /** 防爆、防雷：掉落物只有 5 点生命值，吃一次闪电就会被直接销毁。 */
    @Override
    public boolean canBeHurtBy(ItemStack stack, DamageSource source) {
        return !source.is(DamageTypeTags.IS_EXPLOSION) && !source.is(DamageTypes.LIGHTNING_BOLT);
    }
}
