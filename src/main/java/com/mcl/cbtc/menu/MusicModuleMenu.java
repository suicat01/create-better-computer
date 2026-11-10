package com.mcl.cbtc.menu;

import com.mcl.cbtc.item.MusicModuleItem;
import com.mcl.cbtc.registry.CbcMenus;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * 网络音乐模块卡的歌单界面：54 格（与大箱子一致），下方玩家背包。
 * <p>
 * 容器挂在物品自己身上，所以界面里改动会实时写回模块卡的 NBT。
 */
public class MusicModuleMenu extends AbstractContainerMenu {
    /** 歌单的 9 列布局，与大箱子一致。 */
    private static final int COLUMNS = 9;
    /** 歌单区左上角。 */
    private static final int SONGS_X = 8;
    private static final int SONGS_Y = 18;
    /** 玩家背包与快捷栏的纵向位置。 */
    public static final int INVENTORY_Y = 148;
    public static final int HOTBAR_Y = 206;

    private final Container container;
    /** 打开界面时模块卡所在的背包槽；服务端为准，-1 表示未知。 */
    private final int cardSlot;

    public MusicModuleMenu(int id, Inventory inventory, ItemStack stack, int cardSlot,
            java.util.function.Consumer<ItemStack> sink) {
        this(id, inventory, new MusicPlaylistContainer(stack, sink), cardSlot);
    }

    public MusicModuleMenu(int id, Inventory inventory, FriendlyByteBuf buf) {
        this(id, inventory, new MusicPlaylistContainer(readStack(inventory, buf)), -1);
    }

    /** 从网络包里取出模块卡本身：歌单容器是挂在物品上的，必须拿到同一份物品。 */
    private static ItemStack readStack(Inventory inventory, FriendlyByteBuf buf) {
        return ItemStack.STREAM_CODEC.decode(
                new net.minecraft.network.RegistryFriendlyByteBuf(
                        buf, inventory.player.registryAccess()));
    }

    private MusicModuleMenu(int id, Inventory inventory, Container container, int cardSlot) {
        super(CbcMenus.MUSIC_MODULE.get(), id);
        this.container = container;
        this.cardSlot = cardSlot;

        for (int i = 0; i < MusicModuleItem.SLOTS; i++) {
            int col = i % COLUMNS;
            int row = i / COLUMNS;
            addSlot(new Slot(container, i, SONGS_X + col * 18, SONGS_Y + row * 18) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    // 歌单里只收歌曲唱片
                    return isSongDisc(stack);
                }
            });
        }

        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(inventory, col + row * 9 + 9,
                        SONGS_X + col * 18, INVENTORY_Y + row * 18) {
                    @Override
                    public boolean mayPickup(Player player) {
                        // 正在当歌单容器的模块卡不能被拿走：拿走它会让界面里的记录
                        // 与卡上那 54 格脱钩，随后就能把两边的内容各拿一次
                        return !isOpenCard(this.getContainerSlot());
                    }

                    @Override
                    public boolean mayPlace(ItemStack stack) {
                        return !isOpenCard(this.getContainerSlot());
                    }
                });
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(inventory, col, SONGS_X + col * 18, HOTBAR_Y) {
                @Override
                public boolean mayPickup(Player player) {
                    return !isOpenCard(this.getContainerSlot());
                }

                @Override
                public boolean mayPlace(ItemStack stack) {
                    return !isOpenCard(this.getContainerSlot());
                }
            });
        }
    }

    /** 这个背包槽是不是打开界面时那张模块卡。 */
    private boolean isOpenCard(int slot) {
        return cardSlot >= 0 && slot == cardSlot;
    }

    /** 是否为网络音乐机的歌曲唱片。 */
    static boolean isSongDisc(ItemStack stack) {
        return com.mcl.cbtc.integration.netmusic.NetMusic.isSongDisc(stack);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (slot == null || !slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack copy = stack.copy();
        int songs = MusicModuleItem.SLOTS;
        if (index < songs) {
            if (!moveItemStackTo(stack, songs, slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else if (!moveItemStackTo(stack, 0, songs, false)) {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        return copy;
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        // 界面关闭后歌单记录只是卡上数据的一份副本，清掉它避免留存在内存里
        container.clearContent();
    }

    @Override
    public boolean stillValid(Player player) {
        // 歌单数据只存在模块卡里，卡一旦离开原槽位，界面里的记录就与卡脱钩了，
        // 必须让界面失效，否则可以把界面里的唱片拿走而不影响卡上那份，等于复制物品
        if (cardSlot < 0) {
            return true;
        }
        if (cardSlot >= player.getInventory().getContainerSize()) {
            return false;
        }
        ItemStack card = player.getInventory().getItem(cardSlot);
        return card.getItem() instanceof MusicModuleItem && card.getCount() == 1;
    }
}
