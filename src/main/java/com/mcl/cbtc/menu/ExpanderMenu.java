package com.mcl.cbtc.menu;

import com.mcl.cbtc.blockentity.ExpanderBlockEntity;
import com.mcl.cbtc.registry.CbcMenus;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * CB拓展器界面容器：6 个升级模块槽位。
 */
public class ExpanderMenu extends AbstractContainerMenu {
    /** 6 个模块槽位排成一行。 */
    private static final int COLUMNS = 6;

    private final Container container;

    public ExpanderMenu(int id, Inventory inventory, ExpanderBlockEntity expander) {
        this(id, inventory, (Container) expander);
    }

    public ExpanderMenu(int id, Inventory inventory, FriendlyByteBuf buf) {
        this(id, inventory, containerAt(inventory, buf));
    }

    private ExpanderMenu(int id, Inventory inventory, Container container) {
        super(CbcMenus.EXPANDER.get(), id);
        this.container = container;
        container.startOpen(inventory.player);

        // 模块槽：与原版发射器界面一致的两行三列 3×3 格位
        for (int i = 0; i < ExpanderBlockEntity.SLOTS; i++) {
            int col = i % 3;
            int row = i / 3;
            addSlot(new Slot(container, i, 62 + col * 18, 17 + row * 18) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    // 只有升级模块可以放进拓展器；模块物品由后续更新提供
                    return stack.getItem() instanceof com.mcl.cbtc.item.UpgradeModuleItem;
                }
            });
        }

        // 玩家背包
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(inventory, col + row * 9 + 9, 8 + col * 18, 84 + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(inventory, col, 8 + col * 18, 142));
        }
    }

    private static Container containerAt(Inventory inventory, FriendlyByteBuf buf) {
        var pos = buf.readBlockPos();
        if (inventory.player.level().getBlockEntity(pos)
                instanceof ExpanderBlockEntity expander) {
            return expander;
        }
        return new SimpleContainer(ExpanderBlockEntity.SLOTS);
    }

    public Container container() {
        return container;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (slot == null || !slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack copy = stack.copy();
        int moduleSlots = ExpanderBlockEntity.SLOTS;
        if (index < moduleSlots) {
            // 模块槽 → 背包
            if (!moveItemStackTo(stack, moduleSlots, slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else {
            // 背包 → 模块槽
            if (!moveItemStackTo(stack, 0, moduleSlots, false)) {
                return ItemStack.EMPTY;
            }
        }
        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        return copy;
    }

    @Override
    public boolean stillValid(Player player) {
        return container.stillValid(player);
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        container.stopOpen(player);
    }
}
