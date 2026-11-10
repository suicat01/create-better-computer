package com.mcl.cbtc.item;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;

/**
 * 局域网模块卡：装入 CB 拓展器后，让该电脑加入指定局域网。
 * <p>
 * 交互：手持模块卡右键空气，输入局域网 ID 与密码。ID 首次使用即按当次密码注册，
 * 之后同 ID 必须密码一致才能加入。
 */
public class LanModuleItem extends UpgradeModuleItem {
    private static final String LAN_ID = "LanId";
    private static final String LAN_PASSWORD = "LanPassword";

    public LanModuleItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide) {
            com.mcl.cbtc.client.LanJoinScreen.open(hand, lanId(stack), password(stack));
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    /** 卡上保存的局域网 ID；未设置时为空串。 */
    public static String lanId(ItemStack stack) {
        return tag(stack).getString(LAN_ID);
    }

    /** 卡上保存的密码；未设置时为空串。 */
    public static String password(ItemStack stack) {
        return tag(stack).getString(LAN_PASSWORD);
    }

    /** 是否已完整配置（ID 与密码都非空）。 */
    public static boolean isConfigured(ItemStack stack) {
        return !lanId(stack).isBlank() && !password(stack).isBlank();
    }

    /** 写入局域网 ID 与密码。 */
    public static void setNetwork(ItemStack stack, String lanId, String password) {
        CompoundTag tag = tag(stack);
        tag.putString(LAN_ID, lanId == null ? "" : lanId.trim());
        tag.putString(LAN_PASSWORD, password == null ? "" : password);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    private static CompoundTag tag(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data == null ? new CompoundTag() : data.copyTag();
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return isConfigured(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        String id = lanId(stack);
        if (id.isBlank()) {
            tooltip.add(Component.translatable("item.cbtc.lan_module.empty").withStyle(ChatFormatting.GRAY));
        } else {
            tooltip.add(Component.translatable("item.cbtc.lan_module.bound", id).withStyle(ChatFormatting.AQUA));
        }
        tooltip.add(Component.translatable("item.cbtc.lan_module.tooltip").withStyle(ChatFormatting.DARK_GRAY));
    }
}
