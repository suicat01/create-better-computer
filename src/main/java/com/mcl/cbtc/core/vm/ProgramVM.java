package com.mcl.cbtc.core.vm;

import com.mcl.cbtc.core.Node;
import com.mcl.cbtc.core.NodeType;
import com.mcl.cbtc.core.Program;
import com.mcl.cbtc.core.Value;
import com.mcl.cbtc.core.Wire;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.jetbrains.annotations.Nullable;

/**
 * 积木程序执行器（服务端）。
 * <p>
 * 执行模型：
 * <ul>
 *   <li>"启动时"和"每刻"是两条独立的执行流，各自顺着 FLOW 线走；</li>
 *   <li>数据口（数值/布尔/文本）在使用时递归求值，因此不需要手动排求值顺序；</li>
 *   <li>"等待 N 刻"会给该执行流挂一个倒计时，倒计时结束后从中断处继续；</li>
 *   <li>循环每跑完一轮内置 1 tick 冷却，随后重新执行循环块本身决定下一轮
 *       （计数型累加次数、条件型重新判断条件），既不会空转卡死服务器，也不限循环次数。</li>
 * </ul>
 */
public final class ProgramVM {
    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger("cbtc-vm");
    /** 数据口递归深度上限，防止循环连线的死递归。 */
    private static final int EVAL_DEPTH = 32;
    /** 单条执行流每 tick 推进积木数的上限，防止异常连线把服务端主线程卡死。 */
    private static final int MAX_STEPS_PER_TICK = 4096;
    /** 聊天记录保留时长（刻）：消息发出一刻后 1 秒内仍可被积木识别。 */
    private static final int CHAT_HISTORY_TICKS = 20;
    /** 声音默认最大传播范围（格）。装入扬声器升级模块后由 host 提高到 50。 */
    public static final int MAX_SOUND_RANGE = 12;
    /** 装入扬声器升级模块后的最大传播范围（格）。 */
    public static final int MAX_SOUND_RANGE_SPEAKER = 50;

    private static final String ON_START = "on_start";
    private static final String ON_RUN = "on_run";
    private static final String ON_BROADCAST = "on_broadcast";

    private final Map<String, Value> variables = new HashMap<>();
    /** 本机广播变量：与普通变量分开存放，名字相同也互不影响。 */
    private final Map<String, Value> broadcastVariables = new HashMap<>();
    /** 每个"收到广播"积木最近一次收到的值。 */
    private final Map<Integer, Value> bcInbox = new HashMap<>();
    /** 本 tick 收到的广播频道名，供"收到广播"条件积木查询。 */
    private final List<String> receivedChannels = new ArrayList<>();
    /** 本 tick 收到的局域网广播，供"条件收到局域网广播"查询。 */
    private final List<LanReceipt> receivedLanReceipts = new ArrayList<>();
    /** 局域网广播的待处理消息。 */
    private final List<LanReceipt> lanPending = new ArrayList<>();
    /** 本 tick 收到的互联网广播频道名，供"条件收到互联网广播"查询。 */
    private final List<String> receivedNetChannels = new ArrayList<>();
    /** 互联网广播的待处理消息。 */
    private final List<String> netPending = new ArrayList<>();
    /** 本刻收到的聊天内容（已按发信目标过滤），供"识别聊天框内容"查询。 */
    private final List<ChatMessage> chatInbox = new ArrayList<>();
    /** 服务器 tick 末尾派发来的聊天，下一次 tick 开头转入 chatInbox。 */
    private final List<ChatMessage> chatPending = new ArrayList<>();
    /** 玩家发言的原始记录，供识别积木按玩家名过滤。 */
    private final List<ChatMessage> playerChatPending = new ArrayList<>();
    private final List<ChatMessage> playerChatInbox = new ArrayList<>();
    /** 最近 1 秒内的聊天记录，识别与监听积木从这里查，不受派发时序影响。 */
    private final List<TimedChat> chatHistory = new ArrayList<>();
    /** 上一次触发"当…时"时看到的最新聊天所在刻，用来识别"又来了新消息"。 */
    private long lastChatSeenTick = -1;
    /** 监听状态剩余刻数；大于 0 表示正在监听。 */
    private int listeningTicks;
    /** "当…时"积木上一次判断时条件是否成立，用于只在上跳沿触发。 */
    private final Map<Integer, Boolean> conditionArmed = new HashMap<>();
    /**
     * 正在运行的齿轮箱积木：积木 id → 模式。
     * <p>
     * 积木只在执行到它时下发一次指令，之后的每刻由这里负责收尾：
     * 只剩点动需要每刻续期，见 {@link #maintainGearboxes()}。
     */
    private final Map<Integer, String> gearboxRuns = new HashMap<>();
    /** 点动积木最近一次真正被执行的刻，用来判断"还按着"还是"已经松开"。 */
    private final Map<Integer, Long> jogRunTicks = new HashMap<>();
    /** 打字机按键已按住的刻数：达到长按阈值后转为每刻连发。 */
    private final Map<Integer, Integer> keyHoldTicks = new HashMap<>();
    /** 按键条件积木上一次判断时是否按着，用于条件积木独立累计按住刻数。 */
    private final Map<Integer, Boolean> keyConditionArmed = new HashMap<>();
    /** 长按判定阈值：按住 0.2 秒（4 刻）视为长按。 */
    private static final int KEY_LONG_PRESS_TICKS = 4;
    private final List<Flow> flows = new ArrayList<>();
    private final List<Inbox> inbox = new ArrayList<>();
    private final List<WirelessSignal> wirelessInbox = new ArrayList<>();
    /** 无线红石模块当前的持续信号强度，条件积木读取它判断"此刻是否有信号"。 */
    private int wirelessStrength;
    /** 按频率对记录的持续强度，键是"红物品|蓝物品"，供带频道的条件积木精确查询。 */
    private final Map<String, Integer> wirelessByChannel = new HashMap<>();

    private final MachineHost host;
    private Program program = new Program();

    private boolean startFlowArmed = true;
    private long currentTick;
    private String lastError = "";
    private int lastExecutingNode = -1;

    public ProgramVM(MachineHost host) {
        this.host = host;
    }

    public void setProgram(Program program) {
        this.program = program;
        reset();
        // 结构 dump：排查"积木看着连上了、实际没连上"这类问题时直接看日志即可
        if (!program.isEmpty()) {
            LOGGER.info("装载程序：{}", program.summary());
            for (Node n : program.nodes()) {
                LOGGER.info("  积木 #{} {} 参数={}", n.id(), n.typeId(), n.params());
            }
            for (Wire w : program.wires()) {
                LOGGER.info("  连线 #{}:{} -> #{}:{}", w.fromNode(), w.fromPort(), w.toNode(), w.toPort());
            }
        }
    }

    public long currentTick() {
        return currentTick;
    }

    public String lastError() {
        return lastError;
    }

    public void clearError() {
        lastError = "";
    }

    public int lastExecutingNode() {
        return lastExecutingNode;
    }

    public Map<String, Value> variables() {
        return variables;
    }

    /** 复位：清空执行流、变量与广播接收状态。 */
    public void reset() {
        flows.clear();
        inbox.clear();
        wirelessInbox.clear();
        variables.clear();
        broadcastVariables.clear();
        bcInbox.clear();
        receivedChannels.clear();
        receivedLanReceipts.clear();
        lanPending.clear();
        receivedNetChannels.clear();
        netPending.clear();
        chatInbox.clear();
        chatPending.clear();
        playerChatInbox.clear();
        playerChatPending.clear();
        chatHistory.clear();
        lastChatSeenTick = -1;
        listeningTicks = 0;
        conditionArmed.clear();
        gearboxRuns.clear();
        jogRunTicks.clear();
        keyHoldTicks.clear();
        keyConditionArmed.clear();
        wirelessStrength = 0;
        wirelessByChannel.clear();
        startFlowArmed = true;
        lastExecutingNode = -1;
    }

    public void tick(long tick) {
        this.currentTick = tick;
        lastExecutingNode = -1;
        if (program.nodes().isEmpty()) {
            return;
        }
        receivedChannels.clear();
        clearLanReceipts();
        receivedNetChannels.clear();
        flushChat();
        playerChatInbox.clear();
        playerChatInbox.addAll(playerChatPending);
        playerChatPending.clear();
        recordChatHistory();
        flushBroadcasts();
        flushWirelessRedstone();
        flushLanBroadcasts();
        flushInternet();
        // 监听状态按刻倒计时，超时自动退出
        if (listeningTicks > 0) {
            listeningTicks--;
        }
        // "当此电脑运行时" 与 "启动时" 一样，每次开机只触发一次
        if (startFlowArmed) {
            startFlowArmed = false;
            for (String trigger : List.of(ON_RUN, ON_START)) {
                for (Node node : program.nodesOfType(trigger)) {
                    Flow flow = new Flow();
                    flow.enter(program, node.id(), "exec");
                    flows.add(flow);
                }
            }
        }
        // 本刻是否有新聊天进到历史里：聊天可能在"当…时"判断之后才到，
        // 光看上跳沿会漏掉"条件一直成立、只是又来了新消息"的情况
        long newestChatTick = chatHistory.isEmpty() ? -1 : chatHistory.get(chatHistory.size() - 1).tick();
        boolean newChat = newestChatTick >= 0 && newestChatTick != lastChatSeenTick;
        lastChatSeenTick = newestChatTick;
        // "当…时"：条件由不成立变为成立时触发一次；条件持续成立时，每来一条新聊天也重新触发
        for (Node node : program.nodesOfType("when")) {
            boolean now = eval(node, "cond", EVAL_DEPTH).asBool();
            boolean was = conditionArmed.getOrDefault(node.id(), false);
            conditionArmed.put(node.id(), now);
            if (now && (!was || newChat)) {
                Flow flow = new Flow();
                flow.enter(program, node.id(), "exec");
                flows.add(flow);
            }
        }
        // "当打字机按下某键时"：按下立刻触发一次，按住超过 0.2 秒后继续每刻连发，
        // 即短按能响应、长按也能持续响应。
        for (Node node : program.nodesOfType("typewriter_key")) {
            KeyState state = trackKey(node);
            if (state.justPressed() || state.longPressing()) {
                Flow flow = new Flow();
                flow.enter(program, node.id(), "exec");
                flows.add(flow);
            }
        }
        // "当打字机长按某键时"：按下后前 0.2 秒完全不响应，满阈值起每刻触发，松开即停。
        for (Node node : program.nodesOfType("typewriter_long_press")) {
            KeyState state = trackKey(node);
            if (state.longPressing()) {
                Flow flow = new Flow();
                flow.enter(program, node.id(), "exec");
                flows.add(flow);
            }
        }
        int before = flows.size();
        for (Flow flow : flows) {
            stepFlow(flow);
        }
        flows.removeIf(flow -> flow.done);
        // 齿轮箱收尾：点动续期
        maintainGearboxes();
        // 执行流结束只记一次，用来区分"正常跑完"和"执行中被断开"
        int ended = before - flows.size();
        if (ended > 0) {
            LOGGER.info("执行流结束 {} 条（tick={}，最后节点={}）", ended, currentTick, lastExecutingNode);
        }
    }

    /**
     * 齿轮箱积木的每刻收尾。
     * <p>
     * 只剩点动需要每刻维持，语义是"按住才转"。判断依据不是去猜上游是什么，
     * 而是看这条点动积木本刻有没有真的被执行到：
     * 按键松开后循环体不再进入，积木不再执行，续期自然中断，齿轮箱随后自行停转。
     * 这样即使积木嵌在"重复执行""如果"里也不会误判。
     */
    private void maintainGearboxes() {
        for (Map.Entry<Integer, String> entry : new ArrayList<>(gearboxRuns.entrySet())) {
            if (!"jog".equals(entry.getValue())) {
                gearboxRuns.remove(entry.getKey());
                continue;
            }
            long last = jogRunTicks.getOrDefault(entry.getKey(), -1L);
            // 容差取自齿轮箱侧的保活时长，两边必须一致：
            // 按下后前几刻按键事件积木不触发，这里若判太严会把还没按稳的点动提前丢掉
            if (currentTick - last <= com.mcl.cbtc.blockentity.CBGearboxBlockEntity.JOG_GRACE_TICKS) {
                host.keepJogging(entry.getKey());
            } else {
                // 已经连续几刻没被执行，点动结束，清掉记录
                gearboxRuns.remove(entry.getKey());
                jogRunTicks.remove(entry.getKey());
            }
        }
    }

    // ---------------------------------------------------------------- 广播

    /** 收到一次广播（由总线在 tick 间派发）。 */
    public void receiveBroadcast(String channel, Value value) {
        if (inbox.size() < 256) {
            inbox.add(new Inbox(channel, value));
        }
    }

    private void flushBroadcasts() {
        // 广播的有效期只有本 tick：先清掉上一轮记录，条件积木查询时才不会误判
        receivedChannels.clear();
        if (inbox.isEmpty()) {
            return;
        }
        List<Inbox> batch = new ArrayList<>(inbox);
        inbox.clear();
        for (Inbox message : batch) {
            String channel = message.channel().trim();
            receivedChannels.add(channel);
            for (Node node : broadcastReceivers()) {
                if (!node.param("channel").trim().equalsIgnoreCase(channel)) {
                    continue;
                }
                bcInbox.put(node.id(), message.value());
                Flow flow = new Flow();
                flow.enter(program, node.id(), "exec");
                flows.add(flow);
            }
        }
    }

    /** 本 tick 是否收到过指定频道的广播。 */
    private boolean receivedBroadcast(Node node) {
        String channel = node.param("channel").trim();
        for (String received : receivedChannels) {
            if (received.equalsIgnoreCase(channel)) {
                return true;
            }
        }
        return false;
    }

    /** 本机的"收到广播"积木；兼容旧的 on_broadcast 类型。 */
    private List<Node> broadcastReceivers() {
        List<Node> result = new ArrayList<>(program.nodesOfType("broadcast_receive"));
        result.addAll(program.nodesOfType(ON_BROADCAST));
        return result;
    }

    private record Inbox(String channel, Value value) {
    }

    // ---------------------------------------------------------------- 局域网

    /** 收到一次局域网广播（由局域网总线在 tick 间派发）。 */
    public void receiveLanBroadcast(String networkId, String channel, Value value) {
        if (lanPending.size() < 256) {
            lanPending.add(new LanReceipt(com.mcl.cbtc.lan.LanRegistry.normalize(networkId), channelPath(channel)));
        }
    }

    /**
     * 频道名归一化：局域网总线传入的是 {@code cbtc:广播1}，而积木里填的是 {@code 广播1}。
     * 两边统一只比较路径部分，避免命名空间导致永远匹配不上。
     */
    private static String channelPath(String raw) {
        String s = raw == null ? "" : raw.trim();
        int colon = s.indexOf(':');
        return colon < 0 ? s : s.substring(colon + 1);
    }

    /**
     * 积木的"局域网"参数只认单个网，所以这里必须完全一致；
     * 参数为空时什么也不匹配，避免旧存档里没选网的积木对全部网生效。
     */
    private static boolean matchesNetwork(Node node, String network) {
        String wanted = com.mcl.cbtc.lan.LanRegistry.normalize(node.param("network"));
        return !wanted.isEmpty() && wanted.equals(network);
    }

    /**
     * 每刻派发局域网广播：广播在上一 tick 末尾由总线送达，所以这里要先把"本刻收到"的频道
     * 记下来再派发，条件积木同一 tick 内才能读到；记录在下一 tick 开头才清空。
     */
    private void flushLanBroadcasts() {
        if (lanPending.isEmpty()) {
            return;
        }
        List<LanReceipt> batch = new ArrayList<>(lanPending);
        lanPending.clear();
        for (LanReceipt receipt : batch) {
            receivedLanReceipts.add(receipt);
            for (Node node : program.nodesOfType("lan_receive")) {
                if (!matchesNetwork(node, receipt.network())) {
                    continue;
                }
                if (!channelPath(node.param("channel")).equalsIgnoreCase(receipt.channel())) {
                    continue;
                }
                Flow flow = new Flow();
                flow.enter(program, node.id(), "exec");
                flows.add(flow);
            }
        }
    }

    /** 清空上一 tick 的局域网接收记录。 */
    private void clearLanReceipts() {
        receivedLanReceipts.clear();
    }

    /** 本 tick 是否在指定局域网收到过指定频道的广播。 */
    private boolean receivedLanBroadcast(Node node) {
        String channel = channelPath(node.param("channel"));
        for (LanReceipt receipt : receivedLanReceipts) {
            if (!matchesNetwork(node, receipt.network())) {
                continue;
            }
            if (receipt.channel().equalsIgnoreCase(channel)) {
                return true;
            }
        }
        return false;
    }

    /** 一条局域网广播的接收记录：来自哪个网、哪个频道。 */
    private record LanReceipt(String network, String channel) {
    }

    // ---------------------------------------------------------------- 互联网

    /** 收到一次互联网广播（由互联网总线在 tick 间派发）。 */
    public void receiveInternetBroadcast(String channel) {
        if (netPending.size() < 256) {
            netPending.add(channelPath(channel));
        }
    }

    /** 互联网广播只认频道名，比较同样只取冒号后的路径部分。 */
    private void flushInternet() {
        if (netPending.isEmpty()) {
            return;
        }
        List<String> batch = new ArrayList<>(netPending);
        netPending.clear();
        for (String channel : batch) {
            receivedNetChannels.add(channel);
            for (Node node : program.nodesOfType("net_receive")) {
                if (!channelPath(node.param("channel")).equalsIgnoreCase(channel)) {
                    continue;
                }
                Flow flow = new Flow();
                flow.enter(program, node.id(), "exec");
                flows.add(flow);
            }
        }
    }

    /** 本 tick 是否收到过指定频道的互联网广播。 */
    private boolean receivedInternetBroadcast(Node node) {
        String channel = channelPath(node.param("channel"));
        for (String received : receivedNetChannels) {
            if (received.equalsIgnoreCase(channel)) {
                return true;
            }
        }
        return false;
    }

    // ---------------------------------------------------------------- 聊天框

    /**
     * 收到一条聊天内容（由电脑端按发信目标过滤后投递）。
     * targetMatched 表示这条聊天是不是冲本机（或本机关心的目标）来的。
     */
    /** 玩家发言直接进入待读队列，不再等服务器 tick 末尾的总线派发。 */
    public void receivePlayerChat(String username, String text) {
        if (playerChatPending.size() < 256) {
            playerChatPending.add(new ChatMessage(text, true, username));
        }
    }

    public void receiveChat(String text, boolean targetMatched) {
        if (chatPending.size() < 256) {
            chatPending.add(new ChatMessage(text, targetMatched));
        }
    }

    /**
     * 把服务器 tick 末尾收到的聊天搬进本刻可读的列表。
     * 聊天由 ChatBus 派发，可能落在本刻执行之前或之后，因此除了本刻列表，
     * 还会并进 1 秒的历史窗口，识别积木从窗口里查，不会因为时序差一 tick 而漏掉。
     */
    private void flushChat() {
        // 聊天只在收到的这一刻有效，没有新消息就清空，避免旧消息被反复识别
        chatInbox.clear();
        if (!chatPending.isEmpty()) {
            chatInbox.addAll(chatPending);
            chatPending.clear();
        }
    }

    /** 把本刻收到的聊天并入最近 1 秒的历史窗口。 */
    private void recordChatHistory() {
        for (ChatMessage message : playerChatInbox) {
            chatHistory.add(new TimedChat(currentTick, message));
        }
        for (ChatMessage message : chatInbox) {
            chatHistory.add(new TimedChat(currentTick, message));
        }
        chatHistory.removeIf(entry -> currentTick - entry.tick() > CHAT_HISTORY_TICKS);
    }

    /**
     * 识别聊天框内容：关键词必须包含，发信目标和积木里填的一致。
     * 目标为 @e（全体玩家和电脑）时不限定来源，其余按电脑端过滤的结果判定。
     */
    private boolean recognizedChat(Node node) {
        String keyword = node.param("keyword").trim();
        String target = node.param("target").trim();
        boolean anyone = "@e".equalsIgnoreCase(target);
        for (TimedChat entry : chatHistory) {
            ChatMessage message = entry.message();
            if (!message.sender().isEmpty()) {
                // 玩家发言：@a / @e 全收，其余按玩家名匹配
                if (!"@a".equalsIgnoreCase(target) && !anyone
                        && !message.sender().equalsIgnoreCase(target.startsWith("@") ? target.substring(1) : target)) {
                    continue;
                }
            } else if (!anyone && !message.targetMatched()) {
                // 电脑发来的消息：只有目标命中或 @e 才算
                continue;
            }
            if (keyword.isEmpty() || message.text().contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    /** 最近 1 秒的聊天里是否包含触发词；触发词留空表示任意文字。 */
    private boolean heardChat(String trigger) {
        for (TimedChat entry : chatHistory) {
            if (trigger.isEmpty() || entry.message().text().contains(trigger)) {
                return true;
            }
        }
        return false;
    }

    /** 一条被本机接收到的聊天：内容 + 是否来自积木关心的发信目标。 */
    public record ChatMessage(String text, boolean targetMatched, String sender) {
        public ChatMessage(String text, boolean targetMatched) {
            this(text, targetMatched, "");
        }
    }

    /** 聊天 + 到达的刻，用于 1 秒历史窗口。 */
    private record TimedChat(long tick, ChatMessage message) {
    }

    // ---------------------------------------------------------------- 无线红石

    public void receiveWirelessRedstone(int strength) {
        if (wirelessInbox.size() < 256) {
            wirelessInbox.add(new WirelessSignal(strength));
        }
    }

    /**
     * 每刻同步无线红石模块的当前状态。
     * 条件积木要判断的是"此刻有没有信号"，所以必须读到持续状态，而不是只在变化的瞬间为真。
     *
     * @param byChannel 各频率对（"红物品|蓝物品"）当前的强度
     */
    public void setWirelessStrength(Map<String, Integer> byChannel) {
        wirelessByChannel.clear();
        int max = 0;
        for (Map.Entry<String, Integer> entry : byChannel.entrySet()) {
            int value = Math.max(0, entry.getValue());
            if (value > 0) {
                wirelessByChannel.put(entry.getKey(), value);
                max = Math.max(max, value);
            }
        }
        this.wirelessStrength = max;
    }

    private void flushWirelessRedstone() {
        if (wirelessInbox.isEmpty()) {
            return;
        }
        List<WirelessSignal> batch = new ArrayList<>(wirelessInbox);
        wirelessInbox.clear();
        for (WirelessSignal signal : batch) {
            int strength = signal.strength();
            for (Node node : program.nodesOfType("redstone_receive")) {
                Flow flow = new Flow();
                flow.enter(program, node.id(), "exec");
                flows.add(flow);
            }
            for (Node node : program.nodesOfType("redstone_receive_compare")) {
                if (!compare(node.param("op"), strength, numberOrParam(node, "strength")).asBool()) {
                    continue;
                }
                Flow flow = new Flow();
                flow.enter(program, node.id(), "exec");
                flows.add(flow);
            }
        }
    }

    private record WirelessSignal(int strength) {
    }

    /**
     * 取该积木所填频道的当前强度；没有信号返回 0。
     * 读的是持续状态，所以信号保持时条件会一直成立，配合"当…时"的边沿检测只触发一次。
     */
    private int receivedStrength(Node node) {
        String key = channelKey(node.param("channel"), node.param("channel2"));
        return wirelessByChannel.getOrDefault(key, 0);
    }

    /** 与 LinkEndpoint 保持一致：空串按红石粉处理，用"红物品|蓝物品"做键。 */
    private static String channelKey(String first, String second) {
        return itemKey(first) + "|" + itemKey(second);
    }

    private static String itemKey(String id) {
        return id == null || id.isBlank() ? "minecraft:redstone" : id;
    }

    // ---------------------------------------------------------------- 执行流

    private void stepFlow(Flow flow) {
        int steps = 0;
        while (!flow.done && flow.waitTicks <= 0) {
            // 兜底：单条执行流每 tick 最多推进这么多积木。
            // 万一连线成环或者某块积木没有出口，也不能让服务端主线程死循环卡死
            if (++steps > MAX_STEPS_PER_TICK) {
                lastError = "程序单刻执行步数超过上限，已停止";
                flow.done = true;
                return;
            }
            if (flow.nextNode < 0) {
                flow.done = true;
                return;
            }
            // 固定时长等待结束：从等待块自身的 exec 出口继续下一块
            if (flow.resumeAfterWait) {
                flow.resumeAfterWait = false;
                lastExecutingNode = flow.nextNode;
                flow.enter(program, flow.nextNode, "exec");
                continue;
            }
            Node node = program.node(flow.nextNode).orElse(null);
            if (node == null) {
                flow.done = true;
                return;
            }
            lastExecutingNode = node.id();
            String next = execute(flow, node);
            if (flow.done) {
                return;
            }
            if (flow.breakOut) {
                // "退出循环"：跳出最内层循环，从该循环块的 done 出口继续
                flow.breakOut = false;
                int loopId = flow.loopStack.isEmpty() ? -1 : flow.loopStack.pop();
                if (loopId < 0) {
                    flow.done = true;
                    return;
                }
                flow.enter(program, loopId, "done");
                continue;
            }
            if (next != null) {
                flow.enter(program, node.id(), next);
            }
            // 等待类积木：本 tick 到此为止，执行流仍停在等待块上，等倒计时归零后再续跑。
            // 固定时长的等待在续跑时直接跳到下一块；条件等待则重新执行本块再判断。
            if (flow.waitTicks > 0) {
                flow.resumeAfterWait = !flow.waitsForCondition;
                break;
            }
        }
        if (flow.waitTicks > 0) {
            flow.waitTicks--;
        }
    }

    /**
     * 执行一个节点。
     *
     * @return 从哪个输出口继续；null 表示停在这里（本 tick 不再前进）
     */
    @Nullable
    private String execute(Flow flow, Node node) {
        NodeType type = node.type();
        if (type == null) {
            // 旧存档里可能留着已经删除或改名的积木（例如早期的 narrate）。
            // 这里必须结束执行流：直接返回 null 会让 stepFlow 停在同一个节点上原地空转，卡死服务端主线程
            lastError = "未知积木类型：" + node.typeId() + "（可能是旧存档里已删除的积木）";
            flow.done = true;
            return null;
        }
        switch (type.id()) {
            case ON_START, ON_RUN, ON_BROADCAST, "redstone_receive", "redstone_receive_compare",
                    "lan_receive", "net_receive" -> {
                return "exec";
            }
            case "forever" -> {
                flow.enterLoop(node.id());
                return "body";
            }
            case "repeat_n" -> {
                int times = (int) Math.max(0, Math.min(100000, numberOrParam(node, "times")));
                int done = flow.repeatCounter.getOrDefault(node.id(), 0);
                if (done < times) {
                    flow.repeatCounter.put(node.id(), done + 1);
                    flow.enterLoop(node.id());
                    return "body";
                }
                flow.repeatCounter.remove(node.id());
                flow.leaveLoop(node.id());
                return "done";
            }
            case "repeat_until" -> {
                if (eval(node, "cond", EVAL_DEPTH).asBool()) {
                    flow.leaveLoop(node.id());
                    return "done";
                }
                flow.enterLoop(node.id());
                return "body";
            }
            case "break" -> {
                flow.breakOut = true;
                return null;
            }
            case "if" -> {
                // 标准「如果」：执行到这里时只判断一次，不自带循环
                return eval(node, "cond", EVAL_DEPTH).asBool() ? "then" : "done";
            }
            case "typewriter_key", "when" -> {
                // 由 tick 扫描事件后直接进入 exec
                return "exec";
            }
            case "if_else" -> {
                // 按顺序取第一个成立的分支；都不成立则走"否则"
                int branches = Math.max(0, parseInt(node.param("branches")));
                if (eval(node, "cond", EVAL_DEPTH).asBool()) {
                    return "then";
                }
                for (int i = 1; i <= branches; i++) {
                    if (eval(node, "cond" + i, EVAL_DEPTH).asBool()) {
                        return "else" + i;
                    }
                }
                return "else";
            }
            case "wait_until" -> {
                if (eval(node, "cond", EVAL_DEPTH).asBool()) {
                    return "exec";
                }
                flow.waitTicks = 1; // 每个 tick 重新检查一次条件
                flow.waitsForCondition = true;
                return null;
            }
            case "wait_seconds" -> {
                // 参数直接按刻计；接入时间积木时它已经换算好，填数字也当作刻
                int ticks = (int) Math.round(Math.max(0, Math.min(72000, numberOrParam(node, "seconds"))));
                if (ticks <= 0) {
                    return "exec";
                }
                flow.waitTicks = ticks;
                flow.waitsForCondition = false;
                return null;
            }
            case "wait" -> {
                int ticks = (int) Math.max(0, Math.min(72000, eval(node, "ticks", EVAL_DEPTH).asNumber()));
                if (ticks <= 0) {
                    return "exec";
                }
                flow.waitTicks = ticks;
                flow.waitsForCondition = false;
                return null;
            }
            case "var_set" -> {
                String name = node.param("name").trim();
                if (!name.isEmpty()) {
                    variables.put(name, eval(node, "value", EVAL_DEPTH));
                }
                return "exec";
            }
            case "drive_set" -> {
                // 写进硬盘物品的 NBT，硬盘换到别的电脑后仍能读到
                String name = node.param("name").trim();
                if (!name.isEmpty()) {
                    host.setDriveVariable(name, eval(node, "value", EVAL_DEPTH));
                }
                return "exec";
            }
            case "bc_var_set" -> {
                String name = node.param("name").trim();
                if (!name.isEmpty()) {
                    broadcastVariables.put(name, eval(node, "value", EVAL_DEPTH));
                }
                return "exec";
            }
            case "broadcast_send" -> {
                // 本地广播：只发给本机，不经过全局总线
                String channel = node.param("channel").trim();
                if (!channel.isEmpty()) {
                    inbox.add(new Inbox(channel, Value.ZERO));
                }
                return "exec";
            }
            case "lan_send" -> {
                String channel = node.param("channel").trim();
                if (!channel.isEmpty()) {
                    host.sendLanBroadcast(node.param("network").trim(), channel);
                }
                return "exec";
            }
            case "lan_var_set" -> {
                String name = node.param("name").trim();
                if (!name.isEmpty()) {
                    host.setLanVariable(node.param("network").trim(), name, eval(node, "value", EVAL_DEPTH));
                }
                return "exec";
            }
            case "seq_mem_add" -> {
                mutateSeq("mem", "", node.param("name").trim(), list -> list.add(seqItem(node, "item")));
                return "exec";
            }
            case "seq_mem_remove" -> {
                double target = numberOrParam(node, "value");
                mutateSeq("mem", "", node.param("name").trim(),
                        list -> list.removeIf(item -> matchesSeqItem(item, target)));
                return "exec";
            }
            case "seq_mem_clear" -> {
                mutateSeq("mem", "", node.param("name").trim(), java.util.List::clear);
                return "exec";
            }
            case "seq_drive_add" -> {
                mutateSeq("drive", "", node.param("name").trim(), list ->
                        list.add(seqItem(node, "item")));
                return "exec";
            }
            case "seq_drive_remove" -> {
                double target = numberOrParam(node, "value");
                mutateSeq("drive", "", node.param("name").trim(),
                        list -> list.removeIf(item -> matchesSeqItem(item, target)));
                return "exec";
            }
            case "seq_drive_clear" -> {
                mutateSeq("drive", "", node.param("name").trim(), java.util.List::clear);
                return "exec";
            }
            case "seq_lan_add" -> {
                mutateSeq("lan", node.param("network").trim(), node.param("name").trim(), list ->
                        list.add(seqItem(node, "item")));
                return "exec";
            }
            case "seq_lan_remove" -> {
                double target = numberOrParam(node, "value");
                mutateSeq("lan", node.param("network").trim(), node.param("name").trim(),
                        list -> list.removeIf(item -> matchesSeqItem(item, target)));
                return "exec";
            }
            case "seq_lan_clear" -> {
                mutateSeq("lan", node.param("network").trim(), node.param("name").trim(),
                        java.util.List::clear);
                return "exec";
            }
            case "seq_net_add" -> {
                mutateSeq("net", "", node.param("name").trim(), list -> list.add(seqItem(node, "item")));
                return "exec";
            }
            case "seq_net_remove" -> {
                double target = numberOrParam(node, "value");
                mutateSeq("net", "", node.param("name").trim(),
                        list -> list.removeIf(item -> matchesSeqItem(item, target)));
                return "exec";
            }
            case "seq_net_clear" -> {
                mutateSeq("net", "", node.param("name").trim(), java.util.List::clear);
                return "exec";
            }
            case "lan_received" -> {
                // 条件积木没有执行体，只在求值时读取本刻是否收到广播
                return "exec";
            }
            case "net_send" -> {
                String channel = node.param("channel").trim();
                if (!channel.isEmpty()) {
                    host.sendInternetBroadcast(channel);
                }
                return "exec";
            }
            case "net_var_set" -> {
                String name = node.param("name").trim();
                if (!name.isEmpty()) {
                    host.setInternetVariable(name, eval(node, "value", EVAL_DEPTH));
                }
                return "exec";
            }
            case "net_received" -> {
                // 条件积木没有执行体，只在求值时读取本刻是否收到广播
                return "exec";
            }
            case "chat_send" -> {
                // 面向全体玩家，不需要发信目标
                host.sendChat(eval(node, "text", EVAL_DEPTH).asText(), "@a");
                return "exec";
            }
            case "chat_whisper" -> {
                host.sendChat(eval(node, "text", EVAL_DEPTH).asText(), node.param("target").trim());
                return "exec";
            }
            case "chat_recognize" -> {
                // 条件积木没有执行体，只在条件口里被求值
                return "exec";
            }
            case "chat_listen" -> {
                // 本刻听到触发词就进入监听状态，并按"监听时长"续期；时长按刻计
                String word = node.param("trigger").trim();
                if (heardChat(word)) {
                    int ticks = (int) Math.round(Math.max(1, Math.min(72000, numberOrParam(node, "duration"))));
                    listeningTicks = ticks;
                }
                return "exec";
            }
            case "gearbox_control" -> {
                // 转速改成倍率档：只允许 ×1 / ×2，输出转速 = 输入转速 × 倍率。
                // 与机械动力原版序列齿轮箱的两档倍率完全一致，进度与时长同口径。
                double ratio = speedRatio(node.param("speed"));
                String direction = node.param("direction").trim();
                boolean counterClockwise = isCounterClockwise(direction);
                String mode = modeKey(node.param("mode"));
                float speed = (float) (counterClockwise ? -ratio : ratio);
                // 旋转至选了"最短路径"时，方向改由差值决定，这里打上标记
                if ("rotate_to".equals(mode) && direction.startsWith("最短")) {
                    mode = "rotate_to_shortest";
                }
                // 每刻收尾记录只用于点动续期，这里先清掉再按当前模式重新登记
                gearboxRuns.remove(node.id());
                double value = "jog".equals(mode) ? 0 : numberOrParam(node, "value");
                if ("angle".equals(mode)) {
                    // 角度允许任意值：负数代表往回转
                    value = Math.max(-360, Math.min(360, value));
                } else if ("rotate_to".equals(mode) || "rotate_to_shortest".equals(mode)) {
                    // 旋转至：目标朝向相对零点，归一到一圈之内
                    value = ((value % 360) + 360) % 360;
                }
                host.writeGearbox(node.param("device"), speed, mode, value);
                // 点动需要每刻续期，这里记下"本刻执行过"；持续一直转，等停止积木打断
                if ("jog".equals(mode)) {
                    gearboxRuns.put(node.id(), mode);
                    jogRunTicks.put(node.id(), currentTick);
                }
                return "exec";
            }
            case "gearbox_stop" -> {
                // 立即停掉指定齿轮箱的一切旋转，任何模式都能被打断
                host.stopGearbox(node.param("device"));
                return "exec";
            }
            case "speed_controller_set" -> {
                // 转速填绝对值，方向由顺逆时针选择器决定
                double rpm = Math.abs(numberOrParam(node, "rpm"));
                boolean counterClockwise = isCounterClockwise(node.param("direction").trim());
                host.setSpeedController(node.param("device"),
                        (float) (counterClockwise ? -rpm : rpm));
                return "exec";
            }
            case "target_locator_set" -> {
                host.writeTargetLocator(node.param("device"), node.param("axis").toLowerCase(),
                        numberOrParam(node, "value"));
                return "exec";
            }
            case "broadcast_receive" -> {
                return "body";
            }
            case "play_sound" -> {
                String device = node.param("device").trim();
                host.playSound(node.param("sound").trim(),
                        parseDouble(node.param("volume")),
                        (int) Math.max(0, Math.min(host.maxSoundRange(device), parseDouble(node.param("range")))),
                        device);
                return "exec";
            }
            case "murmur" -> {
                host.murmur(textOrParam(node, "text"), node.param("device").trim());
                return "exec";
            }
            case "stop_sound" -> {
                host.stopSounds(node.param("device").trim());
                return "exec";
            }
            case "music_play" -> {
                String device = node.param("device").trim();
                host.playMusic(textOrParam(node, "song"), parseDouble(node.param("volume")),
                        (int) Math.max(0, Math.min(host.maxSoundRange(device), parseDouble(node.param("range")))),
                        device);
                return "exec";
            }
            case "music_play_index" -> {
                String device = node.param("device").trim();
                host.playMusicFromPlaylist(node.param("playlist").trim(),
                        (int) numberOrParam(node, "index"), parseDouble(node.param("volume")),
                        (int) Math.max(0, Math.min(host.maxSoundRange(device), parseDouble(node.param("range")))),
                        device);
                return "exec";
            }
            case "repeat" -> {
                int times = (int) Math.max(0, Math.min(100000, eval(node, "times", EVAL_DEPTH).asNumber()));
                int done = flow.repeatCounter.getOrDefault(node.id(), 0);
                if (done < times) {
                    flow.repeatCounter.put(node.id(), done + 1);
                    flow.enterLoop(node.id());
                    return "body";
                }
                flow.repeatCounter.remove(node.id());
                flow.leaveLoop(node.id());
                return "done";
            }
            case "while" -> {
                if (!eval(node, "cond", EVAL_DEPTH).asBool()) {
                    flow.leaveLoop(node.id());
                    return "done";
                }
                flow.enterLoop(node.id());
                return "body";
            }
            case "link_send" -> {
                host.setLinkStrength((int) eval(node, "strength", EVAL_DEPTH).asNumber());
                return "exec";
            }
            case "redstone_send" -> {
                host.sendWirelessRedstone(node.param("channel"), node.param("channel2"),
                        node.param("mode"),
                        (int) Math.max(1, Math.min(72000, numberOrParam(node, "phase"))),
                        (int) Math.max(0, Math.min(72000, numberOrParam(node, "gap"))),
                        (int) Math.max(1, Math.min(72000, numberOrParam(node, "lit"))),
                        (int) Math.max(0, Math.min(15, numberOrParam(node, "strength"))));
                return "exec";
            }
            case "bc_send" -> {
                host.broadcast(node.param("channel"), eval(node, "value", EVAL_DEPTH));
                return "exec";
            }
            case "display" -> {
                host.setDisplayText(parseInt(node.param("line")), eval(node, "text", EVAL_DEPTH).asText());
                return "exec";
            }
            case "wireless_display" -> {
                host.writeWirelessDisplay(node.param("device"), Math.max(0, parseInt(node.param("line")) - 1),
                        eval(node, "text", EVAL_DEPTH).asText());
                return "exec";
            }
            case "packager_send" -> {
                int count = (int) Math.max(1, Math.min(100000, numberOrParam(node, "count")));
                host.sendPackage(node.param("item"), count, textOrParam(node, "address"));
                return "exec";
            }
            default -> {
                // 有类型但没实现执行分支的积木同样不能原地空转
                lastError = "积木没有可执行的出口：" + type.id();
                flow.done = true;
                return null;
            }
        }
    }

    private static int parseInt(String s) {
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** 齿轮箱方向参数：以"反"或"逆"字开头（或内部键 reverse）即反转，其余按正转处理。 */
    private static boolean isCounterClockwise(String direction) {
        if (direction == null) {
            return false;
        }
        String text = direction.trim();
        return text.startsWith("反") || text.startsWith("逆") || text.equals("reverse");
    }

    /**
     * 齿轮箱转速档位 → 倍率。
     * <p>
     * 只认两档：{@code ×1} 与 {@code ×2}，输出转速为输入转速的对应倍数。
     * 旧存档里存的是 RPM 数字，这里按"大于 1 视为 ×2"折算，避免直接失效。
     */
    private static double speedRatio(String speed) {
        String text = speed == null ? "" : speed.trim();
        if (text.startsWith("×2") || text.equals("2") || text.equals("两倍")) {
            return 2;
        }
        if (text.startsWith("×1") || text.equals("1") || text.equals("一倍")) {
            return 1;
        }
        double value = parseDouble(text);
        if (Double.isNaN(value) || value <= 0) {
            return 1;
        }
        return value > 1 ? 2 : 1;
    }

    /** 齿轮箱模式参数 → 内部键；认不出时按定角模式处理。 */
    private static String modeKey(String mode) {
        String text = mode == null ? "" : mode.trim();
        if (text.startsWith("持续") || text.equals("continuous")) {
            return "steady";
        }
        if (text.startsWith("点动") || text.equals("jog")) {
            return "jog";
        }
        if (text.startsWith("旋转至") || text.equals("rotate_to")) {
            return "rotate_to";
        }
        // 定角与无法识别的都按定角处理（定时模式已移除）
        return "angle";
    }

    // ---------------------------------------------------------------- 求值

    private Value eval(Node node, String port, int depth) {
        if (depth <= 0) {
            return Value.ZERO;
        }
        List<Wire> incoming = program.wiresTo(node.id(), port);
        if (incoming.isEmpty()) {
            return Value.ZERO;
        }
        Node source = program.node(incoming.get(0).fromNode()).orElse(null);
        return source == null ? Value.ZERO : evalNode(source, depth - 1);
    }

    private Value evalNode(Node source, int depth) {
        NodeType type = source.type();
        if (type == null) {
            return Value.ZERO;
        }
        return switch (type.id()) {
            case "const_number" -> Value.of(parseDouble(source.param("value")));
            case "const_text" -> Value.of(source.param("value"));
            case "text_join" -> Value.of(joinText(source, depth));
            case "warehouse_free_space" -> Value.of(host.warehouseFreeSpace());
            case "warehouse_capacity" -> Value.of(host.warehouseCapacity());
            case "warehouse_item_count" -> Value.of(host.warehouseItemCount(source.param("item")));
            case "var_get" -> variables.getOrDefault(source.param("name").trim(), Value.ZERO);
            case "drive_get" -> host.driveVariable(source.param("name").trim());
            case "bc_var_get" -> broadcastVariables.getOrDefault(source.param("name").trim(), Value.ZERO);
            case "broadcast_receive" -> bcInbox.getOrDefault(source.id(), Value.ZERO);
            case ON_BROADCAST -> bcInbox.getOrDefault(source.id(), Value.ZERO);
            case "compare" -> compareValues(source.param("op"),
                    evalOrParam(source, "a", depth),
                    evalOrParam(source, "b", depth));
            // 随机数：从数列里随机抽一项
            case "random_int" -> Value.of(randomFromSeq(seqOf(source, "list", depth)));
            case "math" -> {
                if (isPercentConversion(source)) {
                    yield new Value.Num(eval(source, "a", depth).asNumber(), true);
                }
                yield mathChain(source, depth);
            }
            case "logic" -> logic(source.param("op"),
                    eval(source, "a", depth).asBool(),
                    eval(source, "b", depth).asBool());
            // 估读：按四舍五入保留到指定小数位
            case "math_round" -> {
                double digits = Math.max(0, Math.min(10, numberOrParam(source, "digits")));
                double scale = Math.pow(10, digits);
                yield Value.of(Math.round(numberOrParam(source, "value") * scale) / scale);
            }
            // 单目数学积木的"数值"既可填也可嵌：没接线时读槽位里填的数字
            case "math_abs" -> Value.of(Math.abs(numberOrParam(source, "value")));
            case "math_negate" -> Value.of(-numberOrParam(source, "value"));
            case "math_scientific" -> Value.of(scientificNotation(numberOrParam(source, "value")));
            // ---------------------------------------------------------------- 数列
            // 统计类：按名字取那条数列，再算数量 / 总和 / 平均值 / 文本
            case "seq_mem_count" -> Value.of(readSeq("mem", "", source.param("name").trim()).size());
            case "seq_mem_sum" -> Value.of(seqSum(readSeq("mem", "", source.param("name").trim())));
            case "seq_mem_average" -> Value.of(seqAverage(readSeq("mem", "", source.param("name").trim())));
            case "seq_mem_text" -> new Value.Seq(
                    new java.util.ArrayList<>(readSeq("mem", "", source.param("name").trim())));
            case "seq_drive_count" -> Value.of(seqItemsOf(host.driveVariable(source.param("name").trim())).size());
            case "seq_drive_sum" -> Value.of(seqSum(seqItemsOf(host.driveVariable(source.param("name").trim()))));
            case "seq_drive_average" -> Value.of(seqAverage(seqItemsOf(host.driveVariable(source.param("name").trim()))));
            case "seq_drive_text" -> new Value.Seq(new java.util.ArrayList<>(
                    seqItemsOf(host.driveVariable(source.param("name").trim()))));
            case "seq_lan_count" -> Value.of(seqItemsOf(host.lanVariable(
                    source.param("network").trim(), source.param("name").trim())).size());
            case "seq_lan_sum" -> Value.of(seqSum(seqItemsOf(host.lanVariable(
                    source.param("network").trim(), source.param("name").trim()))));
            case "seq_lan_average" -> Value.of(seqAverage(seqItemsOf(host.lanVariable(
                    source.param("network").trim(), source.param("name").trim()))));
            case "seq_lan_text" -> new Value.Seq(new java.util.ArrayList<>(
                    seqItemsOf(host.lanVariable(
                            source.param("network").trim(), source.param("name").trim()))));
            case "seq_net_count" -> Value.of(seqItemsOf(host.internetVariable(source.param("name").trim())).size());
            case "seq_net_sum" -> Value.of(seqSum(seqItemsOf(host.internetVariable(source.param("name").trim()))));
            case "seq_net_average" -> Value.of(seqAverage(seqItemsOf(host.internetVariable(source.param("name").trim()))));
            case "seq_net_text" -> new Value.Seq(new java.util.ArrayList<>(
                    seqItemsOf(host.internetVariable(source.param("name").trim()))));
            case "redstone_received" -> Value.of(receivedStrength(source) > 0);
            case "redstone_received_compare" -> Value.of(
                    compare(source.param("op"), receivedStrength(source), numberOrParam(source, "strength")).asBool());
            case "broadcast_received" -> Value.of(receivedBroadcast(source));
            case "lan_received" -> Value.of(receivedLanBroadcast(source));
            case "lan_var_get" -> host.lanVariable(source.param("network").trim(), source.param("name").trim());
            case "net_received" -> Value.of(receivedInternetBroadcast(source));
            case "net_var_get" -> host.internetVariable(source.param("name").trim());
            case "chat_recognize" -> Value.of(recognizedChat(source));
            case "chat_any" -> Value.of(!playerChatInbox.isEmpty() || !chatInbox.isEmpty());
            case "chat_listening" -> Value.of(listeningTicks > 0);
            case "nav_table_pos" -> Value.of(host.readNavTable(source.param("device"), source.param("axis").toLowerCase()));
            case "target_locator_pos" -> Value.of(host.readTargetLocator(source.param("device"),
                    source.param("axis").toLowerCase()));
            case "nav_table_target" -> Value.of(host.readNavTable(source.param("device"),
                    "target_" + source.param("axis").toLowerCase()));
            case "nav_table_distance" -> Value.of(host.readNavTable(source.param("device"), "distance"));
            case "info_receive" -> host.infoLine(source.param("device"), source.param("line"));
            case "nav_bearing_angle" -> Value.of(host.readNavTable(source.param("device"), "bearing"));
            case "nav_bearing_direction" -> Value.of(directionOf(
                    host.readNavTable(source.param("device"), "bearing")).equals(source.param("direction")));
            case "nav_bearing_range" -> Value.of(bearingInRange(
                    host.readNavTable(source.param("device"), "bearing"),
                    numberOrParam(source, "min"), numberOrParam(source, "max")));
            // 连线目标角：选了齿轮箱就输出"旋转至"可直接用的绝对目标角，
            // 没选齿轮箱时退回旧语义（目标相对 A→B 轴线的偏差，0° = 正对目标）
            case "nav_line_angle" -> {
                String gearbox = source.param("device_gearbox");
                if (gearbox == null || gearbox.isBlank()) {
                    yield Value.of(-host.navBaselineBearing(
                            source.param("device_a"), source.param("device_b")));
                }
                yield Value.of(host.navBaselineAbsoluteTarget(
                        source.param("device_a"), source.param("device_b"), gearbox));
            }
            case "nav_line_direction" -> Value.of(lineSideOf(host.navBaselineBearing(
                    source.param("device_a"), source.param("device_b"))).equals(source.param("direction"))
                    && directionOf(host.readNavTable(source.param("device_a"), "bearing"))
                            .equals(source.param("bearing")));
            case "nav_line_range" -> Value.of(lineSideOf(host.navBaselineBearing(
                    source.param("device_a"), source.param("device_b"))).equals(source.param("direction"))
                    && bearingInRange(host.readNavTable(source.param("device_a"), "bearing"),
                            numberOrParam(source, "min"), numberOrParam(source, "max")));
            // 角度常量的数字框既可填也可嵌：没接线时读参数，接线时取嵌入积木的值
            case "angle_value" -> Value.of(wrapAngle(numberOrParam(source, "amount")));
            case "gearbox_angle" -> Value.of(host.gearboxAngle(source.param("device")));
            case "speed_controller_get" -> Value.of(
                    Math.abs(host.speedControllerSpeed(source.param("device"))));
            case "angle_math" -> angleMathChain(source, depth);
            case "angle_compare" -> Value.of(angleCompare(source.param("op"),
                    eval(source, "a", depth).asNumber(),
                    eval(source, "b", depth).asNumber()));
            case "typewriter_key_down" -> Value.of(keyDownShort(source));
            case "typewriter_key_held" -> Value.of(keyHeld(source));
            case "time_write" -> Value.of(timeWrite(source));
            case "time_current" -> Value.of(currentTime(source.param("unit")));
            case "time_math" -> timeMathChain(source, depth);
            case "time_compare" -> Value.of(timeCompare(source.param("op"),
                    timeOf(eval(source, "a", depth)), timeOf(eval(source, "b", depth))));
            case "music_song_name" -> host.musicState(source.param("device"), "song");
            case "music_song_artist" -> host.musicState(source.param("device"), "artist");
            case "music_song_total" -> host.musicState(source.param("device"), "total");
            case "music_song_time" -> host.musicState(source.param("device"), "time");
            case "music_song_lyric" -> host.musicState(source.param("device"),
                    source.param("lang").trim().equals("original") ? "lyric_original" : "lyric");
            default -> Value.ZERO;
        };
    }

    /**
     * 打字机按键是否处于长按状态：仍被按住，且连续按住的刻数已达长按阈值。
     */
    private boolean keyHeld(Node node) {
        return heldTicks(node) >= KEY_LONG_PRESS_TICKS;
    }

    /**
     * 打字机按键是否处于"按下中但还没到长按"的状态。
     * <p>
     * 与 {@link #keyHeld} 严格互补：按住且未满阈值时为 true，满阈值后转为 false。
     */
    private boolean keyDownShort(Node node) {
        int held = heldTicks(node);
        return held >= 0 && held < KEY_LONG_PRESS_TICKS;
    }

    /**
     * 条件积木自己维护按住刻数：返回 -1 表示当前没按住。
     * <p>
     * 事件积木每刻会先走 {@link #trackKey}，但条件积木可能单独使用，
     * 所以这里也要能独立累计，不能只依赖事件积木写入的记录。
     */
    private int heldTicks(Node node) {
        int key = parseInt(node.param("key"));
        boolean down = host.pressedKeys(node.param("device")).contains(key);
        if (!down) {
            keyHoldTicks.remove(node.id());
            keyConditionArmed.put(node.id(), false);
            return -1;
        }
        boolean wasDown = keyConditionArmed.getOrDefault(node.id(), false);
        keyConditionArmed.put(node.id(), true);
        if (!wasDown) {
            // 刚按下的这一帧：从第 0 刻开始
            keyHoldTicks.put(node.id(), 0);
            return 0;
        }
        int held = keyHoldTicks.getOrDefault(node.id(), 0) + 1;
        keyHoldTicks.put(node.id(), held);
        return held;
    }

    /**
     * 更新一条打字机按键积木的按住状态，并返回本刻的判定结果。
     * <p>
     * 同一条积木每刻只会调用一次，所以按住刻数在这里累加；
     * 松开时清零，下次按下重新从 0 开始计数。
     */
    private KeyState trackKey(Node node) {
        int key = parseInt(node.param("key"));
        boolean down = host.pressedKeys(node.param("device")).contains(key);
        if (!down) {
            keyHoldTicks.remove(node.id());
            conditionArmed.put(node.id(), false);
            return new KeyState(false, false);
        }
        boolean wasDown = conditionArmed.getOrDefault(node.id(), false);
        conditionArmed.put(node.id(), true);
        if (!wasDown) {
            // 刚按下这一帧：记为第 0 刻
            keyHoldTicks.put(node.id(), 0);
            return new KeyState(true, false);
        }
        int held = keyHoldTicks.getOrDefault(node.id(), 0) + 1;
        keyHoldTicks.put(node.id(), held);
        return new KeyState(false, held >= KEY_LONG_PRESS_TICKS);
    }

    /** 单条按键积木本刻的状态：是否刚按下、是否已进入长接连发。 */
    private record KeyState(boolean justPressed, boolean longPressing) {
    }


    /**
     * 书写时间：把"数值 + 单位"换算成时间点。
     * <p>
     * 游戏类单位换算成游戏刻，现实类单位换算成现实秒，两者分别存进各自分量。
     */
    private Value.Time timeWrite(Node node) {
        double amount = numberOrParam(node, "amount");
        long game = 0;
        long real = 0;
        switch (node.param("unit").trim()) {
            case "游戏日", "game_day" -> game = Math.round(amount * 24000L);
            case "游戏时", "game_hour" -> game = Math.round(amount * 1000L);
            case "游戏分", "game_minute" -> game = Math.round(amount * 20L);
            case "现实年", "real_year" -> real = Math.round(amount * 365L * 86400L);
            case "现实日", "real_day" -> real = Math.round(amount * 86400L);
            case "现实时", "real_hour" -> real = Math.round(amount * 3600L);
            case "现实分", "real_minute" -> real = Math.round(amount * 60L);
            default -> real = Math.round(amount);
        }
        return new Value.Time(game, real);
    }

    /**
     * 当前时间：按单位读取游戏时间或现实时间。
     * <p>
     * 游戏日返回世界总天数，游戏时 / 游戏分按当前游戏日内的时间拆算；
     * 现实年 / 日 / 时 / 分 / 秒按本机时钟读取。
     */
    private Value.Time currentTime(String unit) {
        long game = 0;
        long real = 0;
        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        switch (unit.trim()) {
            case "游戏日", "game_day" -> game = host.gameTotalDays() * 24000L;
            case "游戏时", "game_hour" -> game = host.gameTicksOfDay();
            case "游戏分", "game_minute" -> game = host.gameTicksOfDay();
            case "现实年", "real_year" -> real = (long) now.getYear() * 365L * 86400L
                    + (now.getDayOfYear() - 1L) * 86400L + now.toLocalTime().toSecondOfDay();
            case "现实日", "real_day" -> real = (now.getDayOfYear() - 1L) * 86400L
                    + now.toLocalTime().toSecondOfDay();
            case "现实时", "real_hour" -> real = now.toLocalTime().toSecondOfDay();
            case "现实分", "real_minute" -> real = now.toLocalTime().toSecondOfDay();
            default -> real = now.toLocalTime().toSecondOfDay();
        }
        return new Value.Time(game, real);
    }

    /**
     * 把任意值转成时间点：时间值直接取用，数值按游戏刻理解，文本尝试解析成数字。
     */
    private static Value.Time timeOf(Value value) {
        if (value instanceof Value.Time time) {
            return time;
        }
        return new Value.Time(Math.round(value.asNumber()), 0);
    }

    /** 时间加减：结果的每个分量各自相减，得到时间差。 */
    private static Value.Time timeMath(String op, Value.Time a, Value.Time b) {
        boolean subtract = "-".equals(op) || "－".equals(op);
        long game = subtract ? a.gameTicks() - b.gameTicks() : a.gameTicks() + b.gameTicks();
        long real = subtract ? a.realSeconds() - b.realSeconds() : a.realSeconds() + b.realSeconds();
        return new Value.Time(game, real);
    }

    /** 时间比较：先比游戏分量，游戏分量相同时再比现实分量。 */
    private static boolean timeCompare(String op, Value.Time a, Value.Time b) {
        int cmp = compareTime(a, b);
        return switch (op) {
            case "<" -> cmp < 0;
            case "<=" -> cmp <= 0;
            case ">=" -> cmp >= 0;
            case "==", "=" -> cmp == 0;
            case "!=", "≠" -> cmp != 0;
            default -> cmp > 0;
        };
    }

    private static int compareTime(Value.Time a, Value.Time b) {
        int game = Long.compare(a.gameTicks(), b.gameTicks());
        return game != 0 ? game : Long.compare(a.realSeconds(), b.realSeconds());
    }

    private boolean isPercentConversion(Node node) {
        if (!("*".equals(node.param("op")) || "×".equals(node.param("op")))) {
            return false;
        }
        // b 是输入端口不是参数：没连线时看槽位里填的字面值，有连线时要求来源是写着 100% 的数字常量
        if (program.wiresTo(node.id(), "b").isEmpty()) {
            return "100%".equals(node.param("b").trim());
        }
        Node source = program.node(program.wiresTo(node.id(), "b").get(0).fromNode()).orElse(null);
        return source != null && "const_number".equals(source.typeId())
                && "100%".equals(source.param("value").trim());
    }

    // ---------------------------------------------------------------- 数列

    /**
     * 按名字修改一条数列：读出→改→写回。
     * <p>
     * 槽位里嵌的是变量（变量读取积木）时，写入的是"引用项"，
     * 之后变量变了数列里那一项也跟着变；否则写入的是常量快照。
     */
    private void mutateSeq(String scope, String network, String name,
                           java.util.function.Consumer<java.util.List<Value.Item>> action) {
        if (name == null || name.isEmpty()) {
            return;
        }
        java.util.List<Value.Item> items = new java.util.ArrayList<>(readSeq(scope, network, name));
        action.accept(items);
        writeSeq(scope, network, name, new Value.Seq(items));
    }

    /** 读一条数列；不是数列时当作空数列。 */
    private java.util.List<Value.Item> readSeq(String scope, String network, String name) {
        Value stored = "mem".equals(scope) ? memorySeq(name) : host.scopedVariable(scope, network, name);
        return stored instanceof Value.Seq seq ? seq.items() : java.util.List.of();
    }

    /** 写回一条数列。 */
    private void writeSeq(String scope, String network, String name, Value value) {
        switch (scope) {
            // 本机数列存在运行内存里，和普通变量同一张表，关机即清空
            case "mem" -> variables.put(name, value);
            case "drive" -> host.setDriveVariable(name, value);
            case "lan" -> host.setLanVariable(network, name, value);
            case "net" -> host.setInternetVariable(name, value);
            default -> {
            }
        }
    }

    /** 本机数列；名字没被用作普通变量时返回空值。 */
    private Value memorySeq(String name) {
        return variables.getOrDefault(name, Value.ZERO);
    }

    /**
     * 构造要写进数列的一项。
     * <p>
     * 槽位接的是"读取变量"类积木时，写成对那个变量的引用；否则写常量。
     */
    private Value.Item seqItem(Node node, String port) {
        java.util.List<Wire> incoming = program.wiresTo(node.id(), port);
        if (!incoming.isEmpty()) {
            Node source = program.node(incoming.get(0).fromNode()).orElse(null);
            if (source != null && source.type() != null) {
                switch (source.typeId()) {
                    // 本机变量存在运行内存里，同样可以被引用
                    case "var_get" -> {
                        return Value.Item.reference("mem", "", source.param("name").trim());
                    }
                    case "drive_get" -> {
                        return Value.Item.reference("drive", "", source.param("name").trim());
                    }
                    case "lan_var_get" -> {
                        return Value.Item.reference("lan", source.param("network").trim(),
                                source.param("name").trim());
                    }
                    case "net_var_get" -> {
                        return Value.Item.reference("net", "", source.param("name").trim());
                    }
                    default -> {
                    }
                }
            }
        }
        return Value.Item.constant(numberOrParam(node, port));
    }

    /**
     * 从数列里随机抽一项的数值；没有可抽的项时返回 0。
     */
    private double randomFromSeq(java.util.List<Value.Item> items) {
        if (items.isEmpty()) {
            return 0;
        }
        return seqItemValue(items.get((int) (Math.random() * items.size())));
    }

    /** 取一个数列口的内容：接了线就求值，没接线就按参数里填的数列名读本机数列。 */
    private java.util.List<Value.Item> seqOf(Node node, String port, int depth) {
        if (program.wiresTo(node.id(), port).isEmpty()) {
            return readSeq("mem", "", node.param(port).trim());
        }
        return seqItemsOf(eval(node, port, depth));
    }

    /**
     * 这一项是不是要删的那个值。
     * <p>
     * 引用项按它此刻的值比较；浮点用极小误差容忍，避免 0.1+0.2 这类表示误差删不掉。
     */
    private boolean matchesSeqItem(Value.Item item, double target) {
        return Math.abs(seqItemValue(item) - target) < 1e-9;
    }

    /** 数列的每一项取当前数值：引用项去查变量的现值。 */
    private double seqItemValue(Value.Item item) {
        if (!item.isReference()) {
            return item.literal();
        }
        Value value = "mem".equals(item.scope())
                ? memorySeq(item.name())
                : host.scopedVariable(item.scope(), item.network(), item.name());
        return value.asNumber();
    }

    /** 把一个值当作数列的项列表；不是数列时当作空。 */
    private java.util.List<Value.Item> seqItemsOf(Value value) {
        return value instanceof Value.Seq seq ? seq.items() : java.util.List.of();
    }

    private double seqSum(java.util.List<Value.Item> items) {
        double total = 0;
        for (Value.Item item : items) {
            total += seqItemValue(item);
        }
        return total;
    }

    private double seqAverage(java.util.List<Value.Item> items) {
        if (items.isEmpty()) {
            return 0;
        }
        return seqSum(items) / items.size();
    }

    /** 取一个数值：槽位里嵌了积木就用积木的值，否则用积木上填的数字。 */
    private double numberOrParam(Node node, String port) {
        if (!program.wiresTo(node.id(), port).isEmpty()) {
            return eval(node, port, EVAL_DEPTH).asNumber();
        }
        return parseDouble(node.param(port));
    }

    private String textOrParam(Node node, String port) {
        if (!program.wiresTo(node.id(), port).isEmpty()) {
            return eval(node, port, EVAL_DEPTH).asText();
        }
        return node.param(port);
    }

    /** 把方位角（-180° ~ +180°，正东为 0°）归入八方方位，返回内部键。 */
    private static String directionOf(double bearing) {
        if (bearing > -22.5 && bearing <= 22.5) {
            return "east";
        }
        if (bearing > 22.5 && bearing <= 67.5) {
            return "northeast";
        }
        if (bearing > 67.5 && bearing <= 112.5) {
            return "north";
        }
        if (bearing > 112.5 && bearing <= 157.5) {
            return "northwest";
        }
        if (bearing > 157.5 || bearing < -157.5) {
            return "west";
        }
        if (bearing >= -157.5 && bearing < -112.5) {
            return "southwest";
        }
        if (bearing >= -112.5 && bearing < -67.5) {
            return "south";
        }
        return "southeast";
    }

    /** 把基线方位角（正对目标 = 0°）归入前 / 后两半。只用两档，所以以 ±90° 为界。 */
    private static String lineSideOf(double bearing) {
        return bearing > -90 && bearing <= 90 ? "front" : "back";
    }

    /** 方位角是否落在 [min, max] 区间内。 */
    private static boolean bearingInRange(double bearing, double min, double max) {
        if (min <= max) {
            return bearing >= min && bearing <= max;
        }
        // 跨 ±180° 的区间（如 170° 到 -170°）
        return bearing >= min || bearing <= max;
    }

    /** 取字符串开头的数字（含正负号与小数），用于容错带单位的参数。 */
    private static final java.util.regex.Pattern NUMBER_PREFIX =
            java.util.regex.Pattern.compile("^[-+]?\\d*\\.?\\d+");

    private static double parseDouble(String s) {
        if (s == null) {
            return 0;
        }
        // 参数可能带单位（如 2.4°/t、90度），只取开头的数字部分
        java.util.regex.Matcher m = NUMBER_PREFIX.matcher(s.trim());
        if (!m.find()) {
            return 0;
        }
        try {
            return Double.parseDouble(m.group());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** 计算：一个数 运算符 另一个数。 */
    private Value mathChain(Node node, int depth) {
        return math(node.param("op"), eval(node, "a", depth).asNumber(),
                eval(node, "b", depth).asNumber());
    }

    /** 角度运算：结果按圆周归一。 */
    private Value angleMathChain(Node node, int depth) {
        return Value.of(angleMath(node.param("op"), eval(node, "a", depth).asNumber(),
                eval(node, "b", depth).asNumber()));
    }

    /** 时间运算：两个时间点做加减。 */
    private Value timeMathChain(Node node, int depth) {
        return Value.of(timeMath(node.param("op"), timeOf(eval(node, "a", depth)),
                timeOf(eval(node, "b", depth))));
    }

    /** 拼接文本：两个文本口接在一起，再依次接上追加的 t1、t2… */
    private String joinText(Node node, int depth) {
        StringBuilder sb = new StringBuilder(eval(node, "a", depth).asText());
        sb.append(eval(node, "b", depth).asText());
        int extra = parseExtra(node);
        for (int i = 1; i <= extra; i++) {
            sb.append(eval(node, "t" + i, depth).asText());
        }
        return sb.toString();
    }

    /** 读取积木上记录的追加项数；解析失败或为负时按 0 处理。 */
    private static int parseExtra(Node node) {
        try {
            return Math.max(0, Integer.parseInt(node.param("extra").trim()));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static Value math(String op, double a, double b) {
        return switch (op) {
            case "-" -> Value.of(a - b);
            case "*", "×" -> Value.of(a * b);
            case "/", "÷" -> Value.of(b == 0 ? 0 : a / b);
            case "%" -> Value.of(b == 0 ? 0 : a % b);
            // 乘方：a 的 b 次方。负数开分数次方会得到 NaN，按 0 处理
            case "^" -> {
                double result = Math.pow(a, b);
                yield Value.of(Double.isNaN(result) ? 0 : result);
            }
            default -> Value.of(a + b);
        };
    }

    /**
     * 科学计数法文本：把数值写成"尾数 * 10的N次方"。
     * <p>
     * 例：12345 → 1.2345 * 10^4；0.0012 → 1.2 * 10^-3；0 → 0 * 10^0。
     */
    private static String scientificNotation(double value) {
        if (value == 0 || Double.isNaN(value) || Double.isInfinite(value)) {
            return "0 * 10^0";
        }
        boolean negative = value < 0;
        double abs = Math.abs(value);
        int exponent = (int) Math.floor(Math.log10(abs));
        double mantissa = abs / Math.pow(10, exponent);
        // 浮点误差可能让尾数变成 9.999… 或 10.0，这里收拢一下
        if (mantissa >= 10) {
            mantissa /= 10;
            exponent++;
        }
        String shown = String.valueOf(Math.round(mantissa * 1e6) / 1e6);
        if (shown.endsWith(".0")) {
            shown = shown.substring(0, shown.length() - 2);
        }
        return (negative ? "-" : "") + shown + " * 10^" + exponent;
    }

    // ---------------------------------------------------------------- 随机数

    /** 求值一个数据口；没接线时读槽位里填的值，这样填数列名字之类的场景也能工作。 */
    private Value evalOrParam(Node node, String port, int depth) {
        if (!program.wiresTo(node.id(), port).isEmpty()) {
            return eval(node, port, depth);
        }
        String raw = node.param(port);
        return raw == null || raw.isBlank() ? Value.ZERO : Value.of(parseDouble(raw));
    }

    /**
     * 比较：任一方是数列时逐项比较，只要有一项满足就算成立。
     * <p>
     * 例如数列 [-1, 0, 0.5, 2] 与 1 比"大于"，因为 2 大于 1，结果为真。
     */
    private Value compareValues(String op, Value a, Value b) {
        java.util.List<Value.Item> left = a instanceof Value.Seq seq ? seq.items() : null;
        java.util.List<Value.Item> right = b instanceof Value.Seq seq ? seq.items() : null;
        if (left == null && right == null) {
            return compare(op, a.asNumber(), b.asNumber());
        }
        int count = Math.max(left == null ? 1 : left.size(), right == null ? 1 : right.size());
        for (int i = 0; i < count; i++) {
            double x = left == null ? a.asNumber()
                    : seqItemValue(left.get(Math.min(i, left.size() - 1)));
            double y = right == null ? b.asNumber()
                    : seqItemValue(right.get(Math.min(i, right.size() - 1)));
            if (compare(op, x, y).asBool()) {
                return Value.of(true);
            }
        }
        return Value.of(false);
    }

    private static Value compare(String op, double a, double b) {
        return Value.of(switch (op) {
            case "<" -> a < b;
            case "<=" -> a <= b;
            case ">=" -> a >= b;
            case "==" -> a == b;
            case "!=" -> a != b;
            default -> a > b;
        });
    }

    // ---------------------------------------------------------------- 固定角度

    /** 角度归一到 -180 ~ +180：180 与 -180 是同一个朝向。 */
    private static double wrapAngle(double degrees) {
        double value = degrees % 360;
        if (value > 180) {
            value -= 360;
        } else if (value <= -180) {
            value += 360;
        }
        return value;
    }

    /** 角度四则运算；乘除也要归一，所以结果一律再过一次 wrapAngle。 */
    private static double angleMath(String op, double a, double b) {
        double result = switch (op) {
            case "-" -> a - b;
            case "*", "×" -> a * b;
            case "/", "÷" -> b == 0 ? 0 : a / b;
            default -> a + b;
        };
        return wrapAngle(result);
    }

    /** 角度比较；等于类比较带容差，避免逐刻逼近时永远判不等。 */
    private static boolean angleCompare(String op, double a, double b) {
        double epsilon = com.mcl.cbtc.blockentity.CBGearboxBlockEntity.ANGLE_EPSILON;
        return switch (op) {
            case "<" -> a < b;
            case "<=" -> a <= b;
            case ">=" -> a >= b;
            case "==", "=" -> Math.abs(a - b) <= epsilon;
            case "!=", "≠" -> Math.abs(a - b) > epsilon;
            default -> a > b;
        };
    }

    private static Value logic(String op, boolean a, boolean b) {
        return Value.of(switch (op.toLowerCase(Locale.ROOT)) {
            case "or", "或", "||" -> a || b;
            case "xor", "异或" -> a ^ b;
            case "not", "非" -> !a;
            default -> a && b;
        });
    }

    // ---------------------------------------------------------------- 内部结构

    private static final class Flow {
        private int nextNode = -1;
        private int waitTicks;
        /** 等待结束后是否要从下一块继续（固定时长等待用；条件等待需要重复检查条件）。 */
        private boolean resumeAfterWait;
        /** 本次等待是否是"保持等待直到"这类需要每个 tick 重新检查条件的积木。 */
        private boolean waitsForCondition;
        private boolean done;
        /** 请求跳出最内层循环（由"重复执行，如果…则终止"设置）。 */
        private boolean breakOut;
        /** 当前正在执行的循环块，栈顶是最内层。 */
        private final java.util.ArrayDeque<Integer> loopStack = new java.util.ArrayDeque<>();
        private final Map<Integer, Integer> repeatCounter = new HashMap<>();

        private void enter(Program program, int nodeId, String port) {
            Optional<Wire> wire = program.wireFrom(nodeId, port);
            if (wire.isPresent()) {
                this.nextNode = wire.get().toNode();
                return;
            }
            enterEndOfChain(program, nodeId);
        }

        /**
         * 链条走到末端，且没有从当前节点接出去的线。
         * 等待类积木自己返回 null（不提供 next），所以必须在这里按"链尾"处理，
         * 否则 nextNode 会一直停在这块积木上，每个 tick 重新执行并重置倒计时。
         */
        private void enterEndOfChain(Program program, int nodeId) {
            // 从链尾沿普通 exec 链向上回溯，直到找到"从循环体/分支口接进来"的那一块。
            // 同层堆叠（A:exec -> B:exec）属于同一段链，不能只看直接上游。
            int cursor = nodeId;
            java.util.Set<Integer> visited = new java.util.HashSet<>();
            while (visited.add(cursor)) {
                List<Wire> incoming = program.wiresTo(cursor, "exec");
                if (incoming.isEmpty()) {
                    break;
                }
                Wire wire = incoming.get(0);
                Node owner = program.node(wire.fromNode()).orElse(null);
                if (owner == null || owner.type() == null) {
                    break;
                }
                if (wire.fromPort().equals("body") || wire.fromPort().startsWith("then")
                        || wire.fromPort().startsWith("else")) {
                    if (inArtifactLoop(owner, wire.fromPort())) {
                        // 一轮循环结束：内置 1 tick 冷却，随后重新执行循环块本身——
                        // 计数型在那里累加次数，条件型在那里重新判断条件。
                        // waitsForCondition=true 让 stepFlow 冷却后重跑循环块，而不是跳到它的出口。
                        this.resumeAfterWait = false;
                        this.waitsForCondition = true;
                        this.waitTicks = 2;
                        this.nextNode = owner.id();
                        return;
                    }
                    // 分支执行完后：优先走 done 口接出的积木；done 没接线时，
                    // 继续向上回溯外层循环，不能把整个脚本误判为结束。
                    if ("if".equals(owner.type().id()) || "if_else".equals(owner.type().id())) {
                        Optional<Wire> doneWire = program.wireFrom(owner.id(), "done");
                        if (doneWire.isPresent()) {
                            this.nextNode = doneWire.get().toNode();
                            return;
                        }
                        // done 没接线：从 if 积木继续向上找外层循环，不能停在 done 上
                        cursor = owner.id();
                        continue;
                    }
                    // 旧循环类型或未知类型走到末端，不吞掉整条事件链。
                    this.leaveLoop(owner.id());
                    enter(program, owner.id(), "done");
                    return;
                }
                // 普通同层堆叠：继续向上找
                cursor = owner.id();
            }
            this.nextNode = -1;
            this.done = true;
        }

        /** 进入一轮循环：循环块每轮都会重新执行，同一块已在栈顶时不重复压栈。 */
        private void enterLoop(int nodeId) {
            if (loopStack.isEmpty() || loopStack.peek() != nodeId) {
                loopStack.push(nodeId);
            }
        }

        /** 回跳循环时去掉上一轮留下的栈记录，避免退出嵌套循环时跳错层。 */
        private void leaveLoop(int nodeId) {
            while (!loopStack.isEmpty()) {
                if (loopStack.pop() == nodeId) {
                    break;
                }
            }
        }

        private boolean inArtifactLoop(Node owner, String port) {
            // 只有"body"口代表循环体；then/else/elseN 是条件积木的内部分支，
            // 它们走完后要继续向上找外层循环，不能当成循环回跳处理。
            if (!"body".equals(port)) {
                return false;
            }
            String id = owner.type().id();
            return "forever".equals(id) || "repeat_n".equals(id) || "repeat_until".equals(id)
                    || "while".equals(id) || "repeat".equals(id);
        }
    }
}
