package com.mcl.cbtc.compat;

import com.mcl.cbtc.CreateBetterComputer;
import com.mcl.cbtc.registry.CbcItems;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.client.CuriosRendererRegistry;
import top.theillusivec4.curios.api.client.ICurioRenderer;

/**
 * 枪炮手的瞄准组件在饰品栏里的头部渲染。
 * <p>
 * 做法与机械动力的工程师护目镜一致：借一个只含空头部部件的 {@link HumanoidModel} 取得头部旋转，
 * 再用物品的 HEAD 显示上下文把物品模型画在头上。只有装了饰品栏模组才会加载本类。
 */
public class GunnerSightCurioRenderer implements ICurioRenderer {
    /** 只用来取头部姿态的空模型层。 */
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(
            ResourceLocation.fromNamespaceAndPath(CreateBetterComputer.MODID, "gunner_sight"), "gunner_sight");

    private final HumanoidModel<LivingEntity> model;

    public GunnerSightCurioRenderer(ModelPart root) {
        this.model = new HumanoidModel<>(root);
    }

    /** 头部部件留空，这样模型本身不画任何东西，只提供位置与旋转。 */
    public static MeshDefinition mesh() {
        MeshDefinition mesh = HumanoidModel.createMesh(CubeDeformation.NONE, 0.0F);
        mesh.getRoot().addOrReplaceChild("head", new CubeListBuilder(), PartPose.ZERO);
        return mesh;
    }

    /** 把本渲染器挂到瞄准组件上。 */
    public static void register() {
        CuriosRendererRegistry.register(CbcItems.GUNNER_SIGHT.get(),
                () -> new GunnerSightCurioRenderer(
                        Minecraft.getInstance().getEntityModels().bakeLayer(LAYER)));
    }

    @Override
    public <T extends LivingEntity, M extends EntityModel<T>> void render(ItemStack stack, SlotContext slotContext,
            PoseStack poseStack, RenderLayerParent<T, M> renderLayerParent, MultiBufferSource bufferSource,
            int packedLight, float limbSwing, float limbSwingAmount, float partialTicks, float ageInTicks,
            float netHeadYaw, float headPitch) {
        LivingEntity entity = slotContext.entity();
        model.setupAnim(entity, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch);
        model.prepareMobModel(entity, limbSwing, limbSwingAmount, partialTicks);
        ICurioRenderer.followHeadRotations(entity, model.head);

        ModelPart head = model.head;
        poseStack.pushPose();
        poseStack.translate(head.x / 16.0, head.y / 16.0, head.z / 16.0);
        poseStack.mulPose(Axis.ZP.rotation(head.zRot));
        poseStack.mulPose(Axis.YP.rotation(head.yRot));
        poseStack.mulPose(Axis.XP.rotation(head.xRot));
        // 与原版戴在头上的方块物品一致：下移 1/4 格、上下翻转、缩放到 0.625
        poseStack.translate(0.0, -0.25, 0.0);
        poseStack.mulPose(Axis.ZP.rotationDegrees(180.0F));
        poseStack.scale(0.625F, 0.625F, 0.625F);

        Minecraft minecraft = Minecraft.getInstance();
        minecraft.getItemRenderer().renderStatic(stack, ItemDisplayContext.HEAD, packedLight,
                OverlayTexture.NO_OVERLAY, poseStack, bufferSource, minecraft.level, 0);
        poseStack.popPose();
    }
}
