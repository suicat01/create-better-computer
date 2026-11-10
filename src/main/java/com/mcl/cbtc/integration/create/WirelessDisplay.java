package com.mcl.cbtc.integration.create;

import com.simibubi.create.api.behaviour.display.DisplayTarget;
import com.simibubi.create.content.redstone.displayLink.DisplayLinkBlockEntity;
import com.simibubi.create.content.redstone.displayLink.DisplayLinkContext;
import com.simibubi.create.content.redstone.nixieTube.NixieTubeBlock;
import com.simibubi.create.content.redstone.nixieTube.NixieTubeBlockEntity;
import com.simibubi.create.content.trains.display.FlapDisplayBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

/** 无线显示卡的设备直写：仅在程序执行到输出积木时更新。 */
public final class WirelessDisplay {
    private WirelessDisplay() {
    }

    public static boolean write(Level level, BlockPos pos, int line, String text) {
        if (line < 0) {
            return false;
        }
        // 显示设备可能装在物理结构里，按定位穿透取方块实体（主世界坐标与结构内坐标都支持）
        BlockEntity be = com.mcl.cbtc.device.Devices.blockEntityAt(level,
                new com.mcl.cbtc.device.Devices.DeviceLocation(null, pos));
        if (be == null) {
            return false;
        }
        if (be instanceof FlapDisplayBlockEntity display) {
            FlapDisplayBlockEntity controller = display.getController();
            if (controller == null || line >= controller.getLines().size()) {
                return false;
            }
            controller.applyTextManually(line, Component.literal(text == null ? "" : text));
            return true;
        }
        if (be instanceof DisplayLinkBlockEntity link) {
            return writeLink(level, link, line, text);
        }
        if (be instanceof NixieTubeBlockEntity) {
            // 辉光管只有一行；采用 Create 原生组件序列化，并让同一整排辉光管一起更新
            if (line != 0) {
                return false;
            }
            String json = Component.Serializer.toJson(Component.literal(text == null ? "" : text),
                    level.registryAccess());
            return NixieTubeBlock.walkNixies(level, pos, false, (tubePos, index) -> {
                if (level.getBlockEntity(tubePos) instanceof NixieTubeBlockEntity tube) {
                    tube.displayCustomText(json, index);
                }
            });
        }
        DisplayTarget target = DisplayTarget.get(level, pos);
        if (target != null && level.getBlockEntity(pos) != null) {
            // 无真实显示链接的设备需要由显示链接转发，翻牌显示器已在上面处理。
            return false;
        }
        return false;
    }

    /** 读取设备真实可写行数，供积木生成下拉选项。 */
    public static int lineCount(Level level, BlockPos pos) {
        BlockEntity be = com.mcl.cbtc.device.Devices.blockEntityAt(level,
                new com.mcl.cbtc.device.Devices.DeviceLocation(null, pos));
        if (be instanceof FlapDisplayBlockEntity display) {
            FlapDisplayBlockEntity controller = display.getController();
            return controller == null ? 1 : Math.max(1, controller.getLines().size());
        }
        if (be instanceof DisplayLinkBlockEntity link && link.activeTarget != null) {
            return Math.max(1, link.activeTarget.provideStats(new DisplayLinkContext(level, link)).maxRows());
        }
        return 1;
    }

    private static boolean writeLink(Level level, DisplayLinkBlockEntity link, int line, String text) {
        DisplayLinkContext context = new DisplayLinkContext(level, link);
        if (link.activeTarget == null) {
            return false;
        }
        link.activeTarget.acceptText(line, java.util.List.of(Component.literal(text == null ? "" : text)), context);
        return true;
    }
}
