package com.mcl.cbtc.item;

import java.util.List;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

/**
 * 目标定位模块：记录一个世界坐标（X / Z），可作为航空学导航台的导航目标。
 * <p>
 * 右键空气打开坐标编辑界面；放入导航台后导航台会指向这里写入的 X / Z。
 * 与外设绑定模块卡在工作台合成后可被积木读写坐标。
 */
public class TargetLocatorItem extends Item {
    private static final String TAG_X = "TargetX";
    private static final String TAG_Z = "TargetZ";
    private static final String TAG_ID = "ModuleId";

    public TargetLocatorItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide) {
            // 未设置过坐标时，默认填玩家当前所在位置，避免导航台指向世界原点
            double x = isSet(stack) ? x(stack) : Math.floor(player.getX());
            double z = isSet(stack) ? z(stack) : Math.floor(player.getZ());
            com.mcl.cbtc.client.TargetLocatorScreen.open(hand, x, z);
        } else {
            ensureId(stack);
            com.mcl.cbtc.device.TargetLocatorBridge.ensureTarget(stack);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        // 交给方块（比如航空学导航台）处理右键；这里只补上导航目标组件
        ensureId(context.getItemInHand());
        com.mcl.cbtc.device.TargetLocatorBridge.ensureTarget(context.getItemInHand());
        return InteractionResult.PASS;
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
        if (level.isClientSide) {
            return;
        }
        ensureId(stack);
        com.mcl.cbtc.device.TargetLocatorBridge.ensureTarget(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        if (isSet(stack)) {
            tooltip.add(Component.translatable("item.cbtc.target_locator.position",
                    format(x(stack)), format(z(stack))).withStyle(ChatFormatting.AQUA));
        } else {
            tooltip.add(Component.translatable("item.cbtc.target_locator.unset").withStyle(ChatFormatting.GRAY));
        }
        tooltip.add(Component.translatable("item.cbtc.target_locator.tooltip").withStyle(ChatFormatting.DARK_GRAY));
    }

    private static String format(double value) {
        return Math.abs(value - Math.round(value)) < 1.0E-6
                ? Long.toString(Math.round(value))
                : String.format("%.2f", value);
    }

    /** 是否写入过坐标。 */
    public static boolean isSet(ItemStack stack) {
        return tag(stack).contains(TAG_X) && tag(stack).contains(TAG_Z);
    }

    /** 写入的 X 坐标；未写入时为 0。 */
    public static double x(ItemStack stack) {
        return tag(stack).getDouble(TAG_X);
    }

    /** 写入的 Z 坐标；未写入时为 0。 */
    public static double z(ItemStack stack) {
        return tag(stack).getDouble(TAG_Z);
    }

    /** 写入坐标，并确保模块有稳定 ID（供外设模块卡引用）。 */
    public static void setPosition(ItemStack stack, double x, double z) {
        CompoundTag tag = tag(stack);
        tag.putDouble(TAG_X, x);
        tag.putDouble(TAG_Z, z);
        if (tag.getString(TAG_ID).isBlank()) {
            tag.putString(TAG_ID, UUID.randomUUID().toString());
        }
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    /** 模块 ID：外设模块卡靠它找到这个模块；未生成时为空串。 */
    public static String moduleId(ItemStack stack) {
        return tag(stack).getString(TAG_ID);
    }

    /** 模块 ID，缺失时生成一个并写回。 */
    public static String ensureId(ItemStack stack) {
        String id = moduleId(stack);
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
