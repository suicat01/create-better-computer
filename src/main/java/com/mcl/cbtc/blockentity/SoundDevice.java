package com.mcl.cbtc.blockentity;

import net.minecraft.core.BlockPos;

/**
 * 可作为"播放声音"发声点的方块实体。
 * <p>
 * CB音响与讲解石都实现本接口：发声位置取 {@link #getBlockPos()}，
 * 可听范围由各自的 {@link #soundRange()} 决定，积木里填的更大范围会被夹到这个上限。
 */
public interface SoundDevice {
    BlockPos getBlockPos();

    /** 该设备允许的最大可听范围（格）。 */
    int soundRange();
}
