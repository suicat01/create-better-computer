package com.mcl.cbtc.item;

import com.mcl.cbtc.compat.Curios;
import com.mcl.cbtc.compat.CuriosCompat;
import com.mcl.cbtc.device.TargetLocatorBridge;
import com.mcl.cbtc.registry.CbcItems;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.ChatFormatting;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.LodestoneTracker;
import net.minecraft.world.level.Level;

/**
 * 绑定指南针：由普通指南针与磁石挂件在工作台合成得到。
 * <p>
 * 物品自己在玩家的背包 / 盔甲栏 / 副手里被 tick 时，把携带对应挂件的玩家位置写进原版
 * {@code LODESTONE_TRACKER} 组件，客户端指针按原版逻辑指向那名玩家。
 * 这样不需要每刻扫描所有玩家背包，也不需要在静态字段里缓存服务器实例。
 */
public class BoundCompassItem extends Item {
    /** 记录绑定的挂件 ID。 */
    private static final String TAG_BOUND = "CbtcBoundCharm";

    public BoundCompassItem(Properties properties) {
        super(properties);
    }

    /** 用磁石挂件生成一个绑定指南针；挂件自身的 ID 会被补上并沿用。 */
    public static ItemStack create(ItemStack charm) {
        ItemStack compass = new ItemStack(CbcItems.BOUND_COMPASS.get());
        CompoundTag tag = tag(compass);
        tag.putString(TAG_BOUND, LodestoneCharmItem.ensureId(charm));
        compass.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        // 装了航空学时，导航台也能把它当成导航目标（指向随身携带挂件的玩家）
        TargetLocatorBridge.ensureBoundCompassTarget(compass);
        return compass;
    }

    /** 绑定指南针记住的挂件 ID；不是绑定指南针时返回空串。 */
    public static String boundCharmId(ItemStack stack) {
        if (!(stack.getItem() instanceof BoundCompassItem)) {
            return "";
        }
        return tag(stack).getString(TAG_BOUND);
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
        if (level.isClientSide || !(entity instanceof ServerPlayer owner)) {
            return;
        }
        String id = boundCharmId(stack);
        if (id.isBlank()) {
            return;
        }
        // 旧存档里的绑定指南针可能还没有导航目标组件，这里补上
        TargetLocatorBridge.ensureBoundCompassTarget(stack);
        MinecraftServer server = owner.getServer();
        if (server == null) {
            return;
        }
        ServerPlayer charmHolder = holderOf(server, id);
        if (charmHolder != null) {
            updateTarget(stack, charmHolder);
        }
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.cbtc.bound_compass.tooltip")
                .withStyle(ChatFormatting.GRAY));
    }

    /** 找到随身携带指定挂件的玩家；找不到返回 null。 */
    public static ServerPlayer holderOf(MinecraftServer server, String charmId) {
        if (server == null || charmId == null || charmId.isBlank()) {
            return null;
        }
        List<ServerPlayer> players = server.getPlayerList().getPlayers();
        for (ServerPlayer player : players) {
            if (holdsCharm(player, charmId)) {
                return player;
            }
        }
        return null;
    }

    private static void updateTarget(ItemStack stack, ServerPlayer holder) {
        GlobalPos target = GlobalPos.of(holder.serverLevel().dimension(), holder.blockPosition());
        LodestoneTracker current = stack.get(DataComponents.LODESTONE_TRACKER);
        if (current != null && current.tracked() && current.target().isPresent()
                && current.target().get().equals(target)) {
            return;
        }
        stack.set(DataComponents.LODESTONE_TRACKER, new LodestoneTracker(Optional.of(target), true));
    }

    /** 只要挂件在背包、盔甲栏、副手或饰品栏里，这名玩家就算持有者。 */
    private static boolean holdsCharm(ServerPlayer player, String charmId) {
        for (ItemStack stack : heldStacks(player)) {
            if (stack.getItem() instanceof LodestoneCharmItem
                    && charmId.equals(LodestoneCharmItem.id(stack))) {
                return true;
            }
        }
        return Curios.loaded() && CuriosCompat.isWearing(player, charmId);
    }

    private static List<ItemStack> heldStacks(ServerPlayer player) {
        List<ItemStack> stacks = new ArrayList<>();
        stacks.addAll(player.getInventory().items);
        stacks.addAll(player.getInventory().armor);
        stacks.addAll(player.getInventory().offhand);
        return stacks;
    }

    private static CompoundTag tag(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data == null ? new CompoundTag() : data.copyTag();
    }
}
