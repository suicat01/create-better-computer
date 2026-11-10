package com.mcl.cbtc.compat.jei;

import com.mcl.cbtc.CreateBetterComputer;
import com.mcl.cbtc.item.SculptingRecipes;
import com.mcl.cbtc.registry.CbcItems;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import net.minecraft.resources.ResourceLocation;

/** JEI 集成：把捏塑创生之手的捏塑表展示成可查询的配方。 */
@JeiPlugin
public class CbcJeiPlugin implements IModPlugin {
    private static final ResourceLocation UID = CreateBetterComputer.asResource("jei_plugin");

    @Override
    public ResourceLocation getPluginUid() {
        return UID;
    }

    @Override
    public void registerCategories(IRecipeCategoryRegistration registration) {
        registration.addRecipeCategories(new SculptingRecipeCategory(registration.getJeiHelpers().getGuiHelper()));
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        registration.addRecipes(SculptingRecipeCategory.TYPE, SculptingRecipes.all());
    }

    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        registration.addRecipeCatalyst(CbcItems.SCULPTING_HAND.get(), SculptingRecipeCategory.TYPE);
    }
}
