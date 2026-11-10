package com.mcl.cbtc.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import java.util.List;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;
import org.joml.Matrix4f;

/**
 * 把默念文字画在发声方块上方。
 * <p>
 * 必须做成方块实体渲染器而不是世界坐标绘制：设备装在 Sable 物理结构上时，
 * Sable 渲染子世界方块实体的那一刻已经把结构姿态（位置、倾角）压进 poseStack，
 * 在这里只管相对方块自己的坐标画字，文字就会随结构移动和倾斜，
 * 与网络音乐机歌词完全一致（它也是在 {@code MusicPlayerRenderer.renderLyric} 里画的）。
 * <p>
 * 朝向与缩放照搬歌词：以相机 yaw/pitch 反向旋转，使文字面朝屏幕，再按 -0.025 缩放。
 */
public class MurmurRenderer<T extends BlockEntity> implements BlockEntityRenderer<T> {
    /** 文字相对方块角落的锚点：方块正上方，与歌词一致。 */
    private static final double ANCHOR_X = 0.5;
    private static final double ANCHOR_Y = 1.625;
    private static final double ANCHOR_Z = 0.5;

    private final Font font;

    public MurmurRenderer(BlockEntityRendererProvider.Context context) {
        this.font = context.getFont();
    }

    @Override
    public void render(T blockEntity, float partialTick, PoseStack poseStack, MultiBufferSource buffer,
                       int packedLight, int packedOverlay) {
        List<MurmurTexts.Entry> entries = MurmurTexts.at(blockEntity.getBlockPos());
        if (entries.isEmpty()) {
            return;
        }
        Camera camera = Minecraft.getInstance().gameRenderer.getMainCamera();
        int background = (int) (Minecraft.getInstance().options.getBackgroundOpacity(0.25F) * 255.0F) << 24;
        for (MurmurTexts.Entry entry : entries) {
            poseStack.pushPose();
            poseStack.translate(ANCHOR_X, ANCHOR_Y, ANCHOR_Z);
            poseStack.mulPose(Axis.YN.rotationDegrees(camera.getYRot()));
            poseStack.mulPose(Axis.XN.rotationDegrees(-camera.getXRot()));
            poseStack.scale(-0.025F, -0.025F, 0.025F);

            Matrix4f matrix = poseStack.last().pose();
            Component line = Component.literal(entry.text());
            float x = -font.width(line) / 2.0F;
            font.drawInBatch(line, x, 0, 0xFFFFFFFF, false,
                    matrix, buffer, Font.DisplayMode.NORMAL,
                    background, LightTexture.FULL_BRIGHT);
            poseStack.popPose();
        }
    }

    /** 文字飘在方块外，不走常规视锥裁剪，避免转头就消失。 */
    @Override
    public boolean shouldRenderOffScreen(T blockEntity) {
        return true;
    }

    /** 文字比方块本体高，渲染包围盒要放大，否则会被区块裁剪掉。 */
    @Override
    public AABB getRenderBoundingBox(T blockEntity) {
        return new AABB(blockEntity.getBlockPos()).inflate(2.0);
    }
}
