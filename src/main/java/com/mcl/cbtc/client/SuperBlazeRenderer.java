package com.mcl.cbtc.client;

import com.mcl.cbtc.CreateBetterComputer;
import com.mcl.cbtc.entity.SuperBlaze;
import net.minecraft.client.model.BlazeModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;

/**
 * 超级烈焰人渲染：模型与动画直接复用原版烈焰人，只替换贴图。
 */
public class SuperBlazeRenderer extends MobRenderer<SuperBlaze, BlazeModel<SuperBlaze>> {
    private static final ResourceLocation TEXTURE =
            CreateBetterComputer.asResource("textures/entity/super_blaze.png");

    public SuperBlazeRenderer(EntityRendererProvider.Context context) {
        super(context, new BlazeModel<>(context.bakeLayer(ModelLayers.BLAZE)), 0.5F);
    }

    @Override
    protected int getBlockLightLevel(SuperBlaze entity, BlockPos pos) {
        // 与烈焰人一致：自发光，全亮度渲染
        return 15;
    }

    @Override
    public ResourceLocation getTextureLocation(SuperBlaze entity) {
        return TEXTURE;
    }
}
