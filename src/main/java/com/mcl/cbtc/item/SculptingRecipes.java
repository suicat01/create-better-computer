package com.mcl.cbtc.item;

import com.mcl.cbtc.registry.CbcEntities;
import com.mcl.cbtc.registry.CbcItems;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.allay.Allay;
import net.minecraft.world.entity.monster.Blaze;
import net.minecraft.world.entity.monster.Shulker;
import net.minecraft.world.entity.monster.breeze.Breeze;
import net.minecraft.world.item.ItemStack;

/**
 * 捏塑创生之手的捏塑结果一览。
 * <p>
 * 捏塑逻辑与 JEI 配方展示共用同一张表，避免两处各自维护导致对不上。
 */
public final class SculptingRecipes {
    /** 一种生物被捏塑后得到的物品。 */
    public record Sculpt(EntityType<? extends LivingEntity> entity, ItemStack result) {
    }

    private static final List<Sculpt> ALL = List.of(
            new Sculpt(EntityType.BREEZE, new ItemStack(CbcItems.BREEZE_RADIATOR.get())),
            new Sculpt(EntityType.ALLAY, new ItemStack(CbcItems.ALLAY_SOUND_CARD.get())),
            new Sculpt(EntityType.BLAZE, new ItemStack(CbcItems.BLAZE_COMPUTE_CHIP.get())),
            new Sculpt(CbcEntities.SUPER_BLAZE.get(), new ItemStack(CbcItems.BLAZE_SUPERCOMPUTER_UNIT.get())),
            new Sculpt(EntityType.SHULKER, new ItemStack(CbcItems.SHULKER_CHIP.get())));

    private SculptingRecipes() {
    }

    public static List<Sculpt> all() {
        return ALL;
    }

    /** 目标生物对应要捏塑出的物品；不能捏塑时返回空栈。 */
    public static ItemStack resultFor(LivingEntity target) {
        // 超级烈焰人是烈焰人的子类，必须先判断它，否则会被普通烈焰人分支截走
        if (target instanceof com.mcl.cbtc.entity.SuperBlaze) {
            return new ItemStack(CbcItems.BLAZE_SUPERCOMPUTER_UNIT.get());
        }
        if (target instanceof Breeze) {
            return new ItemStack(CbcItems.BREEZE_RADIATOR.get());
        }
        if (target instanceof Allay) {
            return new ItemStack(CbcItems.ALLAY_SOUND_CARD.get());
        }
        if (target instanceof Blaze) {
            return new ItemStack(CbcItems.BLAZE_COMPUTE_CHIP.get());
        }
        if (target instanceof Shulker) {
            return new ItemStack(CbcItems.SHULKER_CHIP.get());
        }
        return ItemStack.EMPTY;
    }

    /** 生物名称（取各生物自己的译名）。 */
    public static Component displayName(EntityType<? extends LivingEntity> type) {
        return type.getDescription();
    }
}
