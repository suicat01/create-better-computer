package com.mcl.cbtc.menu;

import com.mcl.cbtc.blockentity.CBSpeakerBlockEntity;
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
 * CB音响界面容器：只有一个扬声器升级槽。
 */
public class SpeakerMenu extends AbstractContainerMenu {
    /** 只有一格，放在界面上方正中的位置。 */
    private static final int SLOT_X = 80;
    private static final int SLOT_Y = 35;

    private final Container container;

    public SpeakerMenu(int id, Inventory inventory, CBSpeakerBlockEntity speaker) {
        this(id, inventory, (Container) speaker);
    }

    public SpeakerMenu(int id, Inventory inventory, FriendlyByteBuf buf) {
        this(id, inventory, containerAt(inventory, buf));
    }

    private SpeakerMenu(int id, Inventory inventory, Container container) {
        super(CbcMenus.SPEAKER.get(), id);
        this.container = container;
        container.startOpen(inventory.player);

        addSlot(new Slot(container, 0, SLOT_X, SLOT_Y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                // 只有扬声器升级可以放进音响
                return stack.getItem() instanceof com.mcl.cbtc.item.SpeakerModuleItem;
            }

            @Override
            public int getMaxStackSize() {
                return 1;
            }
        });

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
                instanceof CBSpeakerBlockEntity speaker) {
            return speaker;
        }
        return new SimpleContainer(CBSpeakerBlockEntity.SLOTS);
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
        if (index == 0) {
            // 升级槽 → 背包
            if (!moveItemStackTo(stack, 1, slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else {
            // 背包 → 升级槽
            if (!moveItemStackTo(stack, 0, 1, false)) {
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
