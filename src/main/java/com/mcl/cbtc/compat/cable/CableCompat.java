package com.mcl.cbtc.compat.cable;

import com.mcl.cbtc.block.CBExpanderBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.fml.ModList;

/**
 * 线缆（Drive-By-Wire with Sable）联动的软依赖入口。
 * <p>
 * 没装线缆模组时全部退化为空操作；拓展器也用普通方块。
 * 引用线缆 API 的两个类都通过反射创建，避免类加载阶段直接解析到缺失的类。
 */
public final class CableCompat {
    public static final String MOD_ID = "drivebywire";
    /** 模块卡提供给拓展器的序列号数量：1~64。 */
    public static final int CHANNELS = 64;

    private static final boolean LOADED = ModList.get().isLoaded(MOD_ID);
    private static Bridge bridge;

    /** 真正操作线缆网络的桥接方法，只在装了线缆模组时才存在。 */
    public interface Bridge {
        void setSignal(Level level, BlockPos pos, String channel, int strength);

        void clearSignals(Level level, BlockPos pos);
    }

    private CableCompat() {
    }

    public static boolean loaded() {
        return LOADED;
    }

    /**
     * 创建拓展器方块。
     * <p>
     * 装了线缆模组时返回实现了 {@code MultiChannelWireSource} 的子类：
     * 玩家用线缆右键拓展器后可用滚轮在 1~64 之间挑序列号。
     */
    public static CBExpanderBlock createExpanderBlock(BlockBehaviour.Properties properties) {
        if (LOADED) {
            try {
                Class<?> type = Class.forName("com.mcl.cbtc.compat.cable.CableExpanderBlock");
                return (CBExpanderBlock) type.getConstructor(BlockBehaviour.Properties.class)
                        .newInstance(properties);
            } catch (ReflectiveOperationException e) {
                com.mcl.cbtc.CreateBetterComputer.LOGGER.warn("线缆拓展器创建失败，退回普通拓展器", e);
            }
        }
        return new CBExpanderBlock(properties);
    }

    private static Bridge bridge() {
        if (!LOADED) {
            return null;
        }
        if (bridge == null) {
            try {
                Class<?> type = Class.forName("com.mcl.cbtc.compat.cable.DriveByWireBridge");
                bridge = (Bridge) type.getConstructor().newInstance();
            } catch (ReflectiveOperationException e) {
                com.mcl.cbtc.CreateBetterComputer.LOGGER.warn("线缆信号桥接初始化失败", e);
                return null;
            }
        }
        return bridge;
    }

    /** 写入某格拓展器指定序列号的线缆信号（强度 0~15）。 */
    public static void setSignal(Level level, BlockPos pos, int serial, int strength) {
        Bridge target = bridge();
        if (target == null || level == null || pos == null) {
            return;
        }
        int channel = Math.max(1, Math.min(CHANNELS, serial));
        target.setSignal(level, pos, Integer.toString(channel), Math.max(0, Math.min(15, strength)));
    }

    /** 清掉某格拓展器写入过的全部线缆信号。 */
    public static void clearSignals(Level level, BlockPos pos) {
        Bridge target = bridge();
        if (target != null && level != null && pos != null) {
            target.clearSignals(level, pos);
        }
    }
}
