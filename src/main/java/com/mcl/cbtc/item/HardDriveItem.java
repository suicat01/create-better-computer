package com.mcl.cbtc.item;

import com.mcl.cbtc.core.Value;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import org.jetbrains.annotations.Nullable;

/**
 * 硬盘：装入 CB 拓展器后，给电脑提供一块可持久化的变量存储区。
 * <p>
 * 数据直接写在物品自身的 NBT 里，因此把硬盘拆下来装到另一台电脑上，
 * 之前存的变量仍然可以读到。存有变量时物品带附魔光效，
 * 鼠标悬浮会列出每个变量的名称与当前值。
 */
public class HardDriveItem extends UpgradeModuleItem {
    /** 变量表所在的 NBT 键：一个 CompoundTag，键是变量名，值是 {@link Value#save} 写出的结构。 */
    private static final String VARIABLES = "HardDriveVars";

    public HardDriveItem(Properties properties) {
        super(properties);
    }

    /** 读取一块变量；不存在时返回 null。 */
    @Nullable
    public static Value get(ItemStack stack, String name) {
        CompoundTag tag = tag(stack);
        if (!tag.contains(VARIABLES, Tag.TAG_COMPOUND)) {
            return null;
        }
        CompoundTag vars = tag.getCompound(VARIABLES);
        String key = name == null ? "" : name.trim();
        if (key.isEmpty() || !vars.contains(key, Tag.TAG_COMPOUND)) {
            return null;
        }
        return Value.load(vars.getCompound(key));
    }

    /** 写入或更新一块变量。 */
    public static void set(ItemStack stack, String name, Value value) {
        String key = name == null ? "" : name.trim();
        if (key.isEmpty()) {
            return;
        }
        CompoundTag tag = tag(stack);
        CompoundTag vars = tag.contains(VARIABLES, Tag.TAG_COMPOUND)
                ? tag.getCompound(VARIABLES)
                : new CompoundTag();
        CompoundTag entry = new CompoundTag();
        value.save(entry);
        vars.put(key, entry);
        tag.put(VARIABLES, vars);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    /** 硬盘里保存的全部变量名，按写入顺序。 */
    public static List<String> names(ItemStack stack) {
        CompoundTag tag = tag(stack);
        if (!tag.contains(VARIABLES, Tag.TAG_COMPOUND)) {
            return List.of();
        }
        return new ArrayList<>(tag.getCompound(VARIABLES).getAllKeys());
    }

    /** 是否有任意变量，用于决定是否显示附魔光效。 */
    public static boolean hasData(ItemStack stack) {
        return !names(stack).isEmpty();
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return hasData(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        List<String> names = names(stack);
        if (names.isEmpty()) {
            tooltip.add(Component.translatable("item.cbtc.hard_drive.empty").withStyle(ChatFormatting.GRAY));
        } else {
            tooltip.add(Component.translatable("item.cbtc.hard_drive.count", names.size())
                    .withStyle(ChatFormatting.AQUA));
            for (String name : names) {
                Value value = get(stack, name);
                tooltip.add(Component.literal("· " + name + " = " + (value == null ? "" : value.asText()))
                        .withStyle(ChatFormatting.GRAY));
            }
        }
        tooltip.add(Component.translatable("item.cbtc.hard_drive.tooltip").withStyle(ChatFormatting.DARK_GRAY));
    }

    private static CompoundTag tag(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data == null ? new CompoundTag() : data.copyTag();
    }
}
