package com.mcl.cbtc.integration.netmusic;

import com.github.tartaricacid.netmusic.api.lyric.LyricParser;
import com.github.tartaricacid.netmusic.api.lyric.LyricRecord;
import com.github.tartaricacid.netmusic.client.audio.MusicPlayManager;
import com.github.tartaricacid.netmusic.client.audio.NetMusicAudioStream;
import com.github.tartaricacid.netmusic.init.InitSounds;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.AudioStream;
import net.minecraft.client.sounds.SoundBufferLibrary;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundSource;
import java.net.URL;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.jetbrains.annotations.Nullable;

/**
 * 客户端网络歌曲播放器。
 * <p>
 * 网络音频只能在客户端解码，所以整条播放链路都在这里：
 * 收到服务端下发的歌单条目 → 解析直链 → 播放 → 同时把播放进度与当前歌词回传给服务端，
 * 供"读取歌曲信息"类积木取用。
 */
public final class MusicPlayer {
    /** 网易云直链里提取歌曲 id。 */
    private static final Pattern NETEASE_ID = Pattern.compile("^.*?\\?id=(\\d+)\\.mp3$");

    /** 当前正在播放的实例；同一时刻只保留一首。 */
    private static CBCMusicSound current;
    /** 当前这首歌是否还要限定接收者。 */
    private static java.util.Set<UUID> listeners = java.util.Set.of();
    /** 当前声源坐标串，用于让服务端区分是电脑还是某台音响在发声。 */
    @Nullable
    private static String sourceKey;
    /** 当前这首歌的直链，用来判断重复请求是不是同一首。 */
    private static String playingUrl = "";
    /** 直链解析是否还在进行中。 */
    private static boolean pending;

    private MusicPlayer() {
    }

    /**
     * 播放一首歌。
     *
     * @param source 发声位置，决定立体声定位
     */
    public static void play(BlockPos source, String url, String name, int seconds, String artists) {
        play(source, url, name, seconds, artists, 1.0f, java.util.Set.of());
    }

    /**
     * 播放一首歌，并限制发声范围。
     *
     * @param volume    音量（0~1）
     * @param listeners 允许听到的玩家；为空表示不做限制
     */
    public static void play(BlockPos source, String url, String name, int seconds, String artists,
                            float volume, java.util.Set<UUID> listeners) {
        String key = source.getX() + "," + source.getY() + "," + source.getZ();
        // 同一首歌在同一位置还在播（或正在解析直链）时直接忽略：
        // 播放积木常放在循环里，重复请求会让声音池瞬间被打满，进而拖死游戏
        if (key.equals(sourceKey) && url.equals(playingUrl) && (current != null || pending)) {
            return;
        }
        stop();
        MusicPlayer.listeners = listeners == null ? java.util.Set.of() : listeners;
        sourceKey = key;
        playingUrl = url;
        pending = true;
        float clamped = (float) Math.max(0, Math.min(1, volume));
        MusicPlayManager.play(url, name, resolved -> {
            LyricRecord lyric = fetchLyric(url, name);
            current = new CBCMusicSound(source, resolved, seconds, lyric, clamped);
            current.songName = name;
            current.artist = artists;
            pending = false;
            return current;
        });
    }

    /** 这首歌是否允许该客户端听到。 */
    public static boolean canHear(UUID player) {
        return listeners.isEmpty() || listeners.contains(player);
    }

    /** 当前声源坐标串；没有在播时返回 null。 */
    @Nullable
    public static String sourceKey() {
        return current == null ? null : sourceKey;
    }

    /** 停止当前播放并清空上报状态。 */
    public static void stop() {
        if (current != null) {
            current.stopNow();
            current = null;
        }
        // 允许听众表与声源一起清掉，否则下一次播放会被上一首的限定条件挡住
        listeners = java.util.Set.of();
        sourceKey = null;
        playingUrl = "";
        pending = false;
    }

    /** 当前歌词行；{@code translated} 为真是中文译文，否则是原文。 */
    @Nullable
    public static String lyricLine(boolean translated) {
        return current == null ? null : current.lyricLine(translated);
    }

    /** 当前播放到的秒数；没有在播时返回 -1。 */
    public static int elapsedSeconds() {
        return current == null ? -1 : current.elapsedSeconds();
    }

    /** 当前歌曲总秒数；没有在播时返回 -1。 */
    public static int totalSeconds() {
        return current == null ? -1 : current.tickTimes / 20;
    }

    /** 当前歌名；没有在播时返回 null。 */
    @Nullable
    public static String songName() {
        return current == null ? null : current.songName;
    }

    /** 当前作曲家；没有在播时返回 null。 */
    @Nullable
    public static String artist() {
        return current == null ? null : current.artist;
    }

    /** 拉取歌词；非网易云直链或未开启歌词时返回 null。 */
    @Nullable
    private static LyricRecord fetchLyric(String url, String songName) {
        if (!url.startsWith(MusicPlayManager.MUSIC_163_URL)
                || !com.github.tartaricacid.netmusic.config.GeneralConfig.ENABLE_PLAYER_LYRICS.get()) {
            return null;
        }
        Matcher matcher = NETEASE_ID.matcher(url);
        if (!matcher.find()) {
            return null;
        }
        try {
            long id = Long.parseLong(matcher.group(1));
            return LyricParser.parseLyric(
                    com.github.tartaricacid.netmusic.NetMusic.NET_EASE_WEB_API.lyric(id), songName);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 我们自己维护的音效实例。
     * <p>
     * 不复用 netmusic 的 {@code NetMusicSound}：它会在 tick 里检查坐标上是否有唱片机方块，
     * 没有就直接停止播放。积木的播放源可能是电脑或音响，因此这里只按自己的生命周期收尾。
     */
    private static final class CBCMusicSound extends AbstractTickableSoundInstance {
        private final URL songUrl;
        private final int tickTimes;
        private final int totalTicks;
        private final BlockPos pos;
        private final @Nullable LyricRecord lyricRecord;
        private int tick;
        private String songName = "";
        private String artist = "";

        CBCMusicSound(BlockPos pos, URL songUrl, int timeSecond, @Nullable LyricRecord lyricRecord,
                      float volume) {
            super(InitSounds.NET_MUSIC.get(), SoundSource.RECORDS, SoundInstance.createUnseededRandom());
            this.songUrl = songUrl;
            this.x = pos.getX() + 0.5f;
            this.y = pos.getY() + 0.5f;
            this.z = pos.getZ() + 0.5f;
            this.tickTimes = Math.max(1, timeSecond) * 20;
            this.totalTicks = this.tickTimes;
            this.volume = volume <= 0 ? 1.0f : volume;
            this.pos = pos;
            this.lyricRecord = lyricRecord;
        }

        @Override
        public void tick() {
            tick++;
            if (lyricRecord != null) {
                lyricRecord.updateCurrentLine(tick);
            }
            if (tick > tickTimes + 50) {
                // 播完了：保留声源键让上报器把"已停止"发出去，再清空当前实例
                current = null;
                stop();
            }
        }

        void stopNow() {
            stop();
        }

        int elapsedSeconds() {
            return tick / 20;
        }

        @Nullable
        String lyricLine(boolean translated) {
            if (lyricRecord == null) {
                return null;
            }
            var map = translated ? lyricRecord.getTransLyrics() : lyricRecord.getLyrics();
            if (map == null || map.isEmpty()) {
                return null;
            }
            // 取最后一个时间戳不大于当前进度的行
            int lineTick = -1;
            for (int key : map.keySet()) {
                if (key <= tick) {
                    lineTick = key;
                } else {
                    break;
                }
            }
            return lineTick < 0 ? null : map.get(lineTick);
        }

        @Override
        public CompletableFuture<AudioStream> getStream(SoundBufferLibrary soundBuffers, Sound sound,
                                                        boolean looping) {
            return CompletableFuture.supplyAsync(() -> {
                try {
                    return new NetMusicAudioStream(songUrl);
                } catch (Exception e) {
                    return null;
                }
            }, Util.backgroundExecutor());
        }
    }
}
