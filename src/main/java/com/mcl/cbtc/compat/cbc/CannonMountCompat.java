package com.mcl.cbtc.compat.cbc;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.items.IItemHandler;

/**
 * 火控单位（智能炮座 / 智能固定炮座）对外的操作入口。
 * <p>
 * 只有确认装了 CBC、且方块实体确实是本模组的智能炮座时才会加载本类，
 * 未装 CBC 时不会触碰任何炮座类型。
 */
public final class CannonMountCompat {
    /** 机炮炮弹标签。 */
    private static final TagKey<Item> AUTOCANNON_CARTRIDGES = tag("autocannon_cartridges");
    /** 大炮炮弹标签。 */
    private static final TagKey<Item> BIG_CANNON_PROJECTILES = tag("big_cannon_projectiles");

    private CannonMountCompat() {
    }

    private static TagKey<Item> tag(String path) {
        return TagKey.create(Registries.ITEM,
                ResourceLocation.fromNamespaceAndPath(CbcCannons.CBC_MODID, path));
    }

    /** 方块实体是不是本模组的智能炮座（可旋转或固定式）。 */
    public static boolean isSmartMount(BlockEntity be) {
        return be instanceof SmartCannonMountBlockEntity || be instanceof SmartFixedCannonMountBlockEntity;
    }

    /** 让炮座瞄准世界坐标中的一点。 */
    public static void aim(BlockEntity be, Vec3 target) {
        if (be instanceof SmartCannonMountBlockEntity mount) {
            mount.cbtc$aimAt(target);
        } else if (be instanceof SmartFixedCannonMountBlockEntity mount) {
            mount.cbtc$aimAt(target);
        }
    }

    /** 炮座内可用的炮弹总数。 */
    public static int ammo(BlockEntity be) {
        if (be instanceof SmartCannonMountBlockEntity mount) {
            return mount.cbtc$ammo();
        }
        if (be instanceof SmartFixedCannonMountBlockEntity mount) {
            return mount.cbtc$ammo();
        }
        return 0;
    }

    /** 统计炮内物品栏里的炮弹数量；机炮炮弹与大炮炮弹都算。 */
    static int countAmmo(IItemHandler handler) {
        if (handler == null) {
            return 0;
        }
        int total = 0;
        for (int slot = 0; slot < handler.getSlots(); slot++) {
            ItemStack stack = handler.getStackInSlot(slot);
            if (stack.isEmpty()) {
                continue;
            }
            if (stack.is(AUTOCANNON_CARTRIDGES) || stack.is(BIG_CANNON_PROJECTILES)) {
                total += stack.getCount();
            }
        }
        return total;
    }
}
