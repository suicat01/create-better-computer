package com.mcl.cbtc.client;

import com.mcl.cbtc.compat.cbc.SmartCannonMountBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.createmod.catnip.animation.AnimationTickHolder;
import net.createmod.catnip.render.CachedBuffers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.joml.Quaternionf;
import rbasamoyai.createbigcannons.index.CBCBlockPartials;

/**
 * 智能炮座的动态部件渲染器：旋转炮架与炮架轴。
 * <p>
 * 直接照搬 CBC 的 {@code CannonMountBlockEntityRenderer}，但去掉了两处传动轴绘制
 * （纵向的 SHAFT_HALF 与沿旋转轴的整根传动轴），这就是"没有传动杆"的视觉差异。
 */
public final class SmartCannonMountRenderer extends com.simibubi.create.foundation.blockEntity.renderer.SafeBlockEntityRenderer<SmartCannonMountBlockEntity> {
    public SmartCannonMountRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    protected void renderSafe(SmartCannonMountBlockEntity blockEntity, float partialTicks, PoseStack poseStack,
                              MultiBufferSource bufferSource, int light, int overlay) {
        Level level = blockEntity.getLevel();
        if (level == null) {
            return;
        }
        BlockState state = blockEntity.getBlockState();
        boolean upsideDown = state.getValue(BlockStateProperties.VERTICAL_DIRECTION) == Direction.UP;
        VertexConsumer consumer = bufferSource.getBuffer(RenderType.solid());

        float yaw = blockEntity.getYawOffset(AnimationTickHolder.getPartialTicks(level) - 1.0F);
        Quaternionf yawRotation = upsideDown
                ? Axis.ZP.rotationDegrees(180.0F).mul(Axis.YP.rotationDegrees(yaw))
                : Axis.YP.rotationDegrees(-yaw);
        poseStack.pushPose();
        CachedBuffers.partial(SmartCannonMountModels.ROTATING_MOUNT, state)
                .translate(0.0D, upsideDown ? -1.0D : 1.0D, 0.0D)
                .light(light)
                .rotateCentered(yawRotation)
                .renderInto(poseStack, consumer);

        float pitch = blockEntity.getPitchOffset(partialTicks - 1.0F);
        Quaternionf pitchRotation = upsideDown
                ? Axis.XP.rotationDegrees(pitch)
                : Axis.XP.rotationDegrees(-pitch);
        Quaternionf combined = new Quaternionf(yawRotation).mul(pitchRotation);
        CachedBuffers.partialFacing(CBCBlockPartials.CANNON_CARRIAGE_AXLE, state, Direction.NORTH)
                .translate(0.0D, upsideDown ? -2.0D : 2.0D, 0.0D)
                .rotateCentered(combined)
                .light(light)
                .renderInto(poseStack, consumer);
        poseStack.popPose();
    }
}
