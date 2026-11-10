package com.mcl.cbtc.menu;

import com.mcl.cbtc.blockentity.CBComputerBlockEntity;
import com.mcl.cbtc.registry.CbcMenus;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

/**
 * CB电脑编程界面容器。程序同步走过网络包，容器本身不带槽位。
 */
public class ComputerMenu extends AbstractContainerMenu {
    private final CBComputerBlockEntity computer;

    public ComputerMenu(int id, Inventory inventory, CBComputerBlockEntity computer) {
        super(CbcMenus.COMPUTER.get(), id);
        this.computer = computer;
    }

    public ComputerMenu(int id, Inventory inventory, FriendlyByteBuf buf) {
        this(id, inventory, (CBComputerBlockEntity) inventory.player.level()
                .getBlockEntity(buf.readBlockPos()));
    }

    public CBComputerBlockEntity computer() {
        return computer;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return computer != null && !computer.isRemoved()
                && player.distanceToSqr(computer.getBlockPos().getCenter()) <= 64;
    }
}
