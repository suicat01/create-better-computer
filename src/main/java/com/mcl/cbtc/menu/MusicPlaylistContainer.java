package com.mcl.cbtc.menu;

import com.mcl.cbtc.item.MusicModuleItem;
import net.minecraft.core.NonNullList;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * 网络音乐模块卡的歌单容器：54 格，数据直接落在物品 NBT 上。
 * <p>
 * 每次改动都会立刻写回物品，因此关掉界面、把卡拆下来都不会丢歌单。
 */
public class MusicPlaylistContainer implements Container {
    private final ItemStack stack;
    private final NonNullList<ItemStack> items =
            NonNullList.withSize(MusicModuleItem.SLOTS, ItemStack.EMPTY);
    /** 装回真实物品的句柄：服务端把它写回玩家背包，客户端为空。 */
    private final java.util.function.Consumer<ItemStack> sink;

    public MusicPlaylistContainer(ItemStack stack) {
        this(stack, null);
    }

    public MusicPlaylistContainer(ItemStack stack, java.util.function.Consumer<ItemStack> sink) {
        this.stack = stack;
        this.sink = sink;
        MusicModuleItem.loadInto(stack, items);
    }

    /** 数据以 {@code stack} 为准；有 sink 时把它同步回玩家背包里的那张卡。 */
    private void flush() {
        MusicModuleItem.savePlaylist(stack, items);
        if (sink != null) {
            sink.accept(stack);
        }
    }

    @Override
    public int getContainerSize() {
        return MusicModuleItem.SLOTS;
    }

    @Override
    public boolean isEmpty() {
        for (ItemStack item : items) {
            if (!item.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    @Override
    public ItemStack getItem(int slot) {
        return slot >= 0 && slot < items.size() ? items.get(slot) : ItemStack.EMPTY;
    }

    @Override
    public ItemStack removeItem(int slot, int amount) {
        ItemStack result = ContainerHelper.removeItem(items, slot, amount);
        if (!result.isEmpty()) {
            setChanged();
        }
        return result;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        ItemStack result = ContainerHelper.takeItem(items, slot);
        setChanged();
        return result;
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        if (slot < 0 || slot >= items.size()) {
            return;
        }
        items.set(slot, stack);
        setChanged();
    }

    @Override
    public void setChanged() {
        flush();
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }

    @Override
    public void clearContent() {
        // 只清掉内存里的这份副本，不能经 setChanged()/flush() 把清空结果写回模块卡，
        // 否则关界面会把卡上真正的歌单一起清空
        items.clear();
    }
}
