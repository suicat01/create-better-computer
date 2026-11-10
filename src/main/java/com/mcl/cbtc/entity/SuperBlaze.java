package com.mcl.cbtc.entity;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Blaze;
import net.minecraft.world.level.Level;

/**
 * 超级烈焰人：由烈焰人改色而来的独立生物。
 * 暂时完全沿用原版烈焰人的行为与动画，后续再单独调整。
 */
public class SuperBlaze extends Blaze {
    public SuperBlaze(EntityType<? extends Blaze> type, Level level) {
        super(type, level);
    }
}
