package com.mcl.cbtc.item;

import java.util.function.Supplier;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import org.jetbrains.annotations.Nullable;

/**
 * CB电脑 / CB拓展器 / CB音响 的物品形态。
 * <p>
 * 这三种方块会把方块实体数据随物品一起带走。1.0.0 早期版本写数据时漏了
 * {@code BLOCK_ENTITY_DATA} 必需的 {@code id} 字段，导致保存时抛出
 * "Missing id for entity" 崩溃。这里在物品读取与存续期间自动补齐 id，
 * 旧存档里已经坏掉的物品也能恢复正常。
 * <p>
 * 方块实体类型用 {@link Supplier} 延迟获取：物品注册早于方块实体注册，
 * 直接在构造时取值会拿到未绑定的 DeferredHolder 并崩溃。
 */
public class BlockEntityCarrierItem extends BlockItem {

    private final Supplier<? extends BlockEntityType<?>> type;

    public BlockEntityCarrierItem(Block block, Supplier<? extends BlockEntityType<?>> type,
                                  Properties properties) {
        super(block, properties);
        this.type = type;
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
        super.inventoryTick(stack, level, entity, slot, selected);
        fixMissingId(stack);
    }

    /**
     * 物品从存档读出来时立即修正，不必等到进入玩家背包再靠 tick 修补，
     * 避免退出保存时仍然崩溃。
     */
    @Override
    public void verifyComponentsAfterLoad(ItemStack stack) {
        super.verifyComponentsAfterLoad(stack);
        fixMissingId(stack);
    }

    /** 缺少 id 的方块实体数据补上类型 id；数据正常时不做任何改动。 */
    private void fixMissingId(ItemStack stack) {
        CustomData data = stack.get(DataComponents.BLOCK_ENTITY_DATA);
        if (data == null) {
            return;
        }
        CompoundTag tag = data.copyTag();
        if (tag.isEmpty() || tag.contains("id")) {
            return;
        }
        stack.set(DataComponents.BLOCK_ENTITY_DATA, CustomData.of(withId(tag, type.get())));
    }

    /** 给方块实体数据补上类型 id，供写入方复用。 */
    public static CompoundTag withId(@Nullable CompoundTag tag, BlockEntityType<?> type) {
        CompoundTag data = tag == null ? new CompoundTag() : tag.copy();
        if (!data.contains("id")) {
            data.putString("id", BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(type).toString());
        }
        return data;
    }
}
