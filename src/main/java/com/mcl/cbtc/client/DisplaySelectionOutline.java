package com.mcl.cbtc.client;

import com.mcl.cbtc.item.DisplayLinkModuleItem;
import com.simibubi.create.api.behaviour.display.DisplayTarget;
import net.createmod.catnip.outliner.Outliner;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** 用机械动力同款 Outliner 绘制选中设备，颜色与线宽沿用显示链接的原始参数。 */
@EventBusSubscriber(modid = "cbtc", value = Dist.CLIENT)
public final class DisplaySelectionOutline {
    private static final String OUTLINE_KEY = "cbtc_display_module_selection";
    private static BlockPos lastPos;
    private static AABB lastBounds;

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.level == null) {
            lastPos = null;
            return;
        }
        for (InteractionHand hand : InteractionHand.values()) {
            var stack = player.getItemInHand(hand);
            if (!(stack.getItem() instanceof DisplayLinkModuleItem)) {
                continue;
            }
            CustomData data = stack.get(DataComponents.CUSTOM_DATA);
            if (data == null) {
                continue;
            }
            CompoundTag selected = data.copyTag().getCompound("SelectedDisplay");
            if (!selected.contains("Pos") || !minecraft.level.dimension().location().toString()
                    .equals(selected.getString("Dim"))) {
                continue;
            }
            BlockPos pos = BlockPos.of(selected.getLong("Pos"));
            if (!pos.equals(lastPos)) {
                DisplayTarget target = DisplayTarget.get(minecraft.level, pos);
                if (target != null) {
                    // 与 DisplayLinkBlockItem 相同：由显示设备提供整个多方块结构的范围
                    lastBounds = target.getMultiblockBounds(minecraft.level, pos);
                } else {
                    VoxelShape shape = minecraft.level.getBlockState(pos).getShape(minecraft.level, pos);
                    lastBounds = (shape.isEmpty() ? new AABB(BlockPos.ZERO) : shape.bounds()).move(pos);
                }
                lastPos = pos;
            }
            Outliner.getInstance().showAABB(OUTLINE_KEY, lastBounds)
                    .colored(0xFFB894)
                    .lineWidth(1 / 16f);
            return;
        }
        lastPos = null;
    }
}
