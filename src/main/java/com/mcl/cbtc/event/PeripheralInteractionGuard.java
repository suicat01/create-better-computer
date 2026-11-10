package com.mcl.cbtc.event;

import com.mcl.cbtc.CreateBetterComputer;
import com.mcl.cbtc.device.DeviceKind;
import com.mcl.cbtc.device.Devices;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.TriState;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/** 外设绑定卡的交互协调；不干预航空学原版打字机。 */
@EventBusSubscriber(modid = CreateBetterComputer.MODID)
public final class PeripheralInteractionGuard {
    /** 反查范围：以目标方块为中心、半径 32 格内的 CB 电脑都会被检查。 */
    private static final int SCAN_RADIUS = 32;

    private PeripheralInteractionGuard() {
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        Level level = event.getLevel();
        BlockPos pos = event.getPos();
        DeviceKind kind = Devices.kindAt(level, pos);
        if (kind == null) {
            return;
        }
        boolean holdingBindCard = event.getItemStack().getItem() instanceof com.mcl.cbtc.item.DeviceModuleItem;

        // 其它外设：只有手持绑定卡时才需要"跳过方块自带的设置界面"
        if (holdingBindCard && hasOwnScreen(kind)) {
            event.setUseBlock(TriState.FALSE);
        }
    }

    /** 这些外设右键时会打开自己的设置界面，会抢走绑定卡的交互。 */
    private static boolean hasOwnScreen(DeviceKind kind) {
        return kind == DeviceKind.ALTITUDE_SENSOR || kind == DeviceKind.SPEAKER;
    }
}
