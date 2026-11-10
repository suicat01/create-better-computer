package com.mcl.cbtc.client;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SoundInstance;

/**
 * 记录本模组通过积木播放的声音实例，供"停止声音"精确停止。
 * <p>
 * 不依赖 SoundManager.stop() 全停：那个会把玩家环境音、音乐一起掐掉；
 * 这里只停自己播出来的那些，并且能中断龙吼这类长音效。
 */
public final class PlayingSounds {
    private static final List<SoundInstance> ACTIVE = new ArrayList<>();

    private PlayingSounds() {
    }

    /** 登记一条正在播放的声音。 */
    public static void track(SoundInstance instance) {
        synchronized (ACTIVE) {
            ACTIVE.add(instance);
        }
    }

    /** 停止本模组播放的全部声音。 */
    public static void stopAll() {
        var manager = Minecraft.getInstance().getSoundManager();
        synchronized (ACTIVE) {
            for (SoundInstance instance : ACTIVE) {
                manager.stop(instance);
            }
            ACTIVE.clear();
        }
    }
}
