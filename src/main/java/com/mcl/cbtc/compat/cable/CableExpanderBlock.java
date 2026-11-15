package com.mcl.cbtc.compat.cable;

import com.mcl.cbtc.block.CBExpanderBlock;
import com.mojang.serialization.MapCodec;
import edn.stratodonut.drivebywire.wire.MultiChannelWireSource;
import java.util.ArrayList;
import java.util.List;

/**
 * 装过线缆升级模块卡的 CB 拓展器。
 * <p>
 * 线缆模组在玩家用线缆选中一个"信号源"后会读取它的频道表，
 * 这里给出 1~64 共 64 个序列号，玩家滚轮即可切换。
 * 该子类只在装了线缆模组时才会被创建。
 */
public class CableExpanderBlock extends CBExpanderBlock implements MultiChannelWireSource {
    public static final MapCodec<CableExpanderBlock> CODEC = simpleCodec(CableExpanderBlock::new);

    private static final List<String> CHANNELS = buildChannels();

    public CableExpanderBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends CBExpanderBlock> codec() {
        return CODEC;
    }

    @Override
    public List<String> wire$getChannels() {
        return CHANNELS;
    }

    @Override
    public String wire$nextChannel(String current, boolean forward) {
        int index = CHANNELS.indexOf(current);
        if (index < 0) {
            return CHANNELS.get(0);
        }
        return CHANNELS.get(Math.floorMod(index + (forward ? 1 : -1), CHANNELS.size()));
    }

    private static List<String> buildChannels() {
        List<String> channels = new ArrayList<>(CableCompat.CHANNELS);
        for (int i = 1; i <= CableCompat.CHANNELS; i++) {
            channels.add(Integer.toString(i));
        }
        return List.copyOf(channels);
    }
}
