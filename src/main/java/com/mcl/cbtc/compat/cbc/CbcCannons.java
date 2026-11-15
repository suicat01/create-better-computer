package com.mcl.cbtc.compat.cbc;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;

import com.mcl.cbtc.registry.CbcBlockEntities;
import com.mcl.cbtc.registry.CbcBlocks;
import com.mcl.cbtc.registry.CbcItems;

/**
 * 智能炮座 / 智能固定炮座：Create Big Cannons 火炮底座的复刻版。
 * <p>
 * 方块实体完全继承 CBC 的实现，只把传动轴去掉（{@code hasShaftTowards} 恒为 false），
 * 并用自己的贴图与模型。CBC 是软依赖：未安装时这里不注册任何东西，也不会加载相关类。
 */
public final class CbcCannons {
    public static final String CBC_MODID = "createbigcannons";

    private static final boolean LOADED = ModList.get().isLoaded(CBC_MODID);

    /** 智能炮座（可旋转的火炮底座）。未装 CBC 时为 null。 */
    public static DeferredBlock<SmartCannonMountBlock> SMART_CANNON_MOUNT;
    /** 智能固定炮座。未装 CBC 时为 null。 */
    public static DeferredBlock<SmartFixedCannonMountBlock> SMART_FIXED_CANNON_MOUNT;

    public static DeferredHolder<BlockEntityType<?>, BlockEntityType<SmartCannonMountBlockEntity>> SMART_CANNON_MOUNT_BE;
    public static DeferredHolder<BlockEntityType<?>, BlockEntityType<SmartFixedCannonMountBlockEntity>> SMART_FIXED_CANNON_MOUNT_BE;

    public static DeferredItem<BlockItem> SMART_CANNON_MOUNT_ITEM;
    public static DeferredItem<BlockItem> SMART_FIXED_CANNON_MOUNT_ITEM;

    private CbcCannons() {
    }

    public static boolean loaded() {
        return LOADED;
    }

    /**
     * 在注册事件触发前追加注册。必须在 {@link CbcBlocks#register} 与 {@link CbcItems#register}
     * 之后调用，这样方块/物品/方块实体的延迟注册器都已经在总线上。
     */
    public static void register(IEventBus bus) {
        if (!LOADED) {
            return;
        }
        SMART_CANNON_MOUNT = CbcBlocks.BLOCKS.register("smart_cannon_mount",
                () -> new SmartCannonMountBlock(mountProperties()));
        SMART_FIXED_CANNON_MOUNT = CbcBlocks.BLOCKS.register("smart_fixed_cannon_mount",
                () -> new SmartFixedCannonMountBlock(mountProperties()));

        SMART_CANNON_MOUNT_BE = CbcBlockEntities.TYPES.register("smart_cannon_mount",
                () -> BlockEntityType.Builder
                        .of(SmartCannonMountBlockEntity::new, SMART_CANNON_MOUNT.get())
                        .build(null));
        SMART_FIXED_CANNON_MOUNT_BE = CbcBlockEntities.TYPES.register("smart_fixed_cannon_mount",
                () -> BlockEntityType.Builder
                        .of(SmartFixedCannonMountBlockEntity::new, SMART_FIXED_CANNON_MOUNT.get())
                        .build(null));

        SMART_CANNON_MOUNT_ITEM = CbcItems.ITEMS.registerSimpleBlockItem(SMART_CANNON_MOUNT);
        SMART_FIXED_CANNON_MOUNT_ITEM = CbcItems.ITEMS.registerSimpleBlockItem(SMART_FIXED_CANNON_MOUNT);
    }

    /** 方块属性和 CBC 的火炮底座保持一致。 */
    private static BlockBehaviour.Properties mountProperties() {
        return BlockBehaviour.Properties.of()
                .mapColor(MapColor.METAL)
                .strength(3.0F, 6.0F)
                .sound(SoundType.METAL)
                .requiresCorrectToolForDrops()
                .noOcclusion();
    }

    /** 该方块状态是不是智能炮座。 */
    public static boolean isSmartMount(BlockState state) {
        return SMART_CANNON_MOUNT != null && state.is(SMART_CANNON_MOUNT.get());
    }

    /** 该方块状态是不是智能固定炮座。 */
    public static boolean isSmartFixedMount(BlockState state) {
        return SMART_FIXED_CANNON_MOUNT != null && state.is(SMART_FIXED_CANNON_MOUNT.get());
    }
}
