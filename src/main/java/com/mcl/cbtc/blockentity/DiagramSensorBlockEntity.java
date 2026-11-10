package com.mcl.cbtc.blockentity;

import com.mcl.cbtc.registry.CbcBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * CB结构图解传感器的方块实体。
 * <p>
 * 自身不存数据，只作为结构图解的可绑定落点：
 * 读取时由方块所在位置反查所属的物理结构，再取质量与各项受力。
 */
public class DiagramSensorBlockEntity extends BlockEntity {
    public DiagramSensorBlockEntity(BlockPos pos, BlockState state) {
        super(CbcBlockEntities.DIAGRAM_SENSOR.get(), pos, state);
    }
}
