package com.mcl.cbtc.compat.jei;

import com.mcl.cbtc.CreateBetterComputer;
import com.mcl.cbtc.item.SculptingRecipes;
import com.mcl.cbtc.registry.CbcItems;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.gui.widgets.IRecipeExtrasBuilder;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.AbstractRecipeCategory;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

/** JEI 分类：捏塑创生之手能把哪种生物捏塑成什么物品。 */
public class SculptingRecipeCategory extends AbstractRecipeCategory<SculptingRecipes.Sculpt> {
    public static final RecipeType<SculptingRecipes.Sculpt> TYPE = RecipeType.create(
            CreateBetterComputer.MODID, "sculpting", SculptingRecipes.Sculpt.class);

    /** 生物预览方框，画在配方上方。 */
    private static final int ENTITY_X = 1;
    private static final int ENTITY_Y = 2;
    private static final int ENTITY_SIZE = 32;
    /** 生物名称与操作提示。 */
    private static final int NAME_X = 38;
    private static final int NAME_Y = 2;
    private static final int HINT_X = 4;
    private static final int HINT_Y = 20;
    /** 槽位行、箭头与文字的位置。 */
    private static final int ROW_Y = 38;
    private static final int ARROW_X = 46;
    private static final int RESULT_X = 68;

    private final IDrawable background;
    private final Font font;

    public SculptingRecipeCategory(IGuiHelper guiHelper) {
        super(TYPE, Component.translatable("cbtc.jei.sculpting"),
                guiHelper.createDrawableIngredient(VanillaTypes.ITEM_STACK,
                        new ItemStack(CbcItems.SCULPTING_HAND.get())),
                94, 60);
        this.background = guiHelper.createBlankDrawable(getWidth(), getHeight());
        this.font = Minecraft.getInstance().font;
    }

    @Override
    public IDrawable getBackground() {
        return background;
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, SculptingRecipes.Sculpt recipe, IFocusGroup focuses) {
        // 左：捏塑创生之手；中：被捏塑的生物（没有刷怪蛋时只留生物预览）；右：产物
        builder.addInputSlot(1, ROW_Y)
                .addItemStack(new ItemStack(CbcItems.SCULPTING_HAND.get()))
                .setStandardSlotBackground();
        ItemStack egg = SpawnEggLookup.eggOf(recipe.entity());
        if (!egg.isEmpty()) {
            builder.addInputSlot(23, ROW_Y).addItemStack(egg).setStandardSlotBackground();
        }
        builder.addOutputSlot(RESULT_X, ROW_Y).addItemStack(recipe.result()).setOutputSlotBackground();
    }

    @Override
    public boolean isHandled(SculptingRecipes.Sculpt recipe) {
        return true;
    }

    @Override
    public void createRecipeExtras(IRecipeExtrasBuilder builder, SculptingRecipes.Sculpt recipe,
                                   IRecipeSlotsView slots, IFocusGroup focuses) {
        builder.addRecipeArrow(ARROW_X, ROW_Y);
    }

    @Override
    public void draw(SculptingRecipes.Sculpt recipe, IRecipeSlotsView slots, GuiGraphics graphics,
                     double mouseX, double mouseY) {
        drawEntityPreview(graphics, recipe.entity(), ENTITY_X, ENTITY_Y, ENTITY_SIZE);
        Component name = SculptingRecipes.displayName(recipe.entity());
        graphics.drawString(font, name, NAME_X, NAME_Y, 0xFF404040, false);
        Component hint = Component.translatable("cbtc.jei.sculpting.hint");
        graphics.drawString(font, hint, HINT_X, HINT_Y, 0xFF808080, false);
    }

    /** 在方框里画一只固定朝向的生物；拿不到实体实例时什么也不画。 */
    private static void drawEntityPreview(GuiGraphics graphics, EntityType<? extends LivingEntity> type,
                                          int x, int y, int size) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }
        LivingEntity entity = type.create(minecraft.level);
        if (entity == null) {
            return;
        }
        float scale = size / Math.max(1.0F, Math.max(entity.getBbWidth(), entity.getBbHeight()));
        InventoryScreen.renderEntityInInventoryFollowsAngle(graphics, x, y, x + size, y + size,
                (int) (scale * 20), 0.0625F, (float) Math.PI, 0.0F, entity);
    }
}
