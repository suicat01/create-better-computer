package com.mcl.cbtc.registry;

import com.mcl.cbtc.CreateBetterComputer;
import com.mcl.cbtc.entity.SuperBlaze;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.monster.Blaze;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** 生物实体注册。 */
public final class CbcEntities {
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(Registries.ENTITY_TYPE, CreateBetterComputer.MODID);

    public static final DeferredHolder<EntityType<?>, EntityType<SuperBlaze>> SUPER_BLAZE =
            ENTITY_TYPES.register("super_blaze", () -> EntityType.Builder
                    .of(SuperBlaze::new, MobCategory.MONSTER)
                    .fireImmune()
                    .sized(0.6F, 1.8F)
                    .clientTrackingRange(8)
                    .build("super_blaze"));

    private CbcEntities() {
    }

    public static void register(IEventBus modEventBus) {
        ENTITY_TYPES.register(modEventBus);
        modEventBus.addListener(CbcEntities::registerAttributes);
    }

    private static void registerAttributes(EntityAttributeCreationEvent event) {
        // 复用原版烈焰人的属性，并把生命提升到 80、近战伤害提升到 16
        event.put(SUPER_BLAZE.get(), Blaze.createAttributes()
                .add(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH, 80.0)
                .add(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE, 16.0)
                .build());
    }
}
