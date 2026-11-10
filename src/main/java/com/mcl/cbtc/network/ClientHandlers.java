package com.mcl.cbtc.network;

import com.mcl.cbtc.blockentity.CBComputerBlockEntity;
import net.minecraft.client.Minecraft;

/**
 * 客户端网络包处理。放在这里是为了让 {@link CbcNetwork} 不直接引用客户端类，
 * 避免服务端加载时触发客户端类缺失。
 */
public final class ClientHandlers {
    private ClientHandlers() {
    }

    public static void onProgramSync(ProgramSyncPayload payload) {
        var level = Minecraft.getInstance().level;
        if (level == null) {
            return;
        }
        if (level.getBlockEntity(payload.pos()) instanceof CBComputerBlockEntity computer) {
            computer.applyProgram(payload.program());
            computer.setComputerName(payload.computerName());
        }
        if (Minecraft.getInstance().screen instanceof com.mcl.cbtc.client.ComputerScreen screen) {
            screen.acceptProgram(payload.program(), payload.computerName());
        }
    }

    /** 更新电脑界面底部的"外设链接"状态行。 */
    public static void onDeviceStatus(DeviceStatusPayload payload) {
        if (Minecraft.getInstance().screen instanceof com.mcl.cbtc.client.ComputerScreen screen) {
            screen.acceptDeviceStatus(payload.lines(), payload.storageModule(), payload.wirelessRedstoneModule(),
                    payload.lanNetworks(), payload.internetModule(), payload.speakerModule(),
                    payload.hardDrive(), payload.songs(), payload.playlists());
        }
    }

    public static void onWarehouseItems(WarehouseItemsPayload payload) {
        if (Minecraft.getInstance().screen instanceof com.mcl.cbtc.client.ComputerScreen screen) {
            screen.acceptWarehouseItems(payload.stacks());
        }
    }

    public static void onDisplayDevices(DisplayDevicesPayload payload) {
        if (Minecraft.getInstance().screen instanceof com.mcl.cbtc.client.ComputerScreen screen) {
            screen.acceptDisplayDevices(payload.devices());
        }
    }

    /** 外设清单：供"读取外设""当打字机按下"两类积木的下拉使用。 */
    public static void onDeviceList(DeviceListPayload payload) {
        if (Minecraft.getInstance().screen instanceof com.mcl.cbtc.client.ComputerScreen screen) {
            screen.acceptPeripherals(payload.devices());
        }
    }

    /** 外设选中状态：描边高亮、打开命名框或清除。 */
    public static void onDeviceSelect(DeviceSelectPayload payload) {
        switch (payload.kind()) {
            case DeviceSelectPayload.SELECTED -> {
                var player = Minecraft.getInstance().player;
                if (player != null) {
                    com.mcl.cbtc.client.DeviceSelection.set(payload.pos(),
                            player.level().dimension().location());
                }
            }
            case DeviceSelectPayload.OPEN_NAME -> {
                com.mcl.cbtc.client.DeviceSelection.clear();
                com.mcl.cbtc.client.DeviceNameScreen.open(net.minecraft.world.InteractionHand.MAIN_HAND,
                        payload.pos(), payload.deviceName(), payload.kindName(), payload.currentName());
            }
            default -> com.mcl.cbtc.client.DeviceSelection.clear();
        }
    }

    /** 局域网加入结果：在聊天栏下方提示成功或失败原因。 */
    public static void onLanJoinResult(LanJoinResultPayload payload) {
        var player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }
        player.displayClientMessage(net.minecraft.network.chat.Component
                .translatable(payload.message()).withStyle(payload.ok()
                        ? net.minecraft.ChatFormatting.GREEN
                        : net.minecraft.ChatFormatting.RED), true);
    }

    /** 播放一首网络歌曲：交给客户端播放器，并开始定期回传进度与歌词。 */
    public static void onMusicPlay(MusicPlayPayload payload) {
        if (!com.mcl.cbtc.integration.netmusic.NetMusic.loaded()) {
            return;
        }
        // 空链接表示服务端要求停止
        if (payload.url().isEmpty()) {
            com.mcl.cbtc.integration.netmusic.MusicPlayer.stop();
            com.mcl.cbtc.integration.netmusic.MusicReporter.stop();
            return;
        }
        // 服务端只给范围内的玩家下发，这里直接播放即可
        com.mcl.cbtc.integration.netmusic.MusicPlayer.play(
                payload.source(), payload.url(), payload.name(), payload.seconds(), payload.artists(),
                payload.volume(), java.util.Set.of());
        com.mcl.cbtc.integration.netmusic.MusicReporter.start();
    }

    /** 默念：不发声，只在方块上方浮现一行小字。 */
    public static void onMurmur(MurmurPayload payload) {
        com.mcl.cbtc.client.MurmurTexts.add(payload.key(), payload.pos(), payload.text());
    }

    /** 播放音效。范围 0 表示不限距离。 */
    public static void onSoundPlay(SoundPlayPayload payload) {
        Minecraft mc = Minecraft.getInstance();
        var player = mc.player;
        var level = mc.level;
        if (player == null || level == null) {
            return;
        }
        double sx = payload.pos().getX() + 0.5;
        double sy = payload.pos().getY() + 0.5;
        double sz = payload.pos().getZ() + 0.5;
        double distance = Math.sqrt(player.distanceToSqr(sx, sy, sz));
        if (SoundPlayPayload.STOP_SOUND.equals(payload.sound())) {
            // 停止：只停掉本模组播放的声音
            com.mcl.cbtc.client.PlayingSounds.stopAll();
            return;
        }
        if (payload.range() > 0 && distance > payload.range()) {
            return;
        }
        if (!payload.sound().isEmpty()) {
            var id = net.minecraft.resources.ResourceLocation.tryParse(payload.sound());
            if (id == null || !level.registryAccess()
                    .registryOrThrow(net.minecraft.core.registries.Registries.SOUND_EVENT).containsKey(id)) {
                return;
            }
            // 用 SimpleSoundInstance 播放并留一份引用，停止时可以直接停掉它
            var instance = new net.minecraft.client.resources.sounds.SimpleSoundInstance(
                    id,
                    net.minecraft.sounds.SoundSource.BLOCKS,
                    payload.volume(), 1.0F,
                    net.minecraft.util.RandomSource.create(),
                    false, 0,
                    net.minecraft.client.resources.sounds.SoundInstance.Attenuation.NONE,
                    sx, sy, sz, true);
            mc.getSoundManager().play(instance);
            com.mcl.cbtc.client.PlayingSounds.track(instance);
        }
    }
}
