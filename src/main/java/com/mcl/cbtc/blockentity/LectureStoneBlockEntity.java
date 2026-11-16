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
 * 讲解石的方块实体：一种没有升级槽的音响，可听范围固定为 {@link #RANGE} 格。
 * <p>
 * 不能插入扬声器升级卡，所以范围不会变化，也不会打开任何界面。
 */
public class LectureStoneBlockEntity extends BlockEntity implements SoundDevice, DeviceIdentity {
    /** 固定的声音传播范围（格）。 */
    public static final int RANGE = 6;

    /** 方块外设的持久身份：绑定靠它指到这台方块，结构搬动也不丢。 */
    @Nullable
    private java.util.UUID deviceId;

    public LectureStoneBlockEntity(BlockPos pos, BlockState state) {
        super(CbcBlockEntities.LECTURE_STONE.get(), pos, state);
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

    @Override
    public int soundRange() {
        return RANGE;
    }
}
