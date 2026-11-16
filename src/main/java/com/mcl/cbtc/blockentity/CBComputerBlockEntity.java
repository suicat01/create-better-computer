package com.mcl.cbtc.blockentity;

import com.mcl.cbtc.CreateBetterComputer;
import com.mcl.cbtc.core.Node;
import com.mcl.cbtc.core.NodeTypes;
import com.mcl.cbtc.core.Program;
import com.mcl.cbtc.core.Value;
import com.mcl.cbtc.core.vm.MachineHost;
import com.mcl.cbtc.core.vm.ProgramVM;
import com.mcl.cbtc.integration.create.LinkEndpoint;
import com.mcl.cbtc.menu.ComputerMenu;
import com.mcl.cbtc.network.ProgramSyncPayload;
import com.mcl.cbtc.network.MusicStateStore;
import com.mcl.cbtc.block.CBComputerBlock;
import com.mcl.cbtc.registry.CbcBlockEntities;
import com.mcl.cbtc.signal.BroadcastBus;
import com.mcl.cbtc.signal.ChannelId;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

/**
 * CB电脑主机。服务端负责执行积木程序，客户端只负责打开编辑界面。
 */
public class CBComputerBlockEntity extends BlockEntity implements MenuProvider, MachineHost, BroadcastBus.Listener,
        com.mcl.cbtc.lan.LanBus.Listener, com.mcl.cbtc.net.InternetBus.Listener, com.mcl.cbtc.net.ChatBus.Listener {
    /** 显示链接最多可读出的行数。 */
    public static final int DISPLAY_LINES = 8;
    /** 开机时屏幕两帧的切换间隔（tick）。 */
    private static final int ANIM_TICKS_PER_FRAME = 10;
    private static final java.time.format.DateTimeFormatter REAL_TIME_FORMAT =
            java.time.format.DateTimeFormatter.ofPattern("yyyy年M月d日HH:mm:ss");

    private final Program program = new Program();
    private final ProgramVM vm = new ProgramVM(this);
    private final LinkEndpoint link;
    private final String[] displayLines = new String[DISPLAY_LINES];

    /** 是否已开机。关机时虚拟机不推进，屏幕显示关闭贴图。 */
    private boolean powered;
    /** 计算机名字，放置时由玩家指定，用作聊天输出前缀。 */
    private String name = "CB电脑";

    private ItemStack frequencyFirst = ItemStack.EMPTY;
    private ItemStack frequencySecond = ItemStack.EMPTY;
    private int linkStrength;
    private String groupId = "";
    /** 上一次已写进日志的虚拟机错误，用来避免每 tick 重复打印。 */
    private String lastVmError = "";
    /** 上一次发送朗读/音效的 gameTime，用于节流。 */
    private long lastSoundSendTick = -1;
    private int wirelessReceived;
    /** 按频率对分别记录收到的强度，键是"红物品|蓝物品"。 */
    private final java.util.Map<String, Integer> wirelessReceivedByChannel = new java.util.LinkedHashMap<>();
    private int lastWirelessReceived;
    private int wirelessSendStrength;
    /** 发信积木执行后保持点亮的刻数：1 刻，松开/停止后下一刻立即熄灭。 */
    private static final long SEND_PULSE_TICKS = 1;
    /** 发信积木最近一次执行的游戏刻：超过脉冲保持时间就熄灭。 */
    private long wirelessSendTick = -1;
    /** 当前是否处于发信状态。 */
    private boolean wirelessSending;

    /** 线缆发信当前亮着的序列号。 */
    private final java.util.Set<Integer> cableActiveSerials = new java.util.HashSet<>();
    /** 序列号 → 最近一次执行发信积木的游戏刻。 */
    private final java.util.Map<Integer, Long> cableSendTick = new java.util.HashMap<>();
    /** 线缆发信写入的拓展器位置。 */
    private net.minecraft.core.BlockPos cableSendPos;

    /** 当前已加入的局域网：键是规范化后的局域网 ID；换卡或拔卡时随 tick 同步增删。 */
    private final java.util.Map<String, com.mcl.cbtc.lan.LanBus.LanNetwork> lanNetworks =
            new java.util.LinkedHashMap<>();
    /** 当前是否已接入互联网（拓展器里有互联网模块卡）。 */
    private boolean internetJoined;

    /** 本机最近一次网络歌曲播放的声源与听众，读取类积木据此取状态。 */
    private String musicSourceKey = "";
    /** 当前正在播放的歌曲直链；用于避免循环里每刻重复下发同一首歌。 */
    private String musicUrl = "";
    private java.util.Set<java.util.UUID> musicListeners = java.util.Set.of();

    /** 本次发声的方块位置；null 表示当前没有在发声。 */
    private net.minecraft.core.BlockPos soundParticlePos;
    /** 发声剩余刻数：每次发声都会重置，持续播放时被反复续期。 */
    private int soundParticleTicks;
    /** 粒子生成节流：每隔几刻冒一次，避免刷屏。 */
    private static final int SOUND_PARTICLE_INTERVAL = 4;
    private int soundParticleTimer;

    /** 连线方位角缺少 B 设备时的提示节流，避免每刻刷日志。 */
    private long lastNavLineWarnTick = -1000;

    /** 火控单位正在执行的「瞄准并击杀全部锁定目标」任务；null 表示没有任务。 */
    @Nullable
    private KillJob killJob;

    /** 火控击杀任务：任务期间持续瞄准并开火，玩家的瞄准/开火指令被忽略。 */
    private static final class KillJob {
        final String sentryId;
        final String sightId;
        final String order;

        KillJob(String sentryId, String sightId, String order) {
            this.sentryId = sentryId;
            this.sightId = sightId;
            this.order = order;
        }
    }

    public CBComputerBlockEntity(BlockPos pos, BlockState state) {
        super(CbcBlockEntities.COMPUTER.get(), pos, state);
        this.link = new LinkEndpoint(() -> this.level, pos, true);
        java.util.Arrays.fill(displayLines, "");
    }

    public Program program() {
        return program;
    }

    /**
     * 把方块实体上的数据写进物品的方块实体数据组件。
     * 掉落物重新放置时由 BlockItem 自动回填，拆下来再装回去程序不会丢。
     * <p>
     * BLOCK_ENTITY_DATA 里必须带 {@code id}（方块实体类型 id），否则保存物品时
     * 会因为 "Missing id for entity" 崩溃。
     */
    public void saveToStack(ItemStack stack) {
        if (level == null) {
            return;
        }
        CompoundTag data = new CompoundTag();
        saveAdditional(data, level.registryAccess());
        CustomData.set(DataComponents.BLOCK_ENTITY_DATA, stack,
                com.mcl.cbtc.item.BlockEntityCarrierItem.withId(data, CbcBlockEntities.COMPUTER.get()));
    }

    public ProgramVM vm() {
        return vm;
    }

    public String[] displayLines() {
        return displayLines;
    }

    public LinkEndpoint linkEndpoint() {
        return link;
    }

    // ---------------------------------------------------------------- tick

    public void serverTick() {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        if (powered) {
            updateLanMembership();
            updateInternetMembership();
            updateWirelessChannels();
            // 条件积木要判断"此刻是否有信号"，所以每刻同步持续强度，而不只在变化时投递
            vm.setWirelessStrength(wirelessReceivedByChannel);
            if (wirelessReceived != lastWirelessReceived) {
                lastWirelessReceived = wirelessReceived;
                vm.receiveWirelessRedstone(wirelessReceived);
            }
            vm.tick(serverLevel.getGameTime());
            // 火控击杀任务独立于积木执行流运行，每刻推进一次
            tickKillJob();
        }
        // 发信积木"按住才亮"：本刻没再执行就熄灭，关机时也走这里清零
        tickWirelessTransmitter();
        tickCableTransmitter();
        tickSoundParticles();
        if (serverLevel.getGameTime() % 20 == 0) {
            for (var player : serverLevel.players()) {
                if (player.containerMenu instanceof ComputerMenu menu && menu.computer() == this
                        && player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
                    PacketDistributor.sendToPlayer(serverPlayer,
                            new com.mcl.cbtc.network.WarehouseItemsPayload(worldPosition, warehouseItems()));
                net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(serverPlayer,
                        new com.mcl.cbtc.network.DisplayDevicesPayload(worldPosition, displayDeviceEntries()));
                // 局域网卡片可随时插拔，状态也随周期同步，积木里的"局域网"下拉才能及时刷新
                net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(serverPlayer,
                        new com.mcl.cbtc.network.DeviceStatusPayload(worldPosition, deviceStatus(),
                                hasStorageModule(), hasWirelessRedstoneModule(), hasCableModule(), installedLanIds(),
                                hasInternetModule(), hasSpeakerModule(), hasHardDrive(),
                                songNames(), playlistIds()));
                }
            }
        }
        // 虚拟机报错只写一次日志，避免每 tick 刷屏
        String error = vm.lastError();
        if (!error.isEmpty() && !error.equals(lastVmError)) {
            lastVmError = error;
            CreateBetterComputer.LOGGER.warn("[{}] 程序执行出错：{} {}", name, error, worldPosition.toShortString());
        }
        updateRunningState();
    }

    /** 开关机。潜行右键调用，开机时播放开机音效。 */
    public void togglePower() {
        powered = !powered;
        lastVmError = "";
        CreateBetterComputer.LOGGER.info("[{}] {}，节点 {} 个 {}", name, powered ? "开机" : "关机",
                program.nodes().size(), worldPosition.toShortString());
        if (powered) {
            vm.setProgram(program);
            if (level != null) {
                level.playSound(null, worldPosition, SoundEvents.NOTE_BLOCK_PLING.value(),
                        SoundSource.BLOCKS, 0.7F, 1.4F);
            }
        } else {
            vm.reset();
            // 关机时火控击杀任务一并结束，避免重新开机后继续抢占瞄准/开火
            killJob = null;
            leaveLan();
            leaveInternet();
            // 关机时正在放的网络歌曲也要一起停掉
            stopMusic();
        }
        updateRunningState();
        setChanged();
    }

    public boolean isPowered() {
        return powered;
    }

    /** 把 blockstate 上的 running / frame 同步到实际状态。running 只表示开关机，与是否有程序无关。 */
    private void updateRunningState() {
        BlockState state = getBlockState();
        boolean running = powered;
        int frame = powered ? (int) ((level.getGameTime() / ANIM_TICKS_PER_FRAME) % 2) : 0;
        if (state.getValue(CBComputerBlock.RUNNING) != running || state.getValue(CBComputerBlock.FRAME) != frame) {
            level.setBlock(worldPosition, state
                    .setValue(CBComputerBlock.RUNNING, running)
                    .setValue(CBComputerBlock.FRAME, frame), 3);
        }
    }

    // ---------------------------------------------------------------- 程序编辑

    public void applyProgram(Program incoming) {
        program.replaceWith(incoming);
        vm.setProgram(program);
        resubscribeBroadcasts();
        setChanged();
        CreateBetterComputer.LOGGER.info("[{}] 程序已更新：{} {}", name, program.summary(),
                worldPosition.toShortString());
        for (var w : program.wires()) {
            CreateBetterComputer.LOGGER.info("[{}] 连线 {}:{} -> {}:{}", name,
                    w.fromNode(), w.fromPort(), w.toNode(), w.toPort());
        }
    }

    private void resubscribeBroadcasts() {
        BroadcastBus.unsubscribeAll(this);
        for (var node : program.nodesOfType("on_broadcast")) {
            BroadcastBus.subscribe(ChannelId.parse(node.param("channel")), this);
        }
    }

    // ---------------------------------------------------------------- 局域网

    /** 拓展器里是否有已配置的局域网模块卡。 */
    public boolean hasLanModule() {
        return !installedLanIds().isEmpty();
    }

    /** 拓展器里所有已配置模块卡给出的局域网 ID，按槽位顺序去重；可同时加入多个局域网。 */
    public java.util.List<String> installedLanIds() {
        ExpanderBlockEntity expander = attachedExpander();
        if (expander == null) {
            return java.util.List.of();
        }
        java.util.List<String> ids = new ArrayList<>();
        for (int i = 0; i < expander.getContainerSize(); i++) {
            ItemStack stack = expander.getItem(i);
            if (stack.getItem() instanceof com.mcl.cbtc.item.LanModuleItem
                    && com.mcl.cbtc.item.LanModuleItem.isConfigured(stack)) {
                String id = com.mcl.cbtc.lan.LanRegistry.normalize(com.mcl.cbtc.item.LanModuleItem.lanId(stack));
                if (!id.isEmpty() && !ids.contains(id)) {
                    ids.add(id);
                }
            }
        }
        return ids;
    }

    /** 按拓展器里的模块卡把电脑挂到对应的局域网上：新卡加入，拔掉的卡退出。 */
    private void updateLanMembership() {
        java.util.List<String> targets = installedLanIds();
        java.util.Iterator<java.util.Map.Entry<String, com.mcl.cbtc.lan.LanBus.LanNetwork>> it =
                lanNetworks.entrySet().iterator();
        while (it.hasNext()) {
            var entry = it.next();
            if (!targets.contains(entry.getKey())) {
                entry.getValue().leave(this);
                it.remove();
            }
        }
        for (String id : targets) {
            if (!lanNetworks.containsKey(id)) {
                com.mcl.cbtc.lan.LanBus.LanNetwork network = com.mcl.cbtc.lan.LanBus.network(id);
                network.join(this);
                lanNetworks.put(id, network);
            }
        }
    }

    /** 关闭或拆掉电脑时退出全部局域网，避免残留在成员表里。 */
    public void leaveLan() {
        for (var network : lanNetworks.values()) {
            network.leave(this);
        }
        lanNetworks.clear();
    }

    /**
     * 积木指定的目标局域网。
     * 积木只能选单个网，所以参数为空（没选、或选的网已被拔卡）时不发往任何网。
     */
    private java.util.List<com.mcl.cbtc.lan.LanBus.LanNetwork> targetNetworks(String network) {
        String id = com.mcl.cbtc.lan.LanRegistry.normalize(network);
        if (id.isEmpty()) {
            return java.util.List.of();
        }
        var one = lanNetworks.get(id);
        return one == null ? java.util.List.of() : java.util.List.of(one);
    }

    @Override
    public void onLanBroadcast(com.mcl.cbtc.lan.LanBus.LanMessage message) {
        vm.receiveLanBroadcast(message.networkId(), message.channel().toString(), message.value());
    }

    @Override
    public void sendLanBroadcast(String network, String channel) {
        if (channel == null || channel.isBlank()) {
            return;
        }
        // 积木里填的是裸频道名，统一走默认命名空间，避免重复叠加前缀
        ChannelId.Message message = new ChannelId.Message(ChannelId.parse(channel), Value.ZERO, false);
        for (var lan : targetNetworks(network)) {
            lan.publish(message);
        }
    }

    @Override
    public Value lanVariable(String network, String name) {
        if (name == null || name.isBlank()) {
            return Value.ZERO;
        }
        var lan = lanNetworks.get(com.mcl.cbtc.lan.LanRegistry.normalize(network));
        return lan == null ? Value.ZERO : lan.variable(name.trim());
    }

    @Override
    public void setLanVariable(String network, String name, Value value) {
        if (name == null || name.isBlank()) {
            return;
        }
        for (var lan : targetNetworks(network)) {
            lan.setVariable(name.trim(), value);
        }
    }

    @Override
    public void onBroadcast(ChannelId.Message message) {
        vm.receiveBroadcast(message.channel().toString(), message.value());
    }

    // ---------------------------------------------------------------- 互联网

    /** 拓展器里是否装了互联网模块卡。 */
    public boolean hasInternetModule() {
        ExpanderBlockEntity expander = attachedExpander();
        if (expander == null) {
            return false;
        }
        for (int i = 0; i < expander.getContainerSize(); i++) {
            if (expander.getItem(i).getItem() instanceof com.mcl.cbtc.item.InternetModuleItem) {
                return true;
            }
        }
        return false;
    }

    /** 按拓展器里的互联网模块卡决定是否接入互联网；拔卡后退出。 */
    private void updateInternetMembership() {
        boolean present = hasInternetModule();
        if (present == internetJoined) {
            return;
        }
        internetJoined = present;
        if (present) {
            com.mcl.cbtc.net.InternetBus.join(this);
            com.mcl.cbtc.net.ChatBus.join(this);
        } else {
            leaveInternet();
        }
    }

    /** 关机或拆掉电脑时退出互联网，避免残留在成员表里。 */
    public void leaveInternet() {
        com.mcl.cbtc.net.InternetBus.leave(this);
        com.mcl.cbtc.net.ChatBus.leave(this);
        internetJoined = false;
    }

    @Override
    public boolean internetConnected() {
        return internetJoined;
    }

    @Override
    public void onInternetBroadcast(com.mcl.cbtc.net.InternetBus.Message message) {
        vm.receiveInternetBroadcast(message.channel().toString());
    }

    @Override
    public void sendInternetBroadcast(String channel) {
        if (!internetJoined || channel == null || channel.isBlank()) {
            return;
        }
        com.mcl.cbtc.net.InternetBus.publish(
                new ChannelId.Message(ChannelId.parse(channel), Value.ZERO, false));
    }

    @Override
    public Value internetVariable(String name) {
        if (!internetJoined || name == null || name.isBlank()) {
            return Value.ZERO;
        }
        return com.mcl.cbtc.net.InternetNet.variable(name.trim());
    }

    @Override
    public void setInternetVariable(String name, Value value) {
        if (!internetJoined || name == null || name.isBlank()) {
            return;
        }
        com.mcl.cbtc.net.InternetNet.setVariable(name.trim(), value);
    }

    @Override
    public void onChat(com.mcl.cbtc.net.ChatBus.ChatEvent event) {
        // 玩家说话的默认目标是全体电脑，凡是接入互联网的电脑都能识别；
        // 电脑之间点名时，只有被点名的那台认这条消息。
        vm.receiveChat(event.text(), event.reachesComputer(name));
    }

    /** 玩家聊天按积木的目标筛选；@a / @e 接受所有玩家，@玩家名只接受该玩家。 */
    public void onPlayerChat(String username, String text) {
        vm.receivePlayerChat(username, text);
    }

    @Override
    public void sendChat(String text, String target) {
        if (!(level instanceof ServerLevel serverLevel) || text == null || text.isEmpty()) {
            return;
        }
        com.mcl.cbtc.net.ChatTarget.Target parsed = com.mcl.cbtc.net.ChatTarget.parse(target);
        var server = serverLevel.getServer();
        if (com.mcl.cbtc.net.ChatTarget.ALL_PLAYERS.equalsIgnoreCase(target == null ? "" : target.trim())) {
            // 「在聊天框发送」面向全体玩家，不能套用私聊的收件提示
            Component broadcast = Component.literal("[" + name + "] ").withStyle(ChatFormatting.GOLD)
                    .append(Component.literal(text).withStyle(ChatFormatting.WHITE));
            server.getPlayerList().broadcastSystemMessage(broadcast, false);
            CreateBetterComputer.LOGGER.info("[{}] 聊天 → @a：{} {}", name, text, worldPosition.toShortString());
            return;
        }
        // 与原版 /msg 相同：接收方看到"[电脑名 悄悄地对你说] 内容"
        Component line = Component.translatable("commands.message.display.incoming",
                Component.literal(name).withStyle(ChatFormatting.GOLD), text);
        // 私聊只面向玩家：目标一律按玩家名处理，找不到人就不发
        var player = server.getPlayerList().getPlayerByName(parsed.name());
        if (player != null) {
            player.sendSystemMessage(line);
        }
        CreateBetterComputer.LOGGER.info("[{}] 聊天 → {}：{} {}", name, target, text,
                worldPosition.toShortString());
    }

    // ---------------------------------------------------------------- MachineHost

    @Override
    public void setLinkStrength(int strength) {
        this.linkStrength = Math.max(0, Math.min(15, strength));
        link.setStrength(linkStrength);
    }

    @Override
    public void broadcast(String channel, Value value) {
        if (channel == null || channel.isBlank()) {
            return;
        }
        BroadcastBus.publish(ChannelId.parse(channel), value, !groupId.isBlank());
    }

    @Override
    public void chat(String text) {
        if (!(level instanceof ServerLevel serverLevel) || text == null || text.isBlank()) {
            return;
        }
        Component line = Component.literal("[" + name + "] ").withStyle(ChatFormatting.GOLD)
                .append(Component.literal(text).withStyle(ChatFormatting.WHITE));
        serverLevel.getServer().getPlayerList().broadcastSystemMessage(line, false);
        // 聊天输出同样写进日志，方便排查程序是否真的在执行
        CreateBetterComputer.LOGGER.info("[{}] {} {}", name, text, worldPosition.toShortString());
    }

    @Override
    public long gameTotalDays() {
        return level == null ? 0 : level.getGameTime() / 24000L;
    }

    @Override
    public long gameTicksOfDay() {
        return level == null ? 0 : level.getDayTime() % 24000L;
    }

    @Override
    public int displayLine() {
        return 0;
    }

    @Override
    public void setDisplayText(int line, String text) {
        if (line < 0 || line >= DISPLAY_LINES) {
            return;
        }
        displayLines[line] = text == null ? "" : text;
    }

    @Override
    public Value query(String key) {
        return Value.ZERO;
    }

    /**
     * 朗读/音效的发送节流：同一台电脑每 tick 最多发出 1 条，避免循环里每 tick 刷屏。
     * 返回 true 表示本次允许发送。
     */
    private boolean allowSoundSend() {
        long now = level == null ? 0 : level.getGameTime();
        if (now <= lastSoundSendTick) {
            return false;
        }
        lastSoundSendTick = now;
        return true;
    }

    @Override
    public void murmur(String text, String device) {
        if (!(level instanceof ServerLevel serverLevel) || text == null || text.isBlank()) {
            return;
        }
        if (!allowSoundSend()) {
            return;
        }
        // 默念不发声，只把文字发给同一维度里的玩家，由客户端画在方块上方
        // 下发设备自身的方块坐标（不投影到主世界）：客户端在方块实体渲染阶段画字，
        // Sable 渲染结构时会施加结构姿态，文字于是跟着结构移动、倾斜
        net.minecraft.core.BlockPos local = soundSourcePos(device);
        // 用设备身份而不是坐标做 key：同一设备重复默念只更新同一条浮字
        String key = murmurKey(serverLevel, device, local);
        for (var player : serverLevel.players()) {
            if (player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
                net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(serverPlayer,
                        new com.mcl.cbtc.network.MurmurPayload(local, text, key));
            }
        }
    }

    @Override
    public void playSound(String soundId, double volume, int range, String device) {
        if (!(level instanceof ServerLevel serverLevel) || soundId == null || soundId.isBlank()) {
            return;
        }
        if (!allowSoundSend()) {
            return;
        }
        // 目前只有"此电脑"能发声；接入音响等外设后在这里按 device 换成外设坐标
        // 发声点同样要投影到主世界，否则装在移动结构上的电脑声音会落在结构存储区里
        net.minecraft.core.BlockPos source =
                com.mcl.cbtc.device.Devices.projectPos(serverLevel, soundSourcePos(device));
        net.minecraft.resources.ResourceLocation id =
                net.minecraft.resources.ResourceLocation.tryParse(soundId);
        if (id == null || !serverLevel.getServer().registryAccess()
                .registryOrThrow(net.minecraft.core.registries.Registries.SOUND_EVENT).containsKey(id)) {
            CreateBetterComputer.LOGGER.warn("[{}] 未知音效 id：{}", name, soundId);
            return;
        }
        // 按范围挑选接收者：范围内的客户端自己播放，能看到复述/音量设置生效
        double radius = Math.max(0, range);
        for (var player : serverLevel.players()) {
            if (player.distanceToSqr(source.getX() + 0.5, source.getY() + 0.5,
                    source.getZ() + 0.5) <= radius * radius) {
                if (player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
                    net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(serverPlayer,
                            new com.mcl.cbtc.network.SoundPlayPayload(source, id.toString(),
                                    (float) Math.max(0, Math.min(1, volume)), range));
                }
            }
        }
        markSoundPlayed(source);
    }

    /**
     * 发声位置。
     * <p>
     * "此电脑"回落到电脑自身坐标；选的是音响且那台音响在线时，
     * 用音响的坐标发声，于是声音从音响那里传出来。
     */
    private net.minecraft.core.BlockPos soundSourcePos(String device) {
        if (isSelfSoundDevice(device)) {
            return worldPosition;
        }
        com.mcl.cbtc.blockentity.SoundDevice speaker = speakerAt(device);
        return speaker == null ? worldPosition : speaker.getBlockPos();
    }

    /**
     * 默念浮字的稳定身份：与投影后的世界坐标无关。
     * 电脑用自身在结构存储区的坐标（结构移动时它不变），音响用绑定的设备 id。
     */
    private String murmurKey(net.minecraft.server.level.ServerLevel serverLevel, String device,
                             net.minecraft.core.BlockPos local) {
        if (isSelfSoundDevice(device)) {
            return "self:" + serverLevel.dimension().location() + ":" + local.asLong();
        }
        return "dev:" + device;
    }

    /**
     * 记一次发声，用于在发声方块顶端冒音符粒子。
     * <p>
     * 每刻都被调用也没关系：位置相同就只续期，位置变了就改挂到新方块上。
     * 剩余刻数是"多久没再发声就停"的宽限，给持续播放一个稳定的显示窗口。
     */
    private void markSoundPlayed(net.minecraft.core.BlockPos pos) {
        soundParticlePos = pos;
        soundParticleTicks = 10;
    }

    /** 在发声方块顶端冒一个彩色音符。 */
    private void spawnSoundParticles() {
        if (soundParticlePos == null || !(level instanceof ServerLevel serverLevel)) {
            return;
        }
        // 音符合成器上方的悬浮点：稍微偏一点，看起来是从方块里飘出来的
        double x = soundParticlePos.getX() + 0.5 + (level.random.nextDouble() - 0.5) * 0.7;
        double y = soundParticlePos.getY() + 1.0 + level.random.nextDouble() * 0.2;
        double z = soundParticlePos.getZ() + 0.5 + (level.random.nextDouble() - 0.5) * 0.7;
        serverLevel.sendParticles(net.minecraft.core.particles.ParticleTypes.NOTE,
                x, y, z, 1, 0, 0.15, 0, level.random.nextDouble());
    }

    /** 每刻维护发声粒子的有效期与节流。 */
    private void tickSoundParticles() {
        if (soundParticleTicks <= 0) {
            soundParticlePos = null;
            soundParticleTimer = 0;
            return;
        }
        soundParticleTicks--;
        if (++soundParticleTimer < SOUND_PARTICLE_INTERVAL) {
            return;
        }
        soundParticleTimer = 0;
        spawnSoundParticles();
    }

    // ---------------------------------------------------------------- 网络歌曲

    /** 歌曲库里全部歌名，供积木的歌曲下拉使用。 */
    public java.util.List<String> songNames() {
        java.util.List<String> names = new java.util.ArrayList<>();
        for (ItemStack disc : allSongDiscs()) {
            com.mcl.cbtc.integration.netmusic.NetMusic.Song info =
                    com.mcl.cbtc.integration.netmusic.NetMusic.song(disc);
            if (info != null && !names.contains(info.name())) {
                names.add(info.name());
            }
        }
        return names;
    }

    /** 全部歌单（即音乐模块卡）的 UUID 清单，供积木的歌单下拉使用。 */
    public java.util.List<String> playlistIds() {
        ExpanderBlockEntity expander = attachedExpander();
        java.util.List<String> ids = new ArrayList<>();
        if (expander == null) {
            return ids;
        }
        for (ItemStack module : expander.musicModules()) {
            java.util.UUID id = com.mcl.cbtc.item.MusicModuleItem.id(module);
            if (id != null) {
                ids.add(id.toString());
            }
        }
        return ids;
    }

    /** 拓展器里全部音乐模块卡构成的曲目表，按模块顺序、每张卡内按槽位顺序。 */
    private java.util.List<ItemStack> allSongDiscs() {
        ExpanderBlockEntity expander = attachedExpander();
        if (expander == null) {
            return java.util.List.of();
        }
        java.util.List<ItemStack> discs = new java.util.ArrayList<>();
        for (ItemStack module : expander.musicModules()) {
            discs.addAll(com.mcl.cbtc.item.MusicModuleItem.playlist(module));
        }
        return discs;
    }

    @Override
    public boolean playMusic(String song, double volume, int range, String device) {
        if (song == null || song.isBlank()) {
            return false;
        }
        for (ItemStack disc : allSongDiscs()) {
            com.mcl.cbtc.integration.netmusic.NetMusic.Song info =
                    com.mcl.cbtc.integration.netmusic.NetMusic.song(disc);
            if (info != null && (info.name().equals(song) || disc.getHoverName().getString().equals(song))) {
                return startMusic(info, volume, range, device);
            }
        }
        CreateBetterComputer.LOGGER.warn("[{}] 歌曲库里没有这首歌：{}", name, song);
        return false;
    }

    @Override
    public boolean playMusicFromPlaylist(String playlist, int index, double volume, int range, String device) {
        ExpanderBlockEntity expander = attachedExpander();
        if (expander == null) {
            return false;
        }
        String wanted = playlist == null ? "" : playlist.trim();
        for (ItemStack module : expander.musicModules()) {
            java.util.UUID id = com.mcl.cbtc.item.MusicModuleItem.id(module);
            // 没选歌单时退化为"所有模块的第一首"，方便只有一个模块的常见情况
            if (!wanted.isEmpty() && (id == null || !id.toString().equalsIgnoreCase(wanted))) {
                continue;
            }
            java.util.List<ItemStack> songs = com.mcl.cbtc.item.MusicModuleItem.playlist(module);
            int at = index - 1;
            if (at >= 0 && at < songs.size()) {
                com.mcl.cbtc.integration.netmusic.NetMusic.Song info =
                        com.mcl.cbtc.integration.netmusic.NetMusic.song(songs.get(at));
                if (info != null) {
                    return startMusic(info, volume, range, device);
                }
            }
        }
        return false;
    }

    /** 把一首歌下发给发声位置附近的玩家。 */
    private boolean startMusic(com.mcl.cbtc.integration.netmusic.NetMusic.Song info,
                               double volume, int range, String device) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return false;
        }
        if (!com.mcl.cbtc.integration.netmusic.NetMusic.loaded()) {
            return false;
        }
        net.minecraft.core.BlockPos source = soundSourcePos(device);
        String sourceKey = MusicStateStore.sourceKey(source);
        // 换了一台发声设备：上一台就不该继续响，直接让它的听众停下
        if (!musicSourceKey.isEmpty() && !musicSourceKey.equals(sourceKey)) {
            stopMusic();
        }
        // 同一首歌在同一台设备上仍在播时什么都不做：
        // 播放积木常被放在循环里每刻执行，重复下发会让客户端每刻重启一次播放，
        // 声音池被瞬间占满，主线程卡死在创建音频流上。
        // 客户端上报过"已停止"后状态查不到，此时允许重新播放
        if (sourceKey.equals(musicSourceKey) && info.url().equals(musicUrl)
                && pickMusicState(sourceKey) != null) {
            // 歌还在放：只续期粒子，不重新下发
            markSoundPlayed(source);
            return true;
        }
        double radius = Math.max(0, range);
        java.util.Set<java.util.UUID> listeners = new java.util.HashSet<>();
        for (var player : serverLevel.players()) {
            if (player.distanceToSqr(source.getX() + 0.5, source.getY() + 0.5,
                    source.getZ() + 0.5) <= radius * radius
                    && player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
                com.mcl.cbtc.integration.netmusic.NetMusic.playTo(serverPlayer, source, info,
                        (float) Math.max(0, Math.min(1, volume)), (int) radius);
                listeners.add(serverPlayer.getUUID());
            }
        }
        // 本次发声的声源与听众登记下来：读取类积木按"谁在听、哪个设备在响"取状态
        musicSourceKey = sourceKey;
        musicUrl = info.url();
        musicListeners = listeners;
        markSoundPlayed(source);
        return true;
    }

    /** 停掉本机发起的网络歌曲：通知所有听众的客户端，并清空服务端记录。 */
    private void stopMusic() {
        if (!(level instanceof ServerLevel serverLevel) || musicSourceKey.isEmpty()) {
            return;
        }
        net.minecraft.core.BlockPos source = parseSourcePos(musicSourceKey);
        for (java.util.UUID id : musicListeners) {
            var player = serverLevel.getServer().getPlayerList().getPlayer(id);
            if (player != null) {
                com.mcl.cbtc.integration.netmusic.NetMusic.stopTo(player, source);
            }
        }
        MusicStateStore.forgetSourceKey(musicSourceKey);
        musicSourceKey = "";
        musicUrl = "";
        musicListeners = java.util.Set.of();
    }

    /** 从"x,y,z"声源串还原坐标；解析不出来时退回本机位置。 */
    private net.minecraft.core.BlockPos parseSourcePos(String key) {
        String[] parts = key.split(",");
        if (parts.length != 3) {
            return worldPosition;
        }
        try {
            return new net.minecraft.core.BlockPos(Integer.parseInt(parts[0].trim()),
                    Integer.parseInt(parts[1].trim()), Integer.parseInt(parts[2].trim()));
        } catch (NumberFormatException e) {
            return worldPosition;
        }
    }

    @Override
    public Value musicState(String device, String key) {
        if (!(level instanceof ServerLevel)) {
            return Value.ZERO;
        }
        return readMusicState(MusicStateStore.sourceKey(soundSourcePos(device)), key);
    }

    /** 从所有听众的上报里挑一条该声源的状态。 */
    private Value readMusicState(String sourceKey, String key) {
        com.mcl.cbtc.network.MusicStatePayload state = pickMusicState(sourceKey);
        if (state == null) {
            return switch (key) {
                case "song", "artist", "lyric", "lyric_original" -> Value.of("");
                default -> Value.ZERO;
            };
        }
        return switch (key) {
            case "song" -> Value.of(state.songName());
            case "artist" -> Value.of(state.artist());
            case "total" -> Value.of(state.total());
            case "time" -> Value.of(state.elapsed());
            case "lyric" -> Value.of(state.lyricTranslated().isBlank()
                    ? state.lyricOriginal() : state.lyricTranslated());
            case "lyric_original" -> Value.of(state.lyricOriginal());
            default -> Value.ZERO;
        };
    }

    /** 在听众里找一条对应声源的上报。 */
    @org.jetbrains.annotations.Nullable
    private com.mcl.cbtc.network.MusicStatePayload pickMusicState(String sourceKey) {
        for (java.util.UUID id : musicListeners) {
            com.mcl.cbtc.network.MusicStatePayload state = MusicStateStore.state(id, sourceKey);
            if (state != null) {
                return state;
            }
        }
        return null;
    }

    @Override
    public void stopSounds(String device) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        // "停止声音"也停掉本机正在放的网络歌曲，避免歌一直响下去
        if (isSelfSoundDevice(device)
                || musicSourceKey.equals(MusicStateStore.sourceKey(soundSourcePos(device)))) {
            stopMusic();
        }
        net.minecraft.core.BlockPos source = soundSourcePos(device);
        // 向范围内的玩家下发"停止"，由客户端清空朗读队列并停止声音
        double radius = maxSoundRange();
        for (var player : serverLevel.players()) {
            if (player.distanceToSqr(source.getX() + 0.5, source.getY() + 0.5,
                    source.getZ() + 0.5) > radius * radius) {
                continue;
            }
            if (player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
                net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(serverPlayer,
                        new com.mcl.cbtc.network.SoundPlayPayload(source,
                                com.mcl.cbtc.network.SoundPlayPayload.STOP_SOUND, 1.0F,
                                (int) radius));
            }
        }
    }

    // ---------------------------------------------------------------- 仓储物流

    @Nullable
    private ExpanderBlockEntity attachedExpander() {
        if (level == null) {
            return null;
        }
        for (var direction : net.minecraft.core.Direction.values()) {
            if (level.getBlockEntity(worldPosition.relative(direction)) instanceof ExpanderBlockEntity expander
                    && expander.isAttached()) {
                return expander;
            }
        }
        return null;
    }

    @Nullable
    private ItemStack storageLinkModule() {
        ExpanderBlockEntity expander = attachedExpander();
        return expander == null ? null : expander.storageLinkModule();
    }

    @Override
    public int warehouseFreeSpace() {
        ItemStack module = storageLinkModule();
        return module == null ? 0 : com.mcl.cbtc.integration.create.CreateLogistics.freeSpace(module);
    }

    @Override
    public int warehouseCapacity() {
        ItemStack module = storageLinkModule();
        return module == null ? 0 : com.mcl.cbtc.integration.create.CreateLogistics.capacity(module);
    }

    public java.util.List<com.simibubi.create.content.logistics.BigItemStack> warehouseItems() {
        ItemStack module = storageLinkModule();
        if (module == null || com.mcl.cbtc.item.StorageLinkModuleItem.network(module) == null) {
            return java.util.List.of();
        }
        return com.simibubi.create.content.logistics.packagerLink.LogisticsManager.getSummaryOfNetwork(
                com.mcl.cbtc.item.StorageLinkModuleItem.network(module), true).getStacksByCount();
    }

    @Override
    public int warehouseItemCount(String itemId) {
        ItemStack module = storageLinkModule();
        return module == null ? 0 : com.mcl.cbtc.integration.create.CreateLogistics.itemCount(module, itemId);
    }

    @Override
    public boolean sendPackage(String itemId, int count, String address) {
        ItemStack module = storageLinkModule();
        return module != null
                && com.mcl.cbtc.integration.create.CreateLogistics.sendPackage(module, itemId, count, address);
    }

    public java.util.List<ItemStack> displayModules() {
        ExpanderBlockEntity expander = attachedExpander();
        if (expander == null) {
            return java.util.List.of();
        }
        java.util.List<ItemStack> result = new ArrayList<>();
        for (int i = 0; i < expander.getContainerSize(); i++) {
            ItemStack stack = expander.getItem(i);
            if (stack.getItem() instanceof com.mcl.cbtc.item.DisplayLinkModuleItem
                    && !com.mcl.cbtc.item.DisplayLinkModuleItem.devices(stack).isEmpty()) {
                result.add(stack);
            }
        }
        return result;
    }

    public boolean hasDisplayModule() {
        return !displayModules().isEmpty();
    }

    /** 插在拓展器里的全部外设绑定模块卡。 */
    public java.util.List<ItemStack> deviceModules() {
        ExpanderBlockEntity expander = attachedExpander();
        if (expander == null) {
            return java.util.List.of();
        }
        java.util.List<ItemStack> result = new ArrayList<>();
        for (int i = 0; i < expander.getContainerSize(); i++) {
            ItemStack stack = expander.getItem(i);
            if (stack.getItem() instanceof com.mcl.cbtc.item.DeviceModuleItem
                    && !com.mcl.cbtc.item.DeviceModuleItem.devices(stack).isEmpty()) {
                result.add(stack);
            }
        }
        return result;
    }

    public boolean hasDeviceModule() {
        return !deviceModules().isEmpty();
    }

    /** 所有已绑定外设的 id → 记录，供积木按 id 找到坐标。读取时顺手把失效的定位校正写回。 */
    public java.util.Map<String, com.mcl.cbtc.item.DeviceModuleItem.Device> boundDevices() {
        java.util.Map<String, com.mcl.cbtc.item.DeviceModuleItem.Device> result = new java.util.HashMap<>();
        for (ItemStack stack : deviceModules()) {
            var before = stack.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA);
            for (var device : com.mcl.cbtc.item.DeviceModuleItem.devices(stack, level)) {
                result.put(device.id(), device);
            }
            var after = stack.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA);
            if (!java.util.Objects.equals(before, after)) {
                setChanged();
            }
        }
        return result;
    }

    /** 外设清单，下发给客户端做下拉候选。 */
    public java.util.List<com.mcl.cbtc.network.DeviceListPayload.Entry> peripheralEntries() {
        java.util.List<com.mcl.cbtc.network.DeviceListPayload.Entry> result = new ArrayList<>();
        for (var device : boundDevices().values()) {
            // 发声设备带上自己的范围上限，客户端夹取输入时按设备算，不按电脑算
            result.add(new com.mcl.cbtc.network.DeviceListPayload.Entry(device.id(), device.name(),
                    device.kind().id(), maxSoundRange(device.id())));
        }
        return result;
    }

    /**
     * 读取导航台的坐标或距离。设备不是导航台、不在线或目标为空时返回 0。
     */
    @Override
    public double readNavTable(String deviceId, String key) {
        com.mcl.cbtc.item.DeviceModuleItem.Device device = boundDevices().get(deviceId);
        if (device == null || device.kind() != com.mcl.cbtc.device.DeviceKind.NAV_TABLE) {
            return 0;
        }
        if (!(level instanceof ServerLevel serverLevel)) {
            return 0;
        }
        ServerLevel target = serverLevel.getServer().getLevel(net.minecraft.resources.ResourceKey.create(
                net.minecraft.core.registries.Registries.DIMENSION, device.dimension()));
        return target == null ? 0 : com.mcl.cbtc.device.Devices.navTable(target, com.mcl.cbtc.device.Devices.currentPos(device.id(), device.pos()), key);
    }

    /** 读取目标定位模块的 X / Z 坐标。 */
    @Override
    public double readTargetLocator(String deviceId, String axis) {
        com.mcl.cbtc.item.DeviceModuleItem.Device device = boundDevices().get(deviceId);
        if (device == null || device.kind() != com.mcl.cbtc.device.DeviceKind.TARGET_LOCATOR) {
            return 0;
        }
        return com.mcl.cbtc.device.TargetLocatorBridge.read(device.id(), axis);
    }

    /** 改写目标定位模块的 X / Z 坐标。 */
    @Override
    public void writeTargetLocator(String deviceId, String axis, double value) {
        com.mcl.cbtc.item.DeviceModuleItem.Device device = boundDevices().get(deviceId);
        if (device == null || device.kind() != com.mcl.cbtc.device.DeviceKind.TARGET_LOCATOR) {
            return;
        }
        com.mcl.cbtc.device.TargetLocatorBridge.write(device.id(), axis, value);
    }

    /** 读取信息转发器第 N 行（从 1 起）的内容；纯数字自动转成可运算的数值。 */
    @Override
    public Value infoLine(String deviceId, String lineParam) {
        com.mcl.cbtc.item.DeviceModuleItem.Device device = boundDevices().get(deviceId);
        if (device == null || device.kind() != com.mcl.cbtc.device.DeviceKind.INFO_TRANSMITTER) {
            return Value.of("");
        }
        if (!(level instanceof ServerLevel serverLevel)) {
            return Value.of("");
        }
        ServerLevel target = serverLevel.getServer().getLevel(net.minecraft.resources.ResourceKey.create(
                net.minecraft.core.registries.Registries.DIMENSION, device.dimension()));
        int line = parseLine(lineParam);
        String text = target == null || line <= 0 ? ""
                : com.mcl.cbtc.device.Devices.infoLine(target,
                        com.mcl.cbtc.device.Devices.currentPos(device.id(), device.pos()), line);
        return infoValue(text);
    }

    /** 积木里的行号参数可能是数字输入框写的字符串（"3"），解析失败时返回 0。 */
    private static int parseLine(String raw) {
        if (raw == null || raw.isBlank()) {
            return 0;
        }
        try {
            return (int) Double.parseDouble(raw.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /**
     * 收到的数据是单个数字时自动变成数值，方便直接参与运算。
     * 允许"气压：42.00%"这种带标签/单位的单数字文本；含多个数字的文本仍按文本返回。
     */
    private static Value infoValue(String text) {
        if (text == null || text.isBlank()) {
            return Value.of("");
        }
        // 标签 + 数字 + 单位的写法解析成带单位的数值（"转速 120 RPM" → 120，单位 RPM），
        // 解析不了（多个数字、时间格式等）仍按文本返回
        com.mcl.cbtc.core.Amounts.Parsed parsed = com.mcl.cbtc.core.Amounts.parse(text);
        return parsed == null ? Value.of(text)
                : new Value.Num(parsed.value(), false, parsed.unit());
    }

    /**
     * 基线方位角：A（前）导航台的目标点相对 A→B（后，辅助外设）连线的夹角。
     * 前方 = 0°，左 = +90°，右 = -90°。
     */
    @Override
    public double navBaselineBearing(String deviceA, String deviceB) {
        com.mcl.cbtc.item.DeviceModuleItem.Device a = boundDevices().get(deviceA);
        if (a == null || a.kind() != com.mcl.cbtc.device.DeviceKind.NAV_TABLE) {
            return Double.NaN;
        }
        com.mcl.cbtc.item.DeviceModuleItem.Device b = boundDevices().get(deviceB);
        if (b == null || !(level instanceof ServerLevel serverLevel)) {
            // B 没绑定时整条线不成立，方位角没有意义；输出 NaN 便于在界面上直接看出是缺数据
            if (b == null && level != null && level.getGameTime() - lastNavLineWarnTick >= 100) {
                lastNavLineWarnTick = level.getGameTime();
                CreateBetterComputer.LOGGER.warn("[{}] 导航台连线：B 设备（{}）未绑定，方位角输出 NaN",
                        name, deviceB == null || deviceB.isBlank() ? "未选择" : deviceB);
            }
            return Double.NaN;
        }
        ServerLevel target = serverLevel.getServer().getLevel(net.minecraft.resources.ResourceKey.create(
                net.minecraft.core.registries.Registries.DIMENSION, a.dimension()));
        if (target == null) {
            return Double.NaN;
        }
        return com.mcl.cbtc.device.Devices.baselineBearing(target, deviceA,
                new com.mcl.cbtc.device.Devices.DeviceLocation(a.subLevelId(), a.pos()),
                deviceB, new com.mcl.cbtc.device.Devices.DeviceLocation(b.subLevelId(), b.pos()));
    }

    /**
     * 打字机当前按下的按键（GLFW 键码）。供"当…按下…时"积木使用。
     */
    @Override
    public java.util.List<Integer> pressedKeys(String deviceId) {
        com.mcl.cbtc.item.DeviceModuleItem.Device device = boundDevices().get(deviceId);
        if (device == null) {
            return java.util.List.of();
        }
        // 枪式遥控器是物品型外设，按键记录在服务端的遥控器输入状态里
        if (device.kind() == com.mcl.cbtc.device.DeviceKind.GUN_REMOTE) {
            return com.mcl.cbtc.device.GunRemoteState.pressed(device.id());
        }
        if (device.kind() != com.mcl.cbtc.device.DeviceKind.TYPEWRITER) {
            return java.util.List.of();
        }
        if (!(level instanceof ServerLevel serverLevel)) {
            return java.util.List.of();
        }
        ServerLevel target = serverLevel.getServer().getLevel(net.minecraft.resources.ResourceKey.create(
                net.minecraft.core.registries.Registries.DIMENSION, device.dimension()));
        return target == null ? java.util.List.of() : com.mcl.cbtc.device.Devices.pressedKeys(target, com.mcl.cbtc.device.Devices.currentPos(device.id(), device.pos()));
    }

    /**
     * 控制指定齿轮箱的输出转速。设备不存在、类型不符或不在线时什么也不做。
     */
    @Override
    public void writeGearbox(String deviceId, float speed, String mode, double value) {
        com.mcl.cbtc.item.DeviceModuleItem.Device device = boundDevices().get(deviceId);
        if (device == null || device.kind() != com.mcl.cbtc.device.DeviceKind.GEARBOX) {
            return;
        }
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        ServerLevel target = serverLevel.getServer().getLevel(net.minecraft.resources.ResourceKey.create(
                net.minecraft.core.registries.Registries.DIMENSION, device.dimension()));
        if (target == null) {
            return;
        }
        com.mcl.cbtc.device.Devices.controlGearbox(target, com.mcl.cbtc.device.Devices.currentPos(device.id(), device.pos()), speed, mode, value);
    }

    /**
     * 雷达映射：把指定网络控制器提供的目标同步给戴着指定瞄准组件的玩家。
     * <p>
     * 目标来自网络控制器自己的过滤结果，客户端只保留 2 秒，所以这条积木需要放进「重复执行」里持续刷新。
     */
    @Override
    public void mapRadarToSight(String radarDeviceId, String sightDeviceId) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        java.util.Map<String, com.mcl.cbtc.item.DeviceModuleItem.Device> devices = boundDevices();
        com.mcl.cbtc.item.DeviceModuleItem.Device radar = devices.get(radarDeviceId);
        if (radar == null || radar.kind() != com.mcl.cbtc.device.DeviceKind.NETWORK_CONTROLLER
                || radar.dimension() == null) {
            return;
        }
        com.mcl.cbtc.item.DeviceModuleItem.Device sight = devices.get(sightDeviceId);
        if (sight == null || sight.kind() != com.mcl.cbtc.device.DeviceKind.GUNNER_SIGHT) {
            return;
        }
        // 没装雷达模组时不去触碰它的类型
        if (!com.mcl.cbtc.compat.Radar.loaded()) {
            return;
        }
        ServerLevel radarLevel = serverLevel.getServer().getLevel(net.minecraft.resources.ResourceKey.create(
                net.minecraft.core.registries.Registries.DIMENSION, radar.dimension()));
        if (radarLevel == null) {
            return;
        }
        net.minecraft.world.level.block.entity.BlockEntity radarEntity = com.mcl.cbtc.device.Devices.blockEntityAt(
                radarLevel, com.mcl.cbtc.device.Devices.currentPos(radar.id(), radar.pos()));
        java.util.List<com.mcl.cbtc.compat.RadarCompat.TrackedEntity> tracked =
                com.mcl.cbtc.compat.RadarCompat.tracked(radarEntity);
        if (tracked.isEmpty()) {
            return;
        }
        net.minecraft.server.level.ServerPlayer wearer = findSightWearer(radarLevel, sight.id());
        if (wearer == null) {
            return;
        }
        // 默认忽略装备者自己
        int wearerId = wearer.getId();
        java.util.List<com.mcl.cbtc.compat.RadarCompat.TrackedEntity> visible =
                new java.util.ArrayList<>(tracked.size());
        for (com.mcl.cbtc.compat.RadarCompat.TrackedEntity target : tracked) {
            if (target.entityId() != wearerId) {
                visible.add(target);
            }
        }
        if (visible.isEmpty()) {
            return;
        }
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(wearer,
                com.mcl.cbtc.network.SightTargetsPayload.of(radarLevel.dimension().location(), visible));
    }

    /**
     * 哨戒动力臂瞄准：让指定动力臂瞄准指定瞄准组件佩戴者的准星。
     * <p>
     * 每次执行只刷新一次瞄准点，所以要放进「重复执行」里持续调用；
     * 动力臂绑定后已进入接管状态，原版自动索敌不会抢回控制权。
     */
    @Override
    public void sentryAim(String sentryDeviceId, String sightDeviceId) {
        if (!(level instanceof ServerLevel serverLevel) || !com.mcl.cbtc.device.Devices.hasFireControl()) {
            return;
        }
        // 锁定击杀任务期间，瞄准指令交给任务自己处理
        if (killJob != null && killJob.sentryId.equals(sentryDeviceId)) {
            return;
        }
        java.util.Map<String, com.mcl.cbtc.item.DeviceModuleItem.Device> devices = boundDevices();
        net.minecraft.world.level.block.entity.BlockEntity arm = sentryArm(devices.get(sentryDeviceId));
        com.mcl.cbtc.item.DeviceModuleItem.Device sight = devices.get(sightDeviceId);
        if (arm == null || sight == null || sight.kind() != com.mcl.cbtc.device.DeviceKind.GUNNER_SIGHT) {
            return;
        }
        net.minecraft.server.level.ServerPlayer wearer = findSightWearer(serverLevel, sight.id());
        if (wearer == null) {
            return;
        }
        // 准星落在生物上时瞄准它的包围盒中心；否则退回方块准星落点/视线尽头。
        // 直接打方块落点会因为动力臂与玩家视角存在视差而大多擦过生物。
        net.minecraft.world.phys.Vec3 eye = wearer.getEyePosition(1.0F);
        net.minecraft.world.phys.Vec3 look = wearer.getViewVector(1.0F);
        double maxDistance = 256.0D;
        net.minecraft.world.phys.Vec3 reach = look.scale(maxDistance);
        net.minecraft.world.phys.Vec3 blockPoint = wearer.pick(maxDistance, 1.0F, false).getLocation();
        double blockDistanceSqr = blockPoint.distanceToSqr(eye);
        net.minecraft.world.phys.EntityHitResult entityHit =
                net.minecraft.world.entity.projectile.ProjectileUtil.getEntityHitResult(wearer,
                        eye, eye.add(reach),
                        wearer.getBoundingBox().expandTowards(reach).inflate(1.0D),
                        entity -> entity != wearer && !entity.isSpectator() && entity.isPickable(),
                        maxDistance * maxDistance);
        net.minecraft.world.phys.Vec3 point =
                entityHit != null && entityHit.getLocation().distanceToSqr(eye) <= blockDistanceSqr
                        ? entityHit.getEntity().getBoundingBox().getCenter()
                        : blockPoint;
        if (cbtc$isSmartMount(arm)) {
            com.mcl.cbtc.compat.cbc.CannonMountCompat.aim(arm, point);
        } else if (com.mcl.cbtc.device.Devices.hasSentry()) {
            com.mcl.cbtc.compat.SentryArmCompat.aim(arm, point);
        }
    }

    /** 哨戒动力臂射击：每执行一次模拟一次扳机，点射/连发节奏交给枪械自身。 */
    @Override
    public void sentryFire(String sentryDeviceId) {
        if (!(level instanceof ServerLevel) || !com.mcl.cbtc.device.Devices.hasSentry()) {
            return;
        }
        // 锁定击杀任务期间，开火节奏同样由任务接管
        if (killJob != null && killJob.sentryId.equals(sentryDeviceId)) {
            return;
        }
        net.minecraft.world.level.block.entity.BlockEntity arm =
                sentryArm(boundDevices().get(sentryDeviceId));
        if (arm != null) {
            com.mcl.cbtc.compat.SentryArmCompat.fire(arm);
        }
    }

    /** 动力臂换弹：智能炮座不参与换弹，保持原样。 */
    @Override
    public void sentryReload(String sentryDeviceId) {
        if (!(level instanceof ServerLevel) || !com.mcl.cbtc.device.Devices.hasSentry()) {
            return;
        }
        net.minecraft.world.level.block.entity.BlockEntity arm =
                sentryArm(boundDevices().get(sentryDeviceId));
        if (arm != null) {
            com.mcl.cbtc.compat.SentryArmCompat.reload(arm);
        }
    }

    /** 清除瞄准组件锁定的全部目标。 */
    @Override
    public void sightLockClearAll(String sightDeviceId) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        com.mcl.cbtc.item.DeviceModuleItem.Device sight = boundDevices().get(sightDeviceId);
        if (sight == null || sight.kind() != com.mcl.cbtc.device.DeviceKind.GUNNER_SIGHT) {
            return;
        }
        com.mcl.cbtc.device.GunnerSightLocks.clearAll(sight.id());
        com.mcl.cbtc.device.GunnerSightLocks.sync(serverLevel, sight.id());
    }

    /** 清除瞄准组件最后一个锁定的目标。 */
    @Override
    public void sightLockClearLast(String sightDeviceId) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        com.mcl.cbtc.item.DeviceModuleItem.Device sight = boundDevices().get(sightDeviceId);
        if (sight == null || sight.kind() != com.mcl.cbtc.device.DeviceKind.GUNNER_SIGHT) {
            return;
        }
        if (com.mcl.cbtc.device.GunnerSightLocks.clearLast(sight.id())) {
            com.mcl.cbtc.device.GunnerSightLocks.sync(serverLevel, sight.id());
        }
    }

    /**
     * 瞄准组件佩戴者的朝向角。
     * <p>
     * 水平取视线方位角，与 MC 原版朝向一致：正南为 0°、向西为正、正北为 ±180°、正东为 -90°；
     * 上下取俯仰角（抬头为正，-90°~90°）；两者都是度数，可以直接喂给齿轮箱的「旋转至」/「定角」。
     * 没有佩戴者时返回 NaN，这样下游的「旋转至」会忽略这次指令，炮塔不会突然转回 0°。
     */
    @Override
    public double sightAngle(String sightDeviceId, String axis) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return Double.NaN;
        }
        com.mcl.cbtc.item.DeviceModuleItem.Device sight = boundDevices().get(sightDeviceId);
        if (sight == null || sight.kind() != com.mcl.cbtc.device.DeviceKind.GUNNER_SIGHT) {
            return Double.NaN;
        }
        net.minecraft.server.level.ServerPlayer wearer = findSightWearer(serverLevel, sight.id());
        if (wearer == null) {
            return Double.NaN;
        }
        if ("vertical".equalsIgnoreCase(axis == null ? "" : axis.trim())) {
            // 原版俯仰角是低头为正，这里翻成抬头为正
            return -wearer.getXRot();
        }
        net.minecraft.world.phys.Vec3 look = wearer.getViewVector(1.0F);
        // 与 MC 原版朝向同一套：正南 0°，向西为正（玩家朝西时 yaw 90）
        return Math.toDegrees(Math.atan2(-look.x, look.z));
    }

    /**
     * 准星相对"前→后"连线的偏差角：只需要两个外设的坐标，不需要导航台与目标。
     * 0° = 准星与连线同向；没有佩戴者、缺外设或两点重合时返回 NaN。
     */
    @Override
    public double sightLineBearing(String sightDeviceId, String deviceA, String deviceB) {
        net.minecraft.server.level.ServerPlayer wearer = cbtc$sightLineWearer(sightDeviceId);
        if (wearer == null) {
            return Double.NaN;
        }
        return com.mcl.cbtc.device.Devices.directionBearing(wearer.serverLevel(),
                wearer.getViewVector(1.0F), deviceA, cbtc$deviceLocation(deviceA),
                deviceB, cbtc$deviceLocation(deviceB));
    }

    /** 把准星连线角换算成指定齿轮箱的绝对目标角，可以直接接「旋转至」。 */
    @Override
    public double sightLineAbsoluteTarget(String sightDeviceId, String deviceA, String deviceB,
                                          String gearboxDevice) {
        net.minecraft.server.level.ServerPlayer wearer = cbtc$sightLineWearer(sightDeviceId);
        if (wearer == null) {
            return Double.NaN;
        }
        return com.mcl.cbtc.device.Devices.directionAbsoluteTarget(wearer.serverLevel(),
                wearer.getViewVector(1.0F), deviceA, cbtc$deviceLocation(deviceA),
                deviceB, cbtc$deviceLocation(deviceB), gearboxDevice, cbtc$deviceLocation(gearboxDevice));
    }

    /** 瞄准组件的佩戴者；组件没选、类型不对或没人戴时返回 null。 */
    @Nullable
    private net.minecraft.server.level.ServerPlayer cbtc$sightLineWearer(String sightDeviceId) {
        com.mcl.cbtc.item.DeviceModuleItem.Device sight = boundDevices().get(sightDeviceId);
        if (sight == null || sight.kind() != com.mcl.cbtc.device.DeviceKind.GUNNER_SIGHT) {
            return null;
        }
        if (!(level instanceof ServerLevel serverLevel)) {
            return null;
        }
        return findSightWearer(serverLevel, sight.id());
    }

    /** 绑定记录里的定位；没绑定或没坐标时返回 null。 */
    @Nullable
    private com.mcl.cbtc.device.Devices.DeviceLocation cbtc$deviceLocation(String deviceId) {
        com.mcl.cbtc.item.DeviceModuleItem.Device device = boundDevices().get(deviceId);
        return device == null ? null
                : new com.mcl.cbtc.device.Devices.DeviceLocation(device.subLevelId(), device.pos());
    }

    /**
     * 火控单位瞄准并击杀瞄准组件锁定的全部目标。
     * <p>
     * 启动后由 {@link #tickKillJob} 每刻接管瞄准与开火，直到锁定表清空；
     * 目标暂时打不到时保持瞄准等待，不会跳过。
     */
    @Override
    public void fireControlKillAll(String sentryDeviceId, String sightDeviceId, String order) {
        if (!(level instanceof ServerLevel)) {
            return;
        }
        java.util.Map<String, com.mcl.cbtc.item.DeviceModuleItem.Device> devices = boundDevices();
        com.mcl.cbtc.item.DeviceModuleItem.Device sentry = devices.get(sentryDeviceId);
        com.mcl.cbtc.item.DeviceModuleItem.Device sight = devices.get(sightDeviceId);
        if (sentry == null || sentry.kind() != com.mcl.cbtc.device.DeviceKind.SENTRY_ARM
                || sight == null || sight.kind() != com.mcl.cbtc.device.DeviceKind.GUNNER_SIGHT) {
            return;
        }
        killJob = new KillJob(sentry.id(), sight.id(),
                order == null || order.isBlank() ? "lock" : order.trim());
    }

    /** 火控击杀任务的每刻推进：挑第一个目标、瞄准，能打到就扣扳机。 */
    private void tickKillJob() {
        if (killJob == null) {
            return;
        }
        net.minecraft.world.level.block.entity.BlockEntity arm = sentryArm(boundDevices().get(killJob.sentryId));
        if (arm == null || !(arm.getLevel() instanceof ServerLevel armLevel)) {
            killJob = null;
            return;
        }
        java.util.Map<java.util.UUID, Integer> seqs = new java.util.HashMap<>();
        java.util.List<net.minecraft.world.entity.Entity> targets = new java.util.ArrayList<>();
        boolean changed = false;
        for (com.mcl.cbtc.device.GunnerSightLocks.Locked locked
                : com.mcl.cbtc.device.GunnerSightLocks.all(killJob.sightId)) {
            if (!locked.dimension().equals(armLevel.dimension())) {
                continue;
            }
            net.minecraft.world.entity.Entity entity = armLevel.getEntity(locked.entity());
            if (entity == null || !entity.isAlive()) {
                // 已经死亡或离开世界，从锁定表里划掉
                com.mcl.cbtc.device.GunnerSightLocks.remove(killJob.sightId, locked.entity());
                changed = true;
                continue;
            }
            seqs.put(entity.getUUID(), locked.seq());
            targets.add(entity);
        }
        if (changed) {
            com.mcl.cbtc.device.GunnerSightLocks.sync(armLevel, killJob.sightId);
        }
        if (targets.isEmpty()) {
            killJob = null;
            return;
        }
        targets.sort(switch (killJob.order) {
            case "name" -> java.util.Comparator.comparing(
                    (net.minecraft.world.entity.Entity entity) -> entity.getName().getString(),
                    String.CASE_INSENSITIVE_ORDER);
            case "health" -> java.util.Comparator.<net.minecraft.world.entity.Entity>comparingDouble(
                    entity -> entity instanceof net.minecraft.world.entity.LivingEntity living
                            ? living.getMaxHealth() : 0.0F).reversed();
            default -> java.util.Comparator.comparingInt(
                    entity -> seqs.getOrDefault(entity.getUUID(), Integer.MAX_VALUE));
        });
        net.minecraft.world.entity.Entity target = targets.get(0);
        net.minecraft.world.phys.Vec3 point = target.getBoundingBox().getCenter();
        if (cbtc$isSmartMount(arm)) {
            // 智能炮座只跟随转动，开火仍由红石控制
            com.mcl.cbtc.compat.cbc.CannonMountCompat.aim(arm, point);
            return;
        }
        if (!com.mcl.cbtc.device.Devices.hasSentry()) {
            return;
        }
        com.mcl.cbtc.compat.SentryArmCompat.aim(arm, point);
        // 打不到就先保持瞄准等待，等目标进入可攻击状态再扣扳机
        if (cbtc$canHitTarget(arm, armLevel, target)) {
            com.mcl.cbtc.compat.SentryArmCompat.fire(arm);
        }
    }

    /** 火控单位能否打到目标：距离在射程内且视线没有被方块挡住。 */
    private static boolean cbtc$canHitTarget(net.minecraft.world.level.block.entity.BlockEntity arm,
                                              ServerLevel level, net.minecraft.world.entity.Entity target) {
        // 炮座可能在物理结构上：方块坐标在结构存储区，射线检测要用投影后的世界坐标
        net.minecraft.world.phys.Vec3 from = net.minecraft.world.phys.Vec3
                .atCenterOf(com.mcl.cbtc.device.Devices.projectPos(level, arm.getBlockPos()))
                .add(0.0D, 1.0D, 0.0D);
        net.minecraft.world.phys.Vec3 to = target.getBoundingBox().getCenter();
        double distanceSqr = from.distanceToSqr(to);
        if (distanceSqr > 128.0D * 128.0D) {
            return false;
        }
        net.minecraft.world.phys.BlockHitResult hit = level.clip(new net.minecraft.world.level.ClipContext(
                from, to, net.minecraft.world.level.ClipContext.Block.COLLIDER,
                net.minecraft.world.level.ClipContext.Fluid.NONE, (net.minecraft.world.entity.Entity) null));
        return hit.getType() == net.minecraft.world.phys.HitResult.Type.MISS
                || hit.getLocation().distanceToSqr(from) >= distanceSqr - 1.0D;
    }

    /** 火控单位可用弹药：动力臂读弹药箱，智能炮座读炮内炮弹。 */
    @Override
    public double sentryReserveAmmo(String sentryDeviceId) {
        net.minecraft.world.level.block.entity.BlockEntity arm = sentryArmForRead(sentryDeviceId);
        if (arm == null) {
            return 0;
        }
        if (cbtc$isSmartMount(arm)) {
            return com.mcl.cbtc.compat.cbc.CannonMountCompat.ammo(arm);
        }
        return com.mcl.cbtc.device.Devices.hasSentry()
                ? com.mcl.cbtc.compat.SentryArmCompat.reserveAmmo(arm)
                : 0;
    }

    /** 动力臂武器弹匣内弹量：智能炮座没有弹匣概念，返回 0。 */
    @Override
    public double sentryMagazineAmmo(String sentryDeviceId) {
        net.minecraft.world.level.block.entity.BlockEntity arm = sentryArmForRead(sentryDeviceId);
        if (arm == null || cbtc$isSmartMount(arm)) {
            return 0;
        }
        return com.mcl.cbtc.device.Devices.hasSentry()
                ? com.mcl.cbtc.compat.SentryArmCompat.magazineAmmo(arm)
                : 0;
    }

    /** 该方块实体是不是本模组的智能炮座；CBC 未安装时不会触碰它的类型。 */
    private static boolean cbtc$isSmartMount(net.minecraft.world.level.block.entity.BlockEntity be) {
        return be != null && com.mcl.cbtc.compat.cbc.CbcCannons.loaded()
                && com.mcl.cbtc.compat.cbc.CannonMountCompat.isSmartMount(be);
    }

    /** 读取火控单位数值时的公共查表；没有可用火控单位或设备不在线时返回 null。 */
    @Nullable
    private net.minecraft.world.level.block.entity.BlockEntity sentryArmForRead(String sentryDeviceId) {
        if (!(level instanceof ServerLevel) || !com.mcl.cbtc.device.Devices.hasFireControl()) {
            return null;
        }
        return sentryArm(boundDevices().get(sentryDeviceId));
    }

    /** 取的绑定记录对应的哨戒动力臂方块实体；不是动力臂或不在线时返回 null。 */
    @Nullable
    private net.minecraft.world.level.block.entity.BlockEntity sentryArm(
            @Nullable com.mcl.cbtc.item.DeviceModuleItem.Device device) {
        if (!(level instanceof ServerLevel serverLevel) || device == null
                || device.kind() != com.mcl.cbtc.device.DeviceKind.SENTRY_ARM
                || device.dimension() == null) {
            return null;
        }
        ServerLevel armLevel = serverLevel.getServer().getLevel(net.minecraft.resources.ResourceKey.create(
                net.minecraft.core.registries.Registries.DIMENSION, device.dimension()));
        if (armLevel == null) {
            return null;
        }
        return com.mcl.cbtc.device.Devices.blockEntityAt(armLevel,
                com.mcl.cbtc.device.Devices.currentPos(device.id(), device.pos()));
    }

    /** 找到戴着指定 ID 瞄准组件的玩家；原版头部槽位与饰品栏都算。 */
    private net.minecraft.server.level.ServerPlayer findSightWearer(ServerLevel anyLevel, String sightId) {
        if (sightId == null || sightId.isBlank()) {
            return null;
        }
        for (ServerLevel target : anyLevel.getServer().getAllLevels()) {
            for (net.minecraft.server.level.ServerPlayer player : target.players()) {
                if (sightId.equals(com.mcl.cbtc.item.GunnerSightItem.id(
                        player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.HEAD)))) {
                    return player;
                }
                if (com.mcl.cbtc.compat.Curios.loaded()
                        && com.mcl.cbtc.compat.CuriosCompat.isWearingSight(player, sightId)) {
                    return player;
                }
            }
        }
        return null;
    }

    /**
     * 点动续期：本刻仍在执行这条点动积木，通知对应齿轮箱保持转动。
     */
    @Override
    public void keepJogging(int nodeId) {
        var found = program().node(nodeId);
        if (found.isEmpty()) {
            return;
        }
        String deviceId = found.get().param("device");
        com.mcl.cbtc.item.DeviceModuleItem.Device device = boundDevices().get(deviceId);
        if (device == null || device.kind() != com.mcl.cbtc.device.DeviceKind.GEARBOX) {
            return;
        }
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        ServerLevel target = serverLevel.getServer().getLevel(net.minecraft.resources.ResourceKey.create(
                net.minecraft.core.registries.Registries.DIMENSION, device.dimension()));
        if (target == null) {
            return;
        }
        com.mcl.cbtc.device.Devices.keepJogging(target, com.mcl.cbtc.device.Devices.currentPos(device.id(), device.pos()));
    }

    /**
     * 立即停掉指定齿轮箱的旋转（任何模式都能被打断）。
     */
    @Override
    public void stopGearbox(String deviceId) {
        com.mcl.cbtc.item.DeviceModuleItem.Device device = boundDevices().get(deviceId);
        if (device == null || device.kind() != com.mcl.cbtc.device.DeviceKind.GEARBOX) {
            return;
        }
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        ServerLevel target = serverLevel.getServer().getLevel(net.minecraft.resources.ResourceKey.create(
                net.minecraft.core.registries.Registries.DIMENSION, device.dimension()));
        if (target == null) {
            return;
        }
        com.mcl.cbtc.device.Devices.stopGearbox(target, com.mcl.cbtc.device.Devices.currentPos(device.id(), device.pos()));
    }

    /** 指定齿轮箱相对零点的当前角度；设备不存在时返回 0。 */
    @Override
    public double gearboxAngle(String deviceId) {
        com.mcl.cbtc.item.DeviceModuleItem.Device device = boundDevices().get(deviceId);
        if (device == null || device.kind() != com.mcl.cbtc.device.DeviceKind.GEARBOX) {
            return 0;
        }
        if (!(level instanceof ServerLevel serverLevel)) {
            return 0;
        }
        ServerLevel target = serverLevel.getServer().getLevel(net.minecraft.resources.ResourceKey.create(
                net.minecraft.core.registries.Registries.DIMENSION, device.dimension()));
        return target == null ? 0 : com.mcl.cbtc.device.Devices.gearboxAngle(target, com.mcl.cbtc.device.Devices.currentPos(device.id(), device.pos()));
    }

    /** 设置智能转速控制器的目标转速；设备不存在或类型不符时什么也不做。 */
    @Override
    public void setSpeedController(String deviceId, float rpm) {
        com.mcl.cbtc.item.DeviceModuleItem.Device device = boundDevices().get(deviceId);
        if (device == null || device.kind() != com.mcl.cbtc.device.DeviceKind.SPEED_CONTROLLER) {
            return;
        }
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        ServerLevel target = serverLevel.getServer().getLevel(net.minecraft.resources.ResourceKey.create(
                net.minecraft.core.registries.Registries.DIMENSION, device.dimension()));
        if (target == null) {
            return;
        }
        com.mcl.cbtc.device.Devices.setSpeedController(target,
                com.mcl.cbtc.device.Devices.currentPos(device.id(), device.pos()), rpm);
    }

    /** 读取智能转速控制器的输出转速；设备不存在时返回 0。 */
    @Override
    public double speedControllerSpeed(String deviceId) {
        com.mcl.cbtc.item.DeviceModuleItem.Device device = boundDevices().get(deviceId);
        if (device == null || device.kind() != com.mcl.cbtc.device.DeviceKind.SPEED_CONTROLLER) {
            return 0;
        }
        if (!(level instanceof ServerLevel serverLevel)) {
            return 0;
        }
        ServerLevel target = serverLevel.getServer().getLevel(net.minecraft.resources.ResourceKey.create(
                net.minecraft.core.registries.Registries.DIMENSION, device.dimension()));
        return target == null ? 0 : com.mcl.cbtc.device.Devices.speedControllerSpeed(target,
                com.mcl.cbtc.device.Devices.currentPos(device.id(), device.pos()));
    }

    /** 写入硬盘变量；数据存在硬盘物品的 NBT 里，换电脑也能读到。 */
    @Override
    public void setDriveVariable(String name, Value value) {
        ItemStack drive = hardDrive();
        if (drive == null || value == null) {
            return;
        }
        com.mcl.cbtc.item.HardDriveItem.set(drive, name, value);
        // 物品内容变了要同步给客户端，否则界面上看不到新的变化
        setChanged();
    }

    /** 找到当前拓展器里的硬盘物品；没装时返回 null。 */
    private ItemStack hardDrive() {
        ExpanderBlockEntity expander = attachedExpander();
        return expander == null ? null : expander.hardDrive();
    }

    @Override
    public Value driveVariable(String name) {
        ItemStack drive = hardDrive();
        if (drive == null) {
            return Value.ZERO;
        }
        Value value = com.mcl.cbtc.item.HardDriveItem.get(drive, name);
        return value == null ? Value.of("") : value;
    }

    /** 按作用域读取变量：数列里的变量引用项靠它取到当前值。 */
    @Override
    public Value scopedVariable(String scope, String network, String name) {
        if (scope == null || name == null || name.isBlank()) {
            return Value.ZERO;
        }
        return switch (scope) {
            case "drive" -> driveVariable(name);
            case "lan" -> lanVariable(network == null ? "" : network, name);
            case "net" -> internetVariable(name);
            default -> Value.ZERO;
        };
    }

    public java.util.List<com.mcl.cbtc.network.DisplayDevicesPayload.Entry> displayDeviceEntries() {
        java.util.List<com.mcl.cbtc.network.DisplayDevicesPayload.Entry> result = new ArrayList<>();
        for (ItemStack stack : displayModules()) {
            var before = stack.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA);
            for (var device : com.mcl.cbtc.item.DisplayLinkModuleItem.devices(stack, level)) {
                int lines = 1;
                ServerLevel target = level.getServer().getLevel(net.minecraft.resources.ResourceKey.create(
                        net.minecraft.core.registries.Registries.DIMENSION, device.dimension()));
                if (target != null) {
                    lines = com.mcl.cbtc.integration.create.WirelessDisplay.lineCount(target, device.pos());
                }
                result.add(new com.mcl.cbtc.network.DisplayDevicesPayload.Entry(device.id(), device.name(), lines));
            }
            var after = stack.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA);
            if (!java.util.Objects.equals(before, after)) {
                setChanged();
            }
        }
        return result;
    }

    @Override
    public boolean writeWirelessDisplay(String id, int line, String text) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return false;
        }
        for (ItemStack stack : displayModules()) {
            var before = stack.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA);
            boolean written = false;
            for (var device : com.mcl.cbtc.item.DisplayLinkModuleItem.devices(stack, level)) {
                if (!id.equals(device.id())) {
                    continue;
                }
                ServerLevel target = serverLevel.getServer().getLevel(net.minecraft.resources.ResourceKey.create(
                        net.minecraft.core.registries.Registries.DIMENSION, device.dimension()));
                written = target != null
                        && com.mcl.cbtc.integration.create.WirelessDisplay.write(target, device.pos(), line, text);
                break;
            }
            var after = stack.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA);
            if (!java.util.Objects.equals(before, after)) {
                setChanged();
            }
            if (written) {
                return true;
            }
        }
        return false;
    }

    // ---------------------------------------------------------------- 无线红石模块

    public boolean hasWirelessRedstoneModule() {
        ExpanderBlockEntity expander = attachedExpander();
        if (expander == null) {
            return false;
        }
        for (int i = 0; i < expander.getContainerSize(); i++) {
            if (expander.getItem(i).getItem() instanceof com.mcl.cbtc.item.WirelessRedstoneModuleItem) {
                return true;
            }
        }
        return false;
    }

    public void onWirelessRedstoneReceived(String channelKey, int strength) {
        if (strength <= 0) {
            wirelessReceivedByChannel.remove(channelKey);
        } else {
            wirelessReceivedByChannel.put(channelKey, strength);
        }
        // wirelessReceived 保留"所有频道里最强的那个"，供旧的单频道积木使用
        int max = 0;
        for (int value : wirelessReceivedByChannel.values()) {
            max = Math.max(max, value);
        }
        wirelessReceived = max;
    }

    public int wirelessStrengthOf(String channelKey) {
        return wirelessReceivedByChannel.getOrDefault(channelKey, 0);
    }

    /** 接收积木最近使用的一对频率；与发信端共用同一原生 LinkEndpoint。 */
    private void updateWirelessChannels() {
        if (!hasWirelessRedstoneModule()) {
            link.setFrequencies(ItemStack.EMPTY, ItemStack.EMPTY);
            return;
        }
        // 发信积木每刻执行一次，正在发信时不覆盖频率，否则会把刚选定的频道冲掉
        if (wirelessSending) {
            return;
        }
        // 把所有接收类积木的频道都订阅上，这样不同条件积木可以各用一组频道
        List<ItemStack[]> pairs = new ArrayList<>();
        for (String type : List.of("redstone_receive", "redstone_receive_compare",
                "redstone_received", "redstone_received_compare")) {
            for (Node node : program.nodesOfType(type)) {
                pairs.add(new ItemStack[]{
                        wirelessItem(node.param("channel")),
                        wirelessItem(node.param("channel2"))});
            }
        }
        if (!pairs.isEmpty()) {
            link.setChannelPairs(pairs);
            return;
        }
        for (Node node : program.nodesOfType("redstone_send")) {
            setWirelessFrequencies(node.param("channel"), node.param("channel2"));
            return;
        }
    }

    private void setWirelessFrequencies(String first, String second) {
        link.setFrequencies(wirelessItem(first), wirelessItem(second));
    }

    /** 把积木里的物品 ID 字符串转成频率物品；空串按红石粉处理。 */
    private static ItemStack wirelessItem(String id) {
        return net.minecraft.core.registries.BuiltInRegistries.ITEM.get(
                net.minecraft.resources.ResourceLocation.parse(id == null || id.isBlank()
                        ? "minecraft:redstone" : id)).getDefaultInstance();
    }

    /**
     * 发送无线红石信号。
     * <p>
     * 只负责"点亮"：信号在执行的这一 tick 有效，下一 tick 如果没有再执行同一条积木，
     * 由 {@link #tickWirelessTransmitter()} 熄灭。也就是按下亮、松开灭。
     */
    @Override
    public void sendWirelessRedstone(String first, String second, int strength) {
        if (!hasWirelessRedstoneModule()) {
            return;
        }
        setWirelessFrequencies(first, second);
        wirelessSendStrength = Math.max(0, Math.min(15, strength));
        wirelessSendTick = level == null ? 0 : level.getGameTime();
        wirelessSending = true;
        link.setStrength(wirelessSendStrength);
    }

    /** 每刻收尾：超过最短脉冲时长仍没有再执行发信积木就熄灭。 */
    private void tickWirelessTransmitter() {
        if (!wirelessSending || level == null) {
            return;
        }
        if (level.getGameTime() - wirelessSendTick >= SEND_PULSE_TICKS) {
            wirelessSending = false;
            link.setStrength(0);
        }
    }

    // ---------------------------------------------------------------- 线缆发信

    /** 拓展器里是否装了线缆升级模块卡。 */
    public boolean hasCableModule() {
        ExpanderBlockEntity expander = attachedExpander();
        return expander != null && expander.hasCableModule();
    }

    /**
     * 通过线缆向拓展器的某个序列号（1~64）发信。
     * <p>
     * 与无线红石一致：只在执行的这一 tick 亮起，下一 tick 没再执行同一序列号就熄灭。
     */
    @Override
    public void sendCableSignal(int serial, int strength) {
        ExpanderBlockEntity expander = attachedExpander();
        if (level == null || level.isClientSide || expander == null || !expander.hasCableModule()) {
            return;
        }
        int channel = Math.max(1, Math.min(com.mcl.cbtc.compat.cable.CableCompat.CHANNELS, serial));
        int value = Math.max(0, Math.min(15, strength));
        cableSendPos = expander.getBlockPos();
        cableActiveSerials.add(channel);
        cableSendTick.put(channel, level.getGameTime());
        com.mcl.cbtc.compat.cable.CableCompat.setSignal(level, cableSendPos, channel, value);
    }

    /** 每刻收尾：超过最短脉冲时长仍没再执行的序列号全部熄灭。 */
    private void tickCableTransmitter() {
        if (level == null || level.isClientSide || cableSendTick.isEmpty()) {
            return;
        }
        long now = level.getGameTime();
        java.util.Iterator<java.util.Map.Entry<Integer, Long>> iterator = cableSendTick.entrySet().iterator();
        while (iterator.hasNext()) {
            java.util.Map.Entry<Integer, Long> entry = iterator.next();
            if (now - entry.getValue() < SEND_PULSE_TICKS) {
                continue;
            }
            int channel = entry.getKey();
            if (cableSendPos != null) {
                com.mcl.cbtc.compat.cable.CableCompat.setSignal(level, cableSendPos, channel, 0);
            }
            cableActiveSerials.remove(channel);
            iterator.remove();
        }
    }

    /** 停掉所有线缆发信并把信号清零。 */
    private void stopCableSignal() {
        if (level != null && cableSendPos != null) {
            for (int channel : cableActiveSerials) {
                com.mcl.cbtc.compat.cable.CableCompat.setSignal(level, cableSendPos, channel, 0);
            }
        }
        cableActiveSerials.clear();
        cableSendTick.clear();
    }

    // ---------------------------------------------------------------- 频率 / 组

    public ItemStack frequencyFirst() {
        return frequencyFirst;
    }

    public ItemStack frequencySecond() {
        return frequencySecond;
    }

    public void setFrequencies(ItemStack first, ItemStack second) {
        this.frequencyFirst = first.copyWithCount(1);
        this.frequencySecond = second.copyWithCount(1);
        link.setFrequencies(frequencyFirst, frequencySecond);
        setChanged();
    }

    public String groupId() {
        return groupId;
    }

    public String computerName() {
        return name;
    }

    /**
     * 底部状态栏要显示的"外设链接"内容：贴着本电脑背部的拓展器及其中的模块。
     * 没有外设时返回空列表，界面那一行就留空。
     */
    public List<String> deviceStatus() {
        List<String> lines = new ArrayList<>();
        if (level == null) {
            return lines;
        }
        ExpanderBlockEntity expander = attachedExpander();
        if (expander != null) {
            lines.add(Component.translatable("block.cbtc.expander").getString() + " "
                    + Component.translatable("cbtc.gui.device.ready").getString());
            for (int i = 0; i < expander.getContainerSize(); i++) {
                ItemStack stack = expander.getItem(i);
                if (!stack.isEmpty()) {
                    lines.add(stack.getHoverName().getString() + " "
                            + Component.translatable("cbtc.gui.device.ready").getString());
                }
            }
        }
        return lines;
    }

    /**
     * 相邻方块变动：外设可能被放上或拆掉，若编辑器正开着就重新下发连接状态。
     */
    public boolean hasStorageModule() {
        ExpanderBlockEntity expander = attachedExpander();
        return expander != null && expander.hasStorageModule();
    }

    /** 拓展器里是否装了扬声器升级模块卡。 */
    public boolean hasSpeakerModule() {
        ExpanderBlockEntity expander = attachedExpander();
        return expander != null && expander.hasSpeakerModule();
    }

    /** 拓展器里是否装了硬盘。 */
    public boolean hasHardDrive() {
        return hardDrive() != null;
    }

    @Override
    public int maxSoundRange() {
        return hasSpeakerModule()
                ? ProgramVM.MAX_SOUND_RANGE_SPEAKER
                : ProgramVM.MAX_SOUND_RANGE;
    }

    /**
     * 指定设备允许的最大可听范围：电脑看自身模块，音响/讲解石看自己的范围。
     */
    @Override
    public int maxSoundRange(String device) {
        if (isSelfSoundDevice(device)) {
            return maxSoundRange();
        }
        com.mcl.cbtc.blockentity.SoundDevice speaker = speakerAt(device);
        return speaker == null ? maxSoundRange() : speaker.soundRange();
    }

    /** 该设备名是否指"此电脑"（兼容英文环境写入的显示名）。 */
    private static boolean isSelfSoundDevice(String device) {
        return device == null || device.isBlank()
                || com.mcl.cbtc.core.NodeTypes.SOUND_DEVICE_SELF.equals(device)
                || com.mcl.cbtc.core.NodeTypes.SOUND_DEVICE_SELF.equals(device.trim());
    }

    /**
     * 按设备 id 找到对应的发声方块实体（CB音响或讲解石）。
     * <p>
     * 支持跨维度：设备记录里带了维度，取那个维度的同名坐标。
     * 找不到、类型不对或区块未加载时返回 null。
     */
    @Nullable
    private com.mcl.cbtc.blockentity.SoundDevice speakerAt(String deviceId) {
        com.mcl.cbtc.item.DeviceModuleItem.Device device = boundDevices().get(deviceId);
        if (device == null || !isSoundDeviceKind(device.kind())) {
            return null;
        }
        if (!(level instanceof ServerLevel serverLevel)) {
            return null;
        }
        ServerLevel target = serverLevel.getServer().getLevel(net.minecraft.resources.ResourceKey.create(
                net.minecraft.core.registries.Registries.DIMENSION, device.dimension()));
        if (target == null) {
            return null;
        }
        return com.mcl.cbtc.device.Devices.blockEntityAt(target, com.mcl.cbtc.device.Devices.currentPos(device.id(), device.pos()))
                instanceof com.mcl.cbtc.blockentity.SoundDevice s ? s : null;
    }

    /** 该外设种类能否作为发声设备。 */
    private static boolean isSoundDeviceKind(com.mcl.cbtc.device.DeviceKind kind) {
        return kind == com.mcl.cbtc.device.DeviceKind.SPEAKER
                || kind == com.mcl.cbtc.device.DeviceKind.LECTURE_STONE;
    }

    public void onNeighborChanged() {
        if (level == null || level.isClientSide) {
            return;
        }
        if (!hasWirelessRedstoneModule()) {
            wirelessSending = false;
            wirelessSendTick = -1;
            wirelessReceived = 0;
            wirelessReceivedByChannel.clear();
            lastWirelessReceived = 0;
            link.setStrength(0);
        }
        // 拔掉线缆模块卡后，正在进行的线缆发信要立刻停下并清零
        if (!hasCableModule() && !cableSendTick.isEmpty()) {
            stopCableSignal();
        }
        updateWirelessChannels();
        link.refreshNetwork();
        for (var player : level.players()) {
            if (player.containerMenu instanceof com.mcl.cbtc.menu.ComputerMenu menu
                    && menu.computer() == this
                    && player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
                net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(serverPlayer,
                        new com.mcl.cbtc.network.DeviceStatusPayload(worldPosition, deviceStatus(),
                                hasStorageModule(), hasWirelessRedstoneModule(), hasCableModule(), installedLanIds(),
                                hasInternetModule(), hasSpeakerModule(), hasHardDrive(),
                                songNames(), playlistIds()));
            }
        }
    }

    public void setComputerName(String name) {
        this.name = name == null || name.isBlank() ? "CB电脑" : name.trim();
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    /**
     * 绝对目标角：把指定齿轮箱转到这个角度，导航台 A 就正对目标。
     * 结果可直接接「旋转至」；转到后下一轮读到的值不变，不会再来回转。
     */
    @Override
    public double navBaselineAbsoluteTarget(String deviceA, String deviceB, String gearboxDevice) {
        com.mcl.cbtc.item.DeviceModuleItem.Device a = boundDevices().get(deviceA);
        if (a == null || a.kind() != com.mcl.cbtc.device.DeviceKind.NAV_TABLE) {
            return Double.NaN;
        }
        com.mcl.cbtc.item.DeviceModuleItem.Device b = boundDevices().get(deviceB);
        if (b == null || !(level instanceof ServerLevel serverLevel)) {
            return Double.NaN;
        }
        ServerLevel target = serverLevel.getServer().getLevel(net.minecraft.resources.ResourceKey.create(
                net.minecraft.core.registries.Registries.DIMENSION, a.dimension()));
        if (target == null) {
            return Double.NaN;
        }
        com.mcl.cbtc.item.DeviceModuleItem.Device gearbox = boundDevices().get(gearboxDevice);
        return com.mcl.cbtc.device.Devices.baselineAbsoluteTarget(target, deviceA,
                new com.mcl.cbtc.device.Devices.DeviceLocation(a.subLevelId(), a.pos()),
                deviceB, new com.mcl.cbtc.device.Devices.DeviceLocation(b.subLevelId(), b.pos()),
                gearboxDevice,
                gearbox == null ? null : new com.mcl.cbtc.device.Devices.DeviceLocation(
                        gearbox.subLevelId(), gearbox.pos()));
    }

    /**
     * 生成示范程序：电脑开机后默念一行字，用来确认积木能正常跑。
     * <pre>
     * 当此电脑运行时
     *     默念 在 此电脑 文本"你好世界"
     * </pre>
     */
    public void installSampleProgram() {
        Program p = new Program();

        Node run = p.addNode(NodeTypes.ON_RUN, 24, 16);

        Node murmur = p.addNode(NodeTypes.MURMUR, 24, 120);
        murmur.setParam("device", NodeTypes.SOUND_DEVICE_SELF);
        murmur.setParam("text", "你好");

        Node text = p.addNode(NodeTypes.CONST_TEXT, 24, 240);
        text.setParam("value", "你好世界");

        p.connect(run.id(), "exec", murmur.id(), "exec");
        p.connect(text.id(), "value", murmur.id(), "text");

        applyProgram(p);
    }

    /** 生成一个"识别聊天包含关键词"的条件积木。 */
    private static Node keyword(Program p, String word) {
        Node node = p.addNode(NodeTypes.CHAT_RECOGNIZE, 0, 0);
        node.setParam("keyword", word);
        node.setParam("target", "@a");
        return node;
    }

    /** 生成一个发送固定文本到聊天框的积木。 */
    private static Node send(Program p, String text) {
        Node content = p.addNode(NodeTypes.CONST_TEXT, 0, 0);
        content.setParam("value", text);
        Node chat = p.addNode(NodeTypes.CHAT_SEND, 0, 0);
        p.connect(content.id(), "value", chat.id(), "text");
        return chat;
    }

    public void setGroupId(String groupId) {
        this.groupId = groupId == null ? "" : groupId;
        setChanged();
    }

    // ---------------------------------------------------------------- 生命周期

    @Override
    public void onLoad() {
        super.onLoad();
        if (level != null && !level.isClientSide) {
            vm.setProgram(program);
            updateWirelessChannels();
            resubscribeBroadcasts();
        }
    }

    @Override
    public void setRemoved() {
        if (level != null && !level.isClientSide) {
            BroadcastBus.unsubscribeAll(this);
            leaveLan();
            leaveInternet();
            // 电脑被拆掉时，它放的歌也不该继续响
            stopMusic();
            link.leave();
        }
        super.setRemoved();
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.cbtc.computer");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        // 打开界面时把程序与名字一次性发给编辑者
        if (player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
            PacketDistributor.sendToPlayer(serverPlayer,
                    new ProgramSyncPayload(worldPosition, program.copy(), name));
        }
        return new ComputerMenu(id, inventory, this);
    }

    // ---------------------------------------------------------------- 存档

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("Program", program.save());
        tag.put("Freq1", frequencyFirst.saveOptional(registries));
        tag.put("Freq2", frequencySecond.saveOptional(registries));
        tag.putInt("LinkStrength", linkStrength);
        tag.putString("Group", groupId);
        tag.putBoolean("Powered", powered);
        tag.putString("Name", name);

        ListTag lines = new ListTag();
        for (String line : displayLines) {
            lines.add(net.minecraft.nbt.StringTag.valueOf(line));
        }
        tag.put("Display", lines);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        program.replaceWith(Program.load(tag.getCompound("Program")));
        vm.setProgram(program);
        frequencyFirst = ItemStack.parseOptional(registries, tag.getCompound("Freq1"));
        frequencySecond = ItemStack.parseOptional(registries, tag.getCompound("Freq2"));
        linkStrength = tag.getInt("LinkStrength");
        groupId = tag.getString("Group");
        powered = tag.getBoolean("Powered");
        name = tag.getString("Name");
        ListTag lines = tag.getList("Display", net.minecraft.nbt.Tag.TAG_STRING);
        for (int i = 0; i < DISPLAY_LINES; i++) {
            displayLines[i] = i < lines.size() ? lines.getString(i) : "";
        }
    }

    public List<String> displayLineList() {
        List<String> list = new ArrayList<>(DISPLAY_LINES);
        for (String line : displayLines) {
            list.add(line);
        }
        return list;
    }
}
