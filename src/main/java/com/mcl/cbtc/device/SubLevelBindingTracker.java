package com.mcl.cbtc.device;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.level.Level;

/**
 * 监听 Sable 物理结构的组装与拆卸，实时刷新外设绑定的定位缓存。
 * <p>
 * 方块被组装进物理结构后，主世界原坐标失效；反之拆卸后结构内坐标失效。
 * 这里只是把缓存同步到新坐标系；绑定记录的解析任何时候都能脱离缓存独立工作。
 */
final class SubLevelBindingTracker implements dev.ryanhcode.sable.api.sublevel.SubLevelObserver {
    private final Level level;
    /** 组装时 onSubLevelAdded 先于方块搬入结构触发，这里延迟到下一 tick 再换算。 */
    private final List<dev.ryanhcode.sable.sublevel.SubLevel> pendingAssembled = new ArrayList<>();
    /** 拆卸时方块可能还没放回主世界，先捕获预期坐标，后续 tick 反复核对直到找到或超时。 */
    private List<Devices.PendingRemoval> pendingRemovals = new ArrayList<>();

    private SubLevelBindingTracker(Level level) {
        this.level = level;
    }

    @Override
    public void onSubLevelAdded(dev.ryanhcode.sable.sublevel.SubLevel subLevel) {
        pendingAssembled.add(subLevel);
    }

    @Override
    public void onSubLevelRemoved(dev.ryanhcode.sable.sublevel.SubLevel subLevel,
            dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason reason) {
        List<Devices.PendingRemoval> captured = Devices.captureRemovals(level, subLevel);
        if (!captured.isEmpty()) {
            List<Devices.PendingRemoval> merged = new ArrayList<>(pendingRemovals);
            merged.addAll(captured);
            pendingRemovals = merged;
        }
    }

    @Override
    public void tick(dev.ryanhcode.sable.api.sublevel.SubLevelContainer container) {
        if (!pendingRemovals.isEmpty()) {
            // 同一刻有拆有装时先核对拆卸后的主世界位置，再做组装换算
            pendingRemovals = Devices.applyRemovals(level, pendingRemovals);
        }
        if (pendingAssembled.isEmpty()) {
            return;
        }
        for (dev.ryanhcode.sable.sublevel.SubLevel subLevel : pendingAssembled) {
            Devices.onSubLevelAssembled(level, subLevel);
        }
        pendingAssembled.clear();
    }

    /** 在 Sable 容器就绪时注册观察者；只在 Sable 已加载时调用。 */
    static void register() {
        if (!DeviceAccess.hasSable()) {
            return;
        }
        dev.ryanhcode.sable.platform.SableEventPlatform.INSTANCE.onSubLevelContainerReady(
                (level, container) -> {
                    // 只关心服务端的物理结构；客户端容器不参与绑定定位
                    if (container instanceof dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer) {
                        container.addObserver(new SubLevelBindingTracker(level));
                    }
                });
    }
}
