package com.mcl.cbtc.item;

import com.mcl.cbtc.registry.CbcItems;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.allay.Allay;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.breeze.Breeze;
import net.minecraft.world.entity.monster.Shulker;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;

/**
 * 捏塑创生之手（充能状态）。
 * <p>
 * 右键旋风人会把旋风人变成旋风人散热器，右键悦灵会变成悦灵声卡，都以掉落物的形式掉在地上。
 * 每次使用消耗一格耐久，耐久耗尽后退化回未充能状态，共可使用 {@link #MAX_USES} 次。
 */
public class SculptingHandItem extends Item {
    /** 一次充能可使用的最多次数。 */
    public static final int MAX_USES = 32;
    /** 已使用次数，写在物品 NBT 里随物品一起移动。 */
    private static final String USES = "SculptingHandUses";

    public SculptingHandItem(Properties properties) {
        super(properties);
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }

    /** 剩余次数作为耐久条显示，方便一眼看出还能用几次。 */
    @Override
    public boolean isBarVisible(ItemStack stack) {
        return remainingUses(stack) < MAX_USES;
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        return Math.round(13.0F * remainingUses(stack) / MAX_USES);
    }

    @Override
    public int getBarColor(ItemStack stack) {
        return 0x8B5CF6;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.cbtc.sculpting_hand.desc").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.cbtc.sculpting_hand.uses",
                remainingUses(stack), MAX_USES).withStyle(ChatFormatting.AQUA));
    }

    /** 防爆、防雷：掉落物只有 5 点生命值，吃一次闪电就会被直接销毁。 */
    @Override
    public boolean canBeHurtBy(ItemStack stack, DamageSource source) {
        return !source.is(DamageTypeTags.IS_EXPLOSION) && !source.is(DamageTypes.LIGHTNING_BOLT);
    }

    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target, InteractionHand hand) {
        return trySculpt(stack, player, target, hand);
    }

    /**
     * 捏塑逻辑，物品交互和事件里对悦灵的豁免共用。
     * 悦灵会先抢走手里的物品，因此悦灵必须在事件里提前拦下来。
     */
    public static InteractionResult trySculpt(ItemStack stack, Player player, LivingEntity target, InteractionHand hand) {
        ItemStack product = SculptingRecipes.resultFor(target);
        SoundEvent sound;
        if (product.isEmpty()) {
            return InteractionResult.PASS;
        }
        if (target instanceof Breeze) {
            sound = SoundEvents.BREEZE_HURT;
        } else if (target instanceof Allay) {
            sound = SoundEvents.ALLAY_HURT;
        } else if (target instanceof com.mcl.cbtc.entity.SuperBlaze
                || target instanceof net.minecraft.world.entity.monster.Blaze) {
            // 烈焰人与超级烈焰人共用同一个音效
            sound = SoundEvents.BLAZE_HURT;
        } else {
            sound = SoundEvents.SHULKER_HURT;
        }
        Level level = player.level();
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }

        ItemEntity drop = new ItemEntity(level,
                target.getX(), target.getY() + target.getBbHeight() / 2.0, target.getZ(), product);
        drop.setDefaultPickUpDelay();
        level.addFreshEntity(drop);
        level.playSound(null, target.blockPosition(), sound, SoundSource.PLAYERS, 1.0F, 1.0F);
        target.discard();

        int uses = uses(stack);
        if (uses >= MAX_USES) {
            player.setItemInHand(hand, new ItemStack(CbcItems.UNCHARGED_SCULPTING_HAND.get()));
        } else {
            ItemStack used = stack.copy();
            setUses(used, uses + 1);
            player.setItemInHand(hand, used);
        }
        return InteractionResult.SUCCESS;
    }

    /** 下一次使用后的剩余次数，用于提示条。 */
    public static int remainingUses(ItemStack stack) {
        return Math.max(0, MAX_USES - uses(stack));
    }

    private static int uses(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data == null ? 0 : data.copyTag().getInt(USES);
    }

    private static void setUses(ItemStack stack, int uses) {
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        if (uses <= 0) {
            tag.remove(USES);
        } else {
            tag.putInt(USES, uses);
        }
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }
}
