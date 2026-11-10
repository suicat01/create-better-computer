package com.mcl.cbtc.device;

import net.neoforged.fml.ModList;

/** Simulated 软依赖的公开状态查询。 */
public final class DeviceAccessBridge {
    private DeviceAccessBridge() {
    }

    public static boolean hasSimulated() {
        return ModList.get().isLoaded("simulated");
    }
}
