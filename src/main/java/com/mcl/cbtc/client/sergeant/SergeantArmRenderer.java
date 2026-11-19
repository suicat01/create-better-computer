package com.mcl.cbtc.client.sergeant;

import com.mcl.cbtc.blockentity.SergeantValeryBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.simibubi.create.content.kinetics.base.KineticBlockEntityRenderer;
import com.simibubi.create.content.kinetics.mechanicalArm.ArmBlock;
import com.simibubi.create.content.kinetics.mechanicalArm.ArmRenderer;
import dev.engine_room.flywheel.lib.transform.PoseTransformStack;
import dev.engine_room.flywheel.lib.transform.TransformStack;
import net.createmod.catnip.data.Iterate;
import net.createmod.catnip.render.CachedBuffers;
import net.createmod.catnip.render.SuperByteBuffer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 瓦列里中士的渲染器。
 * <p>
 * 逐行照搬 Create 的 {@code ArmRenderer}：角度、底座/下臂/上臂/爪子四段变换、爪尖开合、
 * 以及夹着物品时的物品渲染都保持一致，只把 {@code AllPartialModels} 换成带美术贴图的
 * {@link SergeantArmModels}；下方齿轮由 {@link #getRotatedModel} 交给动力渲染基类绘制。
 * <p>
 * 这里始终走方块实体渲染，不注册 Flywheel 可视化器，避免两套渲染系统重复绘制。
 */
public final class SergeantArmRenderer extends KineticBlockEntityRenderer<SergeantValeryBlockEntity> {
    public SergeantArmRenderer(BlockEntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    protected void renderSafe(SergeantValeryBlockEntity be, float partialTicks, PoseStack ms,
                              MultiBufferSource buffer, int light, int overlay) {
        // 不能调 super：动力渲染基类在装了 Flywheel 时会跳过传动部件，指望可视化器去画，
        // 而本方块没有注册可视化器，齿轮就会消失。这里始终自己画下方齿轮。
        renderRotatingBuffer(be, getRotatedModel(be, be.getBlockState()), ms,
                buffer.getBuffer(getRenderType(be, be.getBlockState())), light);

        SergeantArmAccess access = (SergeantArmAccess) (Object) be;
        ItemStack item = access.cbtc$heldItem();
        boolean hasItem = !item.isEmpty();
        boolean goggles = access.cbtc$goggles();

        ItemRenderer itemRenderer = Minecraft.getInstance().getItemRenderer();
        BakedModel bakedModel = itemRenderer.getModel(item, be.getLevel(), null, 0);
        boolean isBlockItem = hasItem && item.getItem() instanceof BlockItem && bakedModel.isGui3d();
        VertexConsumer builder = buffer.getBuffer(goggles ? RenderType.cutout() : RenderType.solid());
        BlockState state = be.getBlockState();

        PoseStack msLocal = new PoseStack();
        PoseTransformStack msr = TransformStack.of(msLocal);
        boolean inverted = state.getValue(ArmBlock.CEILING);

        float baseAngle = access.cbtc$baseAngle(partialTicks);
        float lowerArmAngle = access.cbtc$lowerArmAngle(partialTicks) - 135.0F;
        float upperArmAngle = access.cbtc$upperArmAngle(partialTicks) - 90.0F;
        float headAngle = access.cbtc$headAngle(partialTicks);

        msr.center();
        if (inverted) {
            msr.rotateXDegrees(180.0F);
        }

        renderArm(builder, ms, msLocal, msr, state, 0xFFFFFF,
                baseAngle, lowerArmAngle, upperArmAngle, headAngle,
                goggles, inverted && goggles, hasItem, isBlockItem, light);

        if (hasItem) {
            ms.pushPose();
            float itemScale = isBlockItem ? 0.5F : 0.625F;
            msr.rotateXDegrees(90.0F);
            msLocal.translate(0.0F, isBlockItem ? -0.5625F : -0.625F, 0.0F);
            msLocal.scale(itemScale, itemScale, itemScale);
            ms.last().pose().mul(msLocal.last().pose());
            itemRenderer.render(item, ItemDisplayContext.FIXED, false, ms, buffer, light, overlay, bakedModel);
            ms.popPose();
        }
    }

    private void renderArm(VertexConsumer builder, PoseStack ms, PoseStack msLocal, TransformStack msr,
                           BlockState blockState, int color, float baseAngle, float lowerArmAngle,
                           float upperArmAngle, float headAngle, boolean goggles, boolean inverted,
                           boolean hasItem, boolean isBlockItem, int light) {
        SuperByteBuffer base = CachedBuffers.partial(SergeantArmModels.BASE, blockState).light(light);
        SuperByteBuffer lowerBody = CachedBuffers.partial(SergeantArmModels.LOWER_BODY, blockState).light(light);
        SuperByteBuffer upperBody = CachedBuffers.partial(SergeantArmModels.UPPER_BODY, blockState).light(light);
        SuperByteBuffer claw = CachedBuffers.partial(
                goggles ? SergeantArmModels.CLAW_BASE_GOGGLES : SergeantArmModels.CLAW_BASE, blockState).light(light);
        SuperByteBuffer upperClawGrip = CachedBuffers.partial(SergeantArmModels.CLAW_GRIP_UPPER, blockState).light(light);
        SuperByteBuffer lowerClawGrip = CachedBuffers.partial(SergeantArmModels.CLAW_GRIP_LOWER, blockState).light(light);

        ArmRenderer.transformBase(msr, baseAngle);
        base.transform(msLocal).renderInto(ms, builder);
        ArmRenderer.transformLowerArm(msr, lowerArmAngle);
        lowerBody.color(color).transform(msLocal).renderInto(ms, builder);
        ArmRenderer.transformUpperArm(msr, upperArmAngle);
        upperBody.color(color).transform(msLocal).renderInto(ms, builder);
        ArmRenderer.transformHead(msr, headAngle);
        if (inverted) {
            msr.rotateZDegrees(180.0F);
        }
        claw.transform(msLocal).renderInto(ms, builder);
        if (inverted) {
            msr.rotateZDegrees(180.0F);
        }
        for (int flip : Iterate.positiveAndNegative) {
            msLocal.pushPose();
            ArmRenderer.transformClawHalf(msr, hasItem, isBlockItem, flip);
            (flip > 0 ? lowerClawGrip : upperClawGrip).transform(msLocal).renderInto(ms, builder);
            msLocal.popPose();
        }
    }

    @Override
    protected SuperByteBuffer getRotatedModel(SergeantValeryBlockEntity be, BlockState state) {
        return CachedBuffers.partial(SergeantArmModels.COG, state);
    }

    @Override
    public boolean shouldRenderOffScreen(SergeantValeryBlockEntity be) {
        return true;
    }
}
