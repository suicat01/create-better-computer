package com.mcl.cbtc.compat.cable;

import edn.stratodonut.drivebywire.compat.ControllerSignalStore;
import edn.stratodonut.drivebywire.wire.WireNetworkManager;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

/** 把积木写入的信号交给线缆网络。只在装了线缆模组时才会被实例化。 */
public final class DriveByWireBridge implements CableCompat.Bridge {
    @Override
    public void setSignal(Level level, BlockPos pos, String channel, int strength) {
        WireNetworkManager.trySetSignalAt(level, pos, channel, strength);
    }

    @Override
    public void clearSignals(Level level, BlockPos pos) {
        ControllerSignalStore.clear(level, pos);
    }
}
