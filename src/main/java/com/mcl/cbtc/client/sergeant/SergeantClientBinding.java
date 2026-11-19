package com.mcl.cbtc.client.sergeant;

import com.simibubi.create.content.kinetics.mechanicalArm.ArmInteractionPointHandler;
import net.minecraft.core.BlockPos;

/**
 * 客户端绑定钩子：把"当前选中点"写回指定的瓦列里中士。
 * <p>
 * 单独放在客户端类里，服务端永远不会加载到 {@link ArmInteractionPointHandler}。
 */
public final class SergeantClientBinding {
    private SergeantClientBinding() {
    }

    public static void flush(BlockPos armPos) {
        ArmInteractionPointHandler.flushSettings(armPos);
    }
}
