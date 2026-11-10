package com.mcl.cbtc.integration.create;

import com.mcl.cbtc.item.StorageLinkModuleItem;
import com.simibubi.create.content.logistics.BigItemStack;
import com.simibubi.create.content.logistics.packager.IdentifiedInventory;
import com.simibubi.create.content.logistics.packager.PackagerBlockEntity;
import com.simibubi.create.content.logistics.packagerLink.LogisticallyLinkedBehaviour;
import com.simibubi.create.content.logistics.packagerLink.LogisticsManager;
import com.simibubi.create.content.logistics.packagerLink.PackagerLinkBlockEntity;
import com.simibubi.create.content.logistics.stockTicker.PackageOrderWithCrafts;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;

/** CB 模块与 Create 物流网络之间的桥接层。 */
public final class CreateLogistics {
    private CreateLogistics() {
    }

    public static int capacity(ItemStack module) {
        UUID network = StorageLinkModuleItem.network(module);
        if (network == null) {
            return 0;
        }
        int total = 0;
        for (IItemHandler inventory : networkInventories(network).values()) {
            for (int slot = 0; slot < inventory.getSlots(); slot++) {
                ItemStack stack = inventory.getStackInSlot(slot);
                total = saturatedAdd(total, slotCapacity(inventory, slot, stack));
            }
        }
        return total;
    }

    /** 官方打包机库存汇总；容量与占用使用相同的官方库存识别规则。 */
    public static int freeSpace(ItemStack module) {
        UUID network = StorageLinkModuleItem.network(module);
        if (network == null) {
            return 0;
        }
        int occupied = 0;
        for (PackagerBlockEntity packager : networkPackagers(network)) {
            occupied = saturatedAdd(occupied, packager.getAvailableItems().getTotalCount());
        }
        return Math.max(0, capacity(module) - occupied);
    }

    public static int itemCount(ItemStack module, String itemId) {
        UUID network = StorageLinkModuleItem.network(module);
        ItemStack item = stackOf(itemId, 1);
        return network == null || item.isEmpty() ? 0
                : LogisticsManager.getSummaryOfNetwork(network, true).getCountOf(item);
    }

    /** 委托 Create 选择同网络打包机、扣除库存并发出指定地址的包裹。 */
    public static boolean sendPackage(ItemStack module, String itemId, int count, String address) {
        UUID network = StorageLinkModuleItem.network(module);
        ItemStack item = stackOf(itemId, count);
        if (network == null || item.isEmpty() || count <= 0 || address == null || address.isBlank()) {
            return false;
        }
        PackageOrderWithCrafts order = PackageOrderWithCrafts.simple(
                List.of(new BigItemStack(item, count)));
        return LogisticsManager.broadcastPackageRequest(network, LogisticallyLinkedBehaviour.RequestType.PLAYER,
                order, null, address.trim());
    }

    private static Map<IItemHandler, IItemHandler> networkInventories(UUID network) {
        Map<IItemHandler, IItemHandler> inventories = new IdentityHashMap<>();
        for (PackagerBlockEntity packager : networkPackagers(network)) {
            IdentifiedInventory identified = packager.targetInventory.getIdentifiedInventory();
            if (identified != null) {
                inventories.put(identified.handler(), identified.handler());
            }
        }
        return inventories;
    }

    /** 与 LogisticsManager 一致：同一 InventoryIdentifier 的多个链接只统计一次。 */
    private static List<PackagerBlockEntity> networkPackagers(UUID network) {
        List<PackagerBlockEntity> result = new java.util.ArrayList<>();
        java.util.Set<com.simibubi.create.api.packager.InventoryIdentifier> identifiers = new java.util.HashSet<>();
        for (LogisticallyLinkedBehaviour link : LogisticallyLinkedBehaviour.getAllPresent(network, false)) {
            if (!(link.blockEntity instanceof PackagerLinkBlockEntity packagerLink)) {
                continue;
            }
            PackagerBlockEntity packager = packagerLink.getPackager();
            if (packager == null || !packager.targetInventory.hasInventory()) {
                continue;
            }
            IdentifiedInventory identified = packager.targetInventory.getIdentifiedInventory();
            if (identified == null) {
                continue;
            }
            if (identified.identifier() == null || identifiers.add(identified.identifier())) {
                result.add(packager);
            }
        }
        return result;
    }

    private static int slotCapacity(IItemHandler inventory, int slot, ItemStack stack) {
        return Math.min(inventory.getSlotLimit(slot), stack.isEmpty() ? 64 : stack.getMaxStackSize());
    }

    private static int saturatedAdd(int a, int b) {
        long result = (long) a + b;
        return result > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) result;
    }

    private static ItemStack stackOf(String itemId, int count) {
        ResourceLocation id = ResourceLocation.tryParse(itemId == null ? "" : itemId.trim());
        if (id == null) {
            return ItemStack.EMPTY;
        }
        Item item = BuiltInRegistries.ITEM.get(id);
        return BuiltInRegistries.ITEM.getDefaultKey().equals(id) ? ItemStack.EMPTY
                : new ItemStack(item, count);
    }
}
