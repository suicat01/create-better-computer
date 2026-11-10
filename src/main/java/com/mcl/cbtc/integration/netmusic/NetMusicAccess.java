package com.mcl.cbtc.integration.netmusic;

import com.github.tartaricacid.netmusic.item.ItemMusicCD;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * 真正触碰网络音乐机类型的类。
 * <p>
 * 只有 {@link NetMusic#loaded()} 为真时才会被调用，
 * 因此未安装该 mod 时本类不会被加载，也就不会触发 NoClassDefFoundError。
 */
final class NetMusicAccess {
    private NetMusicAccess() {
    }

    /** 唱片携带的歌曲信息；不是歌曲唱片时返回 null。 */
    @Nullable
    static NetMusic.Song songInfo(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return null;
        }
        ItemMusicCD.SongInfo info = ItemMusicCD.getSongInfo(stack);
        if (info == null) {
            return null;
        }
        String artists = info.artists == null ? "" : String.join(" / ", info.artists);
        return new NetMusic.Song(info.songUrl, info.songName, info.songTime, artists,
                info.transName == null ? "" : info.transName);
    }
}
