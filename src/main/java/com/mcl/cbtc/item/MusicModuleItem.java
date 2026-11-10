package com.mcl.cbtc.item;

import com.mcl.cbtc.menu.MusicModuleMenu;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * 网络音乐模块卡：装入 CB 拓展器后，为电脑提供网络歌曲的播放能力。
 * <p>
 * 自带一个与大箱子同容量（54 格）的歌单，只能放歌曲唱片；放进去的顺序就是歌单顺序。
 * 模块卡自身的 UUID 同时也是这个歌单的名字，积木里按它挑选歌单。
 * <p>
 * 数据都存在物品 NBT 里，因此把卡拆下来装到别处，歌单仍然完整。
 */
public class MusicModuleItem extends UpgradeModuleItem {
    /** 模块卡 UUID 的 NBT 键。 */
    private static final String ID = "MusicModuleId";
    /** 歌单所在的 NBT 键：一个 ListTag，每项对应一格。 */
    private static final String SONGS = "MusicModuleSongs";
    /** 歌单格数：与大箱子一致。 */
    public static final int SLOTS = 54;

    public MusicModuleItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide) {
            // 第一次打开时补上 UUID，之后这张卡就一直代表同一个歌单
            ensureId(stack);
            ItemStack card = stack.copy();
            InteractionHand openHand = hand;
            int cardSlot = hand == InteractionHand.MAIN_HAND
                    ? player.getInventory().selected
                    : net.minecraft.world.entity.player.Inventory.SLOT_OFFHAND;
            net.minecraft.world.MenuProvider provider = new net.minecraft.world.MenuProvider() {
                @Override
                public Component getDisplayName() {
                    return Component.translatable("item.cbtc.music_module");
                }

                @Override
                public net.minecraft.world.inventory.AbstractContainerMenu createMenu(
                        int id, net.minecraft.world.entity.player.Inventory inventory, Player p) {
                    return new MusicModuleMenu(id, inventory, card, cardSlot,
                            updated -> writeBack(p, openHand, updated));
                }
            };
            if (player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
                // 客户端据手里的模块卡重建歌单，因此这里把卡本身作为附加数据发过去
                serverPlayer.openMenu(provider, buf -> ItemStack.STREAM_CODEC.encode(buf, card));
            }
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    /** 把改好的歌单写回玩家手里那张卡；卡已不在手上就什么也不做。 */
    private static void writeBack(Player player, InteractionHand hand, ItemStack updated) {
        ItemStack held = player.getItemInHand(hand);
        if (held.getItem() instanceof MusicModuleItem) {
            player.setItemInHand(hand, updated);
        }
    }

    /** 模块卡的 UUID；没有时生成并写回。 */
    public static UUID ensureId(ItemStack stack) {
        CompoundTag tag = tag(stack);
        if (tag.hasUUID(ID)) {
            return tag.getUUID(ID);
        }
        UUID id = UUID.randomUUID();
        tag.putUUID(ID, id);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        return id;
    }

    /** 模块卡的 UUID；从未打开过时返回 null。 */
    @Nullable
    public static UUID id(ItemStack stack) {
        if (!(stack.getItem() instanceof MusicModuleItem)) {
            return null;
        }
        CompoundTag tag = tag(stack);
        return tag.hasUUID(ID) ? tag.getUUID(ID) : null;
    }

    /**
     * 读出整条歌单。
     * <p>
     * 只保留非空槽位，并保持原有顺序；积木里的"第 N 首"就是按这个顺序数。
     */
    public static List<ItemStack> playlist(ItemStack stack) {
        List<ItemStack> result = new ArrayList<>();
        CompoundTag tag = tag(stack);
        if (!tag.contains(SONGS, Tag.TAG_LIST)) {
            return result;
        }
        ListTag list = tag.getList(SONGS, Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            ItemStack song = ItemStack.parseOptional(
                    net.minecraft.core.RegistryAccess.EMPTY, entry.getCompound("Item"));
            if (!song.isEmpty()) {
                result.add(song);
            }
        }
        return result;
    }

    /** 把容器里的 54 格写回物品 NBT，空槽位会被跳过。 */
    public static void savePlaylist(ItemStack stack, NonNullList<ItemStack> items) {
        CompoundTag tag = tag(stack);
        ListTag list = new ListTag();
        for (ItemStack item : items) {
            if (item.isEmpty()) {
                continue;
            }
            CompoundTag entry = new CompoundTag();
            entry.put("Item", item.save(net.minecraft.core.RegistryAccess.EMPTY));
            list.add(entry);
        }
        tag.put(SONGS, list);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    /** 把物品 NBT 里的歌单读进 54 格容器。 */
    public static void loadInto(ItemStack stack, NonNullList<ItemStack> items) {
        items.clear();
        List<ItemStack> songs = playlist(stack);
        for (int i = 0; i < Math.min(songs.size(), SLOTS); i++) {
            items.set(i, songs.get(i));
        }
    }

    private static CompoundTag tag(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data == null ? new CompoundTag() : data.copyTag();
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return !playlist(stack).isEmpty();
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        UUID id = id(stack);
        tooltip.add(Component.translatable("item.cbtc.music_module.tooltip").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.cbtc.music_module.count", playlist(stack).size())
                .withStyle(ChatFormatting.AQUA));
        if (id != null) {
            tooltip.add(Component.literal("· " + id).withStyle(ChatFormatting.DARK_GRAY));
        }
    }
}
