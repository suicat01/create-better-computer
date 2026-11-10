package com.mcl.cbtc.client;

import net.createmod.catnip.outliner.Outliner;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * 外设模块卡选中方块时，用机械动力同款 Outliner 画出选中框（与屏幕显示模块同一套样式）。
 * <p>
 * 只在"外设绑定模块卡已选中某个外设"期间出现，主要用于确认到底选中了哪一台。
 */
@EventBusSubscriber(modid = com.mcl.cbtc.CreateBetterComputer.MODID, value = Dist.CLIENT)
public final class DeviceSelectionOutline {
    private static final String OUTLINE_KEY = "cbtc_device_module_selection";

    private DeviceSelectionOutline() {
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        BlockPos pos = DeviceSelection.selected();
        Minecraft minecraft = Minecraft.getInstance();
        if (pos == null || minecraft.level == null) {
            return;
        }
        if (!minecraft.level.dimension().location().equals(DeviceSelection.dimension())) {
            return;
        }
        VoxelShape shape = minecraft.level.getBlockState(pos).getShape(minecraft.level, pos);
        AABB bounds = (shape.isEmpty() ? new AABB(BlockPos.ZERO) : shape.bounds()).move(pos);
        // 颜色与线宽沿用显示链接模块的选中框
        Outliner.getInstance().showAABB(OUTLINE_KEY, bounds)
                .colored(0xFFB894)
                .lineWidth(1 / 16f);
    }
}
