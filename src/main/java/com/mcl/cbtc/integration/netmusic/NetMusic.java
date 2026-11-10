package com.mcl.cbtc.integration.netmusic;

import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * 网络音乐机（netmusic）软依赖闸门。
 * <p>
 * 本类不引用任何 netmusic 类型，因此未安装该 mod 时也能安全加载；
 * 真正访问它的类放在 {@link NetMusicAccess}，只有确认加载后才调用。
 */
public final class NetMusic {
    /** 网络音乐机的 modid。 */
    public static final String MOD_ID = "netmusic";

    private NetMusic() {
    }

    /** 是否装了网络音乐机。 */
    public static boolean loaded() {
        return net.neoforged.fml.ModList.get().isLoaded(MOD_ID);
    }

    /** 这张唱片是不是网络音乐机的歌曲唱片（带歌曲信息的那种）。 */
    public static boolean isSongDisc(ItemStack stack) {
        return loaded() && NetMusicAccess.songInfo(stack) != null;
    }

    /** 唱片里的歌曲信息；不是歌曲唱片时返回 null。 */
    @Nullable
    public static Song song(ItemStack stack) {
        if (!loaded()) {
            return null;
        }
        return NetMusicAccess.songInfo(stack);
    }

    /**
     * 让客户端播放一首网络歌曲。
     * <p>
     * 网络音频只能在客户端解码播放，所以这里只负责把信息发给目标玩家。
     *
     * @param radius 可听范围（格）；范围内的玩家才会收到，0 表示不限制
     */
    public static void playTo(net.minecraft.server.level.ServerPlayer player,
            net.minecraft.core.BlockPos source, Song song, float volume, int radius) {
        if (!loaded() || song == null) {
            return;
        }
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player,
                new com.mcl.cbtc.network.MusicPlayPayload(source, song.url(), song.name(),
                        song.seconds(), song.artists(), volume, radius));
    }

    /**
     * 向范围内的玩家下发"停止网络歌曲"。
     * <p>
     * 只在客户端真的在播放时才需要发，因此由调用方自行决定发给谁。
     */
    public static void stopTo(net.minecraft.server.level.ServerPlayer player,
            net.minecraft.core.BlockPos source) {
        if (!loaded()) {
            return;
        }
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player,
                new com.mcl.cbtc.network.MusicPlayPayload(source, "", "",
                        0, "", 1.0f, 0));
    }

    /** 一首歌的播放所需信息，已经和 netmusic 类型解耦。 */
    public record Song(String url, String name, int seconds, String artists, String transName) {
    }
}
