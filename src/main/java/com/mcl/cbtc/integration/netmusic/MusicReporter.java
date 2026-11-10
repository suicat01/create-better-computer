package com.mcl.cbtc.integration.netmusic;

import com.mcl.cbtc.network.MusicStatePayload;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

/**
 * 客户端网络歌曲状态上报器。
 * <p>
 * 歌曲、进度和歌词都只存在于客户端，服务端的读取类积木需要它们，
 * 因此这里每 10 刻回传一次；停止播放时补发一条"已停止"。
 * <p>
 * 读取类积木还要区分发声设备（此电脑 / 音响），所以上报时带上声源坐标，
 * 服务端据此判断"那台设备此刻放的是哪首歌"。
 */
public final class MusicReporter {
    /** 上报间隔（刻）。 */
    private static final int INTERVAL = 10;

    private static int timer;
    private static boolean running;
    /** 上一次上报用的声源坐标，用于在换曲/停止时补发旧声源的结束状态。 */
    @Nullable
    private static String lastSourceKey;

    private MusicReporter() {
    }

    /** 开始上报。 */
    public static void start() {
        running = true;
        timer = 0;
        // 立刻记下本次声源，停止时才能把"已停止"发到对应的那台设备上
        lastSourceKey = MusicPlayer.sourceKey();
    }

    /** 停止上报，并补发一条"已停止"。 */
    public static void stop() {
        if (running || lastSourceKey != null) {
            sendStopped(lastSourceKey != null ? lastSourceKey : MusicPlayer.sourceKey());
        }
        running = false;
        lastSourceKey = null;
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (!running) {
            return;
        }
        double elapsed = MusicPlayer.elapsedSeconds();
        @Nullable String key = MusicPlayer.sourceKey();
        if (elapsed < 0) {
            sendStopped(lastSourceKey);
            running = false;
            lastSourceKey = null;
            return;
        }
        // 换了一首歌或换了声源：旧声源要补发结束，避免旧设备的读取值一直停在最后一帧
        if (lastSourceKey != null && !lastSourceKey.equals(key)) {
            sendStopped(lastSourceKey);
        }
        lastSourceKey = key;
        if (++timer < INTERVAL) {
            return;
        }
        timer = 0;
        sendCurrent();
    }

    private static void sendCurrent() {
        String original = MusicPlayer.lyricLine(false);
        String translated = MusicPlayer.lyricLine(true);
        PacketDistributor.sendToServer(new MusicStatePayload(
                MusicPlayer.sourceKey(),
                nullToEmpty(MusicPlayer.songName()),
                nullToEmpty(MusicPlayer.artist()),
                MusicPlayer.totalSeconds(),
                MusicPlayer.elapsedSeconds(),
                original == null ? "" : original,
                translated == null ? "" : translated));
    }

    private static void sendStopped(@Nullable String key) {
        // 没有声源键时无从清理，直接跳过；服务端的读取积木会随时间自然过期
        if (key == null || key.isEmpty()) {
            return;
        }
        PacketDistributor.sendToServer(new MusicStatePayload(
                key, "", "", -1, -1, "", ""));
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
