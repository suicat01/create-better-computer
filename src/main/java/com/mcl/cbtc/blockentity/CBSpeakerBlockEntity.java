package com.mcl.cbtc.blockentity;

import com.mcl.cbtc.menu.SpeakerMenu;
import com.mcl.cbtc.registry.CbcBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * CB音响的方块实体：一个升级槽，只接受扬声器升级模块。
 * <p>
 * 传播范围只由音响自己决定：没装升级是 {@link #RANGE_BASE} 格，
 * 装了扬声器升级是 {@link #RANGE_UPGRADED} 格。
 * 电脑自己装的扬声器模块不会影响这里，两者互不相干。
 */
public class CBSpeakerBlockEntity extends BlockEntity implements Container, net.minecraft.world.MenuProvider, SoundDevice {
    /** 未升级时的声音传播范围（格）。 */
    public static final int RANGE_BASE = 20;
    /** 装入扬声器升级后的传播范围（格）。 */
    public static final int RANGE_UPGRADED = 80;
    /** 升级槽数量。 */
    public static final int SLOTS = 1;

    private final NonNullList<ItemStack> items =
            NonNullList.withSize(SLOTS, ItemStack.EMPTY);

    public CBSpeakerBlockEntity(BlockPos pos, BlockState state) {
        super(CbcBlockEntities.SPEAKER.get(), pos, state);
    }

    /** 是否装了扬声器升级。 */
    public boolean hasSpeakerUpgrade() {
        return items.get(0).getItem() instanceof com.mcl.cbtc.item.SpeakerModuleItem;
    }

    /** 当前的声音传播范围（格）。 */
    @Override
    public int soundRange() {
        return hasSpeakerUpgrade() ? RANGE_UPGRADED : RANGE_BASE;
    }

    // ---------------------------------------------------------------- 容器

    @Override
    public int getContainerSize() {
        return SLOTS;
    }

    @Override
    public boolean isEmpty() {
        return items.get(0).isEmpty();
    }

    @Override
    public ItemStack getItem(int slot) {
        return slot == 0 ? items.get(0) : ItemStack.EMPTY;
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
        return ContainerHelper.takeItem(items, slot);
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        if (slot != 0) {
            return;
        }
        items.set(0, stack);
        if (stack.getCount() > getMaxStackSize()) {
            stack.setCount(getMaxStackSize());
        }
        setChanged();
    }

    @Override
    public boolean stillValid(Player player) {
        return level != null && level.getBlockEntity(worldPosition) == this
                && player.distanceToSqr(worldPosition.getCenter()) <= 64;
    }

    @Override
    public void clearContent() {
        items.clear();
        setChanged();
    }

    // ---------------------------------------------------------------- 菜单

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.cbtc.speaker");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return new SpeakerMenu(id, inventory, this);
    }

    // ---------------------------------------------------------------- 存档

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ContainerHelper.saveAllItems(tag, items, registries);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        items.clear();
        ContainerHelper.loadAllItems(tag, items, registries);
    }

    /** 把升级槽里的模块卡写进物品的方块实体数据，拆下再放回时原样恢复。 */
    public void saveToStack(java.util.List<ItemStack> stacks) {
        if (level == null) {
            return;
        }
        CompoundTag data = new CompoundTag();
        saveAdditional(data, level.registryAccess());
        CompoundTag withId = com.mcl.cbtc.item.BlockEntityCarrierItem.withId(data, CbcBlockEntities.SPEAKER.get());
        for (ItemStack stack : stacks) {
            net.minecraft.world.item.component.CustomData.set(
                    net.minecraft.core.component.DataComponents.BLOCK_ENTITY_DATA, stack, withId);
        }
    }
}
