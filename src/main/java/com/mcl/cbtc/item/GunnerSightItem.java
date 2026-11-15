package com.mcl.cbtc.item;

import java.util.UUID;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Equipable;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;

/**
 * 枪炮手的瞄准组件。
 * <p>
 * 没装饰品栏模组时由 {@link Equipable} 戴到头部槽位；装了 Curios 后由
 * {@code data/curios/tags/item/head.json} 决定它可以放进哪些饰品栏槽位。
 * <p>
 * 每件组件自带一个稳定 ID，工作台里和外设绑定模块卡合成后作为物品型外设登记，
 * 存档只记 ID，不依赖物品所在位置。
 */
public class GunnerSightItem extends Item implements Equipable {
    private static final String TAG_ID = "GunnerSightId";

    public GunnerSightItem(Properties properties) {
        super(properties);
    }

    @Override
    public EquipmentSlot getEquipmentSlot() {
        return EquipmentSlot.HEAD;
    }

    /** 允许在附魔台上附魔（色彩修复）。 */
    @Override
    public boolean isEnchantable(ItemStack stack) {
        return true;
    }

    @Override
    public int getEnchantmentValue() {
        return 14;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        // 装了饰品栏模组时交给 Curios 的饰品槽位处理，避免右键把它塞进头盔槽
        if (com.mcl.cbtc.compat.Curios.loaded()) {
            return InteractionResultHolder.pass(player.getItemInHand(hand));
        }
        return swapWithEquipmentSlot(this, level, player, hand);
    }

    /** 组件 ID；没有生成过时返回空串。 */
    public static String id(ItemStack stack) {
        return tag(stack).getString(TAG_ID);
    }

    /** 组件 ID，缺失时生成一个并写回物品。 */
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
