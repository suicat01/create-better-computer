package com.mcl.cbtc.device;

import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import org.jetbrains.annotations.Nullable;

/**
 * 方块外设的持久身份。
 * <p>
 * 绑定记录记的是这个 UUID，而不是坐标：结构组装、拆卸、跨维度移动只会改变方块的位置，
 * 不会改变"它是谁"。只要方块实体还能加载出来，绑定就能靠 UUID 重新指到它身上；
 * 坐标退化成"把区块加载出来"的定位线索。
 * <p>
 * 只有本模组自己的方块能带上这个身份，其它模组的外设没有地方存 UUID，仍按坐标定位。
 */
public interface DeviceIdentity {
    /** 方块实体存档里保存 UUID 的键。 */
    String NBT_ID = "CbtcDeviceId";

    /** 本方块的身份；旧存档里还没写入时为 null。 */
    @Nullable
    UUID deviceId();

    void setDeviceId(@Nullable UUID id);

    /** 取身份，没有就生成一个并写进方块实体。 */
    default UUID ensureDeviceId() {
        UUID id = deviceId();
        if (id == null) {
            id = UUID.randomUUID();
            setDeviceId(id);
        }
        return id;
    }

    /** 从方块实体存档里读身份；缺失或格式错误返回 null。 */
    @Nullable
    static UUID readId(CompoundTag tag) {
        String raw = tag.getString(NBT_ID);
        if (raw.isEmpty()) {
            return null;
        }
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    /** 把身份写进方块实体存档。 */
    static void writeId(CompoundTag tag, @Nullable UUID id) {
        if (id != null) {
            tag.putString(NBT_ID, id.toString());
        }
    }
}
