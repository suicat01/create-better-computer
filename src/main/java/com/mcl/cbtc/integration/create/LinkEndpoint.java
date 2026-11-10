package com.mcl.cbtc.integration.create;

import com.mcl.cbtc.blockentity.CBComputerBlockEntity;
import com.simibubi.create.Create;
import com.simibubi.create.content.redstone.link.IRedstoneLinkable;
import com.simibubi.create.content.redstone.link.RedstoneLinkNetworkHandler;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import net.createmod.catnip.data.Couple;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * 把一台 CB电脑 挂到机械动力的红石链接网络上。
 * <p>
 * 一台电脑可以同时收发多组频率（每个"收到频道信号"积木各占一组），
 * 而 Create 的网络要求一个对象只对应一个 {@link #getNetworkKey()}，
 * 所以这里对内维护多个 {@link Channel} 子端点，每个子端点各自注册进网络。
 */
public final class LinkEndpoint {
    private final Supplier<Level> levelRef;
    private final BlockPos pos;
    /** 当前所有频道，按“红+蓝”频率对去重。 */
    private final Map<String, Channel> channels = new LinkedHashMap<>();
    /** 发送用的强度，所有发送频道共用。 */
    private int strength;

    public LinkEndpoint(Supplier<Level> levelRef, BlockPos pos) {
        this(levelRef, pos, true);
    }

    public LinkEndpoint(Supplier<Level> levelRef, BlockPos pos, boolean red) {
        this.levelRef = levelRef;
        this.pos = pos;
    }

    @Nullable
    private Level level() {
        return levelRef.get();
    }

    /**
     * 用一批频率对替换当前所有频道。每对是 {红频率物品, 蓝频率物品}。
     * 为空表示不订阅任何频道。
     */
    public void setChannelPairs(List<ItemStack[]> pairs) {
        Map<String, Couple<RedstoneLinkNetworkHandler.Frequency>> next = new LinkedHashMap<>();
        for (ItemStack[] pair : pairs) {
            ItemStack first = pair.length > 0 && pair[0] != null ? pair[0] : ItemStack.EMPTY;
            ItemStack second = pair.length > 1 && pair[1] != null ? pair[1] : ItemStack.EMPTY;
            Couple<RedstoneLinkNetworkHandler.Frequency> key = Couple.create(
                    RedstoneLinkNetworkHandler.Frequency.of(first),
                    RedstoneLinkNetworkHandler.Frequency.of(second));
            next.putIfAbsent(keyKey(key), key);
        }
        if (next.keySet().equals(channels.keySet())) {
            return;
        }
        // 退出旧频道里不再需要的，加入新频道，保留交集以免反复掉线
        for (String existing : new ArrayList<>(channels.keySet())) {
            if (!next.containsKey(existing)) {
                channels.remove(existing).leave();
            }
        }
        for (Map.Entry<String, Couple<RedstoneLinkNetworkHandler.Frequency>> entry : next.entrySet()) {
            if (!channels.containsKey(entry.getKey())) {
                Channel channel = new Channel(entry.getValue());
                channels.put(entry.getKey(), channel);
                channel.join();
            }
        }
    }

    /** 兼容旧调用：只订阅一组频率。 */
    public void setFrequencies(@Nullable ItemStack first, @Nullable ItemStack second) {
        List<ItemStack[]> pairs = new ArrayList<>();
        pairs.add(new ItemStack[]{first, second});
        setChannelPairs(pairs);
    }

    public ItemStack firstStack() {
        for (Channel channel : channels.values()) {
            ItemStack first = channel.key.getFirst().getStack();
            if (!first.isEmpty()) {
                return first;
            }
        }
        return ItemStack.EMPTY;
    }

    public ItemStack secondStack() {
        for (Channel channel : channels.values()) {
            ItemStack second = channel.key.getSecond().getStack();
            if (!second.isEmpty()) {
                return second;
            }
        }
        return ItemStack.EMPTY;
    }

    public boolean hasFrequency() {
        return !channels.isEmpty();
    }

    public int strength() {
        return strength;
    }

    /** 设置所有发送频道的强度。 */
    public void setStrength(int strength) {
        int clamped = Math.max(0, Math.min(15, strength));
        if (clamped == this.strength) {
            return;
        }
        this.strength = clamped;
        refreshNetwork();
    }

    /** 加入网络（区块加载/放置时调用）。 */
    public void join() {
        for (Channel channel : channels.values()) {
            channel.join();
        }
    }

    /** 让所有已加入的频道重新参与一次网络刷新（相邻方块变动时调用）。 */
    public void refreshNetwork() {
        Level level = level();
        if (level == null) {
            return;
        }
        for (Channel channel : channels.values()) {
            if (channel.joined) {
                Create.REDSTONE_LINK_NETWORK_HANDLER.updateNetworkOf(level, channel);
            }
        }
    }

    /** 退出网络（区块卸载/被破坏时调用）。 */
    public void leave() {
        for (Channel channel : channels.values()) {
            channel.leave();
        }
        channels.clear();
        this.strength = 0;
    }

    public boolean isJoined() {
        for (Channel channel : channels.values()) {
            if (channel.joined) {
                return true;
            }
        }
        return false;
    }

    /**
     * 当前所有频道在网络里的链接节点（用于调试/界面展示）。
     */
    public Set<IRedstoneLinkable> networkSnapshot() {
        Level level = level();
        if (level == null) {
            return Set.of();
        }
        Set<IRedstoneLinkable> result = new HashSet<>();
        for (Channel channel : channels.values()) {
            if (channel.joined) {
                result.addAll(Create.REDSTONE_LINK_NETWORK_HANDLER.getNetworkOf(level, channel));
            }
        }
        return result;
    }

    private static String keyKey(Couple<RedstoneLinkNetworkHandler.Frequency> key) {
        return key.getFirst().getStack().getItem() + "|" + key.getSecond().getStack().getItem();
    }

    /**
     * 单个频率对的子端点：真正跟 Create 网络交互的对象。
     * 收到信号时把强度按"红物品|蓝物品"上报给方块实体，让各条件积木分别查询。
     */
    private final class Channel implements IRedstoneLinkable {
        private final Couple<RedstoneLinkNetworkHandler.Frequency> key;
        private boolean joined;

        private Channel(Couple<RedstoneLinkNetworkHandler.Frequency> key) {
            this.key = key;
        }

        private void join() {
            Level level = level();
            if (joined || level == null || level.isClientSide) {
                return;
            }
            if (key.getFirst().getStack().isEmpty() && key.getSecond().getStack().isEmpty()) {
                return;
            }
            Create.REDSTONE_LINK_NETWORK_HANDLER.addToNetwork(level, this);
            joined = true;
        }

        private void leave() {
            Level level = level();
            if (!joined || level == null) {
                return;
            }
            joined = false;
            Create.REDSTONE_LINK_NETWORK_HANDLER.removeFromNetwork(level, this);
            if (level.getBlockEntity(pos) instanceof CBComputerBlockEntity computer) {
                computer.onWirelessRedstoneReceived(keyKey(key), 0);
            }
        }

        @Override
        public int getTransmittedStrength() {
            return level() != null && level().isClientSide ? 0 : strength;
        }

        @Override
        public void setReceivedStrength(int power) {
            Level level = level();
            if (level != null && level.getBlockEntity(pos) instanceof CBComputerBlockEntity computer) {
                computer.onWirelessRedstoneReceived(keyKey(key), Math.max(0, Math.min(15, power)));
            }
        }

        @Override
        public boolean isListening() {
            Level level = level();
            return level != null && level.getBlockEntity(pos) instanceof CBComputerBlockEntity computer
                    && computer.hasWirelessRedstoneModule();
        }

        @Override
        public boolean isAlive() {
            Level level = level();
            if (level == null || level.isClientSide || !level.isLoaded(pos)) {
                return false;
            }
            return level.getBlockEntity(pos) instanceof CBComputerBlockEntity;
        }

        @Override
        public Couple<RedstoneLinkNetworkHandler.Frequency> getNetworkKey() {
            return key;
        }

        @Override
        public BlockPos getLocation() {
            return pos;
        }
    }
}
