package com.mcl.cbtc.item;

import com.simibubi.create.content.logistics.packagerLink.PackagerLinkBlockEntity;
import java.util.List;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.nbt.CompoundTag;
import org.jetbrains.annotations.Nullable;

/** 与机械动力仓储链接使用相同 Freq 网络标识的升级模块。 */
public class StorageLinkModuleItem extends UpgradeModuleItem {
    public StorageLinkModuleItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        if (player == null) {
            return InteractionResult.PASS;
        }
        ItemStack stack = context.getItemInHand();
        if (player.isShiftKeyDown()) {
            if (levelIsClient(context.getLevel())) {
                return InteractionResult.SUCCESS;
            }
            if (network(stack) != null) {
                stack.remove(DataComponents.CUSTOM_DATA);
                message(player, "unbound");
                context.getLevel().playSound(null, player.blockPosition(), SoundEvents.UI_BUTTON_CLICK.value(),
                        SoundSource.PLAYERS, 0.4F, 1.2F);
            } else {
                message(player, "not_bound");
            }
            return InteractionResult.CONSUME;
        }
        if (!(context.getLevel().getBlockEntity(context.getClickedPos())
                instanceof PackagerLinkBlockEntity link)) {
            message(player, "click_link");
            return InteractionResult.FAIL;
        }
        if (levelIsClient(context.getLevel())) {
            return InteractionResult.SUCCESS;
        }
        UUID network = link.behaviour.freqId;
        setNetwork(stack, network);
        message(player, "bound");
        context.getLevel().playSound(null, context.getClickedPos(), SoundEvents.EXPERIENCE_ORB_PICKUP,
                SoundSource.BLOCKS, 0.5F, 1.4F);
        return InteractionResult.CONSUME;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!player.isShiftKeyDown()) {
            return InteractionResultHolder.pass(stack);
        }
        if (!level.isClientSide && network(stack) != null) {
            stack.remove(DataComponents.CUSTOM_DATA);
            message(player, "unbound");
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return network(stack) != null;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.cbtc.storage_link_module.tooltip").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable(network(stack) == null
                ? "item.cbtc.storage_link_module.unbound" : "item.cbtc.storage_link_module.bound")
                .withStyle(network(stack) == null ? ChatFormatting.RED : ChatFormatting.AQUA));
    }

    @Nullable
    public static UUID network(ItemStack stack) {
        if (!(stack.getItem() instanceof StorageLinkModuleItem)) {
            return null;
        }
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null) {
            return null;
        }
        CompoundTag tag = data.copyTag();
        return tag.hasUUID("Freq") ? tag.getUUID("Freq") : null;
    }

    private static void setNetwork(ItemStack stack, UUID network) {
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        tag.putUUID("Freq", network);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    private static boolean levelIsClient(Level level) {
        return level.isClientSide;
    }

    private static void message(Player player, String key) {
        player.displayClientMessage(Component.translatable("item.cbtc.storage_link_module." + key), true);
    }
}
