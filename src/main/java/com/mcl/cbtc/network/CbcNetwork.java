package com.mcl.cbtc.network;

import com.mcl.cbtc.CreateBetterComputer;
import com.mcl.cbtc.blockentity.CBComputerBlockEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * 网络包注册。
 * <ul>
 *   <li>{@link ProgramSyncPayload}：服务端 → 客户端，打开界面时下发程序；</li>
 *   <li>{@link ProgramSavePayload}：客户端 → 服务端，点"保存并返回"时回写程序。</li>
 * </ul>
 */
@EventBusSubscriber(modid = CreateBetterComputer.MODID)
public final class CbcNetwork {
    private CbcNetwork() {
    }

    @SubscribeEvent
    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        registrar.playToClient(ProgramSyncPayload.TYPE, ProgramSyncPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> ClientHandlers.onProgramSync(payload)));
        registrar.playToServer(ProgramSavePayload.TYPE, ProgramSavePayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (context.player().level().getBlockEntity(payload.pos())
                            instanceof CBComputerBlockEntity computer) {
                        computer.applyProgram(payload.program());
                        computer.setComputerName(payload.computerName());
                    }
                }));
        registrar.playToClient(SoundPlayPayload.TYPE, SoundPlayPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> ClientHandlers.onSoundPlay(payload)));
        registrar.playToClient(MurmurPayload.TYPE, MurmurPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> ClientHandlers.onMurmur(payload)));
        registrar.playToClient(MusicPlayPayload.TYPE, MusicPlayPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> ClientHandlers.onMusicPlay(payload)));
        registrar.playToServer(MusicStatePayload.TYPE, MusicStatePayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(
                        () -> MusicStateStore.accept(context.player().getUUID(), payload)));
        registrar.playToClient(DeviceStatusPayload.TYPE, DeviceStatusPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> ClientHandlers.onDeviceStatus(payload)));
        registrar.playToClient(WarehouseItemsPayload.TYPE, WarehouseItemsPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> ClientHandlers.onWarehouseItems(payload)));
        registrar.playToClient(DisplayDevicesPayload.TYPE, DisplayDevicesPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> ClientHandlers.onDisplayDevices(payload)));
        registrar.playToClient(DeviceListPayload.TYPE, DeviceListPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> ClientHandlers.onDeviceList(payload)));
        registrar.playToClient(DeviceSelectPayload.TYPE, DeviceSelectPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> ClientHandlers.onDeviceSelect(payload)));
        registrar.playToServer(DeviceBindPayload.TYPE, DeviceBindPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    var stack = context.player().getItemInHand(payload.hand());
                    if (stack.getItem() instanceof com.mcl.cbtc.item.DeviceModuleItem
                            && context.player().distanceToSqr(payload.pos().getCenter()) <= 64) {
                        com.mcl.cbtc.item.DeviceModuleItem.bind(stack, context.player().level(), payload.pos(),
                                payload.name());
                    }
                }));
        registrar.playToServer(TypewriterKeyPayload.TYPE, TypewriterKeyPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (context.player().level().getBlockEntity(payload.pos())
                            instanceof com.mcl.cbtc.blockentity.CBTypewriterBlockEntity typewriter) {
                        typewriter.onKeyInteraction(context.player().getUUID(), null,
                                payload.keyCode(), payload.down());
                    }
                }));
        registrar.playToServer(TypewriterReleasePayload.TYPE, TypewriterReleasePayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (context.player().level().getBlockEntity(payload.pos())
                            instanceof com.mcl.cbtc.blockentity.CBTypewriterBlockEntity typewriter
                            && typewriter.checkUser(context.player().getUUID())) {
                        typewriter.disconnectUser();
                    }
                }));
        registrar.playToClient(LanJoinResultPayload.TYPE, LanJoinResultPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> ClientHandlers.onLanJoinResult(payload)));
        registrar.playToServer(LanJoinPayload.TYPE, LanJoinPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (!(context.player() instanceof net.minecraft.server.level.ServerPlayer player)) {
                        return;
                    }
                    var stack = player.getItemInHand(payload.hand());
                    if (!(stack.getItem() instanceof com.mcl.cbtc.item.LanModuleItem)) {
                        return;
                    }
                    String id = payload.lanId() == null ? "" : payload.lanId().trim();
                    String password = payload.password() == null ? "" : payload.password();
                    if (id.isEmpty() || password.isEmpty()) {
                        PacketDistributor.sendToPlayer(player,
                                new LanJoinResultPayload(false, "cbtc.lan.error.empty"));
                        return;
                    }
                    var result = com.mcl.cbtc.lan.LanRegistry.get(player.server).join(id, password);
                    if (result == com.mcl.cbtc.lan.LanRegistry.Result.WRONG_PASSWORD) {
                        PacketDistributor.sendToPlayer(player,
                                new LanJoinResultPayload(false, "cbtc.lan.error.wrong_password"));
                        return;
                    }
                    com.mcl.cbtc.item.LanModuleItem.setNetwork(stack, id, password);
                    PacketDistributor.sendToPlayer(player, new LanJoinResultPayload(true,
                            result == com.mcl.cbtc.lan.LanRegistry.Result.CREATED
                                    ? "cbtc.lan.created"
                                    : "cbtc.lan.joined"));
                }));
        registrar.playToServer(DisplayBindPayload.TYPE, DisplayBindPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    var stack = context.player().getItemInHand(payload.hand());
                    if (stack.getItem() instanceof com.mcl.cbtc.item.DisplayLinkModuleItem
                            && context.player().distanceToSqr(payload.pos().getCenter()) <= 64) {
                        com.mcl.cbtc.item.DisplayLinkModuleItem.bind(stack, context.player().level(), payload.pos(),
                                payload.name());
                    }
                }));
        registrar.playToServer(TargetLocatorSetPayload.TYPE, TargetLocatorSetPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    var stack = context.player().getItemInHand(payload.hand());
                    if (stack.getItem() instanceof com.mcl.cbtc.item.TargetLocatorItem) {
                        com.mcl.cbtc.item.TargetLocatorItem.setPosition(stack, payload.x(), payload.z());
                    }
                }));
        registrar.playToServer(ProgramRequestPayload.TYPE, ProgramRequestPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (context.player().level().getBlockEntity(payload.pos())
                            instanceof CBComputerBlockEntity computer
                            && context.player() instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
                        PacketDistributor.sendToPlayer(serverPlayer,
                                new ProgramSyncPayload(payload.pos(), computer.program().copy(),
                                        computer.computerName()));
                        PacketDistributor.sendToPlayer(serverPlayer,
                                new DeviceStatusPayload(payload.pos(), computer.deviceStatus(),
                                        computer.hasStorageModule(), computer.hasWirelessRedstoneModule(),
                                        computer.installedLanIds(), computer.hasInternetModule(),
                                        computer.hasSpeakerModule(), computer.hasHardDrive(),
                                        computer.songNames(), computer.playlistIds()));
                        PacketDistributor.sendToPlayer(serverPlayer,
                                new WarehouseItemsPayload(payload.pos(), computer.warehouseItems()));
                        PacketDistributor.sendToPlayer(serverPlayer,
                                new DisplayDevicesPayload(payload.pos(), computer.displayDeviceEntries()));
                        PacketDistributor.sendToPlayer(serverPlayer,
                                new DeviceListPayload(payload.pos(), computer.peripheralEntries()));
                    }
                }));
    }
}
