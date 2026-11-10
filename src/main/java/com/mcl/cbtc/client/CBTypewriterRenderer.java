package com.mcl.cbtc.client;

import com.mcl.cbtc.blockentity.CBTypewriterBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.simibubi.create.foundation.blockEntity.renderer.SmartBlockEntityRenderer;
import dev.engine_room.flywheel.lib.transform.TransformStack;
import dev.simulated_team.simulated.index.SimPartialModels;
import java.util.ArrayList;
import java.util.List;
import net.createmod.catnip.animation.AnimationTickHolder;
import net.createmod.catnip.animation.LerpedFloat;
import net.createmod.catnip.math.AngleHelper;
import net.createmod.catnip.render.CachedBuffers;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/** 与航空学打字机一致的键帽渲染，动画状态完全属于 CB 打字机. */
public class CBTypewriterRenderer extends SmartBlockEntityRenderer<CBTypewriterBlockEntity> {
    private static final List<LerpedFloat> KEYS = new ArrayList<>(14);

    static {
        for (int i = 0; i < 14; i++) {
            KEYS.add(LerpedFloat.linear().startWithValue(0));
        }
    }

    public CBTypewriterRenderer(BlockEntityRendererProvider.Context context) {
        super(context);
    }

    public static void tick() {
        if (Minecraft.getInstance().isPaused() || !TypewriterKeyInput.isActive()) {
            return;
        }
        for (int i = 0; i < KEYS.size(); i++) {
            LerpedFloat key = KEYS.get(i);
            key.chase(TypewriterKeyInput.isKeyCapPressed(i) ? 1 : 0, 0.4, LerpedFloat.Chaser.EXP);
            key.tickChaser();
        }
    }

    public static void resetKeys() {
        for (LerpedFloat key : KEYS) {
            key.startWithValue(0);
        }
    }

    @Override
    protected void renderSafe(CBTypewriterBlockEntity be, float partialTicks, PoseStack ms,
            MultiBufferSource buffer, int light, int overlay) {
        super.renderSafe(be, partialTicks, ms, buffer, light, overlay);
        VertexConsumer vertices = buffer.getBuffer(RenderType.cutout());
        BlockState state = be.getBlockState();
        Direction facing = state.getValue(BlockStateProperties.HORIZONTAL_FACING);
        var pose = TransformStack.of(ms);
        float partial = AnimationTickHolder.getPartialTicks();
        pose.translate(0.5, 0.25, 0.5);
        pose.rotateYDegrees(AngleHelper.horizontalAngle(facing));
        pose.pushPose();
        pose.translate(-0.4375, 0.0625, 0.125);
        pose.pushPose();
        int index = 0;
        for (int i = 0; i < 6; i++) {
            pose.translate(0.125, 0, 0);
            renderKey(ms, vertices, light, partial, state, be, index++, false);
        }
        ms.popPose();
        pose.translate(-0.0625, -0.0625, 0.125);
        pose.pushPose();
        for (int i = 0; i < 7; i++) {
            pose.translate(0.125, 0, 0);
            renderKey(ms, vertices, light, partial, state, be, index++, false);
        }
        ms.popPose();
        pose.translate(0.5, -0.0625, 0.125);
        pose.pushPose();
        renderKey(ms, vertices, light, partial, state, be, index, true);
        ms.popPose();
        ms.popPose();
    }

    private static void renderKey(PoseStack ms, VertexConsumer vertices, int light, float partial,
            BlockState state, CBTypewriterBlockEntity be, int index, boolean spacebar) {
        ms.pushPose();
        if (be.checkUser(Minecraft.getInstance().player.getUUID())) {
            ms.translate(0, -0.046875 * KEYS.get(index).getValue(partial), 0);
        }
        CachedBuffers.partial(spacebar ? SimPartialModels.LINKED_TYPEWRITER_KEY_SPACEBAR
                : SimPartialModels.LINKED_TYPEWRITER_KEY, state).light(light).renderInto(ms, vertices);
        ms.popPose();
    }
}
