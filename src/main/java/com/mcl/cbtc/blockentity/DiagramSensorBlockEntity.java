package com.mcl.cbtc.blockentity;

import com.mcl.cbtc.device.DeviceIdentity;
import com.mcl.cbtc.device.Devices;
import com.mcl.cbtc.registry.CbcBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * CB结构图解传感器的方块实体。
 * <p>
 * 自身不存数据，只作为结构图解的可绑定落点：
 * 读取时由方块所在位置反查所属的物理结构，再取质量与各项受力。
 */
public class DiagramSensorBlockEntity extends BlockEntity implements DeviceIdentity {
    /** 方块外设的持久身份：绑定靠它指到这台方块，结构搬动也不丢。 */
    @Nullable
    private java.util.UUID deviceId;

    public DiagramSensorBlockEntity(BlockPos pos, BlockState state) {
        super(CbcBlockEntities.DIAGRAM_SENSOR.get(), pos, state);
    }

    @Nullable
    @Override
    public java.util.UUID deviceId() {
        return deviceId;
    }

    @Override
    public void setDeviceId(@Nullable java.util.UUID id) {
        deviceId = id;
        setChanged();
    }

    @Override
    public void onLoad() {
        super.onLoad();
        Devices.registerIdentity(level, this);
    }

    @Override
    public void setRemoved() {
        Devices.forgetIdentity(level, this);
        super.setRemoved();
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        DeviceIdentity.writeId(tag, deviceId);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        deviceId = DeviceIdentity.readId(tag);
    }
}
