package com.mcl.cbtc.blockentity;

import com.mcl.cbtc.block.CBComputerBlock;
import com.mcl.cbtc.block.CBExpanderBlock;
import com.mcl.cbtc.menu.ExpanderMenu;
import com.mcl.cbtc.registry.CbcBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
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
 * CB拓展器的方块实体：6 个升级模块槽位，并负责判断自己是否贴紧在电脑背部。
 * <p>
 * "背部贴紧"的判定：拓展器正面朝向的那一格必须是一台 CB 电脑，
 * 且该电脑的正面背对拓展器。
 */
public class ExpanderBlockEntity extends BlockEntity implements Container, net.minecraft.world.MenuProvider {
    /** 升级模块槽位数量。 */
    public static final int SLOTS = 6;

    private final net.minecraft.core.NonNullList<ItemStack> items =
            net.minecraft.core.NonNullList.withSize(SLOTS, ItemStack.EMPTY);

    public ExpanderBlockEntity(BlockPos pos, BlockState state) {
        super(CbcBlockEntities.EXPANDER.get(), pos, state);
    }

    /** 正面朝向。 */
    public Direction facing() {
        BlockState state = getBlockState();
        return state.hasProperty(CBExpanderBlock.FACING)
                ? state.getValue(CBExpanderBlock.FACING)
                : Direction.NORTH;
    }

    /** 正面所贴的那一格是否是一台 CB 电脑。 */
    public boolean isAttached() {
        return attachedComputerPos() != null;
    }

    /** 返回背部贴紧的那台电脑位置；没有连接时返回 null。 */
    @Nullable
    public BlockPos attachedComputerPos() {
        if (level == null) {
            return null;
        }
        BlockPos front = worldPosition.relative(facing());
        BlockState neighbor = level.getBlockState(front);
        if (!(neighbor.getBlock() instanceof CBComputerBlock)) {
            return null;
        }
        // 要求电脑的正面与拓展器朝向一致（即电脑背部朝着拓展器）
        if (!neighbor.hasProperty(CBComputerBlock.FACING)) {
            return null;
        }
        return neighbor.getValue(CBComputerBlock.FACING) == facing() ? front : null;
    }

    public boolean hasStorageModule() {
        return items.stream().anyMatch(stack -> stack.getItem() instanceof com.mcl.cbtc.item.StorageLinkModuleItem);
    }

    /** 是否装了扬声器升级模块卡。 */
    public boolean hasSpeakerModule() {
        return items.stream().anyMatch(stack -> stack.getItem() instanceof com.mcl.cbtc.item.SpeakerModuleItem);
    }

    /** 是否装了硬盘。 */
    public boolean hasHardDrive() {
        return hardDrive() != null;
    }

    /** 第一块已安装的硬盘物品；没装时返回 null。 */
    @Nullable
    public ItemStack hardDrive() {
        for (ItemStack stack : items) {
            if (stack.getItem() instanceof com.mcl.cbtc.item.HardDriveItem) {
                return stack;
            }
        }
        return null;
    }

    /** 已安装的全部网络音乐模块卡，按槽位顺序。 */
    public java.util.List<ItemStack> musicModules() {
        java.util.List<ItemStack> result = new java.util.ArrayList<>();
        for (ItemStack stack : items) {
            if (stack.getItem() instanceof com.mcl.cbtc.item.MusicModuleItem) {
                result.add(stack);
            }
        }
        return result;
    }

    /** 第一块已安装、已绑定网络的仓储链接模块。 */
    @Nullable
    public ItemStack storageLinkModule() {
        for (ItemStack stack : items) {
            if (stack.getItem() instanceof com.mcl.cbtc.item.StorageLinkModuleItem
                    && com.mcl.cbtc.item.StorageLinkModuleItem.network(stack) != null) {
                return stack;
            }
        }
        return null;
    }

    // ---------------------------------------------------------------- 容器

    @Override
    public int getContainerSize() {
        return SLOTS;
    }

    @Override
    public boolean isEmpty() {
        for (ItemStack stack : items) {
            if (!stack.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    @Override
    public ItemStack getItem(int slot) {
        return slot >= 0 && slot < SLOTS ? items.get(slot) : ItemStack.EMPTY;
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
        if (slot < 0 || slot >= SLOTS) {
            return;
        }
        items.set(slot, stack);
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
        return Component.translatable("block.cbtc.expander");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return new ExpanderMenu(id, inventory, this);
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

    /** 把槽位里的模块卡写进物品的方块实体数据，拆下再放回时原样恢复。 */
    public void saveToStack(java.util.List<ItemStack> stacks) {
        if (level == null) {
            return;
        }
        CompoundTag data = new CompoundTag();
        saveAdditional(data, level.registryAccess());
        CompoundTag withId = com.mcl.cbtc.item.BlockEntityCarrierItem.withId(data, CbcBlockEntities.EXPANDER.get());
        for (ItemStack stack : stacks) {
            net.minecraft.world.item.component.CustomData.set(
                    net.minecraft.core.component.DataComponents.BLOCK_ENTITY_DATA, stack, withId);
        }
    }

    /** 模块变化会改变电脑可用的积木，需要刷新编辑者界面。 */
    @Override
    public void setChanged() {
        super.setChanged();
        if (level == null || level.isClientSide) {
            return;
        }
        BlockPos computer = attachedComputerPos();
        if (computer != null && level.getBlockEntity(computer) instanceof CBComputerBlockEntity host) {
            host.onNeighborChanged();
        }
    }
}
