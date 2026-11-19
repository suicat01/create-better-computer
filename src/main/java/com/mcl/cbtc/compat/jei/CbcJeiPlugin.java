package com.mcl.cbtc.compat.jei;

import com.mcl.cbtc.CreateBetterComputer;
import com.mcl.cbtc.item.SculptingRecipes;
import com.mcl.cbtc.registry.CbcItems;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import mezz.jei.api.registration.IGuiHandlerRegistration;
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

    @Override
    public void registerGuiHandlers(IGuiHandlerRegistration registration) {
        // 瓦列里中士的界面是自绘的，不希望 JEI 在它上面画物品栏 / 配方覆盖层：
        // 返回 null 表示这个界面没有 JEI 可用的 GUI 区域，JEI 会整个跳过它
        registration.addGuiScreenHandler(com.mcl.cbtc.client.sergeant.SergeantScreen.class, screen -> null);
    }
}
