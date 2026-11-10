package com.mcl.cbtc.blockentity;

import com.mcl.cbtc.registry.CbcBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 讲解石的方块实体：一种没有升级槽的音响，可听范围固定为 {@link #RANGE} 格。
 * <p>
 * 不能插入扬声器升级卡，所以范围不会变化，也不会打开任何界面。
 */
public class LectureStoneBlockEntity extends BlockEntity implements SoundDevice {
    /** 固定的声音传播范围（格）。 */
    public static final int RANGE = 6;

    public LectureStoneBlockEntity(BlockPos pos, BlockState state) {
        super(CbcBlockEntities.LECTURE_STONE.get(), pos, state);
    }

    @Override
    public int soundRange() {
        return RANGE;
    }
}
