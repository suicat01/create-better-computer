package com.mcl.cbtc.item;

import java.util.List;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Equipable;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;

/**
 * 磁石挂件：饰品。
 * <p>
 * 没装饰品栏模组时由 {@link Equipable} 放进护腿槽位；装了 Curios 后还能放进任意饰品栏槽位
 * （由 {@code data/curios/tags/item/} 下的标签决定）。挂件带一个稳定 ID，
 * 工作台里和一个指南针合成后，指南针会记住这个 ID 并指向佩戴它的玩家。
 */
public class LodestoneCharmItem extends Item implements Equipable {
    private static final String TAG_ID = "CbtcCharmId";

    public LodestoneCharmItem(Properties properties) {
        super(properties);
    }

    @Override
    public EquipmentSlot getEquipmentSlot() {
        return EquipmentSlot.LEGS;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        // 装了饰品栏模组时交给 Curios 的饰品槽位处理，避免右键把它塞进护腿槽
        if (com.mcl.cbtc.compat.Curios.loaded()) {
            return InteractionResultHolder.pass(player.getItemInHand(hand));
        }
        return swapWithEquipmentSlot(this, level, player, hand);
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
        if (level.isClientSide) {
            return;
        }
        ensureId(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.cbtc.lodestone_charm.tooltip.equip")
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.cbtc.lodestone_charm.tooltip.bind")
                .withStyle(ChatFormatting.DARK_GRAY));
        if (flag.isAdvanced()) {
            String id = id(stack);
            if (!id.isBlank()) {
                tooltip.add(Component.literal(id).withStyle(ChatFormatting.DARK_GRAY));
            }
        }
    }

    /** 挂件 ID；没有生成过时返回空串。 */
    public static String id(ItemStack stack) {
        return tag(stack).getString(TAG_ID);
    }

    /** 挂件 ID，缺失时生成一个并写回物品。 */
    public static String ensureId(ItemStack stack) {
        String id = id(stack);
        if (!id.isBlank()) {
            return id;
        }
        id = UUID.randomUUID().toString();
        CompoundTag tag = tag(stack);
        tag.putString(TAG_ID, id);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        return id;
    }

    private static CompoundTag tag(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data == null ? new CompoundTag() : data.copyTag();
    }
}
