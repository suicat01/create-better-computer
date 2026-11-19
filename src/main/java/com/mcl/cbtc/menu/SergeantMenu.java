package com.mcl.cbtc.menu;

import com.mcl.cbtc.blockentity.SergeantValeryBlockEntity;
import com.mcl.cbtc.registry.CbcMenus;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

/**
 * 瓦列里中士的装填指令菜单：没有物品槽，只携带五行的编辑副本。
 * <p>
 * 打开时由服务端把程序与目标名写进附加包；关闭时客户端把编辑结果发回服务端保存。
 */
public class SergeantMenu extends AbstractContainerMenu {
    private final BlockPos pos;
    private final List<String> rows = new ArrayList<>(SergeantValeryBlockEntity.ROWS);
    private final String targetName;

    /** 服务端构造。 */
    public SergeantMenu(int containerId, Inventory inventory, SergeantValeryBlockEntity blockEntity) {
        super(CbcMenus.SERGEANT_VALERY.get(), containerId);
        this.pos = blockEntity.getBlockPos();
        for (int i = 0; i < SergeantValeryBlockEntity.ROWS; i++) {
            rows.add(blockEntity.programRow(i));
        }
        this.targetName = blockEntity.targetName();
    }

    /** 客户端构造：数据全部来自服务端的附加包，方块实体没加载也能打开。 */
    public SergeantMenu(int containerId, Inventory inventory, FriendlyByteBuf buf) {
        super(CbcMenus.SERGEANT_VALERY.get(), containerId);
        this.pos = buf.readBlockPos();
        int count = buf.readVarInt();
        for (int i = 0; i < SergeantValeryBlockEntity.ROWS; i++) {
            rows.add(i < count ? buf.readUtf(128) : "");
        }
        this.targetName = buf.readUtf(128);
    }

    public BlockPos pos() {
        return pos;
    }

    public List<String> rows() {
        return rows;
    }

    public String targetName() {
        return targetName;
    }

    public boolean bound() {
        return !targetName.isEmpty();
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= 64.0;
    }
}
