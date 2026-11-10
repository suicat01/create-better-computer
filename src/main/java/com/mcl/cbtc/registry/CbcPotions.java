package com.mcl.cbtc.registry;

import com.mcl.cbtc.CreateBetterComputer;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.alchemy.Potion;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** 超级烈焰粉酿造的强化力量药水。 */
public final class CbcPotions {
    public static final DeferredRegister<Potion> POTIONS =
            DeferredRegister.create(Registries.POTION, CreateBetterComputer.MODID);

    // 力量 III，3:00
    public static final DeferredHolder<Potion, Potion> STRENGTH_3 = POTIONS.register("strength_3",
            () -> new Potion("strength_3", new MobEffectInstance(MobEffects.DAMAGE_BOOST, 3600, 2)));

    // 红石延长后的力量 III，8:00
    public static final DeferredHolder<Potion, Potion> LONG_STRENGTH_3 = POTIONS.register("long_strength_3",
            () -> new Potion("strength_3", new MobEffectInstance(MobEffects.DAMAGE_BOOST, 9600, 2)));

    // 萤石强化后的力量 IV，1:30
    public static final DeferredHolder<Potion, Potion> STRONG_STRENGTH_3 = POTIONS.register("strong_strength_3",
            () -> new Potion("strength_4", new MobEffectInstance(MobEffects.DAMAGE_BOOST, 1800, 3)));

    private CbcPotions() {
    }

    public static void register(IEventBus modEventBus) {
        POTIONS.register(modEventBus);
    }
}
