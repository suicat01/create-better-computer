package com.mcl.cbtc.registry;

import com.mcl.cbtc.CreateBetterComputer;
import com.mcl.cbtc.recipe.CharmCompassBindRecipe;
import com.mcl.cbtc.recipe.TargetLocatorBindRecipe;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** 本模组自定义配方序列化器。 */
public final class CbcRecipes {
    public static final DeferredRegister<RecipeSerializer<?>> SERIALIZERS =
            DeferredRegister.create(Registries.RECIPE_SERIALIZER, CreateBetterComputer.MODID);

    /** 工作台绑定：目标定位模块 + 外设绑定模块卡。 */
    public static final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<TargetLocatorBindRecipe>>
            TARGET_LOCATOR_BIND = SERIALIZERS.register("target_locator_bind",
                    () -> new RecipeSerializer<TargetLocatorBindRecipe>() {
                        @Override
                        public MapCodec<TargetLocatorBindRecipe> codec() {
                            return MapCodec.unit(TargetLocatorBindRecipe.INSTANCE);
                        }

                        @Override
                        public StreamCodec<RegistryFriendlyByteBuf, TargetLocatorBindRecipe> streamCodec() {
                            return StreamCodec.unit(TargetLocatorBindRecipe.INSTANCE);
                        }
                    });

    /** 工作台绑定：指南针 + 磁石挂件 → 绑定指南针。 */
    public static final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<CharmCompassBindRecipe>>
            CHARM_COMPASS_BIND = SERIALIZERS.register("charm_compass_bind",
                    () -> new RecipeSerializer<CharmCompassBindRecipe>() {
                        @Override
                        public MapCodec<CharmCompassBindRecipe> codec() {
                            return MapCodec.unit(CharmCompassBindRecipe.INSTANCE);
                        }

                        @Override
                        public StreamCodec<RegistryFriendlyByteBuf, CharmCompassBindRecipe> streamCodec() {
                            return StreamCodec.unit(CharmCompassBindRecipe.INSTANCE);
                        }
                    });

    private CbcRecipes() {
    }

    public static void register(IEventBus bus) {
        SERIALIZERS.register(bus);
    }
}
