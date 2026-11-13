package com.mcl.cbtc.client;

import com.mcl.cbtc.core.Node;
import com.mcl.cbtc.core.NodeCategory;
import com.mcl.cbtc.core.NodeType;
import com.mcl.cbtc.core.NodeTypes;
import com.mcl.cbtc.core.PortKind;
import com.mcl.cbtc.core.Program;
import com.mcl.cbtc.core.Wire;
import com.mcl.cbtc.menu.ComputerMenu;
import com.mcl.cbtc.network.ProgramRequestPayload;
import com.mcl.cbtc.network.ProgramSavePayload;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * 积木编程界面（编程猫风格，彻底重做）。
 * <p>
 * 形态约定：
 * <ul>
 *   <li>帽子块（事件）：圆顶、无上缺口、底部凸起，只能做脚本的头部；</li>
 *   <li>命令块：上缺口 + 下凸起，垂直堆叠即执行顺序（内部仍用 FLOW 线记录顺序）；</li>
 *   <li>C 形块（如果/重复/当…循环）：头部 + 左侧脊柱 + 空腔 + 底条，空腔内可再放一条链；</li>
 *   <li>表达式块（数据/运算）：胶囊形，拖进其他积木的凹槽里嵌套；</li>
 *   <li>左侧直接列出所有具体积木，按住拖到画布；拖回左侧或右键删除。</li>
 * </ul>
 * 画布上的堆叠/嵌套关系在放置时自动转成 Program 的连线，虚拟机逻辑不变。
 */
public class ComputerScreen extends AbstractContainerScreen<ComputerMenu> {
    // 机械动力配色：黄铜 + 深色屏幕
    private static final int BRASS_LIGHT = 0xFFF0C86A;
    private static final int BRASS = 0xFFB08D3C;
    private static final int BRASS_DARK = 0xFF6B4E1E;
    private static final int BAR_BG = 0xFF2A241C;
    private static final int SCREEN_BG = 0xFF15120F;
    private static final int SIDEBAR_BG = 0xFF1F1B15;
    private static final int GRID = 0xFF201C16;
    private static final int TEXT = 0xFFE8DCC0;
    private static final int TEXT_DIM = 0xFF9A9285;

    private static final int TOP_BAR = 24;
    private static final int STATUS_H = 16;
    private static final int SIDEBAR_W = 128;

    // 积木度量
    private static final int PAD = 10;
    private static final int ELEM_GAP = 4;
    private static final int STACK_H = 24;
    private static final int REPORTER_H = 22;
    private static final int SLOT_H = 16;
    private static final int PARAM_H = 16;
    private static final int EMPTY_SLOT_W = 34;
    private static final int JOINT_H = 5;
    private static final int NOTCH_X = 14;
    private static final int NOTCH_W = 18;
    private static final int C_INDENT = 16;
    private static final int C_FOOT = 12;
    private static final int C_EMPTY_H = 24;
    /** 分支横条高度。它与所有分支的空腔、按钮共用一套布局结果。 */
    private static final int C_ELSE_BAR = 24;
    /** 空腔上下内唇：内层积木贴住唇内侧，缺口朝向与上层凸起对齐。 */
    private static final int C_LIP = NOTCH_X + NOTCH_W + 4;
    private static final int C_LIP_H = 5;
    private static final int BRANCH_BUTTON = 14;
    private static final int C_RIGHT_PAD = 10;
    private static final int HAT_CURVE = 5;
    private static final int INPUT_TEXT = 0xFF241F16;
    /** 音量拉条尺寸。 */
    private static final int SLIDER_W = 44;
    private static final int SLIDER_H = 10;
    private static final int INPUT_FILL = 0xFFF3EAD6;
    private static final int BLOCK_OUTLINE = 0xFF241F16;

    private final ComputerMenu cbtcMenu;
    private final Program program = new Program();

    // 拖拽状态
    private Node dragging;
    private final Set<Integer> draggedSubtree = new HashSet<>();
    private double grabDX;
    private double grabDY;
    private NodeType draggingNew;
    private double dragX;
    private double dragY;
    /** Ctrl+C 复制下来的积木：与 source 同形的程序片段，粘贴时整体克隆。 */
    private Program clipboard;
    /** 复制时鼠标所在的世界坐标，粘贴时用它把片段平移过去。 */
    private double clipboardAnchorX;
    private double clipboardAnchorY;
    /** 最近一次渲染时的鼠标位置，粘贴用它决定落点。 */
    private double lastMouseX;
    private double lastMouseY;

    // 参数编辑
    private Node paramTarget;
    private int paramIndex;
    private EditBox paramBox;

    // 槽位内联编辑（形如"重复…次"里那个既能填数字又能嵌积木的白框）
    private Node slotEditing;
    private ParamAnchor lastParamHit;
    private String slotEditPort = "";

    // 运算符下拉选择器
    private Node opMenuNode;
    private int opMenuIndex = -1;
    private final List<int[]> opMenuRects = new ArrayList<>(); // 每项 {x, y, w, h}

    // 电脑名字输入框（顶栏中间）
    private EditBox nameBox;
    private static final int NAME_W = 150;

    // 显示区相机：屏幕坐标 = (世界坐标 - pan) * zoom
    private double zoom = 1.0;
    private double panX;
    private double panY;
    private boolean panning;
    /** 正在拖动的音量拉条：节点、参数下标、屏幕上的轨道范围。 */
    private Node sliderNode;
    private int sliderIndex = -1;
    private int sliderLeft;
    private int sliderWidth;
    private double panGrabX;
    private double panGrabY;
    private double panOriginX;
    private double panOriginY;

    private boolean synced;
    private String computerName = "";
    /** 底部"外设链接"状态行内容，由服务端下发。 */
    private java.util.List<String> deviceStatus = java.util.List.of();
    private boolean storageModuleInstalled;
    private boolean wirelessRedstoneModuleInstalled;
    /** 拓展器里是否装了已配置的局域网模块卡。 */
    private boolean lanInstalled;
    /** 拓展器里是否装了互联网模块卡。 */
    private boolean internetInstalled;
    /** 拓展器里是否装了扬声器升级模块卡：决定"播放声音/朗诵"的范围上限是 12 还是 50。 */
    private boolean speakerInstalled;
    /** "当打字机按下"积木正在等待按键：下一次按键直接绑定，非空时状态栏给出提示。 */
    private Node keyCaptureNode;
    /** 电脑当前已加入的局域网 ID 清单，供"局域网"参数下拉选择。 */
    private java.util.List<String> lanNetworks = java.util.List.of();
    /** 拓展器里音乐模块卡提供的全部歌名，供"播放歌曲"积木下拉选择。 */
    private java.util.List<String> songLibrary = java.util.List.of();
    /** 全部歌单（音乐模块卡 UUID），供"播放歌单第几首"积木下拉选择。 */
    private java.util.List<String> playlistIds = java.util.List.of();
    private java.util.List<com.simibubi.create.content.logistics.BigItemStack> warehouseItems = java.util.List.of();
    private Node itemPickerNode;
    private int itemPickerIndex = -1;
    private int itemPickerX;
    private int itemPickerY;
    private int itemPickerW;
    private int itemPickerH;
    private int itemPickerScroll;
    private EditBox itemSearchBox;
    private String itemSearch = "";
    private List<net.minecraft.world.item.ItemStack> filteredPickerItems = List.of();
    private java.util.List<com.mcl.cbtc.network.DisplayDevicesPayload.Entry> displayDevices = java.util.List.of();
    private java.util.List<com.mcl.cbtc.network.DeviceListPayload.Entry> peripheralDevices = java.util.List.of();
    /** 拓展器里是否装了硬盘，决定硬盘变量积木是否出现在侧边栏。 */
    private boolean hardDriveInstalled;
    private Node deviceMenuNode;
    private int deviceMenuIndex = -1;
    private final List<int[]> deviceMenuRects = new ArrayList<>();
    /** 参数下拉一次最多显示的行数，超出部分用滚轮翻页。 */
    private static final int DEVICE_MENU_MAX_ROWS = 10;
    private int deviceMenuScroll;
    private int deviceMenuMaxScroll;
    private int sidebarScroll;
    private int paletteContentH;
    /** 角度表盘：正在拾取角度的积木；null 表示未打开。 */
    private Node angleDialNode;
    private int angleDialIndex = -1;
    /** 表盘每帧算出的位置与尺寸，供命中检测使用。 */
    private int angleDialX;
    private int angleDialY;
    private int angleDialSize;
    /** 是否正按着指针拖动。 */
    private boolean angleDialDragging;

    // 布局结果（每帧重算）
    private final Map<Integer, int[]> worldRects = new LinkedHashMap<>(); // nodeId -> 世界坐标 x,y,w,h
    private final Map<Integer, int[]> rects = new LinkedHashMap<>(); // nodeId -> 屏幕坐标 x,y,w,h（命中检测用）
    private final List<SlotHit> slotHits = new ArrayList<>();
    private final List<ParamHit> paramHits = new ArrayList<>();
    private final List<CavityHit> cavityHits = new ArrayList<>();
    private final List<BranchHit> branchButtons = new ArrayList<>();
    private final List<ExtraHit> extraButtons = new ArrayList<>();
    private final List<PaletteEntry> palette = new ArrayList<>();
    private final Map<Integer, NodeLayout> layouts = new LinkedHashMap<>();
    private final Map<String, Integer> labelWCache = new HashMap<>();

    /** 每个积木实例唯一的布局结果。尺寸、绘制、输入框与命中区域都以它为准。 */
    private static final class NodeLayout {
        int x;
        int y;
        int width;
        int headerHeight;
        int height;
        /** 文字、普通槽位和参数在积木内的位置。 */
        final List<BlockItem> headerItems = new ArrayList<>();
        /** 顶条之后的每一段分支横条，与后面的空腔对应。 */
        final List<BranchLayout> branches = new ArrayList<>();
    }

    private static final class BlockItem {
        final String kind;
        final String key;
        final int index;
        int x;
        int y;
        int width;
        int height;
        String text = "";
        Node child;

        BlockItem(String kind, String key, int index) {
            this.kind = kind;
            this.key = key;
            this.index = index;
        }
    }

    private static final class BranchLayout {
        final String port;
        final String label;
        /** 0 表示末尾的“否则”，正数表示可删除的“否则如果”编号。 */
        final int branchNumber;
        int y;
        int height;
        int bodyHeight;
        final List<BlockItem> items = new ArrayList<>();

        BranchLayout(String port, String label, int branchNumber) {
            this.port = port;
            this.label = label;
            this.branchNumber = branchNumber;
        }
    }

    private record SlotHit(int nodeId, String port, PortKind kind, int x, int y, int w, int h,
                           int worldX, int worldY) {
    }

    private record ParamHit(int nodeId, int index, int x, int y, int w, int h, int worldX, int worldY) {
    }

    /** 参数输入框上一次的停靠位置：命中项偶发缺失时用它兜底，避免编辑状态被清空。 */
    private record ParamAnchor(int x, int y, int w, int h) {
    }

    private record CavityHit(int nodeId, String port, int x, int y, int w, int h) {
    }

    private record PaletteEntry(NodeCategory header, NodeType type, int y) {
    }

    /**
     * 一个吸附候选：mode 1=堆叠到 target 下方，2=放入 target 的空腔，3=嵌入 target 的凹槽 port。
     * <p>
     * {@code kind} 是凹槽实际接受的类型，可能随积木当前模式变化，接线的类型校验要用它。
     */
    private record Snap(int mode, Node target, String port, int x, int y, int w, int h, PortKind kind) {
        Snap(int mode, Node target, String port, int x, int y, int w, int h) {
            this(mode, target, port, x, y, w, h, target == null ? null : slotKindOf(target, port));
        }
    }

    private int canvasX() {
        return SIDEBAR_W;
    }

    private int canvasY() {
        return TOP_BAR;
    }

    private int canvasH() {
        return height - STATUS_H - TOP_BAR;
    }

    // ---------------------------------------------------------------- 相机

    /** 世界坐标 → 屏幕坐标。 */
    private double toScreenX(double worldX) {
        return canvasX() + (worldX - panX) * zoom;
    }

    private double toScreenY(double worldY) {
        return canvasY() + (worldY - panY) * zoom;
    }

    /** 屏幕坐标 → 世界坐标。 */
    private double toWorldX(double screenX) {
        return (screenX - canvasX()) / zoom + panX;
    }

    private double toWorldY(double screenY) {
        return (screenY - canvasY()) / zoom + panY;
    }

    /** 以屏幕上某点为锚点缩放，该点下的内容保持不动。 */
    private void zoomAt(double screenX, double screenY, double factor) {
        double wx = toWorldX(screenX);
        double wy = toWorldY(screenY);
        zoom = Math.max(0.5, Math.min(2.0, zoom * factor));
        panX = wx - (screenX - canvasX()) / zoom;
        panY = wy - (screenY - canvasY()) / zoom;
    }

    private boolean inCanvas(double mx, double my) {
        return mx >= canvasX() && mx < width && my >= canvasY() && my < height - STATUS_H;
    }

    public ComputerScreen(ComputerMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.cbtcMenu = menu;
    }

    public void acceptProgram(Program incoming, String computerName) {
        // 拖动/编辑中不要覆盖本地状态，避免玩家操作被打断
        if (dragging != null || draggingNew != null || paramTarget != null) {
            return;
        }
        program.replaceWith(incoming);
        dropUnknownNodes();
        if (computerName != null && !computerName.isBlank()) {
            this.computerName = computerName;
            if (nameBox != null) {
                nameBox.setValue(computerName);
            }
        }
        synced = true;
    }

    /**
     * 丢掉本版本已不认识的积木（例如旧存档里的 random_int_except 等已删除积木）。
     * 这类积木查不到 NodeType，布局阶段会拿到 null 直接崩溃，因此在装载时清理掉。
     */
    private void dropUnknownNodes() {
        List<Integer> stale = new ArrayList<>();
        for (Node n : program.nodes()) {
            if (n.type() == null) {
                stale.add(n.id());
            }
        }
        for (int id : stale) {
            program.removeNode(id);
        }
        // 旧存档里可能还留着已删除的"定时"模式，改写成定角，避免参数含义错乱
        for (Node n : program.nodes()) {
            if (n.type() != null && "gearbox_control".equals(n.typeId())
                    && n.param("mode").trim().startsWith("定时")) {
                n.setParam("mode", "angle");
            }
        }
    }

    /** 服务端下发的外设连接情况，直接显示在底部状态栏。 */
    public void acceptDeviceStatus(java.util.List<String> lines, boolean storageModule,
            boolean wirelessRedstoneModule, java.util.List<String> lanNetworks, boolean internetModule,
            boolean speakerModule, boolean hardDrive, java.util.List<String> songs,
            java.util.List<String> playlists) {
        this.deviceStatus = lines == null ? java.util.List.of() : java.util.List.copyOf(lines);
        this.storageModuleInstalled = storageModule;
        this.wirelessRedstoneModuleInstalled = wirelessRedstoneModule;
        this.lanNetworks = lanNetworks == null ? java.util.List.of() : java.util.List.copyOf(lanNetworks);
        this.lanInstalled = !this.lanNetworks.isEmpty();
        this.internetInstalled = internetModule;
        this.speakerInstalled = speakerModule;
        this.hardDriveInstalled = hardDrive;
        this.songLibrary = songs == null ? java.util.List.of() : java.util.List.copyOf(songs);
        this.playlistIds = playlists == null ? java.util.List.of() : java.util.List.copyOf(playlists);
        layoutPalette();
    }

    public void acceptWarehouseItems(java.util.List<com.simibubi.create.content.logistics.BigItemStack> items) {
        this.warehouseItems = items == null ? java.util.List.of() : java.util.List.copyOf(items);
    }

    public void acceptDisplayDevices(java.util.List<com.mcl.cbtc.network.DisplayDevicesPayload.Entry> devices) {
        this.displayDevices = devices == null ? java.util.List.of() : java.util.List.copyOf(devices);
    }

    /** 接收服务端下发的已绑定外设清单。 */
    public void acceptPeripherals(java.util.List<com.mcl.cbtc.network.DeviceListPayload.Entry> devices) {
        this.peripheralDevices = devices == null ? java.util.List.of() : java.util.List.copyOf(devices);
    }

    @Override
    protected void init() {
        super.init();
        // 从说明页返回时容器还开着，不需要重新请求程序。
        if (!fromHelp) {
            PacketDistributor.sendToServer(new ProgramRequestPayload(cbtcMenu.computer().getBlockPos()));
        }
        paramBox = new EditBox(font, 0, 0, 120, PARAM_H, Component.translatable("cbtc.gui.param"));
        paramBox.setMaxLength(200);
        paramBox.visible = false;
        addRenderableWidget(paramBox);

        String initialName = computerName.isBlank() ? cbtcMenu.computer().computerName() : computerName;
        if (initialName.isBlank()) {
            initialName = Component.translatable("cbtc.gui.name.default").getString();
        }
        computerName = initialName;
        nameBox = new EditBox(font, (width - NAME_W) / 2, 4, NAME_W, 16,
                Component.translatable("cbtc.gui.name"));
        nameBox.setMaxLength(24);
        nameBox.setValue(initialName);
        nameBox.setHint(Component.translatable("cbtc.gui.name.default"));
        nameBox.setCanLoseFocus(true);
        addRenderableWidget(nameBox);
    }

    private void saveAndClose() {
        commitParamEdit();
        String name = nameBox == null ? computerName : nameBox.getValue().trim();
        computerName = name.isEmpty() ? Component.translatable("cbtc.gui.name.default").getString() : name;
        PacketDistributor.sendToServer(new ProgramSavePayload(cbtcMenu.computer().getBlockPos(), program,
                computerName));
        onClose();
    }

    // ---------------------------------------------------------------- 形状分类

    private static boolean hasFlowInput(NodeType t) {
        for (NodeType.Port p : t.inputs()) {
            if (p.kind() == PortKind.FLOW) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasFlowOutput(NodeType t) {
        for (NodeType.Port p : t.outputs()) {
            if (p.kind() == PortKind.FLOW) {
                return true;
            }
        }
        return false;
    }

    /** 表达式块：没有任何执行流端口，产出数据。 */
    private static boolean isReporter(NodeType t) {
        return !hasFlowInput(t) && !hasFlowOutput(t);
    }

    /** 帽子块（事件）：只有执行流出口。 */
    private static boolean isHat(NodeType t) {
        return !hasFlowInput(t) && hasFlowOutput(t);
    }

    /** C 形块的空腔口，按从上到下的顺序返回：then / else / else1 / else2 ...（已排序）。 */
    private static List<String> cavityPorts(NodeType t) {
        return cavityPorts(t, 0);
    }

    /** 根据积木上记录的"否则如果"分支数，展开空腔列表。 */
    private static List<String> cavityPorts(NodeType t, int branches) {
        List<String> ports = new ArrayList<>();
        if (!"if".equals(t.id()) && !"if_else".equals(t.id())) {
            for (String key : List.of("then", "body")) {
                for (NodeType.Port p : t.outputs()) {
                    if (p.kind() == PortKind.FLOW && p.key().equals(key)) {
                        ports.add(key);
                        break;
                    }
                }
            }
            return ports;
        }
        for (NodeType.Port p : t.outputs()) {
            if (p.kind() == PortKind.FLOW && p.key().equals("then")) {
                ports.add("then");
                break;
            }
        }
        if ("if_else".equals(t.id())) {
            // branches 只表示“否则如果”数量：else1/else2/... 为对应空腔，
            // else 是始终放在最后的兜底空腔。
            for (int i = 1; i <= branches; i++) {
                ports.add("else" + i);
            }
            ports.add("else");
        }
        return ports;
    }

    /**
     * 槽位接收的类型。
     * <p>
     * 「旋转至」的目标角度槽虽然和其它模式共用 value 端口，但只收角度积木，
     * 所以要按积木实例当前模式临时改成角度类型。
     */
    private static PortKind slotKindOf(Node n, String key) {
        if (isAngleSlot(n, key)) {
            return PortKind.ANGLE;
        }
        return n.type() == null ? PortKind.BOOL : slotKind(n.type(), key);
    }

    private static PortKind slotKind(NodeType t, String key) {
        for (NodeType.Port p : t.inputs()) {
            if (p.key().equals(key)) {
                return p.kind();
            }
        }
        return PortKind.BOOL;
    }

    /** 某个积木实例需要的数据凹槽：普通输入口 + "否则如果"分支各自的条件口。 */
    private static List<String> dataPortsOf(Node n) {
        NodeType t = n.type();
        List<String> ports = new ArrayList<>();
        if (t == null) {
            return ports;
        }
        for (NodeType.Port p : t.inputs()) {
            if (p.kind() != PortKind.FLOW) {
                ports.add(p.key());
            }
        }
        return ports;
    }

    /** 某个积木实例当前的空腔口列表：按它自己记录的"否则如果"分支数展开。 */
    private static List<String> cavityPortsOf(Node n) {
        NodeType t = n.type();
        if (t == null) {
            return List.of();
        }
        return cavityPorts(t, parseBranches(n));
    }

    /** 积木上记录的"否则如果"分支数（0 表示只有"否则"）。 */
    private static int parseBranches(Node n) {
        try {
            return Math.max(0, Integer.parseInt(n.param("branches").trim()));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** 第一个空腔口（用于判断是不是 C 形块）。 */
    private static String cavityPort(NodeType t) {
        List<String> ports = cavityPorts(t);
        return ports.isEmpty() ? null : ports.get(0);
    }

    /** 继续往下走的执行流口：exec（命令/帽子）或 done（循环体结束之后）。if 没有继续口。 */
    private static String continuationPort(NodeType t) {
        for (NodeType.Port p : t.outputs()) {
            if (p.kind() == PortKind.FLOW && (p.key().equals("exec") || p.key().equals("done"))) {
                return p.key();
            }
        }
        return null;
    }

    private static String firstDataOut(NodeType t) {
        for (NodeType.Port p : t.outputs()) {
            if (p.kind() != PortKind.FLOW) {
                return p.key();
            }
        }
        return null;
    }

    private static PortKind dataOutKind(NodeType t) {
        for (NodeType.Port p : t.outputs()) {
            if (p.kind() != PortKind.FLOW) {
                return p.kind();
            }
        }
        return PortKind.ANY;
    }

    // ---------------------------------------------------------------- 线 → 结构

    private Node stackNext(Node n) {
        String cont = n.type() == null ? null : continuationPort(n.type());
        if (cont == null) {
            return null;
        }
        Wire w = program.wireFrom(n.id(), cont).orElse(null);
        return w == null ? null : program.node(w.toNode()).orElse(null);
    }

    private Node slotChild(Node n, String port) {
        List<Wire> wires = program.wiresTo(n.id(), port);
        if (wires.isEmpty()) {
            return null;
        }
        return program.node(wires.get(0).fromNode()).orElse(null);
    }

    private boolean isRoot(Node n) {
        NodeType t = n.type();
        if (t == null) {
            return false;
        }
        if (isReporter(t)) {
            // 表达式被嵌进凹槽时不是根
            for (NodeType.Port p : t.outputs()) {
                if (p.kind() != PortKind.FLOW && program.wireFrom(n.id(), p.key()).isPresent()) {
                    return false;
                }
            }
            return true;
        }
        return program.wiresTo(n.id(), "exec").isEmpty();
    }

    private void collectChain(Node n, Set<Integer> out) {
        Node cur = n;
        while (cur != null && out.add(cur.id())) {
            collectChildren(cur, out);
            cur = stackNext(cur);
        }
    }

    private void collectChildren(Node n, Set<Integer> out) {
        NodeType t = n.type();
        if (t == null) {
            return;
        }
        for (BlockItem item : layoutOf(n).headerItems) {
            if (item.child != null) {
                collectChain(item.child, out);
            }
        }
        for (BranchLayout branch : layoutOf(n).branches) {
            for (BlockItem item : branch.items) {
                if (item.child != null) {
                    collectChain(item.child, out);
                }
            }
        }
        for (String cavity : cavityPortsOf(n)) {
            Wire wire = program.wireFrom(n.id(), cavity).orElse(null);
            Node body = wire == null ? null : program.node(wire.toNode()).orElse(null);
            if (body != null) {
                collectChain(body, out);
            }
        }
    }

    // ---------------------------------------------------------------- 尺寸、布局与绘制

    private NodeLayout layoutOf(Node n, int depth) {
        NodeLayout cached = layouts.get(n.id());
        if (cached != null) {
            return cached;
        }
        NodeLayout layout = new NodeLayout();
        layouts.put(n.id(), layout);
        if (n.type() == null || depth > 48) {
            layout.width = 88;
            layout.headerHeight = STACK_H;
            layout.height = STACK_H;
            return layout;
        }
        buildHeader(n, layout, depth);
        buildBranches(n, layout, depth);
        layout.height = layout.headerHeight + (isHat(n.type()) ? HAT_CURVE : 0);
        List<String> cavities = cavityPortsOf(n);
        if (cavities.isEmpty() || isReporter(n.type())) {
            return layout;
        }
        int y = layout.height;
        // 每个空腔都对应一份布局；首个空腔没有额外横条，高度为 0。
        layout.branches.add(0, new BranchLayout(cavities.get(0), "", -1));
        for (BranchLayout branch : layout.branches) {
            branch.y = y;
            y += branch.height;
            branch.bodyHeight = bodyHeight(n, branch.port, depth + 1);
            y += branch.bodyHeight;
        }
        layout.height = y + C_FOOT;
        return layout;
    }

    private void buildHeader(Node n, NodeLayout layout, int depth) {
        NodeType type = n.type();
        if (type == null) {
            return;
        }
        int cursor = PAD;
        String head = "if_else".equals(type.id()) ? tr("cbtc.node.parts.if_else.head") : labelHead(type);
        if (!head.isEmpty() && operatorIndex(n) < 0) {
            cursor = addText(layout.headerItems, cursor, head);
        }
        if ("wireless_display".equals(type.id())) {
            cursor = addText(layout.headerItems, cursor, Component.translatable("cbtc.gui.display_device").getString());
            cursor = addParam(n, layout.headerItems, cursor, type.paramIndex("device"));
            cursor = addText(layout.headerItems, cursor, Component.translatable("cbtc.gui.display_line").getString());
            cursor = addParam(n, layout.headerItems, cursor, type.paramIndex("line"));
            cursor = addText(layout.headerItems, cursor, Component.translatable("cbtc.gui.display_text").getString());
            addSlot(n, layout.headerItems, cursor, "text", depth + 1);
            finishHeader(n, layout, depth);
            return;
        }
        if ("info_receive".equals(type.id())) {
            // 信息获取 [转发器] 的第 [行号] 行的内容
            cursor = addParam(n, layout.headerItems, cursor, type.paramIndex("device"));
            cursor = addText(layout.headerItems, cursor, Component.translatable("cbtc.gui.info_line_prefix").getString());
            cursor = addParam(n, layout.headerItems, cursor, type.paramIndex("line"));
            cursor = addText(layout.headerItems, cursor, Component.translatable("cbtc.gui.info_line_suffix").getString());
            finishHeader(n, layout, depth);
            return;
        }
        if (isWirelessRedstoneType(type)) {
            cursor = addText(layout.headerItems, cursor, Component.translatable("cbtc.redstone.frequency1").getString());
            cursor = addParam(n, layout.headerItems, cursor, type.paramIndex("channel"));
            cursor = addText(layout.headerItems, cursor, Component.translatable("cbtc.redstone.frequency2").getString());
            cursor = addParam(n, layout.headerItems, cursor, type.paramIndex("channel2"));
            if ("redstone_receive_compare".equals(type.id()) || "redstone_received_compare".equals(type.id())) {
                cursor = addText(layout.headerItems, cursor, Component.translatable("cbtc.redstone.when_strength").getString());
                cursor = addParam(n, layout.headerItems, cursor, type.paramIndex("op"));
                addSlot(n, layout.headerItems, cursor, "strength", depth + 1);
            } else if ("redstone_send".equals(type.id())) {
                cursor = addText(layout.headerItems, cursor, Component.translatable("cbtc.param.phase").getString());
                cursor = addSlot(n, layout.headerItems, cursor, "phase", depth + 1);
                cursor = addText(layout.headerItems, cursor, Component.translatable("cbtc.param.gap").getString());
                cursor = addSlot(n, layout.headerItems, cursor, "gap", depth + 1);
                cursor = addText(layout.headerItems, cursor, Component.translatable("cbtc.param.lit").getString());
                cursor = addSlot(n, layout.headerItems, cursor, "lit", depth + 1);
                cursor = addText(layout.headerItems, cursor, Component.translatable("cbtc.param.strength").getString());
                addSlot(n, layout.headerItems, cursor, "strength", depth + 1);
            }
            finishHeader(n, layout, depth);
            return;
        }
        if ("gearbox_control".equals(type.id())) {
            buildGearboxHeader(n, layout, cursor);
            finishHeader(n, layout, depth);
            return;
        }
        if ("gearbox_stop".equals(type.id())) {
            cursor = addText(layout.headerItems, cursor,
                    Component.translatable("cbtc.gui.gearbox_stop_at").getString());
            cursor = addParam(n, layout.headerItems, cursor, type.paramIndex("device"));
            addText(layout.headerItems, cursor,
                    Component.translatable("cbtc.gui.gearbox_stop_action").getString());
            finishHeader(n, layout, depth);
            return;
        }
        if ("speed_controller_set".equals(type.id())) {
            // 设置转速控制器 [设备] 转速 [RPM] RPM [顺时针/逆时针]
            cursor = addText(layout.headerItems, cursor,
                    Component.translatable("cbtc.gui.sc_set_at").getString());
            cursor = addParam(n, layout.headerItems, cursor, type.paramIndex("device"));
            cursor = addText(layout.headerItems, cursor,
                    Component.translatable("cbtc.gui.sc_set_speed").getString());
            cursor = addSlot(n, layout.headerItems, cursor, "rpm", depth + 1);
            cursor = addText(layout.headerItems, cursor,
                    Component.translatable("cbtc.gui.sc_rpm_unit").getString());
            addParam(n, layout.headerItems, cursor, type.paramIndex("direction"));
            finishHeader(n, layout, depth);
            return;
        }
        if ("speed_controller_get".equals(type.id())) {
            // 转速控制器的输出转速 于 [设备]
            cursor = addText(layout.headerItems, cursor,
                    Component.translatable("cbtc.gui.sc_get_speed").getString());
            cursor = addText(layout.headerItems, cursor,
                    Component.translatable("cbtc.gui.blk_of").getString());
            addParam(n, layout.headerItems, cursor, type.paramIndex("device"));
            finishHeader(n, layout, depth);
            return;
        }
        if ("typewriter_long_press".equals(type.id())) {
            cursor = addText(layout.headerItems, cursor, tr("cbtc.gui.blk_long_press_head"));
            cursor = addParam(n, layout.headerItems, cursor, type.paramIndex("device"));
            cursor = addText(layout.headerItems, cursor, tr("cbtc.gui.blk_of"));
            cursor = addParam(n, layout.headerItems, cursor, type.paramIndex("key"));
            addText(layout.headerItems, cursor, tr("cbtc.gui.blk_when_suffix"));
            finishHeader(n, layout, depth);
            return;
        }
        if ("typewriter_key_down".equals(type.id())) {
            cursor = addText(layout.headerItems, cursor, tr("cbtc.gui.blk_typewriter"));
            cursor = addParam(n, layout.headerItems, cursor, type.paramIndex("device"));
            cursor = addText(layout.headerItems, cursor, tr("cbtc.gui.blk_key"));
            addParam(n, layout.headerItems, cursor, type.paramIndex("key"));
            finishHeader(n, layout, depth);
            return;
        }
        if ("typewriter_key_held".equals(type.id())) {
            // "当打字机长按 [设备] 的 [按键] 中"
            cursor = addText(layout.headerItems, cursor, tr("cbtc.gui.blk_typewriter"));
            cursor = addParam(n, layout.headerItems, cursor, type.paramIndex("device"));
            cursor = addText(layout.headerItems, cursor, tr("cbtc.gui.blk_pressed"));
            addParam(n, layout.headerItems, cursor, type.paramIndex("key"));
            finishHeader(n, layout, depth);
            return;
        }
        if ("time_write".equals(type.id()) || "time_duration".equals(type.id())) {
            // 书写时间 / 书写计时：数值框 + 单位选择器
            cursor = addSlot(n, layout.headerItems, cursor, "amount", depth + 1);
            addParam(n, layout.headerItems, cursor, type.paramIndex("unit"));
            finishHeader(n, layout, depth);
            return;
        }
        if ("time_current".equals(type.id())) {
            // 当前时间：单位选择器
            addParam(n, layout.headerItems, cursor, type.paramIndex("unit"));
            finishHeader(n, layout, depth);
            return;
        }
        if ("time_math".equals(type.id()) || "time_compare".equals(type.id())) {
            // 时间运算/比较：左时间 + 运算符 + 右时间
            cursor = addSlot(n, layout.headerItems, cursor, "a", depth + 1);
            cursor = addParam(n, layout.headerItems, cursor, type.paramIndex("op"));
            addSlot(n, layout.headerItems, cursor, "b", depth + 1);
            finishHeader(n, layout, depth);
            return;
        }
        if ("angle_value".equals(type.id())) {
            // 固定角度：数字框（可填可嵌） + °
            cursor = addSlot(n, layout.headerItems, cursor, "amount", depth + 1);
            addText(layout.headerItems, cursor, "°");
            finishHeader(n, layout, depth);
            return;
        }
        if ("gearbox_angle".equals(type.id())) {
            cursor = addText(layout.headerItems, cursor,
                    Component.translatable("cbtc.gui.gearbox_at").getString());
            cursor = addParam(n, layout.headerItems, cursor, type.paramIndex("device"));
            addText(layout.headerItems, cursor,
                    Component.translatable("cbtc.gui.gearbox_angle_suffix").getString());
            finishHeader(n, layout, depth);
            return;
        }
        if ("nav_table_pos".equals(type.id()) || "nav_table_target".equals(type.id())) {
            cursor = addText(layout.headerItems, cursor,
                    tr("nav_table_pos".equals(type.id()) ? "cbtc.gui.blk_navtable_of" : "cbtc.gui.blk_navtable_target_of"));
            cursor = addParam(n, layout.headerItems, cursor, type.paramIndex("device"));
            addParam(n, layout.headerItems, cursor, type.paramIndex("axis"));
            finishHeader(n, layout, depth);
            return;
        }
        if ("target_locator_pos".equals(type.id())) {
            cursor = addParam(n, layout.headerItems, cursor, type.paramIndex("device"));
            addParam(n, layout.headerItems, cursor, type.paramIndex("axis"));
            finishHeader(n, layout, depth);
            return;
        }
        if ("target_locator_set".equals(type.id())) {
            cursor = addParam(n, layout.headerItems, cursor, type.paramIndex("device"));
            cursor = addParam(n, layout.headerItems, cursor, type.paramIndex("axis"));
            cursor = addText(layout.headerItems, cursor, tr("cbtc.gui.blk_set_to"));
            addSlot(n, layout.headerItems, cursor, "value", depth + 1);
            finishHeader(n, layout, depth);
            return;
        }
        if ("nav_table_distance".equals(type.id())) {
            cursor = addText(layout.headerItems, cursor, tr("cbtc.gui.blk_navtable_distance"));
            cursor = addParam(n, layout.headerItems, cursor, type.paramIndex("device"));
            addText(layout.headerItems, cursor, tr("cbtc.gui.blk_meter"));
            finishHeader(n, layout, depth);
            return;
        }
        if ("nav_bearing_direction".equals(type.id())) {
            cursor = addText(layout.headerItems, cursor, tr("cbtc.gui.blk_target_at_navtable"));
            cursor = addParam(n, layout.headerItems, cursor, type.paramIndex("device"));
            cursor = addText(layout.headerItems, cursor, tr("cbtc.gui.blk_of"));
            cursor = addParam(n, layout.headerItems, cursor, type.paramIndex("direction"));
            addText(layout.headerItems, cursor, tr("cbtc.gui.blk_bearing"));
            finishHeader(n, layout, depth);
            return;
        }
        if ("nav_bearing_range".equals(type.id())) {
            cursor = addText(layout.headerItems, cursor, tr("cbtc.gui.blk_target_at_navtable"));
            cursor = addParam(n, layout.headerItems, cursor, type.paramIndex("device"));
            cursor = addText(layout.headerItems, cursor, tr("cbtc.gui.blk_of"));
            cursor = addSlot(n, layout.headerItems, cursor, "min", depth + 1);
            cursor = addText(layout.headerItems, cursor, tr("cbtc.gui.blk_deg_to"));
            cursor = addSlot(n, layout.headerItems, cursor, "max", depth + 1);
            addText(layout.headerItems, cursor, tr("cbtc.gui.blk_deg_range"));
            finishHeader(n, layout, depth);
            return;
        }
        if ("nav_bearing_angle".equals(type.id())) {
            cursor = addText(layout.headerItems, cursor, tr("cbtc.gui.blk_target_at_navtable"));
            cursor = addParam(n, layout.headerItems, cursor, type.paramIndex("device"));
            addText(layout.headerItems, cursor, tr("cbtc.gui.blk_bearing_angle"));
            finishHeader(n, layout, depth);
            return;
        }
        if ("nav_line_direction".equals(type.id())) {
            cursor = addText(layout.headerItems, cursor, tr("cbtc.gui.blk_target_between"));
            cursor = addText(layout.headerItems, cursor, tr("cbtc.gui.blk_front"));
            cursor = addParam(n, layout.headerItems, cursor, type.paramIndex("device_a"));
            cursor = addText(layout.headerItems, cursor, tr("cbtc.gui.blk_back"));
            cursor = addParam(n, layout.headerItems, cursor, type.paramIndex("device_b"));
            cursor = addText(layout.headerItems, cursor, tr("cbtc.gui.blk_of"));
            cursor = addParam(n, layout.headerItems, cursor, type.paramIndex("direction"));
            cursor = addText(layout.headerItems, cursor, tr("cbtc.gui.blk_of"));
            cursor = addParam(n, layout.headerItems, cursor, type.paramIndex("bearing"));
            addText(layout.headerItems, cursor, tr("cbtc.gui.blk_bearing_short"));
            finishHeader(n, layout, depth);
            return;
        }
        if ("nav_line_range".equals(type.id())) {
            cursor = addText(layout.headerItems, cursor, tr("cbtc.gui.blk_target_between"));
            cursor = addText(layout.headerItems, cursor, tr("cbtc.gui.blk_front"));
            cursor = addParam(n, layout.headerItems, cursor, type.paramIndex("device_a"));
            cursor = addText(layout.headerItems, cursor, tr("cbtc.gui.blk_back"));
            cursor = addParam(n, layout.headerItems, cursor, type.paramIndex("device_b"));
            cursor = addText(layout.headerItems, cursor, tr("cbtc.gui.blk_of"));
            cursor = addParam(n, layout.headerItems, cursor, type.paramIndex("direction"));
            cursor = addText(layout.headerItems, cursor, tr("cbtc.gui.blk_bearing_at"));
            cursor = addSlot(n, layout.headerItems, cursor, "min", depth + 1);
            cursor = addText(layout.headerItems, cursor, tr("cbtc.gui.blk_deg_to"));
            cursor = addSlot(n, layout.headerItems, cursor, "max", depth + 1);
            addText(layout.headerItems, cursor, tr("cbtc.gui.blk_deg_range_short"));
            finishHeader(n, layout, depth);
            return;
        }
        if ("nav_line_angle".equals(type.id())) {
            cursor = addText(layout.headerItems, cursor, tr("cbtc.gui.blk_target_between"));
            cursor = addText(layout.headerItems, cursor, tr("cbtc.gui.blk_front"));
            cursor = addParam(n, layout.headerItems, cursor, type.paramIndex("device_a"));
            cursor = addText(layout.headerItems, cursor, tr("cbtc.gui.blk_back"));
            cursor = addParam(n, layout.headerItems, cursor, type.paramIndex("device_b"));
            cursor = addText(layout.headerItems, cursor, tr("cbtc.gui.blk_of"));
            cursor = addParam(n, layout.headerItems, cursor, type.paramIndex("direction"));
            cursor = addText(layout.headerItems, cursor, tr("cbtc.gui.blk_bearing_convert"));
            cursor = addParam(n, layout.headerItems, cursor, type.paramIndex("device_gearbox"));
            addText(layout.headerItems, cursor, tr("cbtc.gui.blk_convert_abs"));
            finishHeader(n, layout, depth);
            return;
        }
        if ("angle_math".equals(type.id()) || "angle_compare".equals(type.id())) {
            // 角度运算/比较：左角度 + 运算符 + 右角度
            cursor = addSlot(n, layout.headerItems, cursor, "a", depth + 1);
            cursor = addParam(n, layout.headerItems, cursor, type.paramIndex("op"));
            addSlot(n, layout.headerItems, cursor, "b", depth + 1);
            finishHeader(n, layout, depth);
            return;
        }
        if ("play_sound".equals(type.id()) || "murmur".equals(type.id())) {
            buildSoundHeader(n, layout, cursor, depth);
            finishHeader(n, layout, depth);
            return;
        }
        if (isMusicNode(type.id())) {
            buildMusicHeader(n, layout, cursor, depth);
            finishHeader(n, layout, depth);
            return;
        }
        if ("math_round".equals(type.id())) {
            // 估读 [数值] 到小数点后第 [位数] 位
            cursor = addText(layout.headerItems, cursor, tr("cbtc.gui.blk_round_head"));
            cursor = addSlot(n, layout.headerItems, cursor, "value", depth + 1);
            cursor = addText(layout.headerItems, cursor, tr("cbtc.gui.blk_round_mid"));
            cursor = addSlot(n, layout.headerItems, cursor, "digits", depth + 1);
            addText(layout.headerItems, cursor, tr("cbtc.gui.blk_round_tail"));
            finishHeader(n, layout, depth);
            return;
        }
        if ("math_abs".equals(type.id()) || "math_negate".equals(type.id())
                || "math_scientific".equals(type.id())) {
            buildMathUnaryHeader(n, layout, cursor, depth);
            finishHeader(n, layout, depth);
            return;
        }
        if ("math_root".equals(type.id())) {
            // 次方根 [开方数]，次数 [次数]
            cursor = addText(layout.headerItems, cursor, tr("cbtc.gui.blk_root_head"));
            cursor = addSlot(n, layout.headerItems, cursor, "value", depth + 1);
            cursor = addText(layout.headerItems, cursor, tr("cbtc.gui.blk_root_mid"));
            addSlot(n, layout.headerItems, cursor, "degree", depth + 1);
            finishHeader(n, layout, depth);
            return;
        }
        if ("random_int".equals(type.id())) {
            buildRandomHeader(n, layout, cursor, depth);
            finishHeader(n, layout, depth);
            return;
        }
        if (isSeqNode(type.id())) {
            buildSeqHeader(n, layout, cursor, depth);
            finishHeader(n, layout, depth);
            return;
        }
        if ("stop_sound".equals(type.id())) {
            // 只停指定设备：未绑定发声外设时只有"此电脑"
            cursor = addText(layout.headerItems, cursor, tr("cbtc.gui.lan_at"));
            addParam(n, layout.headerItems, cursor, type.paramIndex("device"));
            finishHeader(n, layout, depth);
            return;
        }
        if (isLanType(type)) {
            buildLanHeader(n, layout, cursor, depth);
            finishHeader(n, layout, depth);
            return;
        }
        if (isInternetType(type)) {
            buildInternetHeader(n, layout, cursor, depth);
            finishHeader(n, layout, depth);
            return;
        }
        if (isChatType(type)) {
            buildChatHeader(n, layout, cursor, depth);
            finishHeader(n, layout, depth);
            return;
        }
        if ("warehouse_item_count".equals(type.id()) || "packager_send".equals(type.id())) {
            buildLogisticsHeader(n, layout, cursor, depth);
            finishHeader(n, layout, depth);
            return;
        }
        int operator = operatorIndex(n);
        int keyIndex = 0;
        for (NodeType.Port port : type.inputs()) {
            if (port.kind() == PortKind.FLOW) {
                continue;
            }
            if (operator >= 0 && keyIndex == 1) {
                cursor = addParam(n, layout.headerItems, cursor, operator);
            }
            cursor = addSlot(n, layout.headerItems, cursor, port.key(), depth + 1);
            // 「如果…否则」的主行也要带上"那么"，与编程猫一致的"如果 <条件> 那么"形态
            if (keyIndex == 0 && "if_else".equals(type.id())) {
                String then = tr("cbtc.node.parts.if_else.tail");
                if (!then.isEmpty()) {
                    cursor = addText(layout.headerItems, cursor, then);
                }
            }
            if (keyIndex == 0) {
                String tail = labelTail(type);
                if (!tail.isEmpty() && operator < 0) {
                    cursor = addText(layout.headerItems, cursor, tail);
                }
            }
            keyIndex++;
        }
        if (operator >= 0 && keyIndex < 2) {
            cursor = addParam(n, layout.headerItems, cursor, operator);
        }
        // 拼接文本的追加项：每项一个文本口（t1、t2…）
        int extra = extraCount(n);
        for (int i = 1; i <= extra; i++) {
            cursor = addSlot(n, layout.headerItems, cursor, "t" + i, depth + 1);
        }
        for (int i = 0; i < type.params().size(); i++) {
            String key = type.params().get(i);
            if (i != operator && !paramSharesPort(n, i) && !"branches".equals(key)
                    && !"extra".equals(key)) {
                cursor = addParam(n, layout.headerItems, cursor, i);
            }
        }
        finishHeader(n, layout, depth);
        if (isExtendable(type)) {
            // 给右侧的加号/减号按钮留出位置，必须在 finishHeader 之后
            layout.width = Math.max(layout.width, cursor + extraButtonsWidth(n));
        }
    }

    /** 拼接文本上追加的文本口数量。 */
    private static int extraCount(Node n) {
        NodeType type = n.type();
        if (type == null || !type.params().contains("extra")) {
            return 0;
        }
        try {
            return Math.max(0, Integer.parseInt(n.param("extra").trim()));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** 互联网积木：频道 / 变量名各自跟着说明文字。 */
    private void buildInternetHeader(Node n, NodeLayout layout, int cursor, int depth) {
        NodeType type = n.type();
        boolean named = "net_var_set".equals(type.id()) || "net_var_get".equals(type.id());
        cursor = addText(layout.headerItems, cursor,
                Component.translatable(named ? "cbtc.gui.lan_named" : "cbtc.gui.lan_channel").getString());
        cursor = addParam(n, layout.headerItems, cursor, type.paramIndex(named ? "name" : "channel"));
        if ("net_var_set".equals(type.id())) {
            String tail = labelTail(type);
            if (!tail.isEmpty()) {
                cursor = addText(layout.headerItems, cursor, tail);
            }
            addSlot(n, layout.headerItems, cursor, "value", depth + 1);
        }
    }

    /** 聊天积木：发送面向全体玩家；私聊与识别各带一个可 Tab 补全的发信目标。 */
    private void buildChatHeader(Node n, NodeLayout layout, int cursor, int depth) {
        NodeType type = n.type();
        if ("chat_any".equals(type.id()) || "chat_listening".equals(type.id())) {
            // 纯条件积木：只显示积木名称，没有触发词、文本或发信目标
            return;
        }
        if ("chat_listen".equals(type.id())) {
            // 监听积木：触发词 + 监听时长（用槽位，单位由时间积木提供）
            cursor = addText(layout.headerItems, cursor,
                    Component.translatable("cbtc.gui.chat_trigger").getString());
            cursor = addParam(n, layout.headerItems, cursor, type.paramIndex("trigger"));
            cursor = addText(layout.headerItems, cursor,
                    Component.translatable("cbtc.gui.chat_listen_for").getString());
            addSlot(n, layout.headerItems, cursor, "duration", depth + 1);
            return;
        }
        boolean recognize = "chat_recognize".equals(type.id());
        if (recognize) {
            // 识别积木只读聊天内容，没有"要说什么"的文本口，不画 text 槽位
            cursor = addText(layout.headerItems, cursor,
                    Component.translatable("cbtc.gui.chat_keyword").getString());
            cursor = addParam(n, layout.headerItems, cursor, type.paramIndex("keyword"));
        } else {
            cursor = addSlot(n, layout.headerItems, cursor, "text", depth + 1);
        }
        if ("chat_send".equals(type.id())) {
            // 面向全体玩家的那块不显示目标，避免误以为能私聊
            return;
        }
        cursor = addText(layout.headerItems, cursor,
                Component.translatable("cbtc.gui.chat_target").getString());
        addParam(n, layout.headerItems, cursor, type.paramIndex("target"));
    }

    /** 齿轮箱积木：目标齿轮箱 + 方向 + 转速 + 模式 +（定时/定角才有）数值。 */
    private void buildGearboxHeader(Node n, NodeLayout layout, int cursor) {
        NodeType type = n.type();
        cursor = addText(layout.headerItems, cursor,
                Component.translatable("cbtc.gui.gearbox_at").getString());
        cursor = addParam(n, layout.headerItems, cursor, type.paramIndex("device"));
        String mode = n.param("mode");
        cursor = addParam(n, layout.headerItems, cursor, type.paramIndex("direction"));
        cursor = addText(layout.headerItems, cursor,
                Component.translatable("cbtc.gui.gearbox_speed").getString());
        // 转速改成倍率档：×1 / ×2 下拉，输出转速 = 输入转速 × 倍率
        cursor = addParam(n, layout.headerItems, cursor, type.paramIndex("speed"));
        cursor = addText(layout.headerItems, cursor,
                Component.translatable("cbtc.gui.gearbox_rpm").getString());
        cursor = addParam(n, layout.headerItems, cursor, type.paramIndex("mode"));
        if (mode.startsWith(tr("cbtc.gui.mode_angle"))) {
            cursor = addText(layout.headerItems, cursor,
                    Component.translatable("cbtc.gui.gearbox_degrees").getString());
            addSlot(n, layout.headerItems, cursor, "value", 2);
        } else if (mode.startsWith(tr("cbtc.gui.mode_rotate_to"))) {
            // 面向 N 度：数值胶囊在中间、单位跟在后面，与表盘拾取器的写法一致
            cursor = addText(layout.headerItems, cursor,
                    Component.translatable("cbtc.gui.gearbox_facing").getString());
            cursor = addSlot(n, layout.headerItems, cursor, "value", 2);
            addText(layout.headerItems, cursor,
                    Component.translatable("cbtc.gui.gearbox_degrees").getString());
        }
    }

    /** 局域网积木：标出"在哪个局域网、哪个频道 / 变量名"，参数胶囊点开是下拉选择。 */
    private void buildLanHeader(Node n, NodeLayout layout, int cursor, int depth) {
        NodeType type = n.type();
        cursor = addText(layout.headerItems, cursor,
                Component.translatable("cbtc.gui.lan_at").getString());
        cursor = addParam(n, layout.headerItems, cursor, type.paramIndex("network"));
        boolean named = "lan_var_set".equals(type.id()) || "lan_var_get".equals(type.id());
        cursor = addText(layout.headerItems, cursor,
                Component.translatable(named ? "cbtc.gui.lan_named" : "cbtc.gui.lan_channel").getString());
        cursor = addParam(n, layout.headerItems, cursor, type.paramIndex(named ? "name" : "channel"));
        if ("lan_var_set".equals(type.id())) {
            String tail = labelTail(type);
            if (!tail.isEmpty()) {
                cursor = addText(layout.headerItems, cursor, tail);
            }
            addSlot(n, layout.headerItems, cursor, "value", depth + 1);
        }
    }

    /** 仓储积木：明确标出物品、数量和地址，端口仍可接表达式。 */
    private void buildLogisticsHeader(Node n, NodeLayout layout, int cursor, int depth) {
        NodeType type = n.type();
        cursor = addText(layout.headerItems, cursor,
                Component.translatable("cbtc.param.item").getString());
        cursor = addParam(n, layout.headerItems, cursor, type.paramIndex("item"));
        for (NodeType.Port port : type.inputs()) {
            if (port.kind() == PortKind.FLOW) {
                continue;
            }
            cursor = addText(layout.headerItems, cursor,
                    Component.translatable("cbtc.param." + port.key()).getString());
            cursor = addSlot(n, layout.headerItems, cursor, port.key(), depth + 1);
        }
    }

    /** 声音类积木：在参数之间插入"音量""范围"等说明文字。 */
    private void buildSoundHeader(Node n, NodeLayout layout, int cursor, int depth) {
        NodeType type = n.type();
        if (type == null) {
            return;
        }
        // 由哪台设备发声：未绑定发声外设时只有"此电脑"
        cursor = addText(layout.headerItems, cursor, tr("cbtc.gui.lan_at"));
        cursor = addParam(n, layout.headerItems, cursor, type.paramIndex("device"));
        for (NodeType.Port port : type.inputs()) {
            if (port.kind() != PortKind.FLOW) {
                cursor = addSlot(n, layout.headerItems, cursor, port.key(), depth + 1);
            }
        }
        if ("play_sound".equals(type.id())) {
            cursor = addParam(n, layout.headerItems, cursor, type.paramIndex("sound"));
        }
        // 默念没有音量和范围：它不发声，只浮字
        if (type.paramIndex("volume") >= 0) {
            cursor = addText(layout.headerItems, cursor, tr("cbtc.gui.blk_volume"));
            cursor = addParam(n, layout.headerItems, cursor, type.paramIndex("volume"));
            // 音量拉条：与数值框并存，拖动直接改同一个参数
            cursor = addSlider(layout.headerItems, cursor, type.paramIndex("volume"));
        }
        if (type.paramIndex("range") >= 0) {
            cursor = addText(layout.headerItems, cursor, tr("cbtc.gui.blk_range"));
            addParam(n, layout.headerItems, cursor, type.paramIndex("range"));
        }
    }

    /** 网络歌曲相关积木。 */
    private static boolean isMusicNode(String typeId) {
        return "music_play".equals(typeId) || "music_play_index".equals(typeId)
                || "music_song_name".equals(typeId) || "music_song_artist".equals(typeId)
                || "music_song_total".equals(typeId) || "music_song_time".equals(typeId)
                || "music_song_lyric".equals(typeId);
    }

    /** 网络歌曲积木的头部：在 [设备] 播放 [歌曲] 第 [N] 首 声音 范围。 */
    private void buildMusicHeader(Node n, NodeLayout layout, int cursor, int depth) {
        NodeType type = n.type();
        if (type == null) {
            return;
        }
        cursor = addText(layout.headerItems, cursor, tr("cbtc.gui.lan_at"));
        cursor = addParam(n, layout.headerItems, cursor, type.paramIndex("device"));
        if ("music_play".equals(type.id())) {
            cursor = addText(layout.headerItems, cursor, tr("cbtc.gui.blk_play"));
            cursor = addParam(n, layout.headerItems, cursor, type.paramIndex("song"));
        } else if ("music_play_index".equals(type.id())) {
            cursor = addText(layout.headerItems, cursor, tr("cbtc.gui.blk_play_playlist"));
            cursor = addParam(n, layout.headerItems, cursor, type.paramIndex("playlist"));
            cursor = addText(layout.headerItems, cursor, tr("cbtc.gui.blk_number_of"));
            cursor = addSlot(n, layout.headerItems, cursor, "index", depth + 1);
            cursor = addText(layout.headerItems, cursor, tr("cbtc.gui.blk_song_unit"));
        } else {
            cursor = addText(layout.headerItems, cursor, tr("cbtc.gui.blk_playing"));
            cursor = addText(layout.headerItems, cursor, musicStateLabel(type.id()));
            if ("music_song_lyric".equals(type.id())) {
                cursor = addText(layout.headerItems, cursor, "(");
                cursor = addParam(n, layout.headerItems, cursor, type.paramIndex("lang"));
                cursor = addText(layout.headerItems, cursor, ")");
            }
            return;
        }
        for (NodeType.Port port : type.inputs()) {
            if (port.kind() != PortKind.FLOW) {
                cursor = addSlot(n, layout.headerItems, cursor, port.key(), depth + 1);
            }
        }
        cursor = addText(layout.headerItems, cursor, tr("cbtc.gui.blk_volume"));
        cursor = addParam(n, layout.headerItems, cursor, type.paramIndex("volume"));
        cursor = addSlider(layout.headerItems, cursor, type.paramIndex("volume"));
        cursor = addText(layout.headerItems, cursor, tr("cbtc.gui.blk_range"));
        addParam(n, layout.headerItems, cursor, type.paramIndex("range"));
    }

    /** 随机数积木：从数列 [数列名] 里随机抽取一项。 */
    private void buildRandomHeader(Node n, NodeLayout layout, int cursor, int depth) {
        cursor = addText(layout.headerItems, cursor, tr("cbtc.gui.blk_random_head"));
        cursor = addSlot(n, layout.headerItems, cursor, "list", depth + 1);
        addText(layout.headerItems, cursor, tr("cbtc.gui.blk_random_tail"));
    }

    /** 数列积木：硬盘 / 局域网 / 互联网三套，都是"名字 + 操作"。 */
    private static boolean isSeqNode(String typeId) {
        return typeId.startsWith("seq_");
    }

    private void buildSeqHeader(Node n, NodeLayout layout, int cursor, int depth) {
        NodeType type = n.type();
        if (type == null) {
            return;
        }
        String id = type.id();
        boolean lan = id.startsWith("seq_lan_");
        String kind = id.substring(id.lastIndexOf('_') + 1);
        // 本机数列存运行内存，硬盘数列存硬盘；局域网额外标出"在哪个网"
        String prefix = lan ? Component.translatable("cbtc.gui.lan_at").getString()
                : Component.translatable(id.startsWith("seq_mem_") ? "cbtc.gui.blk_seq_mem"
                : "cbtc.gui.blk_seq_drive").getString();
        if (lan) {
            cursor = addText(layout.headerItems, cursor, prefix);
            cursor = addParam(n, layout.headerItems, cursor, type.paramIndex("network"));
            cursor = addText(layout.headerItems, cursor,
                    Component.translatable("cbtc.gui.lan_named").getString());
        } else {
            cursor = addText(layout.headerItems, cursor, prefix);
        }
        cursor = addParam(n, layout.headerItems, cursor, type.paramIndex("name"));
        switch (kind) {
            case "add" -> {
                cursor = addText(layout.headerItems, cursor, tr("cbtc.gui.blk_seq_add"));
                cursor = addSlot(n, layout.headerItems, cursor, "item", depth + 1);
            }
            case "remove" -> {
                cursor = addText(layout.headerItems, cursor, tr("cbtc.gui.blk_seq_remove"));
                cursor = addSlot(n, layout.headerItems, cursor, "value", depth + 1);
            }
            case "clear" -> addText(layout.headerItems, cursor, tr("cbtc.gui.blk_seq_clear"));
            case "count" -> addText(layout.headerItems, cursor, tr("cbtc.gui.blk_seq_count"));
            case "sum" -> addText(layout.headerItems, cursor, tr("cbtc.gui.blk_seq_sum"));
            case "average" -> addText(layout.headerItems, cursor, tr("cbtc.gui.blk_seq_average"));
            default -> addText(layout.headerItems, cursor, tr("cbtc.gui.blk_seq_text"));
        }
    }

    /** 数学类单目积木：绝对值 / 相反数 / 科学计数法。 */
    private void buildMathUnaryHeader(Node n, NodeLayout layout, int cursor, int depth) {
        NodeType type = n.type();
        if (type == null) {
            return;
        }
        switch (type.id()) {
            case "math_abs" -> {
                cursor = addText(layout.headerItems, cursor, tr("cbtc.gui.blk_abs"));
                cursor = addSlot(n, layout.headerItems, cursor, "value", depth + 1);
            }
            case "math_negate" -> {
                cursor = addSlot(n, layout.headerItems, cursor, "value", depth + 1);
                cursor = addText(layout.headerItems, cursor, tr("cbtc.gui.blk_negate"));
            }
            default -> {
                cursor = addText(layout.headerItems, cursor, tr("cbtc.gui.blk_scientific"));
                cursor = addSlot(n, layout.headerItems, cursor, "value", depth + 1);
            }
        }
    }

    /** 读取类音乐积木的说明文字。 */
    private static String musicStateLabel(String typeId) {
        return switch (typeId) {
            case "music_song_name" -> tr("cbtc.gui.blk_song_name");
            case "music_song_artist" -> tr("cbtc.gui.blk_song_artist");
            case "music_song_total" -> tr("cbtc.gui.blk_song_total");
            case "music_song_time" -> tr("cbtc.gui.blk_song_time");
            default -> tr("cbtc.gui.blk_song_lyric");
        };
    }

    /** 0~1 的横向拉条项，绑定到某个参数。 */
    private int addSlider(List<BlockItem> items, int cursor, int index) {
        BlockItem item = new BlockItem("slider", "", index);
        item.x = cursor;
        item.y = 4;
        item.width = SLIDER_W;
        item.height = SLIDER_H;
        items.add(item);
        return cursor + item.width + ELEM_GAP;
    }

    /** 头部收尾：按内层链条宽度扩展、计算高度并整体居中。 */
    private void finishHeader(Node n, NodeLayout layout, int depth) {
        NodeType type = n.type();
        if (type == null) {
            return;
        }
        int cursor = layout.headerItems.isEmpty() ? PAD
                : layout.headerItems.get(layout.headerItems.size() - 1).x
                + layout.headerItems.get(layout.headerItems.size() - 1).width + ELEM_GAP;
        layout.width = Math.max(cursor + PAD - ELEM_GAP, isReporter(type) ? 40 : 88);
        for (String port : cavityPortsOf(n)) {
            Node body = bodyNode(n, port);
            if (body != null) {
                layout.width = Math.max(layout.width, C_INDENT + chainWidth(body, depth + 1) + C_RIGHT_PAD);
            }
        }
        layout.headerHeight = Math.max(isReporter(type) ? REPORTER_H : STACK_H,
                layout.headerItems.stream().mapToInt(item -> item.y + item.height + 4).max().orElse(0));
        centerItems(layout.headerItems, layout.headerHeight);
    }

    private void buildBranches(Node n, NodeLayout layout, int depth) {
        if (!"if_else".equals(n.typeId())) {
            return;
        }
        List<String> cavities = cavityPortsOf(n);
        for (int i = 1; i < cavities.size(); i++) {
            String port = cavities.get(i);
            int number = "else".equals(port) ? 0 : parsePositiveInt(port.substring(4));
            BranchLayout branch = new BranchLayout(port,
                    tr(number == 0 ? "cbtc.gui.blk_else" : "cbtc.gui.blk_else_if"), number);
            int cursor = addText(branch.items, PAD, branch.label);
            if (number > 0) {
                cursor = addSlot(n, branch.items, cursor, "cond" + number, depth + 1);
            }
            // 为按钮保留独立的固定区域，条件再长也不会盖住按钮。
            layout.width = Math.max(layout.width, cursor - ELEM_GAP + C_RIGHT_PAD
                    + BRANCH_BUTTON + C_RIGHT_PAD);
            int contentBottom = branch.items.stream().mapToInt(item -> item.y + item.height + 4).max().orElse(0);
            branch.height = Math.max(C_ELSE_BAR, contentBottom);
            centerItems(branch.items, branch.height);
            layout.branches.add(branch);
        }
    }

    private int addText(List<BlockItem> items, int cursor, String text) {
        BlockItem item = new BlockItem("text", "", -1);
        item.text = text;
        item.x = cursor;
        item.y = (STACK_H - 9) / 2;
        item.width = font.width(text);
        item.height = 9;
        items.add(item);
        return cursor + item.width + ELEM_GAP;
    }

    private int addSlot(Node n, List<BlockItem> items, int cursor, String port, int depth) {
        Node child = slotChild(n, port);
        BlockItem item = new BlockItem("slot", port, -1);
        item.child = child;
        item.x = cursor;
        item.y = 4;
        item.width = child != null ? layoutOf(child, depth).width
                : Math.max(EMPTY_SLOT_W, sharedParamWidth(n, port));
        item.height = child != null ? layoutOf(child, depth).height : SLOT_H;
        items.add(item);
        return cursor + item.width + ELEM_GAP;
    }

    private static void centerItems(List<BlockItem> items, int height) {
        for (BlockItem item : items) {
            item.y = Math.max(0, (height - item.height) / 2);
        }
    }

    private int addParam(Node n, List<BlockItem> items, int cursor, int index) {
        BlockItem item = new BlockItem("param", n.type().params().get(index), index);
        item.x = cursor;
        item.y = 4;
        item.width = paramWidth(n, index);
        item.height = PARAM_H;
        items.add(item);
        return cursor + item.width + ELEM_GAP;
    }

    private static int parsePositiveInt(String value) {
        try {
            return Math.max(0, Integer.parseInt(value));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private NodeLayout layoutOf(Node n) {
        return layoutOf(n, 0);
    }

    private int widthOf(Node n, int depth) {
        NodeLayout layout = layoutOf(n, depth);
        return Math.max(layout.width, n.type() != null && isReporter(n.type()) ? 40 : 88);
    }

    private int heightOf(Node n, int depth) {
        return layoutOf(n, depth).height;
    }

    private int chainWidth(Node n, int depth) {
        int max = 0;
        Node cur = n;
        Set<Integer> seen = new HashSet<>();
        while (cur != null && seen.add(cur.id())) {
            max = Math.max(max, widthOf(cur, depth));
            cur = stackNext(cur);
        }
        return max;
    }

    private int chainHeight(Node n, int depth) {
        int total = 0;
        Node cur = n;
        Set<Integer> seen = new HashSet<>();
        while (cur != null && seen.add(cur.id())) {
            total += heightOf(cur, depth);
            cur = stackNext(cur);
        }
        return total;
    }

    private int bodyHeight(Node n, String port, int depth) {
        Wire wire = program.wireFrom(n.id(), port).orElse(null);
        Node body = wire == null ? null : program.node(wire.toNode()).orElse(null);
        return body == null ? C_EMPTY_H : Math.max(C_EMPTY_H, chainHeight(body, depth));
    }

    /** 每帧只生成一次布局，再按同一棵树放置节点。 */
    private void prepareLayouts() {
        layouts.clear();
        for (Node n : program.nodes()) {
            if (n.type() != null) {
                layoutOf(n);
            }
        }
    }

    private void placeNode(Node n, int x, int y, Set<Integer> seen) {
        NodeLayout layout = layoutOf(n);
        layout.x = x;
        layout.y = y;
        worldRects.put(n.id(), new int[]{x, y, layout.width, layout.height});
        rects.put(n.id(), new int[]{px(x), py(y), px(x + layout.width) - px(x),
                py(y + layout.height) - py(y)});
        placeItems(n, layout.headerItems, x, y + (n.type() != null && isHat(n.type()) ? HAT_CURVE : 0), seen);
        for (BranchLayout branch : layout.branches) {
            placeItems(n, branch.items, x, y + branch.y, seen);
            int bodyX = x + C_INDENT;
            int bodyY = y + branch.y + branch.height;
            cavityHits.add(new CavityHit(n.id(), branch.port, px(bodyX), py(bodyY),
                    Math.max(1, px(x + layout.width - C_RIGHT_PAD) - px(bodyX)),
                    Math.max(1, py(bodyY + branch.bodyHeight) - py(bodyY))));
            Node body = bodyNode(n, branch.port);
            if (body != null) {
                placeChain(body, bodyX, bodyY, seen);
            }
        }
    }

    private Node bodyNode(Node n, String port) {
        Wire wire = program.wireFrom(n.id(), port).orElse(null);
        return wire == null ? null : program.node(wire.toNode()).orElse(null);
    }

    private void placeItems(Node n, List<BlockItem> items, int baseX, int baseY, Set<Integer> seen) {
        for (BlockItem item : items) {
            int x = baseX + item.x;
            int y = baseY + item.y;
            if (item.child != null) {
                if (seen.add(item.child.id())) {
                    placeNode(item.child, x, y, seen);
                }
            } else if (item.kind.equals("slot")) {
                slotHits.add(new SlotHit(n.id(), item.key, slotKindOf(n, item.key), px(x), py(y),
                        px(x + item.width) - px(x), py(y + item.height) - py(y), x, y));
            } else if (item.kind.equals("param")) {
                paramHits.add(new ParamHit(n.id(), item.index, px(x), py(y),
                        px(x + item.width) - px(x), py(y + item.height) - py(y), x, y));
            }
        }
    }

    private void layout() {
        prepareLayouts();
        rects.clear();
        worldRects.clear();
        slotHits.clear();
        paramHits.clear();
        cavityHits.clear();
        branchButtons.clear();
        extraButtons.clear();
        for (Node n : program.nodes()) {
            if (n.type() == null || !isRoot(n)) {
                continue;
            }
            placeChain(n, Math.round(n.x()), Math.round(n.y()), new HashSet<>());
        }
    }

    private int labelWidth(NodeType t) {
        return labelWCache.computeIfAbsent(t.id(), k -> font.width(labelHead(t)));
    }

    /**
     * 带内联数值槽位的积木，标签按"槽位前 / 槽位后"拆成两段。
     * 例如「重复执行 次」在数字框前显示"重复执行"，框后显示"次"。
     */
    private static String[] splitLabel(NodeType t) {
        return switch (t.id()) {
            case "if", "if_else", "repeat_n", "when", "redstone_received", "redstone_received_compare",
                    "broadcast_received", "typewriter_key", "typewriter_key_held", "logic", "wait_seconds",
                    "broadcast_send", "broadcast_receive", "lan_received", "lan_receive", "lan_send",
                    "lan_var_set", "lan_var_get", "net_receive", "net_received", "net_send", "net_var_set",
                    "net_var_get", "chat_recognize", "chat_send", "chat_whisper", "bc_var_set", "bc_var_get",
                    "var_set", "var_get", "drive_set", "drive_get", "angle_value", "gearbox_angle",
                    "angle_math", "angle_compare" -> new String[]{
                            tr("cbtc.node.parts." + t.id() + ".head"),
                            tr("cbtc.node.parts." + t.id() + ".tail")};
            default -> null;
        };
    }

    private String labelHead(NodeType t) {
        String[] parts = splitLabel(t);
        return parts == null ? Component.translatable(t.translationKey()).getString() : parts[0];
    }

    private static String labelTail(NodeType t) {
        String[] parts = splitLabel(t);
        return parts == null ? "" : parts[1];
    }

    /** 取本地化文本；缺键时回落到键名，便于发现漏翻的条目。 */
    private static String tr(String key) {
        return Component.translatable(key).getString();
    }

    /** 参数和同名数据口共存时（如"重复…次"的次数），由数据口统一画成"可填可嵌"的槽位。 */
    private static boolean paramSharesPort(Node n, int index) {
        NodeType t = n.type();
        if (t == null || index < 0 || index >= t.params().size()) {
            return false;
        }
        String key = t.params().get(index);
        if ("branches".equals(key)) {
            return false;
        }
        for (NodeType.Port p : t.inputs()) {
            if (p.key().equals(key) && p.kind() != PortKind.FLOW) {
                return true;
            }
        }
        return false;
    }

    /**
     * 运算符参数的下标（不存在时返回 -1）。
     * <p>
     * 只要参数表里有 op 就按运算符渲染，不再逐个积木枚举：
     * 计算、角度运算、时间运算等只是进制与可选项不同，显示方式应当一致。
     */
    private static int operatorIndex(Node n) {
        NodeType t = n.type();
        if (t == null) {
            return -1;
        }
        return t.paramIndex("op");
    }

    /** 参数在界面上的显示文本：运算符显示为符号（> < = ≠ ≥ ≤ + − × ÷）。 */
    private String displayParam(Node n, int index) {
        NodeType t = n.type();
        if (t == null) {
            return "";
        }
        String stored = n.param(index);
        if (index == operatorIndex(n)) {
            return opGlyph(stored);
        }
        // 打字机积木的按键参数存 GLFW 键码，界面上显示中文键名
        if (isTypewriterNode(n.typeId()) && "key".equals(t.params().get(index))) {
            try {
                return KeyNames.display(Integer.parseInt(stored));
            } catch (NumberFormatException e) {
                return stored;
            }
        }
        // 设备参数存的是绑定记录里的 id，显示成用户起的名字
        if ("device".equals(t.params().get(index))) {
            if (isSoundDeviceNode(n.typeId())) {
                return stored.isBlank() || com.mcl.cbtc.core.NodeTypes.SOUND_DEVICE_SELF.equals(stored)
                        ? tr("cbtc.gui.sound_device_self") : deviceNameOf(stored);
            }
            if (!"wireless_display".equals(n.typeId())) {
                return deviceNameOf(stored);
            }
        }
        // 连线积木的齿轮箱是可选换算，没选时明确显示成偏差角
        if ("device_gearbox".equals(t.params().get(index)) && stored.isBlank()) {
            return tr("cbtc.gui.blk_no_gearbox");
        }
        // 基线方位积木的前后两个设备参数，同样显示成用户起的名字
        if ("device_a".equals(t.params().get(index)) || "device_b".equals(t.params().get(index))
                || "device_gearbox".equals(t.params().get(index))) {
            return deviceNameOf(stored);
        }
        // 局域网参数只认单个网，没选时提示去选
        if ("network".equals(t.params().get(index))) {
            return stored.isBlank() ? Component.translatable("cbtc.gui.lan_select").getString() : stored;
        }
        // 聊天发信目标留空按全体玩家处理，显示成中文便于理解
        if ("target".equals(t.params().get(index))) {
            return chatTargetLabel(stored.isBlank() ? "@a" : stored);
        }
        return stored;
    }

    /** 存储值 → 显示符号。 */
    private static String opGlyph(String stored) {
        return switch (stored) {
            case ">" -> ">";
            case "<" -> "<";
            case "==" -> "=";
            case "!=" -> "\u2260";
            case ">=" -> "\u2265";
            case "<=" -> "\u2264";
            case "*" -> "\u00d7";
            case "/" -> "\u00f7";
            case "-" -> "\u2212";
            case "steady" -> tr("cbtc.gui.op_steady");
            case "blink" -> tr("cbtc.gui.op_blink");
            case "and" -> tr("cbtc.gui.op_and");
            case "or" -> tr("cbtc.gui.op_or");
            case "xor" -> tr("cbtc.gui.op_xor");
            default -> stored;
        };
    }

    private int paramWidth(Node n, int index) {
        NodeType t = n.type();
        int pad = t != null && (index == operatorIndex(n) || isItemParam(n, index)
                || isWirelessRedstoneType(t) && ("color".equals(t.params().get(index))
                || "mode".equals(t.params().get(index)))) ? 18 : 10;
        return Math.max(26, font.width(displayParam(n, index)) + pad);
    }

    /** 与参数同名的槽位、且槽位里没有嵌入积木时，返回按参数文本算出的宽度；否则返回 0。 */
    private int sharedParamWidth(Node n, String portKey) {
        NodeType t = n.type();
        if (t == null) {
            return 0;
        }
        int index = t.paramIndex(portKey);
        if (index < 0) {
            return 0;
        }
        return Math.max(26, font.width(n.param(index)) + 12);
    }

    /** 该槽位是否同时承担"可编辑参数"的角色。 */
    private static boolean slotHasEditableValue(Node n, String portKey) {
        NodeType t = n.type();
        if (t == null) {
            return false;
        }
        // 时间类与角度类槽位只能嵌入对应积木，不画成可输入的框
        if (isTimeSlot(n, portKey) || isAngleSlot(n, portKey)) {
            return false;
        }
        // 端口同时带同名参数时（如计数、强度），可以直接在槽里输入
        return t.paramIndex(portKey) >= 0 || t.params().contains(portKey);
    }

    /**
     * 时间类槽位：等待、监听时长、齿轮箱定时、红石发送的时长参数。
     * 这些一律用暗色凹槽表示，单位与数值都由嵌入的时间积木提供。
     */
    private static boolean isTimeSlot(Node n, String portKey) {
        String typeId = n.typeId();
        if ("wait_seconds".equals(typeId)) {
            return "seconds".equals(portKey);
        }
        if ("chat_listen".equals(typeId)) {
            return "duration".equals(portKey);
        }
        if ("redstone_send".equals(typeId)) {
            return "phase".equals(portKey) || "gap".equals(portKey) || "lit".equals(portKey);
        }
        return false;
    }

    /**
     * 角度类槽位：只能插入角度积木，不能直接输入数字。
     * <p>
     * 「旋转至」的目标角度和角度运算、比较的两个口都属于这一类。
     */
    private static boolean isAngleSlot(Node n, String portKey) {
        NodeType t = n.type();
        if (t == null) {
            return false;
        }
        if ("gearbox_control".equals(t.id())) {
            return "value".equals(portKey) && n.param("mode").startsWith(tr("cbtc.gui.mode_rotate_to"));
        }
        for (NodeType.Port port : t.inputs()) {
            if (port.key().equals(portKey)) {
                return port.kind() == PortKind.ANGLE;
            }
        }
        return false;
    }

    private int placeChain(Node n, int x, int y, Set<Integer> seen) {
        int total = 0;
        Node cur = n;
        while (cur != null && seen.add(cur.id())) {
            placeNode(cur, x, y + total, seen);
            total += heightOf(cur, 0);
            cur = stackNext(cur);
        }
        return total;
    }

    private static boolean ctrlDown() {
        return net.minecraft.client.gui.screens.Screen.hasControlDown();
    }

    // ---------------------------------------------------------------- 渲染

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        layout();
        layoutPalette();
        // 记下鼠标位置：Ctrl+V 粘贴时用它决定落点
        lastMouseX = mouseX;
        lastMouseY = mouseY;

        g.fill(0, 0, width, height, SCREEN_BG);
        // 画布网格：跟着相机缩放平移
        g.enableScissor(canvasX(), canvasY(), width, height - STATUS_H);
        int gridStep = Math.max(8, (int) Math.round(24 * zoom));
        for (double gx = canvasX() + (Math.ceil(panX / 24) * 24 - panX) * zoom; gx < width; gx += gridStep) {
            int ix = (int) Math.round(gx);
            g.fill(ix, canvasY(), ix + 1, height - STATUS_H, GRID);
        }
        for (double gy = canvasY() + (Math.ceil(panY / 24) * 24 - panY) * zoom; gy < height - STATUS_H;
                gy += gridStep) {
            int iy = (int) Math.round(gy);
            g.fill(canvasX(), iy, width, iy + 1, GRID);
        }

        // rects 的插入顺序是"父先子后"的深度优先序：必须按它绘制，
        // 否则后画的父块矩形会把先画好的嵌套子块盖住（绿色积木消失的原因）。
        // 先画主体，最后统一画接缝凸起，避免后画的子块把上一块的凸起覆盖。
        drawBlocks(g, false);
        drawBlocks(g, true);
        drawJoints(g, false);
        drawJoints(g, true);
        g.disableScissor();

        // 侧边栏必须在画布裁剪结束后绘制，否则整栏会被裁掉。
        drawSidebar(g, mouseX, mouseY);

        // 吸附高亮
        Snap snap = currentSnap(mouseX, mouseY);
        if (snap != null && inCanvas(mouseX, mouseY)) {
            g.fill(snap.x, snap.y, snap.x + snap.w, snap.y + snap.h, 0x2EF0C86A);
            g.renderOutline(snap.x - 1, snap.y - 1, snap.w + 2, snap.h + 2, BRASS_LIGHT);
        }
        if (draggingNew != null) {
            drawGhost(g, draggingNew, (int) dragX - 10, (int) dragY - 8);
        }

        drawTopBar(g, mouseX, mouseY);
        drawStatusBar(g, mouseX, mouseY);
        renderOpMenu(g, mouseX, mouseY);

        // 参数输入框跟随胶囊 / 槽位位置
        if (slotEditing != null) {
            SlotHit hit = null;
            for (SlotHit s : slotHits) {
                if (s.nodeId == slotEditing.id() && s.port.equals(slotEditPort)) {
                    hit = s;
                    break;
                }
            }
            ParamAnchor anchor = hit == null ? lastParamHit
                    : new ParamAnchor(hit.x, hit.y, hit.w, hit.h);
            if (anchor == null) {
                // 命中项只在部分帧刷新（例如链被拖起）时会暂时缺失，用上一帧的位置兜底，
                // 否则这里会把 slotEditing 清空，随后回车就提交不到参数上。
                slotEditing = null;
                paramBox.visible = false;
            } else {
                lastParamHit = anchor;
                paramBox.setX(anchor.x);
                paramBox.setY(anchor.y);
                paramBox.setWidth(anchor.w);
                paramBox.setHeight(anchor.h);
                paramBox.visible = true;
            }
        } else if (paramTarget != null) {
            ParamHit hit = null;
            for (ParamHit p : paramHits) {
                if (p.nodeId == paramTarget.id() && p.index == paramIndex) {
                    hit = p;
                    break;
                }
            }
            ParamAnchor anchor = hit == null ? lastParamHit
                    : new ParamAnchor(hit.x, hit.y - 1, hit.w + 4, PARAM_H + 2);
            if (anchor == null) {
                paramTarget = null;
                paramBox.visible = false;
            } else {
                lastParamHit = anchor;
                paramBox.setX(anchor.x);
                paramBox.setY(anchor.y);
                paramBox.setWidth(anchor.w);
                paramBox.setHeight(anchor.h);
                paramBox.visible = true;
            }
        } else {
            paramBox.visible = false;
        }
        // 输入框每帧都会被改位置/尺寸，可聚焦控件在这种变动下会丢焦点，
        // 表现为"能输入、按回车不保存"。这里在刷新之后把焦点重新锚定回输入框。
        if ((paramTarget != null || slotEditing != null) && !paramBox.isFocused() && !isSoundEditing()) {
            paramBox.setFocused(true);
        }
        for (var listener : this.children()) {
            if (listener instanceof net.minecraft.client.gui.components.Renderable r) {
                r.render(g, mouseX, mouseY, partialTick);
            }
        }
        // 音效候选必须画在输入框之后，否则会被输入框盖住
        renderSoundSuggestions(g, mouseX, mouseY);
        renderChatTargetSuggestions(g, mouseX, mouseY);
        renderItemPicker(g, mouseX, mouseY);
        renderDeviceMenu(g, mouseX, mouseY);
        // 表盘画在下拉菜单之上，避免被积木遮挡
        renderAngleDial(g, mouseX, mouseY);
    }

    /**
     * 按布局顺序画积木。每块画完主体立刻画自己的底部凸起（下一块的缺口随后会盖住多余部分）。
     *
     * @param draggedPass false=画未被拖起的积木；true=画正在拖拽的那条链（置顶）
     */
    private void drawBlocks(GuiGraphics g, boolean draggedPass) {
        for (Map.Entry<Integer, int[]> e : rects.entrySet()) {
            boolean isDragged = dragging != null && draggedSubtree.contains(e.getKey());
            if (isDragged != draggedPass) {
                continue;
            }
            Node n = program.node(e.getKey()).orElse(null);
            if (n == null || n.type() == null) {
                continue;
            }
            drawBody(g, n);
        }
    }

    private void drawJoints(GuiGraphics g, boolean draggedPass) {
        for (Map.Entry<Integer, int[]> e : rects.entrySet()) {
            boolean isDragged = dragging != null && draggedSubtree.contains(e.getKey());
            if (isDragged != draggedPass) {
                continue;
            }
            Node n = program.node(e.getKey()).orElse(null);
            if (n == null || n.type() == null) {
                continue;
            }
            NodeType t = n.type();
            if (!isReporter(t) && (isHat(t) || continuationPort(t) != null)) {
                int[] r = worldRect(n);
                joint(g, r[0], r[1] + r[3], colorOf(t.category()));
            }
        }
    }

    /** 顶栏问号按钮的位置与尺寸。 */
    static int helpButtonX(net.minecraft.client.gui.Font font) {
        return 8 + font.width(Component.translatable("block.cbtc.computer").getString()) + 4;
    }

    static final int HELP_BUTTON_Y = 5;
    static final int HELP_BUTTON_SIZE = 14;

    private void drawTopBar(GuiGraphics g, int mouseX, int mouseY) {
        g.fill(0, 0, width, TOP_BAR, BAR_BG);
        g.fill(0, TOP_BAR - 2, width, TOP_BAR, BRASS_DARK);
        g.fill(0, 0, width, 2, BRASS);

        g.drawString(font, Component.translatable("block.cbtc.computer").getString(), 8, 8, TEXT, false);

        int helpX = helpButtonX(font);
        int helpY = HELP_BUTTON_Y;
        boolean helpHover = mouseX >= helpX && mouseX < helpX + HELP_BUTTON_SIZE
                && mouseY >= helpY && mouseY < helpY + HELP_BUTTON_SIZE;
        BlockShapes.squareOutline(g, helpX, helpY, HELP_BUTTON_SIZE, HELP_BUTTON_SIZE,
                helpHover ? BRASS_LIGHT : BRASS);
        g.fill(helpX + 1, helpY + 1, helpX + HELP_BUTTON_SIZE - 1, helpY + HELP_BUTTON_SIZE - 1,
                helpHover ? 0x40F0C86A : 0x20F0C86A);
        g.drawCenteredString(font, "?", helpX + HELP_BUTTON_SIZE / 2, helpY + 3,
                helpHover ? BRASS_LIGHT : TEXT);

        int bw = 76;
        int bx = width - bw - 8;
        int by = 3;
        boolean hover = mouseX >= bx && mouseX < bx + bw && mouseY >= by && mouseY < by + 18;
        g.fill(bx, by, bx + bw, by + 18, hover ? BRASS_LIGHT : BRASS);
        g.fill(bx + 1, by + 1, bx + bw - 1, by + 17, BRASS_DARK);
        String label = Component.translatable("cbtc.gui.save_and_back").getString();
        g.drawString(font, label, bx + (bw - font.width(label)) / 2, by + 5, TEXT, false);
    }

    private void drawStatusBar(GuiGraphics g, int mouseX, int mouseY) {
        int y = height - STATUS_H;
        g.fill(0, y, width, height, BAR_BG);
        g.fill(0, y, width, y + 1, BRASS_DARK);
        if (!synced) {
            String syncing = Component.translatable("cbtc.gui.syncing").getString();
            g.drawString(font, syncing, 8, y + 4, TEXT_DIM, false);
            return;
        }
        // 左下角固定显示"外设链接：<已连接的外设>"，没有外设时只留"外设链接："这一行
        StringBuilder sb = new StringBuilder(Component.translatable("cbtc.gui.devices").getString())
                .append(Component.translatable("cbtc.gui.devices_separator").getString());
        for (int i = 0; i < deviceStatus.size(); i++) {
            if (i > 0) {
                sb.append("  ");
            }
            sb.append(deviceStatus.get(i));
        }
        String line = sb.toString();
        int maxW = width - 16;
        while (font.width(line) > maxW && line.length() > 1) {
            line = line.substring(0, line.length() - 1);
        }
        g.drawString(font, line, 8, y + 4, TEXT_DIM, false);
    }

    // ---------------------------------------------------------------- 侧边栏

    private static boolean isLogisticsType(NodeType type) {
        return "warehouse_free_space".equals(type.id()) || "warehouse_capacity".equals(type.id())
                || "warehouse_item_count".equals(type.id()) || "packager_send".equals(type.id());
    }

    private static boolean isWirelessRedstoneType(NodeType type) {
        return "redstone_receive".equals(type.id()) || "redstone_receive_compare".equals(type.id())
                || "redstone_send".equals(type.id())
                || "redstone_received".equals(type.id()) || "redstone_received_compare".equals(type.id());
    }

    /** 需要外设绑定模块卡才出现的积木。 */
    private static boolean isPeripheralType(NodeType type) {
        return isTypewriterNode(type.id())
                || "gearbox_control".equals(type.id()) || "gearbox_stop".equals(type.id())
                || "gearbox_angle".equals(type.id()) || isNavTableNode(type.id())
                || isTargetLocatorNode(type.id());
    }

    /** 目标定位模块相关积木：只能选已绑定的定位模块。 */
    private static boolean isTargetLocatorNode(String typeId) {
        return "target_locator_pos".equals(typeId) || "target_locator_set".equals(typeId);
    }

    /** 导航台相关积木：只能选已绑定的导航台。 */
    private static boolean isNavTableNode(String typeId) {
        return "nav_table_pos".equals(typeId) || "nav_table_target".equals(typeId)
                || "nav_table_distance".equals(typeId)
                || "nav_bearing_direction".equals(typeId) || "nav_bearing_range".equals(typeId)
                || "nav_bearing_angle".equals(typeId)
                || "nav_line_direction".equals(typeId) || "nav_line_range".equals(typeId)
                || "nav_line_angle".equals(typeId);
    }

    /** 需要装硬盘才出现的积木。 */
    private static boolean isHardDriveType(NodeType type) {
        return "drive_set".equals(type.id()) || "drive_get".equals(type.id())
                || type.id().startsWith("seq_drive_");
    }

    /** 与打字机按键相关的积木：两个事件积木与两个条件积木都只能绑打字机。 */
    private static boolean isTypewriterNode(String typeId) {
        return "typewriter_key".equals(typeId) || "typewriter_long_press".equals(typeId)
                || "typewriter_key_down".equals(typeId) || "typewriter_key_held".equals(typeId);
    }

    /** 需要局域网模块卡才出现的积木。 */
    private static boolean isLanType(NodeType type) {
        return "lan_receive".equals(type.id()) || "lan_received".equals(type.id())
                || "lan_send".equals(type.id()) || "lan_var_set".equals(type.id())
                || "lan_var_get".equals(type.id()) || type.id().startsWith("seq_lan_");
    }

    /** 需要互联网模块卡才出现的积木。 */
    private static boolean isInternetType(NodeType type) {
        return "net_receive".equals(type.id()) || "net_received".equals(type.id())
                || "net_send".equals(type.id()) || "net_var_set".equals(type.id())
                || "net_var_get".equals(type.id()) || type.id().startsWith("seq_net_");
    }

    /** 需要互联网模块卡才出现的积木：互联网积木 + 聊天框收发。 */
    private static boolean needsInternet(NodeType type) {
        return isInternetType(type) || isChatType(type);
    }

    /** 聊天积木：发信目标那一格单独画，标明可以填 @a / @e / @玩家 / @PC:电脑。 */
    private static boolean isChatType(NodeType type) {
        return "chat_send".equals(type.id()) || "chat_whisper".equals(type.id())
                || "chat_recognize".equals(type.id()) || "chat_any".equals(type.id())
                || "chat_listen".equals(type.id()) || "chat_listening".equals(type.id());
    }

    private void layoutPalette() {
        palette.clear();
        int y = TOP_BAR + 8 - sidebarScroll;
        for (NodeCategory cat : NodeCategory.values()) {
            List<NodeType> types = new ArrayList<>();
            for (NodeType t : NodeTypes.all()) {
                if (t.category() == cat && (!isLogisticsType(t) || storageModuleInstalled)
                        && (!isWirelessRedstoneType(t) || wirelessRedstoneModuleInstalled)
                        && (!isPeripheralType(t) || !peripheralDevices.isEmpty())
                        && (!isLanType(t) || lanInstalled)
                        && (!needsInternet(t) || internetInstalled)
                        && (!isHardDriveType(t) || hardDriveInstalled)
                        && (!"info_receive".equals(t.id()) || !infoTransmitterCandidates().isEmpty())
                        && (!"wireless_display".equals(t.id()) || !displayDevices.isEmpty())
                        && (!isMusicNode(t.id()) || !playlistIds.isEmpty())) {
                    types.add(t);
                }
            }
            if (types.isEmpty()) {
                continue;
            }
            palette.add(new PaletteEntry(cat, null, y));
            y += 15;
            for (NodeType t : types) {
                palette.add(new PaletteEntry(null, t, y));
                y += 21;
            }
            y += 3;
        }
        paletteContentH = y - (TOP_BAR + 8 - sidebarScroll) + 10;
    }

    private void drawSidebar(GuiGraphics g, int mouseX, int mouseY) {
        int viewTop = TOP_BAR;
        int viewBottom = height - STATUS_H;
        g.fill(0, viewTop, SIDEBAR_W, viewBottom, SIDEBAR_BG);
        g.fill(SIDEBAR_W - 2, viewTop, SIDEBAR_W, viewBottom, BRASS_DARK);

        // 积木列表裁剪到状态栏之上
        int listBottom = height - STATUS_H;
        g.enableScissor(0, viewTop, SIDEBAR_W, listBottom);
        for (PaletteEntry e : palette) {
            if (e.header != null) {
                if (e.y + 12 > viewTop && e.y < listBottom) {
                    g.drawString(font, Component.translatable(e.header.translationKey()), 8, e.y + 3, TEXT_DIM, false);
                }
                continue;
            }
            if (e.y + 17 < viewTop || e.y > viewBottom) {
                continue;
            }
            NodeType t = e.type;
            int color = colorOf(t.category());
            boolean hover = mouseX >= 6 && mouseX < SIDEBAR_W - 10 && mouseY >= e.y && mouseY < e.y + 17
                    && mouseY < listBottom && mouseX < SIDEBAR_W;
            int cx = 6;
            int cw = SIDEBAR_W - 16;
            if (isReporter(t)) {
                int fill = hover ? shade(color, 1.15F) : color;
                g.fill(cx, e.y, cx + cw, e.y + 17, BLOCK_OUTLINE);
                g.fill(cx + 1, e.y + 1, cx + cw - 1, e.y + 16, fill);
            } else if (isHat(t)) {
                drawMiniHat(g, cx, e.y, cw, 17, hover ? shade(color, 1.15F) : color);
            } else {
                g.fill(cx, e.y, cx + cw, e.y + 17, BLOCK_OUTLINE);
                g.fill(cx + 1, e.y + 1, cx + cw - 1, e.y + 16, hover ? shade(color, 1.15F) : color);
                if (cavityPort(t) == null) {
                    g.fill(cx + NOTCH_X + 2, e.y + 17, cx + NOTCH_X + NOTCH_W - 2, e.y + 18, color);
                    g.fill(cx + NOTCH_X + 4, e.y + 18, cx + NOTCH_X + NOTCH_W - 4, e.y + 20, color);
                }
            }
            String label = Component.translatable(t.translationKey()).getString();
            while (font.width(label) > cw - 12 && label.length() > 1) {
                label = label.substring(0, label.length() - 1);
            }
            int tc = textColorFor(color);
            g.drawString(font, label, cx + 6, e.y + 5, tc, tc == 0xFFFDF6E8);
        }
        g.disableScissor();
    }

    private void drawMiniHat(GuiGraphics g, int x, int y, int w, int h, int color) {
        g.fill(x, y, x + w, y + h, BLOCK_OUTLINE);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, color);
    }

    // ---------------------------------------------------------------- 积木绘制

    /**
     * 把世界坐标的矩形换算成屏幕坐标，供积木绘制用。
     * <p>
     * 一律与 {@link #px}/{@link #py} 同源（向下取整），否则同一块积木的底和
     * 里面的槽位/按钮在缩放后会各算一套边，导致错位。
     */
    private int[] sb(int x, int y, int w, int h) {
        int sx = px(x);
        int sy = py(y);
        return new int[]{sx, sy, Math.max(1, px(x + w) - sx), Math.max(1, py(y + h) - sy)};
    }

    private void drawBody(GuiGraphics g, Node n) {
        NodeLayout layout = layouts.get(n.id());
        NodeType type = n.type();
        if (layout == null || type == null) {
            return;
        }
        int wx = layout.x;
        int wy = layout.y;
        int color = colorOf(type.category());
        int baseY = wy + (isHat(type) ? HAT_CURVE : 0);
        if (isReporter(type)) {
            int[] r = sb(wx, wy, layout.width, layout.height);
            g.fill(r[0], r[1], r[0] + r[2], r[1] + r[3], BLOCK_OUTLINE);
            g.fill(r[0] + 1, r[1] + 1, r[0] + r[2] - 1, r[1] + r[3] - 1, color);
        } else if (isHat(type) && layout.branches.isEmpty()) {
            // 纯帽子块（事件触发）：只有头部和底部接口
            drawHatShape(g, wx, wy, layout.width, layout.height, color);
        } else if (layout.branches.isEmpty()) {
            drawStatementShape(g, wx, wy, layout.width, layout.height, hasFlowInput(type), color);
        } else {
            // 带空腔的块（含"当…时"这种帽子+空腔）走 C 形绘制，
            // 帽子块顶部画成圆角，空腔处才留出真正的凹槽。
            drawCShape(g, n, color);
        }

        int[] hl = sb(wx + 2, baseY + 3, 1, Math.max(2, layout.headerHeight - 6));
        g.fill(hl[0], hl[1], hl[0] + Math.max(1, hl[2]), hl[1] + hl[3], shade(color, 1.25F));
        drawItems(g, n, layout.headerItems, wx, baseY, color);
        if (isExtendable(type)) {
            drawExtraButtons(g, n, layout, baseY);
        }
        for (BranchLayout branch : layout.branches) {
            if (branch.height > 0) {
                drawItems(g, n, branch.items, wx, wy + branch.y, color);
            }
        }
    }

    private void drawStatementShape(GuiGraphics g, int wx, int wy, int width, int height,
                                    boolean notch, int color) {
        int left = px(wx);
        int right = px(wx + width);
        BlockShapes.stackBlock(g, left, py(wy), right - left, py(wy + height) - py(wy), notch,
                px(wx + NOTCH_X) - left, jointWidth(wx), scale(JOINT_H), 0, BLOCK_OUTLINE);
        BlockShapes.stackBlock(g, left + 1, py(wy) + 1, right - left - 2,
                py(wy + height) - py(wy) - 2, notch,
                px(wx + NOTCH_X) - left - 1, jointWidth(wx), scale(JOINT_H), 0, color);
    }

    private int jointWidth(int worldX) {
        return Math.max(5, px(worldX + NOTCH_X + NOTCH_W) - px(worldX + NOTCH_X));
    }

    private void drawItems(GuiGraphics g, Node n, List<BlockItem> items, int baseX, int baseY, int color) {
        for (BlockItem item : items) {
            int x = baseX + item.x;
            int y = baseY + item.y;
            switch (item.kind) {
                case "text" -> drawScaledText(g, item.text, x, y, textColorFor(color));
                case "slot" -> {
                    if (item.child == null) {
                        drawSlot(g, n, item, x, y, color);
                    }
                }
                case "param" -> drawParam(g, n, item, x, y, color);
                case "slider" -> drawSlider(g, n, item, x, y);
                default -> {
                }
            }
        }
    }

    private void drawScaledText(GuiGraphics g, String text, int worldX, int worldY, int color) {
        int[] point = sb(worldX, worldY, 0, 0);
        g.pose().pushPose();
        g.pose().translate(point[0], point[1], 0);
        g.pose().scale((float) zoom, (float) zoom, 1F);
        g.drawString(font, text, 0, 0, color, color == 0xFFFDF6E8);
        g.pose().popPose();
    }

    private void drawSlot(GuiGraphics g, Node n, BlockItem item, int worldX, int worldY, int color) {
        int left = px(worldX);
        int top = py(worldY);
        int width = px(worldX + item.width) - left;
        int height = py(worldY + item.height) - top;
        if (!slotHasEditableValue(n, item.key)) {
            g.fill(left, top, left + width, top + height, BLOCK_OUTLINE);
            g.fill(left + 1, top + 1, left + width - 1, top + height - 1, shade(color, 0.6F));
            return;
        }
        BlockShapes.pill(g, left, top, width, height, INPUT_FILL, BLOCK_OUTLINE);
        if (slotEditing == n && item.key.equals(slotEditPort)) {
            return;
        }
        String value = n.param(item.key);
        while (font.width(value) > item.width - 10 && value.length() > 1) {
            value = value.substring(0, value.length() - 1);
        }
        drawScaledText(g, value, worldX + 5, worldY + (item.height - 9) / 2, INPUT_TEXT);
    }

    private void drawParam(GuiGraphics g, Node n, BlockItem item, int worldX, int worldY, int color) {
        int left = px(worldX);
        int top = py(worldY);
        int width = px(worldX + item.width) - left;
        int height = py(worldY + item.height) - top;
        BlockShapes.pill(g, left, top, width, height, INPUT_FILL, BLOCK_OUTLINE);
        boolean operator = item.index == operatorIndex(n)
                || "wireless_display".equals(n.typeId())
                || ("info_receive".equals(n.typeId()) && "device".equals(item.key));
        if (isItemParam(n, item.index)) {
            g.renderFakeItem(itemStack(n.param(item.index)), left + 2, top + (height - 16) / 2);
            String itemName = itemStack(n.param(item.index)).getHoverName().getString();
            g.enableScissor(left + 20, top, left + width - 3, top + height);
            drawScaledText(g, itemName, worldX + 21, worldY + (item.height - 9) / 2, INPUT_TEXT);
            g.disableScissor();
            BlockShapes.caretDown(g, px(worldX + item.width - 11),
                    py(worldY + item.height / 2) - 1, 0xFF8A6A2F);
            return;
        }
        if (operator) {
            BlockShapes.caretDown(g, px(worldX + item.width - 11),
                    py(worldY + item.height / 2) - 1, 0xFF8A6A2F);
        }
        if (paramTarget == n && paramIndex == item.index) {
            return;
        }
        String value = displayParam(n, item.index);
        // 等待按键时胶囊直接显示提示语，玩家知道现在按什么键就会被绑上去
        if (keyCaptureNode == n && "key".equals(item.key)) {
            value = Component.translatable("cbtc.gui.key_capture").getString();
        }
        if ("wireless_display".equals(n.typeId()) && "device".equals(item.key)) {
            for (var device : displayDevices) {
                if (device.id().equals(n.param(item.index))) {
                    value = device.name();
                    break;
                }
            }
            if (value.isBlank()) {
                value = Component.translatable("cbtc.gui.display_select").getString();
            }
        }
        if ((isTypewriterNode(n.typeId())
                || "gearbox_control".equals(n.typeId()) || "gearbox_stop".equals(n.typeId())
                || "gearbox_angle".equals(n.typeId()))
                && ("device".equals(item.key))) {
            var device = selectedPeripheral(n);
            value = device == null ? Component.translatable("cbtc.gui.device_select").getString()
                    : device.name();
        }
        while (font.width(value) > item.width - (operator ? 18 : 10) && value.length() > 1) {
            value = value.substring(0, value.length() - 1);
        }
        drawScaledText(g, value, worldX + 5, worldY + (item.height - 9) / 2, INPUT_TEXT);
    }

    /** 积木的世界坐标外框（不是屏幕坐标）。 */
    private int[] worldRect(Node n) {
        return worldRects.get(n.id());
    }

    /** 读参数当前值并限制到 0~1，用于拉条位置。 */
    private static float sliderValue(Node n, int index) {
        try {
            double v = Double.parseDouble(n.param(index).trim());
            return (float) Math.max(0, Math.min(1, v));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /**
     * 鼠标落在音量拉条上时开始拖动。
     * 命中区域按世界坐标换算，缩放和平移下都能点中。
     */
    private boolean beginSliderDrag(Node n, double mx, double my) {
        NodeLayout layout = layoutOf(n);
        for (BlockItem item : layout.headerItems) {
            if (!"slider".equals(item.kind)) {
                continue;
            }
            int left = px(layout.x + item.x);
            int top = py(layout.y + item.y);
            int right = px(layout.x + item.x + item.width);
            int bottom = py(layout.y + item.y + item.height);
            // 上下各放宽几个像素，细拉条也容易点中
            if (mx >= left - 2 && mx <= right + 2 && my >= top - 4 && my <= bottom + 4) {
                sliderNode = n;
                sliderIndex = item.index;
                sliderLeft = left;
                sliderWidth = Math.max(1, right - left);
                updateSliderDrag(mx);
                return true;
            }
        }
        return false;
    }

    /** 追加项按钮在世界坐标里的边长与间距：布局用它预留宽度，绘制时再按 zoom 放大。 */
    private static final int EXTRA_BUTTON_W = 11;
    private static final int EXTRA_BUTTON_GAP = 4;

    /** 拼接文本的追加按钮：没追加时只画加号，追加过之后加号与减号并排。 */
    private void drawExtraButtons(GuiGraphics g, Node n, NodeLayout layout, int baseY) {
        int extra = extraCount(n);
        int size = Math.max(7, scale(EXTRA_BUTTON_W));
        if (size % 2 == 0) {
            size++;
        }
        int pad = Math.max(2, scale(EXTRA_BUTTON_GAP));
        // barRight 就是最右按钮的右边缘，与布局时的预留宽度对齐
        int barRight = px(layout.x + layout.width - PAD);
        int by = py(baseY + layout.headerHeight / 2) - size / 2;
        int plusX = barRight - size;
        drawButtonCross(g, plusX, by, size, true);
        extraButtons.add(new ExtraHit(n.id(), 1, plusX, by, size, size));
        if (extra > 0) {
            int minusX = plusX - size - pad;
            drawButtonCross(g, minusX, by, size, false);
            extraButtons.add(new ExtraHit(n.id(), -1, minusX, by, size, size));
        }
    }

    /** 在屏幕坐标画一个方形按钮，withVertical 表示是加号（有竖线）还是减号。 */
    private void drawButtonCross(GuiGraphics g, int bx, int by, int size, boolean withVertical) {
        g.fill(bx, by, bx + size, by + size, BLOCK_OUTLINE);
        g.fill(bx + 1, by + 1, bx + size - 1, by + size - 1, INPUT_FILL);
        int cx = bx + size / 2;
        int cy = by + size / 2;
        int arm = Math.max(2, size / 2 - 2);
        int thick = Math.max(1, (int) Math.round(zoom * 1.5));
        if (thick % 2 == 0) {
            thick++;
        }
        int half = thick / 2;
        g.fill(cx - arm, cy - half, cx + arm + 1, cy + half + 1, BLOCK_OUTLINE);
        if (withVertical) {
            g.fill(cx - half, cy - arm, cx + half + 1, cy + arm + 1, BLOCK_OUTLINE);
        }
    }

    /** 支持追加文本口的积木：目前只有拼接文本。 */
    private static boolean isExtendable(NodeType t) {
        return "text_join".equals(t.id());
    }

    /** 追加按钮的预留宽度（世界坐标）：加号一颗，有追加项时再加一颗减号，末尾留一个内边距。 */
    private int extraButtonsWidth(Node n) {
        int count = extraCount(n) > 0 ? 2 : 1;
        return count * EXTRA_BUTTON_W + (count - 1) * EXTRA_BUTTON_GAP + PAD;
    }

    private record ExtraHit(int nodeId, int delta, int x, int y, int w, int h) {
    }

    /** 按鼠标横坐标更新音量参数（0~1，保留两位小数）。 */
    private void updateSliderDrag(double mx) {
        if (sliderNode == null || sliderIndex < 0) {
            return;
        }
        double ratio = (mx - sliderLeft) / (double) Math.max(1, sliderWidth);
        ratio = Math.max(0, Math.min(1, ratio));
        double value = Math.round(ratio * 100) / 100.0;
        sliderNode.setParam(sliderIndex, value == Math.rint(value)
                ? Long.toString((long) value) : Double.toString(value));
    }

    /** 画音量拉条：轨道 + 已填充部分 + 圆形滑块。 */
    private void drawSlider(GuiGraphics g, Node n, BlockItem item, int worldX, int worldY) {
        float value = sliderValue(n, item.index);
        int left = px(worldX);
        int top = py(worldY);
        int width = Math.max(6, px(worldX + item.width) - left);
        int height = Math.max(3, py(worldY + item.height) - top);
        int trackH = Math.max(2, height / 3);
        int trackY = top + (height - trackH) / 2;
        int knob = Math.max(3, Math.min(5, height / 2));
        // 轨道
        g.fill(left, trackY, left + width, trackY + trackH, 0xFF6C5B3E);
        // 已填充
        int filled = left + Math.round((width - knob) * value);
        g.fill(left, trackY, filled, trackY + trackH, 0xFFF0D79A);
        // 滑块
        int kx = filled;
        g.fill(kx, top, kx + knob, top + height, 0xFF3E3428);
        g.fill(kx, top, kx + knob - 1, top + height - 1, 0xFFF7E9C8);
    }

    private int scale(int v) {
        return Math.max(1, (int) Math.round(v * zoom));
    }

    /**
     * C 形控制块：头部与底条相同宽度，左边只留一条细脊柱。
     * 普通循环只有头部、空腔、底条；if_else 才绘制分支横条与按钮。
     */
    private void drawCShape(GuiGraphics g, Node n, int color) {
        NodeLayout layout = layoutOf(n);
        NodeType type = n.type();
        int wx = layout.x;
        int wy = layout.y;
        int left = px(wx);
        int right = px(wx + layout.width);
        int bottom = py(wy + layout.height);
        int headY = wy + (isHat(type) ? HAT_CURVE : 0);
        int headBottom = py(headY + layout.headerHeight);
        int spineRight = px(wx + C_INDENT);
        // 帽子块（"当…时"）头部没有上方凹槽，普通 C 形块才有
        drawStatementShape(g, wx, headY, layout.width, layout.headerHeight, hasFlowInput(type), color);
        g.fill(left, headBottom, left + 1, bottom - scale(3), BLOCK_OUTLINE);
        g.fill(spineRight - 1, headBottom, spineRight, bottom - scale(3), BLOCK_OUTLINE);
        g.fill(left + 1, headBottom, spineRight - 1, bottom - scale(3), color);
        // 空腔区域留空：只画左脊柱和右侧开口，底部封条
        g.fill(left, bottom - scale(C_FOOT), right, bottom, BLOCK_OUTLINE);
        g.fill(left + 1, bottom - scale(C_FOOT) + 1, right - 1, bottom - 1, color);

        for (int i = 0; i < layout.branches.size(); i++) {
            BranchLayout branch = layout.branches.get(i);
            int bodyY = wy + branch.y + branch.height;
            // 空腔上下各伸出一小段咬合唇，头一条接头的圆头与积木一致。
            int lipWidth = px(wx + C_INDENT + C_LIP) - spineRight;
            g.fill(spineRight, py(bodyY), spineRight + lipWidth, py(bodyY) + scale(C_LIP_H), color);
            BlockShapes.stackBlock(g, spineRight, py(bodyY), lipWidth, scale(JOINT_H), true,
                    px(wx + C_INDENT + NOTCH_X) - spineRight, jointWidth(wx + C_INDENT),
                    scale(JOINT_H), 0, color);
            int lipBottom = py(bodyY + branch.bodyHeight) - scale(C_LIP_H);
            g.fill(spineRight, lipBottom, spineRight + lipWidth, lipBottom + scale(C_LIP_H), color);
            if (i > 0) {
                g.fill(left, py(wy + branch.y), right, py(bodyY), BLOCK_OUTLINE);
                g.fill(left + 1, py(wy + branch.y) + 1, right - 1, py(bodyY) - 1, color);
                drawBranchButton(g, n, branch);
            }
        }
    }

    /**
     * 世界坐标 X → 屏幕像素。
     * <p>
     * 全界面只有这一处做坐标取整：积木主体、槽位、参数胶囊、按钮、文字都走它，
     * 否则同一块积木的各部分在缩放后会各算一套边，出现错位。
     */
    private int px(int worldX) {
        return (int) Math.round(toScreenX(worldX));
    }

    /** 世界坐标 Y → 屏幕像素，与 {@link #px} 同一套取整规则。 */
    private int py(int worldY) {
        return (int) Math.round(toScreenY(worldY));
    }

    private record BranchHit(int nodeId, int addBranch, int x, int y, int w, int h) {
    }

    /**
     * 横条右侧的 + / - 按钮。
     * 最后一条"否则"上是加号（追加一条"否则如果"），中间各条"否则如果"上是减号（删掉该条）。
     */
    private void drawBranchButton(GuiGraphics g, Node n, BranchLayout branch) {
        NodeLayout layout = layoutOf(n);
        boolean isAdd = "else".equals(branch.port);
        int barTop = py(layout.y + branch.y);
        int barBottom = py(layout.y + branch.y + branch.height);
        int barRight = px(layout.x + layout.width - C_RIGHT_PAD);
        // 按钮按屏幕像素取奇数边长，保证十字中心正好落在整数像素上，缩放后仍轴对称
        int size = Math.max(7, (int) Math.round(11 * zoom));
        if (size % 2 == 0) {
            size++;
        }
        int bx = barRight - size - Math.max(2, (int) Math.round(4 * zoom));
        int by = (barTop + barBottom) / 2 - size / 2;
        int accent = shade(colorOf(n.type().category()), isAdd ? 1.18F : 0.95F);
        g.fill(bx, by, bx + size, by + size, BLOCK_OUTLINE);
        g.fill(bx + 1, by + 1, bx + size - 1, by + size - 1, accent);

        int cx = bx + size / 2;
        int cy = by + size / 2;
        int arm = Math.max(2, size / 2 - 2);
        // 线宽取奇数，才能以中心像素为轴对称展开
        int thick = Math.max(1, (int) Math.round(zoom));
        if (thick % 2 == 0) {
            thick++;
        }
        int half = thick / 2;
        g.fill(cx - arm, cy - half, cx + arm + 1, cy + half + 1, 0xFFFFFFFF);
        if (isAdd) {
            g.fill(cx - half, cy - arm, cx + half + 1, cy + arm + 1, 0xFFFFFFFF);
        }
        branchButtons.add(new BranchHit(n.id(), isAdd ? -1 : branch.branchNumber, bx, by, size, size));
    }

    private void drawHatShape(GuiGraphics g, int wx, int wy, int ww, int wh, int color) {
        int[] body = sb(wx, wy, ww, wh);
        g.fill(body[0], body[1], body[0] + body[2], body[1] + body[3], BLOCK_OUTLINE);
        g.fill(body[0] + 1, body[1] + 1, body[0] + body[2] - 1, body[1] + body[3] - 1, color);
    }

    /** 删除指定「否则如果」时，保留其余分支的内容和条件并顺次前移。 */
    private void removeBranch(Node n, int remove) {
        int count = parseBranches(n);
        if (remove < 1 || remove > count) {
            return;
        }
        Map<String, Wire> moved = new LinkedHashMap<>();
        for (Wire w : new ArrayList<>(program.wires())) {
            if (w.fromNode() != n.id()) {
                continue;
            }
            if (w.fromPort().equals("else") || w.fromPort().matches("else\\d+")) {
                int i = w.fromPort().equals("else") ? 0 : Integer.parseInt(w.fromPort().substring(4));
                if (i == 0 || i != remove) {
                    String target = i > remove ? "else" + (i - 1) : w.fromPort();
                    moved.put(target, new Wire(w.fromNode(), target, w.toNode(), w.toPort()));
                }
            }
        }
        program.wires().removeIf(w -> w.fromNode() == n.id()
                && (w.fromPort().equals("else") || w.fromPort().matches("else\\d+")));
        program.wires().addAll(moved.values());

        Map<String, Wire> conditions = new LinkedHashMap<>();
        for (Wire w : new ArrayList<>(program.wires())) {
            if (w.toNode() != n.id() || !w.toPort().matches("cond\\d+")) {
                continue;
            }
            int i = Integer.parseInt(w.toPort().substring(4));
            if (i != remove) {
                conditions.put(i > remove ? "cond" + (i - 1) : w.toPort(),
                            new Wire(w.fromNode(), w.fromPort(), w.toNode(),
                                    i > remove ? "cond" + (i - 1) : w.toPort()));
            }
        }
        program.wires().removeIf(w -> w.toNode() == n.id() && w.toPort().matches("cond\\d+"));
        program.wires().addAll(conditions.values());
        n.setParam("branches", String.valueOf(count - 1));
    }

    /** 底部凸起与顶部凹口共用同一个缩放后的轮廓。 */
    private void joint(GuiGraphics g, int worldX, int worldY, int color) {
        int x = px(worldX + NOTCH_X);
        int y = py(worldY);
        int w = jointWidth(worldX);
        int h = scale(JOINT_H);
        BlockShapes.stackTab(g, x - 1, y, w + 2, h, BLOCK_OUTLINE);
        BlockShapes.stackTab(g, x, y + 1, w, Math.max(1, h - 1), color);
    }

    private void drawGhost(GuiGraphics g, NodeType t, int x, int y) {
        int color = (colorOf(t.category()) & 0x00FFFFFF) | 0xB3000000;
        int w = PAD + labelWidth(t) + PAD;
        for (NodeType.Port p : t.inputs()) {
            if (p.kind() != PortKind.FLOW) {
                w += EMPTY_SLOT_W + ELEM_GAP;
            }
        }
        for (int i = 0; i < t.params().size(); i++) {
            w += Math.max(26, font.width(t.defaultParams().get(i)) + 10) + ELEM_GAP;
        }
        w = Math.max(w - ELEM_GAP, isReporter(t) ? 34 : 88);
        int h;
        if (isReporter(t)) {
            h = REPORTER_H;
            g.fill(x, y, x + w, y + h, BLOCK_OUTLINE);
            g.fill(x + 1, y + 1, x + w - 1, y + h - 1, color);
        } else if (isHat(t)) {
            h = STACK_H + HAT_CURVE;
            miniHat(g, x, y, w, h, color);
        } else if (cavityPort(t) != null) {
            h = STACK_H + C_EMPTY_H + C_FOOT;
            BlockShapes.stackBlock(g, x, y, w, STACK_H, true, NOTCH_X, NOTCH_W, JOINT_H, 0, BLOCK_OUTLINE);
            BlockShapes.stackBlock(g, x + 1, y + 1, w - 2, STACK_H - 2,
                    true, NOTCH_X - 1, NOTCH_W, JOINT_H, 0, color);
            g.fill(x, y + STACK_H, x + C_INDENT, y + h - C_FOOT, color);
            g.fill(x, y + h - C_FOOT, x + w, y + h, color);
        } else {
            h = STACK_H;
            BlockShapes.stackBlock(g, x, y, w, h, true, NOTCH_X, NOTCH_W, JOINT_H, 0, BLOCK_OUTLINE);
            BlockShapes.stackBlock(g, x + 1, y + 1, w - 2, h - 2,
                    true, NOTCH_X - 1, NOTCH_W, JOINT_H, 0, color);
        }
        int baseY = y + (isHat(t) ? HAT_CURVE : 0);
        g.drawString(font, Component.translatable(t.translationKey()), x + PAD, baseY + (STACK_H - 8) / 2,
                0xE6FFFFFF, true);
    }

    /** 幽灵块用的固定尺寸帽形。 */
    private void miniHat(GuiGraphics g, int x, int y, int w, int h, int color) {
        g.fill(x + 8, y, x + w - 8, y + 1, color);
        g.fill(x + 4, y + 1, x + w - 4, y + 2, color);
        g.fill(x + 2, y + 2, x + w - 2, y + 3, color);
        g.fill(x + 1, y + 3, x + w - 1, y + 4, color);
        g.fill(x, y + 4, x + w, y + h, color);
    }

    // ---------------------------------------------------------------- 吸附

    private Snap currentSnap(double mx, double my) {
        if (draggingNew != null) {
            // 幽灵块的左上角世界坐标
            double wx = toWorldX(mx - 10);
            double wy = toWorldY(my - 8);
            return findSnap(draggingNew, Set.of(), mx, my, wx, wy,
                    Math.max(8, (int) ((PAD + labelWidth(draggingNew) + PAD) * zoom)));
        }
        if (dragging != null && dragging.type() != null) {
            int[] r = worldRect(dragging);
            int[] sr = rects.get(dragging.id());
            double wx = r != null ? r[0] : toWorldX(mx);
            double wy = r != null ? r[1] : toWorldY(my);
            return findSnap(dragging.type(), draggedSubtree, mx, my, wx, wy,
                    sr != null ? sr[2] : widthOf(dragging, 0));
        }
        return null;
    }

    /**
     * 找吸附目标。mx/my 是鼠标屏幕坐标，topXW/topYW 是被拖积木上沿的世界坐标。
     */
    private Snap findSnap(NodeType type, Set<Integer> exclude, double mx, double my, double topXW, double topYW,
                          int blockW) {
        double topSX = toScreenX(topXW);
        double topSY = toScreenY(topYW);
        if (isReporter(type)) {
            PortKind out = dataOutKind(type);
            for (SlotHit s : slotHits) {
                if (s.kind.accepts(out) && mx >= s.x - 3 && mx < s.x + s.w + 3 && my >= s.y - 3 && my < s.y + s.h + 3) {
                    return new Snap(3, program.node(s.nodeId).orElse(null), s.port, s.x, s.y, s.w, s.h);
                }
            }
            return null;
        }
        if (!hasFlowInput(type)) {
            return null; // 帽子块只能做头部，不能接在别处
        }
        // 空腔
        for (CavityHit c : cavityHits) {
            Node parent = program.node(c.nodeId).orElse(null);
            if (parent == null || exclude.contains(c.nodeId)) {
                continue;
            }
            if (cavityPort(parent.type()) == null || program.wireFrom(c.nodeId, c.port).isPresent()) {
                continue; // 空腔已被占用
            }
            if (topSX >= c.x - 6 && topSX < c.x + c.w + 6 && topSY >= c.y - 5 && topSY < c.y + c.h + 5) {
                return new Snap(2, parent, c.port, c.x, c.y, c.w, c.h);
            }
        }
        // 堆叠到某块下方
        Snap best = null;
        double bestDist = Double.MAX_VALUE;
        double tol = Math.max(18, 42 * zoom);
        for (Map.Entry<Integer, int[]> e : rects.entrySet()) {
            if (exclude.contains(e.getKey())) {
                continue;
            }
            Node m = program.node(e.getKey()).orElse(null);
            if (m == null || m.type() == null) {
                continue;
            }
            String cont = continuationPort(m.type());
            if (cont == null) {
                continue;
            }
            int[] sr = e.getValue();
            int by = sr[1] + sr[3];
            double dx = Math.abs(topSX - sr[0]);
            double dy = Math.abs(topSY - by);
            if (dx < tol && dy < Math.max(8, 13 * zoom) && dx + dy < bestDist) {
                bestDist = dx + dy;
                int w = dragging != null ? rects.get(dragging.id())[2] : blockW;
                int h = (int) Math.round((dragging != null ? heightOf(dragging, 0) : STACK_H) * zoom);
                best = new Snap(1, m, cont, sr[0], by, w, h);
            }
        }
        return best;
    }

    private void applySnap(Node n, Snap s) {
        if (s == null || s.target == null || n.type() == null) {
            return;
        }
        switch (s.mode) {
            case 3 -> {
                String out = firstDataOut(n.type());
                if (out != null) {
                    // 凹槽的真实类型可能随模式变化（如「旋转至」按角度处理），按吸附时判定的类型接
                    program.connect(n.id(), out, s.target.id(), s.port, s.kind);
                }
            }
            case 2 -> program.connect(s.target.id(), s.port, n.id(), "exec");
            case 1 -> {
                String cont = s.port;
                Wire old = program.wireFrom(s.target.id(), cont).orElse(null);
                if (old != null) {
                    program.disconnect(old.fromNode(), old.fromPort(), old.toNode(), old.toPort());
                }
                program.connect(s.target.id(), cont, n.id(), "exec");
                if (old != null) {
                    // 原来挂在下面的积木：插到刚放下这块的后面；放不下就变回自由积木
                    String ncont = continuationPort(n.type());
                    boolean occupied = ncont != null && program.wireFrom(n.id(), ncont).isPresent();
                    if (ncont != null && !occupied) {
                        program.connect(n.id(), ncont, old.toNode(), "exec");
                    } else {
                        Node orphan = program.node(old.toNode()).orElse(null);
                        if (orphan != null) {
                            int[] or = worldRect(orphan);
                            orphan.moveTo(or != null ? or[0] + 28 : 60, or != null ? or[1] + 28 : 60);
                        }
                    }
                }
            }
            default -> {
            }
        }
    }

    // ---------------------------------------------------------------- 交互

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        // 运算符下拉菜单打开时，先吃掉这次点击
        if (deviceMenuNode != null) {
            return clickDeviceMenu(mx, my, button);
        }
        if (angleDialNode != null) {
            return clickAngleDial(mx, my);
        }
        if (itemPickerNode != null) {
            return clickItemPicker(mx, my, button);
        }
        if (opMenuNode != null) {
            return clickOpMenu(mx, my);
        }
        if (nameBox != null && nameBox.isMouseOver(mx, my)) {
            commitParamEdit();
            nameBox.setFocused(true);
            return nameBox.mouseClicked(mx, my, button);
        }
        if (paramBox.visible && paramBox.isMouseOver(mx, my)) {
            return paramBox.mouseClicked(mx, my, button);
        }
        // 音效候选列表：点击选中并试听
        if (button == 0 && clickSoundSuggestion(mx, my)) {
            return true;
        }
        // 发信目标候选列表：点击选中
        if (button == 0 && clickChatTargetSuggestion(mx, my)) {
            return true;
        }

        // Ctrl + 左键：拖动画布（显示区平移），不碰积木
        if (button == 0 && ctrlDown() && inCanvas(mx, my)) {
            commitParamEdit();
            panning = true;
            panGrabX = mx;
            panGrabY = my;
            panOriginX = panX;
            panOriginY = panY;
            return true;
        }

        // 保存并返回
        int bw = 76;
        int bx = width - bw - 8;
        if (button == 0 && mx >= bx && mx < bx + bw && my >= 3 && my < 21) {
            saveAndClose();
            return true;
        }

        // 帮助说明
        int helpX = helpButtonX(font);
        if (button == 0 && mx >= helpX && mx < helpX + HELP_BUTTON_SIZE
                && my >= HELP_BUTTON_Y && my < HELP_BUTTON_Y + HELP_BUTTON_SIZE) {
            commitParamEdit();
            minecraft.setScreen(new ComputerHelpScreen(this));
            return true;
        }

        if (button == 0) {
            // 拼接文本的追加按钮：加号多一个文本口，减号去掉最后一个
            for (ExtraHit b : extraButtons) {
                if (mx >= b.x && mx < b.x + b.w && my >= b.y && my < b.y + b.h) {
                    Node n = program.node(b.nodeId).orElse(null);
                    if (n != null) {
                        commitParamEdit();
                        int next = Math.max(0, extraCount(n) + b.delta);
                        n.setParam("extra", String.valueOf(next));
                        return true;
                    }
                }
            }
            // 分支按钮：加号追加"否则如果"，减号删掉最后一个
            for (BranchHit b : branchButtons) {
                if (mx >= b.x && mx < b.x + b.w && my >= b.y && my < b.y + b.h) {
                    Node n = program.node(b.nodeId).orElse(null);
                    if (n != null) {
                        commitParamEdit();
                        if (b.addBranch < 0) {
                            n.setParam("branches", String.valueOf(parseBranches(n) + 1));
                        } else {
                            removeBranch(n, b.addBranch);
                        }
                        return true;
                    }
                }
            }
            // 带数值的槽位（如"次数"）：点一下直接进入编辑，不用拖动
            for (SlotHit s : slotHits) {
                if (mx >= s.x && mx < s.x + s.w && my >= s.y && my < s.y + s.h) {
                    Node n = program.node(s.nodeId).orElse(null);
                    if (n != null && slotHasEditableValue(n, s.port)) {
                        startSlotEdit(n, s.port);
                        return true;
                    }
                }
            }
            // 参数胶囊：开始编辑
            for (ParamHit p : paramHits) {
                if (mx >= p.x && mx < p.x + p.w && my >= p.y && my < p.y + p.h) {
                    Node n = program.node(p.nodeId).orElse(null);
                    if (n != null) {
                        openOpMenu(n, p.index);
                        return true;
                    }
                }
            }
            // 侧边栏：拖出一个新积木
            if (mx < SIDEBAR_W) {
                NodeType t = paletteTypeAt(mx, my);
                if (t != null) {
                    commitParamEdit();
                    draggingNew = t;
                    dragX = mx;
                    dragY = my;
                    return true;
                }
            }
            // 画布：拖起已有积木
            Node hit = nodeAt(mx, my);
            if (hit != null) {
                commitParamEdit();
                // 音量拉条优先：点中拉条时进入拖动而不是拖动整块积木
                if (beginSliderDrag(hit, mx, my)) {
                    return true;
                }
                int[] r = worldRect(hit);
                grabDX = toWorldX(mx) - (r != null ? r[0] : 0);
                grabDY = toWorldY(my) - (r != null ? r[1] : 0);
                detach(hit);
                dragging = hit;
                draggedSubtree.clear();
                collectChain(hit, draggedSubtree);
                dragX = mx;
                dragY = my;
                return true;
            }
            commitParamEdit();
            return true;
        }
        if (button == 1) {
            Node hit = nodeAt(mx, my);
            if (hit != null) {
                commitParamEdit();
                deleteSingle(hit);
                return true;
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (angleDialDragging) {
            Double angle = angleDialAt(mx, my);
            if (angle != null) {
                setAngleDialValue(angle);
            }
            return true;
        }
        if (sliderNode != null) {
            updateSliderDrag(mx);
            return true;
        }
        if (panning) {
            panX = panOriginX - (mx - panGrabX) / zoom;
            panY = panOriginY - (my - panGrabY) / zoom;
            return true;
        }
        if (draggingNew != null) {
            dragX = mx;
            dragY = my;
            return true;
        }
        if (dragging != null) {
            dragX = mx;
            dragY = my;
            dragging.moveTo((float) (toWorldX(mx) - grabDX), (float) (toWorldY(my) - grabDY));
            return true;
        }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        if (angleDialDragging) {
            angleDialDragging = false;
            return true;
        }
        if (sliderNode != null) {
            sliderNode = null;
            sliderIndex = -1;
            return true;
        }
        if (panning) {
            panning = false;
            return true;
        }
        if (draggingNew != null) {
            NodeType type = draggingNew;
            draggingNew = null;
            dropNew(type, mx, my);
            return true;
        }
        if (dragging != null) {
            Node n = dragging;
            dragging = null;
            dropExisting(n, mx, my);
            draggedSubtree.clear();
            return true;
        }
        return super.mouseReleased(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double scrollX, double scrollY) {
        // 参数下拉展开时，滚轮翻候选列表
        if (deviceMenuNode != null && !deviceMenuRects.isEmpty() && deviceMenuMaxScroll > 0) {
            int[] first = deviceMenuRects.get(0);
            int[] last = deviceMenuRects.get(deviceMenuRects.size() - 1);
            if (mx >= first[0] && mx < first[0] + first[2]
                    && my >= first[1] && my < last[1] + last[3]) {
                deviceMenuScroll = Math.max(0, Math.min(deviceMenuMaxScroll,
                        deviceMenuScroll - (int) Math.signum(scrollY)));
                return true;
            }
        }
        if (itemPickerNode != null && mx >= itemPickerX && mx < itemPickerX + itemPickerW
                && my >= itemPickerY && my < itemPickerY + itemPickerH) {
            int columns = Math.max(1, (itemPickerW - 10) / 22);
            itemPickerScroll = Math.max(0, itemPickerScroll - (int) Math.signum(scrollY) * columns);
            return true;
        }
        // Ctrl + 滚轮：以鼠标为中心缩放显示区
        if (ctrlDown() && inCanvas(mx, my)) {
            zoomAt(mx, my, scrollY > 0 ? 1.1 : 1 / 1.1);
            return true;
        }
        if (mx < SIDEBAR_W && my >= TOP_BAR && my < height - STATUS_H) {
            int viewH = height - STATUS_H - TOP_BAR - 8;
            int maxScroll = Math.max(0, paletteContentH - viewH);
            sidebarScroll = Math.max(0, Math.min(maxScroll, sidebarScroll - (int) (scrollY * 21)));
            return true;
        }
        return super.mouseScrolled(mx, my, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // 按键拾取：进入监听后按下的任意键直接绑定为该积木的按键
        if (keyCaptureNode != null) {
            if (keyCode == 256) { // Esc：放弃拾取，不改原值
                keyCaptureNode = null;
                return true;
            }
            Node target = keyCaptureNode;
            keyCaptureNode = null;
            if (KeyNames.all().contains(keyCode)) {
                target.setParam("key", Integer.toString(keyCode));
            }
            return true;
        }
        if (nameBox != null && nameBox.isFocused()) {
            if (keyCode == 257 || keyCode == 335) { // 回车：提交名字
                computerName = nameBox.getValue().trim();
                if (computerName.isEmpty()) {
                    computerName = Component.translatable("cbtc.gui.name.default").getString();
                    nameBox.setValue(computerName);
                }
                nameBox.setFocused(false);
                setFocused(null);
                return true;
            }
            if (keyCode == 256) { // Esc：退出名字编辑，不关闭界面
                nameBox.setFocused(false);
                setFocused(null);
                return true;
            }
            if (nameBox.keyPressed(keyCode, scanCode, modifiers)) {
                return true;
            }
            return true;
        }
        if ((paramTarget != null || slotEditing != null) && (paramBox.isFocused() || isSoundEditing())) {
            // 聊天发信目标：Tab 补全 @ 目标（@a / @e / @PC: / 在线玩家）
            if (isChatTargetEditing() && keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_TAB) {
                completeChatTarget();
                return true;
            }
            if (isSoundEditing()) {
                // TAB：用当前输入框内容补齐第一条候选
                if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_TAB || keyCode == 258 || keyCode == 9) {
                    completeSoundParam();
                    return true;
                }
                // 回车：有候选时先试听，再提交
                if (keyCode == 257 || keyCode == 335) {
                    previewSoundParam();
                    commitParamEdit();
                    return true;
                }
            }
            if (keyCode == 257 || keyCode == 335) { // 回车：提交
                commitParamEdit();
                return true;
            }
            if (keyCode == 256) { // Esc：取消编辑而不是关闭界面
                paramTarget = null;
                slotEditing = null;
                paramBox.visible = false;
                paramBox.setFocused(false);
                setFocused(null);
                return true;
            }
            // 其余按键（退格、删除、方向键、Ctrl+V 等）必须交给输入框自己处理
            if (paramBox.keyPressed(keyCode, scanCode, modifiers)) {
                return true;
            }
            return true;
        }
        // 复制/粘贴：在有输入框时已提前返回，所以这里不会抢输入框的 Ctrl+V
        boolean ctrl = (modifiers & org.lwjgl.glfw.GLFW.GLFW_MOD_CONTROL) != 0;
        if (ctrl && keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_C) {
            if (copyDragging()) {
                return true;
            }
        }
        if (ctrl && keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_V) {
            if (pasteClipboard()) {
                return true;
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        if (nameBox != null && nameBox.isFocused()) {
            return nameBox.keyReleased(keyCode, scanCode, modifiers);
        }
        if (paramTarget != null && paramBox.isFocused()) {
            return paramBox.keyReleased(keyCode, scanCode, modifiers);
        }
        return super.keyReleased(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (nameBox != null && nameBox.isFocused()) {
            return nameBox.charTyped(codePoint, modifiers);
        }
        if ((paramTarget != null || slotEditing != null) && paramBox.isFocused()) {
            return paramBox.charTyped(codePoint, modifiers);
        }
        return super.charTyped(codePoint, modifiers);
    }

    private void dropNew(NodeType type, double mx, double my) {
        if (!inCanvas(mx, my)) {
            return; // 拖回侧边栏 = 取消
        }
        double wx = toWorldX(mx - 10);
        double wy = toWorldY(my - 8);
        Snap s = findSnap(type, Set.of(), mx, my, wx, wy, 0);
        Node n = program.addNode(type, (float) wx, (float) wy);
        applySnap(n, s);
    }

    private void dropExisting(Node n, double mx, double my) {
        if (mx < SIDEBAR_W) {
            deleteChain(n); // 拖回侧边栏 = 删除
            return;
        }
        int[] r = worldRect(n);
        double topX = r != null ? r[0] : toWorldX(mx);
        double topY = r != null ? r[1] : toWorldY(my);
        Snap s = n.type() == null ? null
                : findSnap(n.type(), draggedSubtree, mx, my, topX, topY, widthOf(n, 0));
        applySnap(n, s);
    }

    /** 拖起一块：拔掉它与父级的连接（凹槽线 / 堆叠线），自身及子级保留。 */
    private void detach(Node n) {
        NodeType t = n.type();
        if (t == null) {
            return;
        }
        if (isReporter(t)) {
            for (NodeType.Port p : t.outputs()) {
                if (p.kind() == PortKind.FLOW) {
                    continue;
                }
                Wire w = program.wireFrom(n.id(), p.key()).orElse(null);
                if (w != null) {
                    program.disconnect(w.fromNode(), w.fromPort(), w.toNode(), w.toPort());
                }
            }
        }
        for (Wire w : new ArrayList<>(program.wiresTo(n.id(), "exec"))) {
            program.disconnect(w.fromNode(), w.fromPort(), w.toNode(), w.toPort());
        }
        int[] r = worldRect(n);
        if (r != null) {
            n.moveTo(r[0], r[1]);
        }
    }

    /**
     * Ctrl+C：把正在拖的积木（连同嵌套在里面的积木）复制到剪贴板。
     * <p>
     * 只记录一份"形状"，不删除任何东西；落点用按下 Ctrl+C 时鼠标的位置，
     * 这样之后把鼠标移到别处再粘贴，副本会出现在新的位置。
     *
     * @return 是否真的复制了内容
     */
    private boolean copyDragging() {
        Node source = dragging;
        if (source == null || source.type() == null) {
            return false;
        }
        Set<Integer> ids = new HashSet<>();
        collectChain(source, ids);
        clipboard = new Program();
        clipboardAnchorX = toWorldX(lastMouseX);
        clipboardAnchorY = toWorldY(lastMouseY);
        // 片段的坐标保持原样，粘贴时再整体平移
        for (int id : ids) {
            Node n = program.node(id).orElse(null);
            if (n == null) {
                continue;
            }
            Node copy = clipboard.addNode(n.type(), n.x(), n.y());
            for (int i = 0; i < n.params().size(); i++) {
                copy.setParam(i, n.params().get(i));
            }
        }
        // 片段内部的连线（例如积木体内的下一块）也要一起带上
        List<Integer> ordered = new ArrayList<>(ids);
        for (var wire : program.wires()) {
            if (!ids.contains(wire.fromNode()) || !ids.contains(wire.toNode())) {
                continue;
            }
            int fromIndex = indexOfId(ordered, wire.fromNode());
            int toIndex = indexOfId(ordered, wire.toNode());
            if (fromIndex >= 0 && toIndex >= 0) {
                clipboard.connect(clipboard.nodes().get(fromIndex).id(), wire.fromPort(),
                        clipboard.nodes().get(toIndex).id(), wire.toPort());
            }
        }
        return !clipboard.nodes().isEmpty();
    }

    /** 在 id 列表里找下标，用于把原连线映射到片段内对应的节点。 */
    private static int indexOfId(List<Integer> ids, int id) {
        for (int i = 0; i < ids.size(); i++) {
            if (ids.get(i) == id) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Ctrl+V：把剪贴板里的片段贴到当前鼠标位置。
     *
     * @return 是否真的粘贴了内容
     */
    private boolean pasteClipboard() {
        if (clipboard == null || clipboard.nodes().isEmpty()) {
            return false;
        }
        float dx = (float) (toWorldX(lastMouseX) - clipboardAnchorX);
        float dy = (float) (toWorldY(lastMouseY) - clipboardAnchorY);
        // 剪贴板是独立的一份 Program，节点 id 与当前程序毫无关系，
        // 所以只能照着它的内容逐个重建，再把片段内部的连线接回去
        java.util.Map<Integer, Node> mapping = new java.util.HashMap<>();
        for (Node source : clipboard.nodes()) {
            Node created = program.addNode(source.type(), source.x() + dx, source.y() + dy);
            for (int i = 0; i < source.params().size(); i++) {
                created.setParam(i, source.params().get(i));
            }
            mapping.put(source.id(), created);
        }
        for (var wire : clipboard.wires()) {
            Node from = mapping.get(wire.fromNode());
            Node to = mapping.get(wire.toNode());
            if (from != null && to != null) {
                program.connect(from.id(), wire.fromPort(), to.id(), wire.toPort());
            }
        }
        commitParamEdit();
        return !mapping.isEmpty();
    }

    /** 右键删除单块：子积木与后续积木变回自由积木。 */
    private void deleteSingle(Node n) {
        NodeType t = n.type();
        if (t == null) {
            return;
        }
        List<Node> children = new ArrayList<>();
        for (String port : dataPortsOf(n)) {
            Node child = slotChild(n, port);
            if (child != null) {
                children.add(child);
            }
        }
        for (BranchLayout branch : layoutOf(n).branches) {
            if (branch.branchNumber > 0) {
                Node child = slotChild(n, "cond" + branch.branchNumber);
                if (child != null) {
                    children.add(child);
                }
            }
        }
        Node next = stackNext(n);
        if (next != null) {
            children.add(next);
        }
        for (String cavity : cavityPortsOf(n)) {
            Wire wire = program.wireFrom(n.id(), cavity).orElse(null);
            Node body = wire == null ? null : program.node(wire.toNode()).orElse(null);
            if (body != null) {
                children.add(body);
            }
        }
        int orphanIndex = 0;
        for (Node c : children) {
            int[] cr = worldRect(c);
            int offset = 12 + orphanIndex++ * 16;
            c.moveTo(cr != null ? cr[0] + offset : Math.round(n.x()) + offset,
                    cr != null ? cr[1] + offset : Math.round(n.y()) + offset);
        }
        if (paramTarget == n) {
            paramTarget = null;
            paramBox.visible = false;
        }
        program.removeNode(n.id());
    }

    /** 删除整块以及挂在它里面的所有积木（拖到侧边栏时）。 */
    private void deleteChain(Node n) {
        Set<Integer> ids = new HashSet<>();
        collectChain(n, ids);
        if (paramTarget != null && ids.contains(paramTarget.id())) {
            paramTarget = null;
            paramBox.visible = false;
        }
        for (int id : ids) {
            program.removeNode(id);
        }
    }

    private Node nodeAt(double mx, double my) {
        Node best = null;
        for (Map.Entry<Integer, int[]> e : rects.entrySet()) {
            int[] r = e.getValue();
            if (mx >= r[0] && mx < r[0] + r[2] && my >= r[1] && my < r[1] + r[3]) {
                best = program.node(e.getKey()).orElse(best); // 后插入的是子级，优先命中
            }
        }
        return best;
    }

    private NodeType paletteTypeAt(double mx, double my) {
        if (my < TOP_BAR || my >= height - STATUS_H) {
            return null;
        }
        for (PaletteEntry e : palette) {
            if (e.type != null && mx >= 6 && mx < SIDEBAR_W - 10 && my >= e.y && my < e.y + 17) {
                return e.type;
            }
        }
        return null;
    }

    private void startParamEdit(Node n, int index) {
        paramTarget = n;
        paramIndex = index;
        lastParamHit = null;
        paramBox.setValue(n.param(index));
        // 发信目标留空时胶囊显示"全体玩家"，但编辑时输入框应为空，方便直接看全部候选
        if ("target".equals(n.type().params().get(index))) {
            paramBox.setValue("");
        }
        paramBox.setFocused(true);
        paramBox.visible = true;
    }

    /** 当前是否在编辑聊天积木的发信目标。 */
    private boolean isChatTargetEditing() {
        return paramTarget != null && paramIndex >= 0
                && paramIndex < paramTarget.type().params().size()
                && "target".equals(paramTarget.type().params().get(paramIndex));
    }

    /** 发信目标候选：私聊只列在线玩家；识别聊天框额外提供"全体玩家 / 全体玩家和电脑"。 */
    private List<String> chatTargetCandidates() {
        List<String> options = new ArrayList<>();
        boolean whisper = paramTarget != null && "chat_whisper".equals(paramTarget.typeId());
        if (!whisper) {
            options.add(com.mcl.cbtc.net.ChatTarget.ALL_PLAYERS);
            options.add(com.mcl.cbtc.net.ChatTarget.EVERYONE);
        }
        for (String player : onlinePlayerNames()) {
            String option = "@" + player;
            if (!options.contains(option)) {
                options.add(option);
            }
        }
        return options;
    }

    /** 发信候选的中文标签：@a → 全体玩家，@e → 全体玩家和电脑，@PC:x → 电脑 x，@名字 → 玩家名字。 */
    private static String chatTargetLabel(String raw) {
        if (com.mcl.cbtc.net.ChatTarget.ALL_PLAYERS.equalsIgnoreCase(raw)) {
            return Component.translatable("cbtc.gui.chat_all_players").getString();
        }
        if (com.mcl.cbtc.net.ChatTarget.EVERYONE.equalsIgnoreCase(raw)) {
            return Component.translatable("cbtc.gui.chat_everyone").getString();
        }
        if (raw.startsWith("@")) {
            return Component.translatable("cbtc.gui.chat_player", raw.substring(1)).getString();
        }
        return raw;
    }

    /** 当前输入过滤后的发信目标候选；没在编辑目标时返回空。 */
    private List<String> chatTargetFiltered() {
        if (!isChatTargetEditing()) {
            return List.of();
        }
        String typed = paramBox.getValue().trim().toLowerCase(java.util.Locale.ROOT);
        List<String> result = new ArrayList<>();
        for (String option : chatTargetCandidates()) {
            if (typed.isEmpty() || option.toLowerCase(java.util.Locale.ROOT).contains(typed)
                    || chatTargetLabel(option).contains(typed)) {
                result.add(option);
            }
        }
        return result;
    }

    /** Tab 补全发信目标：取第一个以当前输入开头（忽略大小写）的候选。 */
    private void completeChatTarget() {
        String typed = paramBox.getValue().trim();
        String typedLower = typed.toLowerCase(java.util.Locale.ROOT);
        for (String option : chatTargetCandidates()) {
            if (option.toLowerCase(java.util.Locale.ROOT).startsWith(typedLower)) {
                paramBox.setValue(option);
                paramBox.moveCursorToEnd(false);
                return;
            }
        }
        // 已完整输入或没有匹配：换成第一条，方便连续按 Tab 轮换
        List<String> options = chatTargetCandidates();
        if (!options.isEmpty()) {
            paramBox.setValue(options.get(0));
            paramBox.moveCursorToEnd(false);
        }
    }

    /** 画在发信目标输入框下方的候选列表，样式与音效候选一致。 */
    private void renderChatTargetSuggestions(GuiGraphics g, int mouseX, int mouseY) {
        List<String> list = chatTargetFiltered();
        if (list.isEmpty() || paramBox == null || !paramBox.visible) {
            return;
        }
        int itemH = 12;
        int x = paramBox.getX();
        int y = paramBox.getY() + paramBox.getHeight() + 2;
        int w = Math.max(150, paramBox.getWidth());
        int totalH = list.size() * itemH + 2;
        g.fill(x - 1, y - 1, x + w + 1, y + totalH + 1, BRASS_DARK);
        g.fill(x, y, x + w, y + totalH, BAR_BG);
        for (int i = 0; i < list.size(); i++) {
            String option = list.get(i);
            int iy = y + 1 + i * itemH;
            boolean hover = mouseX >= x && mouseX < x + w && mouseY >= iy && mouseY < iy + itemH;
            if (hover) {
                g.fill(x, iy, x + w, iy + itemH, 0xFF3D3428);
            }
            String label = chatTargetLabel(option) + "  §7" + option;
            while (font.width(label.replace("§7", "")) > w - 8 && label.length() > 4) {
                label = label.substring(0, label.length() - 1);
            }
            g.drawString(font, label, x + 4, iy + 2, hover ? BRASS_LIGHT : TEXT, false);
        }
    }

    /** 点击发信目标候选：写入该目标并提交。 */
    private boolean clickChatTargetSuggestion(double mx, double my) {
        List<String> list = chatTargetFiltered();
        if (list.isEmpty() || paramBox == null || !paramBox.visible) {
            return false;
        }
        int itemH = 12;
        int x = paramBox.getX();
        int y = paramBox.getY() + paramBox.getHeight() + 2;
        int w = Math.max(150, paramBox.getWidth());
        for (int i = 0; i < list.size(); i++) {
            int iy = y + 1 + i * itemH;
            if (mx >= x && mx < x + w && my >= iy && my < iy + itemH) {
                paramBox.setValue(list.get(i));
                commitParamEdit();
                return true;
            }
        }
        return false;
    }

    // ---------------------------------------------------------------- 音效选择

    /** 当前是否正在编辑"播放声音"的声音参数。 */
    private boolean isSoundEditing() {
        return paramTarget != null && "play_sound".equals(paramTarget.typeId())
                && paramIndex >= 0 && paramTarget.type() != null
                && "sound".equals(paramTarget.type().params().get(paramIndex));
    }

    /** 当前应展示的音效候选（仅在编辑声音参数时非空）。 */
    private List<SoundCatalog.Entry> soundCandidates() {
        // 不要求 isFocused：刚点开参数时输入框还没拿到焦点，此时也要能看到候选
        if (!isSoundEditing()) {
            return List.of();
        }
        return SoundCatalog.search(paramBox.getValue(), 8);
    }

    /** TAB 补齐：把输入框替换成第一条候选的完整 id。 */
    private void completeSoundParam() {
        List<SoundCatalog.Entry> list = soundCandidates();
        if (list.isEmpty()) {
            return;
        }
        paramBox.setValue(list.get(0).id().toString());
    }

    /** 回车试听：优先播放输入框当前内容对应的音效。 */
    private void previewSoundParam() {
        String raw = paramBox.getValue().trim();
        var id = net.minecraft.resources.ResourceLocation.tryParse(raw);
        if (id != null) {
            SoundCatalog.preview(id);
            return;
        }
        List<SoundCatalog.Entry> list = soundCandidates();
        if (!list.isEmpty()) {
            SoundCatalog.preview(list.get(0).id());
        }
    }

    /** 画在参数输入框下方的音效候选列表。 */
    private void renderSoundSuggestions(GuiGraphics g, int mouseX, int mouseY) {
        List<SoundCatalog.Entry> list = soundCandidates();
        if (list.isEmpty() || paramBox == null || !paramBox.visible) {
            return;
        }
        int itemH = 12;
        int x = paramBox.getX();
        int y = paramBox.getY() + paramBox.getHeight() + 2;
        int w = Math.max(170, paramBox.getWidth());
        int totalH = list.size() * itemH + 2;
        g.fill(x - 1, y - 1, x + w + 1, y + totalH + 1, BRASS_DARK);
        g.fill(x, y, x + w, y + totalH, BAR_BG);
        for (int i = 0; i < list.size(); i++) {
            SoundCatalog.Entry entry = list.get(i);
            int iy = y + 1 + i * itemH;
            boolean hover = mouseX >= x && mouseX < x + w && mouseY >= iy && mouseY < iy + itemH;
            if (hover) {
                g.fill(x, iy, x + w, iy + itemH, 0xFF3D3428);
            }
            // 有翻译时显示"中文名 原名"，没有翻译只显示原名
            String label = entry.display().isEmpty()
                    ? entry.id().toString()
                    : entry.display() + "  §7" + entry.id();
            while (font.width(label.replace("§7", "")) > w - 8 && label.length() > 4) {
                label = label.substring(0, label.length() - 1);
            }
            g.drawString(font, label, x + 4, iy + 2, hover ? BRASS_LIGHT : TEXT, false);
        }
    }

    /** 点击候选行：选中该音效并试听。 */
    private boolean clickSoundSuggestion(double mx, double my) {
        List<SoundCatalog.Entry> list = soundCandidates();
        if (list.isEmpty() || paramBox == null || !paramBox.visible) {
            return false;
        }
        int itemH = 12;
        int x = paramBox.getX();
        int y = paramBox.getY() + paramBox.getHeight() + 2;
        int w = Math.max(170, paramBox.getWidth());
        for (int i = 0; i < list.size(); i++) {
            int iy = y + 1 + i * itemH;
            if (mx >= x && mx < x + w && my >= iy && my < iy + itemH) {
                SoundCatalog.Entry entry = list.get(i);
                paramBox.setValue(entry.id().toString());
                SoundCatalog.preview(entry.id());
                commitParamEdit();
                return true;
            }
        }
        return false;
    }

    /** 运算符类参数（比较关系、计算符号）：点开下拉菜单选择。 */
    private static String[] opChoices(String typeId, String key, Node node) {
        // 转速控制器只看顺逆时针，不涉及"最短路径"
        if ("speed_controller_set".equals(typeId) && "direction".equals(key)) {
            return new String[]{tr("cbtc.gui.dir_cw"), tr("cbtc.gui.dir_ccw")};
        }
        if ("gearbox_control".equals(typeId) && "direction".equals(key)) {
            // 最短路径只对"旋转至"有意义：其余模式必须指定绕行方向
            if (node != null && node.param("mode").trim().startsWith(tr("cbtc.gui.mode_rotate_to"))) {
                return new String[]{tr("cbtc.gui.dir_forward"), tr("cbtc.gui.dir_reverse"),
                        tr("cbtc.gui.dir_shortest")};
            }
            return new String[]{tr("cbtc.gui.dir_forward"), tr("cbtc.gui.dir_reverse")};
        }
        if ("gearbox_control".equals(typeId) && "mode".equals(key)) {
            return new String[]{tr("cbtc.gui.mode_continuous"), tr("cbtc.gui.mode_angle"),
                    tr("cbtc.gui.mode_jog"), tr("cbtc.gui.mode_rotate_to")};
        }
        if ("gearbox_control".equals(typeId) && "speed".equals(key)) {
            // 转速改成档位：与原版序列齿轮箱一致，只有 ×1 / ×2
            return new String[]{"×1", "×2"};
        }
        if (("time_write".equals(typeId) || "time_current".equals(typeId)) && "unit".equals(key)) {
            return timeUnitChoices();
        }
        // 书写计时只关心"多长"，单位给刻 / 秒 / 分三个就够
        if ("time_duration".equals(typeId) && "unit".equals(key)) {
            return new String[]{tr("cbtc.gui.unit_tick"), tr("cbtc.gui.unit_second"), tr("cbtc.gui.unit_minute")};
        }
        if (("nav_table_pos".equals(typeId) || "nav_table_target".equals(typeId)
                || "target_locator_pos".equals(typeId) || "target_locator_set".equals(typeId))
                && "axis".equals(key)) {
            return new String[]{"X", "Z"};
        }
        if ("nav_bearing_direction".equals(typeId) && "direction".equals(key)) {
            return compassChoices();
        }
        if (("nav_line_direction".equals(typeId) || "nav_line_range".equals(typeId)
                || "nav_line_angle".equals(typeId)) && "direction".equals(key)) {
            return new String[]{tr("cbtc.gui.side_front"), tr("cbtc.gui.side_back")};
        }
        if ("nav_line_direction".equals(typeId) && "bearing".equals(key)) {
            return compassChoices();
        }
        if (!"op".equals(key)) {
            return null;
        }
        return switch (typeId) {
            case "angle_math" -> new String[]{"+", "-", "×", "÷"};
            case "angle_compare" -> compareChoices();
            case "time_math" -> new String[]{"+", "-"};
            case "time_compare" -> compareChoices();
            case "compare" -> compareChoices();
            case "math" -> new String[]{"+", "-", "×", "÷", "mod", "^"};
            case "logic" -> logicChoices();
            case "redstone_receive_compare" -> compareChoices();
            case "redstone_received_compare" -> compareChoices();
            default -> null;
        };
    }

    /** 时间单位：游戏日 / 游戏时 / 游戏分 / 现实年 / 现实日 / 现实时 / 现实分 / 现实秒。 */
    private static String[] timeUnitChoices() {
        return new String[]{tr("cbtc.gui.unit_game_day"), tr("cbtc.gui.unit_game_hour"),
                tr("cbtc.gui.unit_game_minute"), tr("cbtc.gui.unit_real_year"),
                tr("cbtc.gui.unit_real_day"), tr("cbtc.gui.unit_real_hour"),
                tr("cbtc.gui.unit_real_minute"), tr("cbtc.gui.unit_real_second")};
    }

    private static String[] compareChoices() {
        return new String[]{tr("cbtc.gui.cmp_gt"), tr("cbtc.gui.cmp_lt"), tr("cbtc.gui.cmp_eq"),
                tr("cbtc.gui.cmp_ne"), tr("cbtc.gui.cmp_ge"), tr("cbtc.gui.cmp_le")};
    }

    private static String[] logicChoices() {
        return new String[]{tr("cbtc.gui.op_and"), tr("cbtc.gui.op_or"), tr("cbtc.gui.op_xor")};
    }

    private static String[] compassChoices() {
        return new String[]{tr("cbtc.gui.dir_east"), tr("cbtc.gui.dir_northeast"),
                tr("cbtc.gui.dir_north"), tr("cbtc.gui.dir_northwest"), tr("cbtc.gui.dir_west"),
                tr("cbtc.gui.dir_southwest"), tr("cbtc.gui.dir_south"), tr("cbtc.gui.dir_southeast")};
    }

    /** 显示文字 → 内部存储值。 */
    private static String opSymbol(String display) {
        if (display.equals(tr("cbtc.gui.op_steady"))) {
            return "steady";
        }
        if (display.equals(tr("cbtc.gui.op_blink"))) {
            return "blink";
        }
        if (display.equals(tr("cbtc.gui.cmp_gt"))) {
            return ">";
        }
        if (display.equals(tr("cbtc.gui.cmp_lt"))) {
            return "<";
        }
        if (display.equals(tr("cbtc.gui.cmp_eq"))) {
            return "==";
        }
        if (display.equals(tr("cbtc.gui.cmp_ne"))) {
            return "!=";
        }
        if (display.equals(tr("cbtc.gui.cmp_ge"))) {
            return ">=";
        }
        if (display.equals(tr("cbtc.gui.cmp_le"))) {
            return "<=";
        }
        if (display.equals(tr("cbtc.gui.op_and"))) {
            return "and";
        }
        if (display.equals(tr("cbtc.gui.op_or"))) {
            return "or";
        }
        if (display.equals(tr("cbtc.gui.op_xor"))) {
            return "xor";
        }
        if (display.equals(tr("cbtc.gui.mode_continuous"))) {
            return "continuous";
        }
        if (display.equals(tr("cbtc.gui.mode_angle"))) {
            return "angle";
        }
        if (display.equals(tr("cbtc.gui.mode_jog"))) {
            return "jog";
        }
        if (display.equals(tr("cbtc.gui.mode_rotate_to"))) {
            return "rotate_to";
        }
        if (display.equals(tr("cbtc.gui.dir_forward"))) {
            return "forward";
        }
        if (display.equals(tr("cbtc.gui.dir_reverse"))) {
            return "reverse";
        }
        if (display.equals(tr("cbtc.gui.dir_shortest"))) {
            return "shortest";
        }
        if (display.equals(tr("cbtc.gui.side_front"))) {
            return "front";
        }
        if (display.equals(tr("cbtc.gui.side_back"))) {
            return "back";
        }
        if (display.equals(tr("cbtc.gui.dir_east"))) {
            return "east";
        }
        if (display.equals(tr("cbtc.gui.dir_northeast"))) {
            return "northeast";
        }
        if (display.equals(tr("cbtc.gui.dir_north"))) {
            return "north";
        }
        if (display.equals(tr("cbtc.gui.dir_northwest"))) {
            return "northwest";
        }
        if (display.equals(tr("cbtc.gui.dir_west"))) {
            return "west";
        }
        if (display.equals(tr("cbtc.gui.dir_southwest"))) {
            return "southwest";
        }
        if (display.equals(tr("cbtc.gui.dir_south"))) {
            return "south";
        }
        if (display.equals(tr("cbtc.gui.dir_southeast"))) {
            return "southeast";
        }
        if (display.equals(tr("cbtc.gui.unit_second"))) {
            return "second";
        }
        if (display.equals(tr("cbtc.gui.unit_minute"))) {
            return "minute";
        }
        if (display.equals(tr("cbtc.gui.unit_hour"))) {
            return "hour";
        }
        if (display.equals(tr("cbtc.gui.unit_tick"))) {
            return "tick";
        }
        if (display.equals(tr("cbtc.gui.unit_game_day"))) {
            return "game_day";
        }
        return display;
    }

    private static boolean isItemParam(Node n, int index) {
        if (n.type() == null) {
            return false;
        }
        if (isWirelessRedstoneType(n.type())) {
            return index == n.type().paramIndex("channel") || index == n.type().paramIndex("channel2");
        }
        return ("warehouse_item_count".equals(n.typeId()) || "packager_send".equals(n.typeId()))
                && index == n.type().paramIndex("item");
    }

    private static net.minecraft.world.item.ItemStack itemStack(String id) {
        var location = net.minecraft.resources.ResourceLocation.tryParse(id);
        if (location == null || !net.minecraft.core.registries.BuiltInRegistries.ITEM.containsKey(location)) {
            return net.minecraft.world.item.ItemStack.EMPTY;
        }
        return new net.minecraft.world.item.ItemStack(
                net.minecraft.core.registries.BuiltInRegistries.ITEM.get(location));
    }

    private void openItemPicker(Node n, int index) {
        commitParamEdit();
        closeOpMenu();
        itemPickerNode = n;
        itemPickerIndex = index;
        itemPickerScroll = 0;
        itemSearch = "";
        if (isWirelessRedstoneType(n.type())) {
            itemSearchBox = new EditBox(font, 0, 0, 100, 14, Component.translatable("cbtc.gui.channel_search"));
            itemSearchBox.setMaxLength(64);
            itemSearchBox.setHint(Component.translatable("cbtc.gui.channel_search"));
            itemSearchBox.setResponder(value -> {
                itemSearch = value;
                itemPickerScroll = 0;
            });
            addWidget(itemSearchBox);
            itemSearchBox.setFocused(true);
        }
        for (ParamHit hit : paramHits) {
            if (hit.nodeId() == n.id() && hit.index() == index) {
                itemPickerX = hit.x();
                itemPickerY = hit.y() + hit.h() + 3;
                break;
            }
        }
    }

    private void closeItemPicker() {
        itemPickerNode = null;
        itemPickerIndex = -1;
        if (itemSearchBox != null) {
            removeWidget(itemSearchBox);
            itemSearchBox = null;
        }
    }

    private List<net.minecraft.world.item.ItemStack> pickerItems() {
        if (itemPickerNode == null || !isWirelessRedstoneType(itemPickerNode.type())) {
            return warehouseItems.stream().map(entry -> entry.stack).toList();
        }
        String search = itemSearch.trim().toLowerCase(java.util.Locale.ROOT);
        List<net.minecraft.world.item.ItemStack> result = new ArrayList<>();
        for (var item : net.minecraft.core.registries.BuiltInRegistries.ITEM) {
            net.minecraft.world.item.ItemStack stack = item.getDefaultInstance();
            String id = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item).toString();
            if (search.isEmpty() || id.toLowerCase(java.util.Locale.ROOT).contains(search)
                    || stack.getHoverName().getString().toLowerCase(java.util.Locale.ROOT).contains(search)) {
                result.add(stack);
            }
        }
        return result;
    }

    private void renderItemPicker(GuiGraphics g, int mouseX, int mouseY) {
        if (itemPickerNode == null || itemPickerIndex < 0) {
            return;
        }
        filteredPickerItems = pickerItems();
        itemPickerW = Math.min(218, Math.max(170, width - itemPickerX - 8));
        itemPickerX = Math.max(4, Math.min(itemPickerX, width - itemPickerW - 4));
        int columns = Math.max(1, (itemPickerW - 10) / 22);
        int rows = Math.max(1, Math.min(7, (filteredPickerItems.size() + columns - 1) / columns));
        int searchHeight = itemSearchBox == null ? 0 : 18;
        itemPickerH = 29 + searchHeight + rows * 22;
        itemPickerY = Math.max(TOP_BAR + 3,
                Math.min(itemPickerY, height - STATUS_H - itemPickerH - 3));
        if (itemSearchBox != null) {
            itemSearchBox.setPosition(itemPickerX + 5, itemPickerY + 20);
            itemSearchBox.setWidth(itemPickerW - 10);
        }
        g.fill(itemPickerX - 2, itemPickerY - 2, itemPickerX + itemPickerW + 2,
                itemPickerY + itemPickerH + 2, BRASS_DARK);
        g.fill(itemPickerX, itemPickerY, itemPickerX + itemPickerW, itemPickerY + itemPickerH, 0xFF2A241C);
        g.drawString(font, Component.translatable(itemSearchBox == null
                ? "cbtc.gui.warehouse_items" : "cbtc.gui.channel_select"), itemPickerX + 7,
                itemPickerY + 6, TEXT, false);
        if (filteredPickerItems.isEmpty()) {
            g.drawString(font, Component.translatable("cbtc.gui.warehouse_empty"), itemPickerX + 7,
                    itemPickerY + 25 + searchHeight, TEXT_DIM, false);
        }
        int visible = columns * rows;
        itemPickerScroll = Math.max(0, Math.min(itemPickerScroll,
                Math.max(0, filteredPickerItems.size() - visible)));
        net.minecraft.world.item.ItemStack hovered = net.minecraft.world.item.ItemStack.EMPTY;
        for (int i = 0; i < visible; i++) {
            int index = itemPickerScroll + i;
            if (index >= filteredPickerItems.size()) {
                break;
            }
            var stack = filteredPickerItems.get(index);
            int x = itemPickerX + 5 + (i % columns) * 22;
            int y = itemPickerY + 22 + searchHeight + (i / columns) * 22;
            boolean hover = mouseX >= x && mouseX < x + 20 && mouseY >= y && mouseY < y + 20;
            g.fill(x, y, x + 20, y + 20, hover ? 0xFF6C5B3E : 0xFF3D3428);
            g.renderFakeItem(stack, x + 2, y + 2);
            if (hover) {
                hovered = stack;
            }
        }
        if (!hovered.isEmpty()) {
            g.renderTooltip(font, hovered, mouseX, mouseY);
        }
    }

    private boolean clickItemPicker(double mx, double my, int button) {
        if (itemSearchBox != null && itemSearchBox.isMouseOver(mx, my)) {
            return itemSearchBox.mouseClicked(mx, my, button);
        }
        int columns = Math.max(1, (itemPickerW - 10) / 22);
        int searchHeight = itemSearchBox == null ? 0 : 18;
        int rows = Math.max(1, Math.min(7,
                (filteredPickerItems.size() + columns - 1) / columns));
        if (button == 0) {
            for (int i = 0; i < columns * rows; i++) {
                int index = itemPickerScroll + i;
                if (index >= filteredPickerItems.size()) {
                    break;
                }
                int x = itemPickerX + 5 + (i % columns) * 22;
                int y = itemPickerY + 22 + searchHeight + (i / columns) * 22;
                if (mx >= x && mx < x + 20 && my >= y && my < y + 20) {
                    itemPickerNode.setParam(itemPickerIndex,
                            net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(
                                    filteredPickerItems.get(index).getItem()).toString());
                    closeItemPicker();
                    return true;
                }
            }
        }
        closeItemPicker();
        return true;
    }

    private void renderDeviceMenu(GuiGraphics g, int mouseX, int mouseY) {
        deviceMenuRects.clear();
        if (deviceMenuNode == null) {
            return;
        }
        ParamHit anchor = null;
        for (ParamHit hit : paramHits) {
            if (hit.nodeId() == deviceMenuNode.id() && hit.index() == deviceMenuIndex) {
                anchor = hit;
                break;
            }
        }
        if (anchor == null) {
            deviceMenuNode = null;
            return;
        }
        String[] options = deviceMenuOptions(deviceMenuNode, deviceMenuIndex);
        if (options.length == 0) {
            deviceMenuNode = null;
            return;
        }
        int x = anchor.x();
        int y = anchor.y() + anchor.h() + 2;
        boolean narrow = "line".equals(deviceMenuNode.type().params().get(deviceMenuIndex));
        int w = narrow ? Math.max(88, anchor.w()) : Math.min(220, Math.max(130, width - x - 8));
        x = Math.max(4, Math.min(x, width - w - 4));
        int visible = Math.min(options.length, DEVICE_MENU_MAX_ROWS);
        deviceMenuMaxScroll = Math.max(0, options.length - visible);
        deviceMenuScroll = Math.max(0, Math.min(deviceMenuMaxScroll, deviceMenuScroll));
        int h = visible * 17 + 4;
        g.fill(x - 1, y - 1, x + w + 1, y + h + 1, BRASS_DARK);
        g.fill(x, y, x + w, y + h, 0xFF2A241C);
        for (int i = 0; i < visible; i++) {
            int index = deviceMenuScroll + i;
            int iy = y + 2 + i * 17;
            boolean hover = mouseX >= x && mouseX < x + w && mouseY >= iy && mouseY < iy + 17;
            if (hover) {
                g.fill(x, iy, x + w, iy + 17, 0xFF6C5B3E);
            }
            g.drawString(font, options[index], x + 5, iy + 4, TEXT, false);
            deviceMenuRects.add(new int[]{x, iy, w, 17, index});
        }
        // 候选超出可视行数时右侧画滚动条，提示可滚轮翻页
        if (deviceMenuMaxScroll > 0) {
            int trackX = x + w - 3;
            g.fill(trackX, y + 2, trackX + 2, y + h - 2, 0xFF1A1611);
            int thumbH = Math.max(8, (h - 4) * visible / options.length);
            int thumbY = y + 2 + (h - 4 - thumbH) * deviceMenuScroll / deviceMenuMaxScroll;
            g.fill(trackX, thumbY, trackX + 2, thumbY + thumbH, 0xFFB8945A);
        }
    }

    /** 当前参数的下拉候选显示文本，与 {@link #deviceMenuValue} 一一对应。 */
    private String[] deviceMenuOptions(Node node, int index) {
        String key = node.type().params().get(index);
        if ("line".equals(key)) {
            String[] lines = new String[selectedDisplayLines(node)];
            for (int i = 0; i < lines.length; i++) {
                lines[i] = Component.translatable("cbtc.gui.display_line_option", i + 1).getString();
            }
            return lines;
        }
        if ("display_device".equals(menuKind(node, index))) {
            return displayDevices.stream().map(d -> d.name()).toArray(String[]::new);
        }
        if ("lan_network".equals(menuKind(node, index))) {
            // 只能选单个局域网：电脑有几个已加入的网就列几个，不提供"全部"
            return lanNetworks.toArray(String[]::new);
        }
        if ("info_device".equals(menuKind(node, index))) {
            java.util.List<String> names = new java.util.ArrayList<>();
            for (var device : infoTransmitterCandidates()) {
                names.add(device.name() + "(" + kindDisplay(device.kind()) + ")");
            }
            return names.toArray(String[]::new);
        }
        if ("peripheral_device".equals(menuKind(node, index))
                || "gearbox_device".equals(menuKind(node, index))) {
            boolean gearbox = "gearbox_device".equals(menuKind(node, index));
            // 连线积木的齿轮箱是可选换算：不选就输出偏差角（给推力/舵这类执行器用）
            boolean optionalGearbox = gearbox && "device_gearbox".equals(key);
            // "当CB打字机按下"只能选打字机；"读取外设"只能选非打字机；齿轮箱积木只列齿轮箱
            boolean keyNode = isTypewriterNode(node.typeId());
            java.util.List<String> names = new java.util.ArrayList<>();
            if (optionalGearbox) {
                names.add(tr("cbtc.gui.blk_no_gearbox"));
            }
            for (var device : peripheralDevices) {
                if (gearbox != "gearbox".equals(device.kind())) {
                    continue;
                }
                if (keyNode != "typewriter".equals(device.kind())) {
                    continue;
                }
                // 音响之类的发声外设没有可读数据，不该出现在"读取外设"里
                if (!gearbox && soundDeviceKinds().contains(device.kind())) {
                    continue;
                }
                // 导航台走专用积木，不出现在"读取外设"里
                if (!gearbox && "nav_table".equals(device.kind())) {
                    continue;
                }
                // 目标定位模块也走专用积木
                if (!gearbox && "target_locator".equals(device.kind())) {
                    continue;
                }
                // 转速控制器走专用积木
                if (!gearbox && "speed_controller".equals(device.kind())) {
                    continue;
                }
                names.add(device.name() + "(" + kindDisplay(device.kind()) + ")");
            }
            return names.toArray(String[]::new);
        }
        if ("locator_device".equals(menuKind(node, index))) {
            java.util.List<String> names = new java.util.ArrayList<>();
            for (var device : peripheralDevices) {
                if ("target_locator".equals(device.kind())) {
                    names.add(device.name() + "(" + kindDisplay(device.kind()) + ")");
                }
            }
            return names.toArray(String[]::new);
        }
        // 转速控制器积木只列转速控制器
        if ("speed_controller_device".equals(menuKind(node, index))) {
            java.util.List<String> names = new java.util.ArrayList<>();
            for (var device : peripheralDevices) {
                if ("speed_controller".equals(device.kind())) {
                    names.add(device.name() + "(" + kindDisplay(device.kind()) + ")");
                }
            }
            return names.toArray(String[]::new);
        }
        if ("nav_table_device".equals(menuKind(node, index))) {
            java.util.List<String> names = new java.util.ArrayList<>();
            for (var device : peripheralDevices) {
                if ("nav_table".equals(device.kind())) {
                    names.add(device.name() + "(" + kindDisplay(device.kind()) + ")");
                }
            }
            return names.toArray(String[]::new);
        }
        if ("nav_line_b".equals(menuKind(node, index))) {
            // 辅助定位点：A 已经是导航台，B 不能再选导航台，否则两点重合、方位角恒为 0
            java.util.List<String> names = new java.util.ArrayList<>();
            for (var device : navLineBCandidates()) {
                names.add(device.name() + "(" + kindDisplay(device.kind()) + ")");
            }
            return names.toArray(String[]::new);
        }
        if ("sound_device".equals(menuKind(node, index))) {
            // 第一个永远是"此电脑"，其后是已绑定的发声外设（音响等）
            java.util.List<String> names = new java.util.ArrayList<>();
            names.add(tr("cbtc.gui.sound_device_self"));
            for (var device : peripheralDevices) {
                if (soundDeviceKinds().contains(device.kind())) {
                    names.add(device.name() + "(" + kindDisplay(device.kind()) + ")");
                }
            }
            return names.toArray(String[]::new);
        }
        if ("music_song".equals(menuKind(node, index))) {
            return songLibrary.toArray(String[]::new);
        }
        if ("music_playlist".equals(menuKind(node, index))) {
            return playlistIds.toArray(String[]::new);
        }
        if ("music_lang".equals(menuKind(node, index))) {
            return new String[]{tr("cbtc.gui.music_lang_translated"), tr("cbtc.gui.music_lang_original")};
        }
        if ("key".equals(key)) {
            return KeyNames.all().stream().map(KeyNames::display).toArray(String[]::new);
        }
        return new String[0];
    }

    /** 已绑定的信息转发器。 */
    private java.util.List<com.mcl.cbtc.network.DeviceListPayload.Entry> infoTransmitterCandidates() {
        java.util.List<com.mcl.cbtc.network.DeviceListPayload.Entry> list = new java.util.ArrayList<>();
        for (var device : peripheralDevices) {
            if ("info_transmitter".equals(device.kind())) {
                list.add(device);
            }
        }
        return list;
    }

    /** "目标在 前[A] 后[B] 的方位角"里 B 的候选：除导航台/定位模块以外的已绑定外设。 */
    private java.util.List<com.mcl.cbtc.network.DeviceListPayload.Entry> navLineBCandidates() {
        java.util.List<com.mcl.cbtc.network.DeviceListPayload.Entry> list = new java.util.ArrayList<>();
        for (var device : peripheralDevices) {
            if (!"nav_table".equals(device.kind()) && !"target_locator".equals(device.kind())) {
                list.add(device);
            }
        }
        return list;
    }

    /** 菜单项被点中时应写入参数的值。 */
    private String deviceMenuValue(Node node, int index, int optionIndex) {
        String key = node.type().params().get(index);
        String kind = menuKind(node, index);
        if ("info_device".equals(kind)) {
            return infoTransmitterCandidates().get(optionIndex).id();
        }
        if ("line".equals(key)) {
            return Integer.toString(optionIndex + 1);
        }
        if ("display_device".equals(menuKind(node, index))) {
            return displayDevices.get(optionIndex).id();
        }
        if ("sound_device".equals(menuKind(node, index))) {
            // 第 0 项固定是"此电脑"，其余按发声外设的顺序对应
            if (optionIndex == 0) {
                return tr("cbtc.gui.sound_device_self");
            }
            java.util.List<com.mcl.cbtc.network.DeviceListPayload.Entry> candidates = new java.util.ArrayList<>();
            for (var device : peripheralDevices) {
                if (soundDeviceKinds().contains(device.kind())) {
                    candidates.add(device);
                }
            }
            return candidates.get(optionIndex - 1).id();
        }
        if ("lan_network".equals(menuKind(node, index))) {
            return lanNetworks.get(optionIndex);
        }
        if ("music_song".equals(menuKind(node, index))) {
            return optionIndex < songLibrary.size() ? songLibrary.get(optionIndex) : "";
        }
        if ("music_playlist".equals(menuKind(node, index))) {
            return optionIndex < playlistIds.size() ? playlistIds.get(optionIndex) : "";
        }
        if ("music_lang".equals(menuKind(node, index))) {
            return MUSIC_LANGUAGES[optionIndex];
        }
        if ("peripheral_device".equals(menuKind(node, index))
                || "gearbox_device".equals(menuKind(node, index))) {
            boolean gearbox = "gearbox_device".equals(menuKind(node, index));
            boolean keyNode = isTypewriterNode(node.typeId());
            java.util.List<com.mcl.cbtc.network.DeviceListPayload.Entry> candidates = new java.util.ArrayList<>();
            for (var device : peripheralDevices) {
                if (gearbox != "gearbox".equals(device.kind())) {
                    continue;
                }
                if (keyNode != "typewriter".equals(device.kind())) {
                    continue;
                }
                // 与 deviceMenuOptions 的过滤保持一致，避免选项与取值错位
                if (!gearbox && soundDeviceKinds().contains(device.kind())) {
                    continue;
                }
                if (!gearbox && "nav_table".equals(device.kind())) {
                    continue;
                }
                if (!gearbox && "target_locator".equals(device.kind())) {
                    continue;
                }
                // 与 deviceMenuOptions 一致：转速控制器只在专用积木里出现
                if (!gearbox && "speed_controller".equals(device.kind())) {
                    continue;
                }
                candidates.add(device);
            }
            if (gearbox && "device_gearbox".equals(key)) {
                // 第 0 项是"不选"，选它就清空参数，回到偏差角输出
                return optionIndex == 0 ? "" : candidates.get(optionIndex - 1).id();
            }
            return candidates.get(optionIndex).id();
        }
        if ("locator_device".equals(menuKind(node, index))) {
            java.util.List<com.mcl.cbtc.network.DeviceListPayload.Entry> candidates = new java.util.ArrayList<>();
            for (var device : peripheralDevices) {
                if ("target_locator".equals(device.kind())) {
                    candidates.add(device);
                }
            }
            return optionIndex < candidates.size() ? candidates.get(optionIndex).id() : "";
        }
        if ("speed_controller_device".equals(menuKind(node, index))) {
            java.util.List<com.mcl.cbtc.network.DeviceListPayload.Entry> candidates = new java.util.ArrayList<>();
            for (var device : peripheralDevices) {
                if ("speed_controller".equals(device.kind())) {
                    candidates.add(device);
                }
            }
            return optionIndex < candidates.size() ? candidates.get(optionIndex).id() : "";
        }
        if ("nav_table_device".equals(menuKind(node, index))) {
            java.util.List<com.mcl.cbtc.network.DeviceListPayload.Entry> candidates = new java.util.ArrayList<>();
            for (var device : peripheralDevices) {
                if ("nav_table".equals(device.kind())) {
                    candidates.add(device);
                }
            }
            return candidates.get(optionIndex).id();
        }
        if ("nav_line_b".equals(menuKind(node, index))) {
            // 与 deviceMenuOptions 的 navLineBCandidates() 保持一致，下标才能对上
            java.util.List<com.mcl.cbtc.network.DeviceListPayload.Entry> candidates = navLineBCandidates();
            return optionIndex < candidates.size() ? candidates.get(optionIndex).id() : "";
        }
        if ("key".equals(key)) {
            return Integer.toString(KeyNames.all().get(optionIndex));
        }
        return "";
    }

    /** 区分同名参数走哪种菜单：显示设备、外设还是局域网。 */
    private static String menuKind(Node node, int index) {
        if (node.type() == null || index < 0 || index >= node.type().params().size()) {
            return "";
        }
        String key = node.type().params().get(index);
        if ("device_gearbox".equals(key)) {
            return "gearbox_device";
        }
        if ("device_a".equals(key)) {
            return "nav_table_device";
        }
        if ("device_b".equals(key)) {
            return "nav_line_b";
        }
        if ("network".equals(key)) {
            return "lan_network";
        }
        if ("song".equals(key) && "music_play".equals(node.typeId())) {
            return "music_song";
        }
        if ("playlist".equals(key) && "music_play_index".equals(node.typeId())) {
            return "music_playlist";
        }
        if ("lang".equals(key) && "music_song_lyric".equals(node.typeId())) {
            return "music_lang";
        }
        if (!"device".equals(key)) {
            return "";
        }
        if ("info_receive".equals(node.typeId())) {
            return "info_device";
        }
        if (isNavTableNode(node.typeId())) {
            return "nav_table_device";
        }
        if (isTargetLocatorNode(node.typeId())) {
            return "locator_device";
        }
        if (isSoundDeviceNode(node.typeId())) {
            return "sound_device";
        }
        if ("gearbox_control".equals(node.typeId()) || "gearbox_stop".equals(node.typeId())
                || "gearbox_angle".equals(node.typeId())) {
            return "gearbox_device";
        }
        if ("speed_controller_set".equals(node.typeId()) || "speed_controller_get".equals(node.typeId())) {
            return "speed_controller_device";
        }
        return "wireless_display".equals(node.typeId()) ? "display_device" : "peripheral_device";
    }

    /** 歌词可选的语言：网络音乐机只提供中文译文与原文。 */
    private static final String[] MUSIC_LANGUAGES = {"translated", "original"};

    /** 播放声音 / 默念 / 停止声音 / 网络歌曲：设备参数指的是"由谁来发声"。 */
    private static boolean isSoundDeviceNode(String typeId) {
        return "play_sound".equals(typeId) || "murmur".equals(typeId) || "stop_sound".equals(typeId)
                || "music_play".equals(typeId) || "music_play_index".equals(typeId)
                || "music_song_name".equals(typeId) || "music_song_artist".equals(typeId)
                || "music_song_total".equals(typeId) || "music_song_time".equals(typeId)
                || "music_song_lyric".equals(typeId);
    }

    /**
     * 能发声的外设种类。
     * <p>
     * CB音响与讲解石；后续再接入其它发声外设时，把它的 kind 加进来即可，
     * UI 会自动列出已绑定的那几台。
     */
    private static java.util.List<String> soundDeviceKinds() {
        return java.util.List.of("speaker", "lecture_stone");
    }

    /** 当前在线玩家名，用于聊天发信目标的 Tab 候选。 */
    private static List<String> onlinePlayerNames() {
        var minecraft = net.minecraft.client.Minecraft.getInstance();
        if (minecraft.getConnection() == null) {
            return java.util.List.of();
        }
        return minecraft.getConnection().getOnlinePlayers().stream()
                .map(info -> info.getProfile().getName()).toList();
    }

    private com.mcl.cbtc.network.DeviceListPayload.Entry selectedPeripheral(Node node) {
        String id = node.param("device");
        for (var device : peripheralDevices) {
            if (device.id().equals(id)) {
                return device;
            }
        }
        return null;
    }

    /** 设备 id → 用户起的名字；未绑定或找不到时给出提示文案。 */
    private String deviceNameOf(String id) {
        if (id == null || id.isBlank()) {
            return Component.translatable("cbtc.gui.device_select").getString();
        }
        for (var device : peripheralDevices) {
            if (device.id().equals(id)) {
                return device.name();
            }
        }
        return Component.translatable("cbtc.gui.device_select").getString();
    }

    private static String kindDisplay(String kindId) {
        com.mcl.cbtc.device.DeviceKind kind = com.mcl.cbtc.device.DeviceKind.byId(kindId);
        return kind == null ? kindId : kind.displayName();
    }

    private boolean clickDeviceMenu(double mx, double my, int button) {
        if (button == 0) {
            String[] options = deviceMenuOptions(deviceMenuNode, deviceMenuIndex);
            for (int[] r : deviceMenuRects) {
                int i = r[4];
                if (i >= options.length) {
                    continue;
                }
                if (mx >= r[0] && mx < r[0] + r[2] && my >= r[1] && my < r[1] + r[3]) {
                    String key = deviceMenuNode.type().params().get(deviceMenuIndex);
                    deviceMenuNode.setParam(deviceMenuIndex, deviceMenuValue(deviceMenuNode, deviceMenuIndex, i));
                    if ("line".equals(key)) {
                        clampDisplayLine(deviceMenuNode);
                    } else if ("device".equals(key)) {
                        // 发声设备换了，范围上限跟着换，超出新上限的填值要收回
                        clampNodeRange(deviceMenuNode);
                    }
                    deviceMenuNode = null;
                    return true;
                }
            }
        }
        deviceMenuNode = null;
        return true;
    }

    private int selectedDisplayLines(Node node) {
        String id = node.param("device");
        for (var device : displayDevices) {
            if (device.id().equals(id)) {
                return Math.max(1, device.maxLines());
            }
        }
        return 1;
    }

    private void clampDisplayLine(Node node) {
        int line;
        try {
            line = Integer.parseInt(node.param("line"));
        } catch (NumberFormatException e) {
            line = 1;
        }
        node.setParam("line", Integer.toString(Math.max(1, Math.min(line, selectedDisplayLines(node)))));
    }

    private void openOpMenu(Node n, int index) {
        if (n.type() == null) {
            return;
        }
        if (index >= n.type().params().size()) {
            return;
        }
        String key = n.type().params().get(index);
        if ("lan_network".equals(menuKind(n, index))) {
            commitParamEdit();
            deviceMenuNode = n;
            deviceMenuIndex = index;
            deviceMenuScroll = 0;
            return;
        }
        // 网络歌曲：歌曲 / 歌单 / 歌词语言都用下拉列表选，不必手打
        if ("music_song".equals(menuKind(n, index)) || "music_playlist".equals(menuKind(n, index))
                || "music_lang".equals(menuKind(n, index))) {
            commitParamEdit();
            deviceMenuNode = n;
            deviceMenuIndex = index;
            deviceMenuScroll = 0;
            return;
        }
        if ("chat_target".equals(menuKind(n, index))) {
            // 发信目标是输入框：直接打字，Tab 补全候选
            commitParamEdit();
            startParamEdit(n, index);
            return;
        }
        // 导航台 / 辅助定位外设：下拉选已绑定的设备
        if ("nav_table_device".equals(menuKind(n, index)) || "nav_line_b".equals(menuKind(n, index))
                || "gearbox_device".equals(menuKind(n, index))) {
            commitParamEdit();
            deviceMenuNode = n;
            deviceMenuIndex = index;
            deviceMenuScroll = 0;
            return;
        }
        if ("wireless_display".equals(n.typeId()) && ("device".equals(key) || "line".equals(key))) {
            commitParamEdit();
            deviceMenuNode = n;
            deviceMenuIndex = index;
            deviceMenuScroll = 0;
            return;
        }
        // 信息获取：转发器用下拉，行号直接打字输入
        if ("info_device".equals(menuKind(n, index))) {
            commitParamEdit();
            deviceMenuNode = n;
            deviceMenuIndex = index;
            deviceMenuScroll = 0;
            return;
        }
        // 外设：选设备 / 选按键
        if ((isTypewriterNode(n.typeId())
                || "gearbox_control".equals(n.typeId()) || "gearbox_stop".equals(n.typeId())
                || "gearbox_angle".equals(n.typeId()))
                && ("device".equals(key) || "key".equals(key))) {
            if (isTypewriterNode(n.typeId()) && "key".equals(key)) {
                // 按键改为直接监听：点一下之后按下哪个键就绑哪个键，不用在长列表里找
                commitParamEdit();
                keyCaptureNode = n;
                return;
            }
            commitParamEdit();
            deviceMenuNode = n;
            deviceMenuIndex = index;
            deviceMenuScroll = 0;
            return;
        }
        // 声音类：选发声设备（未绑定外设时只有"此电脑"）
        if (isSoundDeviceNode(n.typeId()) && "device".equals(key)) {
            commitParamEdit();
            deviceMenuNode = n;
            deviceMenuIndex = index;
            deviceMenuScroll = 0;
            return;
        }
        // 导航台：只能选已绑定的导航台
        if (isNavTableNode(n.typeId()) && "device".equals(key)) {
            commitParamEdit();
            deviceMenuNode = n;
            deviceMenuIndex = index;
            deviceMenuScroll = 0;
            return;
        }
        // 目标定位模块：只能选已绑定的定位模块
        if (isTargetLocatorNode(n.typeId()) && "device".equals(key)) {
            commitParamEdit();
            deviceMenuNode = n;
            deviceMenuIndex = index;
            deviceMenuScroll = 0;
            return;
        }
        // 智能转速控制器：只能选已绑定的转速控制器
        if ("speed_controller_device".equals(menuKind(n, index))) {
            commitParamEdit();
            deviceMenuNode = n;
            deviceMenuIndex = index;
            deviceMenuScroll = 0;
            return;
        }
        if (isItemParam(n, index)) {
            openItemPicker(n, index);
            return;
        }
        if ("sound".equals(key) && "play_sound".equals(n.typeId())) {
            // 声音参数：用输入框直接搜索，列表里带中文名与原名
            commitParamEdit();
            startParamEdit(n, index);
            return;
        }
        if (opChoices(n.typeId(), key, n) == null) {
            // 旋转至的角度用表盘拾取，比敲数字直观
            if (isAngleDialParam(n, key)) {
                openAngleDial(n, index);
                return;
            }
            startParamEdit(n, index);
            return;
        }
        commitParamEdit();
        opMenuNode = n;
        opMenuIndex = index;
    }

    private void closeOpMenu() {
        opMenuNode = null;
        opMenuIndex = -1;
        opMenuRects.clear();
    }

    // ---------------------------------------------------------------- 角度表盘

    /** 只有"旋转至"的角度参数走表盘：其它模式的数值含义不是朝向。 */
    private static boolean isAngleDialParam(Node n, String key) {
        return "gearbox_control".equals(n.typeId()) && "value".equals(key)
                && n.param("mode").startsWith(tr("cbtc.gui.mode_rotate_to"));
    }

    private void openAngleDial(Node n, int index) {
        commitParamEdit();
        angleDialNode = n;
        angleDialIndex = index;
        angleDialDragging = false;
    }

    private void closeAngleDial() {
        angleDialNode = null;
        angleDialIndex = -1;
        angleDialDragging = false;
    }

    /** 表盘当前指向的角度（度，顺时针为正），读不出来时按 0 处理。 */
    private double angleDialValue() {
        if (angleDialNode == null) {
            return 0;
        }
        try {
            return Double.parseDouble(angleDialNode.param(angleDialIndex).trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private void setAngleDialValue(double degrees) {
        if (angleDialNode == null) {
            return;
        }
        double rounded = Math.round(degrees * 10) / 10.0;
        String text = (Math.abs(rounded) < 0.05) ? "0" : (rounded == Math.rint(rounded)
                ? Long.toString((long) rounded) : Double.toString(rounded));
        angleDialNode.setParam(angleDialIndex, text);
    }

    /**
     * 把鼠标位置换算成角度：正上方为 0°，顺时针为正、逆时针为负。
     * 返回 null 表示鼠标正落在圆心附近，方向没有意义。
     */
    private Double angleDialAt(double mx, double my) {
        double cx = angleDialX + angleDialSize / 2.0;
        double cy = angleDialY + angleDialSize / 2.0;
        double dx = mx - cx;
        double dy = my - cy;
        if (dx * dx + dy * dy < 4) {
            return null;
        }
        // atan2(dx, -dy)：正上方 0、向右为正，正好与"顺时针为正"一致
        return Math.toDegrees(Math.atan2(dx, -dy));
    }

    /** 画表盘：外圈刻度、零点标记、橙色扇区与指针。 */
    private void renderAngleDial(GuiGraphics g, int mouseX, int mouseY) {
        if (angleDialNode == null) {
            return;
        }
        ParamHit anchor = null;
        for (ParamHit p : paramHits) {
            if (p.nodeId == angleDialNode.id() && p.index == angleDialIndex) {
                anchor = p;
                break;
            }
        }
        if (anchor == null) {
            closeAngleDial();
            return;
        }
        int size = 108;
        int cxScreen = Math.max(4, Math.min(anchor.x - size / 2 + anchor.w / 2, width - size - 4));
        int cyScreen = anchor.y + anchor.h + 3;
        if (cyScreen + size > height - 4) {
            cyScreen = Math.max(4, anchor.y - size - 3);
        }
        angleDialX = cxScreen;
        angleDialY = cyScreen;
        angleDialSize = size;

        int cx = cxScreen + size / 2;
        int cy = cyScreen + size / 2;
        int outer = size / 2 - 5;
        int inner = outer - 11;

        // 面板底
        g.fill(cxScreen - 1, cyScreen - 1, cxScreen + size + 1, cyScreen + size + 1, BRASS_DARK);
        g.fill(cxScreen, cyScreen, cxScreen + size, cyScreen + size, 0xFFF7F7F4);
        g.fill(cxScreen, cyScreen, cxScreen + size, cyScreen + 1, 0xFFE0E0DC);

        // 圆环：用逐行扫描画实心圆，避免依赖贴图
        for (int y = -outer; y <= outer; y++) {
            int half = (int) Math.sqrt((double) outer * outer - (double) y * y);
            g.fill(cx - half, cy + y, cx + half, cy + y + 1, 0xFFE8E8E4);
        }
        for (int y = -inner; y <= inner; y++) {
            int half = (int) Math.sqrt((double) inner * inner - (double) y * y);
            g.fill(cx - half, cy + y, cx + half, cy + y + 1, 0xFFFFFFFF);
        }

        // 刻度：每 15° 一根，正方向四根加长并染成黄色
        for (int deg = 0; deg < 360; deg += 15) {
            double rad = Math.toRadians(deg);
            boolean major = deg % 90 == 0;
            int lineInner = major ? inner + 1 : inner + 5;
            int x1 = cx + (int) Math.round(Math.sin(rad) * lineInner);
            int y1 = cy - (int) Math.round(Math.cos(rad) * lineInner);
            int x2 = cx + (int) Math.round(Math.sin(rad) * (outer - 2));
            int y2 = cy - (int) Math.round(Math.cos(rad) * (outer - 2));
            drawDialLine(g, x1, y1, x2, y2, major ? 0xFFF5C542 : 0xFFD8D8D2);
        }

        // 橙色扇区：从 0° 沿转向扫到当前角度
        double value = angleDialValue();
        double sweep = Math.max(-360, Math.min(360, value));
        if (Math.abs(sweep) > 0.05) {
            int steps = Math.max(1, (int) Math.ceil(Math.abs(sweep)));
            int mid = (outer + inner) / 2;
            int bandW = (outer - inner) / 2;
            for (int i = 0; i < steps; i++) {
                double from = sweep * i / steps;
                double to = sweep * (i + 1) / steps;
                double rad = Math.toRadians((from + to) / 2);
                int px = cx + (int) Math.round(Math.sin(rad) * mid);
                int py = cy - (int) Math.round(Math.cos(rad) * mid);
                g.fill(px - bandW / 2, py - bandW / 2, px - bandW / 2 + bandW, py - bandW / 2 + bandW,
                        0xFFF5A65B);
            }
        }

        // 指针：从圆心指向当前角度，尖端在环内侧
        double rad = Math.toRadians(sweep);
        int tipX = cx + (int) Math.round(Math.sin(rad) * (inner - 3));
        int tipY = cy - (int) Math.round(Math.cos(rad) * (inner - 3));
        drawDialLine(g, cx, cy, tipX, tipY, 0xFF9E9E96);
        g.fill(cx - 2, cy - 2, cx + 2, cy + 2, 0xFF9E9E96);

        // 当前角度读数
        String label = Math.round(sweep * 10) / 10.0 + "°";
        int labelW = font.width(label);
        g.drawString(font, label, cx - labelW / 2, cyScreen + size - 12, 0xFF5A5A54, false);
    }

    /** 画一条任意方向的线：按较长轴逐段填充。 */
    private static void drawDialLine(GuiGraphics g, int x1, int y1, int x2, int y2, int color) {
        int steps = Math.max(Math.abs(x2 - x1), Math.abs(y2 - y1));
        if (steps == 0) {
            g.fill(x1, y1, x1 + 1, y1 + 1, color);
            return;
        }
        for (int i = 0; i <= steps; i++) {
            int x = x1 + (x2 - x1) * i / steps;
            int y = y1 + (y2 - y1) * i / steps;
            g.fill(x, y, x + 1, y + 1, color);
        }
    }

    private boolean clickAngleDial(double mx, double my) {
        if (mx < angleDialX || mx >= angleDialX + angleDialSize
                || my < angleDialY || my >= angleDialY + angleDialSize) {
            closeAngleDial();
            return true;
        }
        Double angle = angleDialAt(mx, my);
        if (angle != null) {
            setAngleDialValue(angle);
            angleDialDragging = true;
        }
        return true;
    }

    /** 在胶囊下方画出运算符选项列表，并记录每项的位置。 */
    private void renderOpMenu(GuiGraphics g, int mouseX, int mouseY) {
        opMenuRects.clear();
        if (opMenuNode == null || opMenuNode.type() == null) {
            return;
        }
        String[] choices = opChoices(opMenuNode.typeId(), opMenuNode.type().params().get(opMenuIndex), opMenuNode);
        if (choices == null) {
            closeOpMenu();
            return;
        }
        ParamHit anchor = null;
        for (ParamHit p : paramHits) {
            if (p.nodeId == opMenuNode.id() && p.index == opMenuIndex) {
                anchor = p;
                break;
            }
        }
        if (anchor == null) {
            closeOpMenu();
            return;
        }
        int itemH = 14;
        int w = Math.max(anchor.w, 52);
        int x = anchor.x;
        int y = anchor.y + anchor.h + 1;
        int totalH = choices.length * itemH + 2;
        g.fill(x - 1, y - 1, x + w + 1, y + totalH + 1, BRASS_DARK);
        g.fill(x, y, x + w, y + totalH, 0xFF2A241C);
        String current = opMenuNode.param(opMenuIndex);
        for (int i = 0; i < choices.length; i++) {
            int iy = y + 1 + i * itemH;
            boolean hover = mouseX >= x && mouseX < x + w && mouseY >= iy && mouseY < iy + itemH;
            boolean selected = opSymbol(choices[i]).equals(current);
            if (hover) {
                g.fill(x, iy, x + w, iy + itemH, 0xFF3D3428);
            }
            int color = selected ? BRASS_LIGHT : TEXT;
            g.drawString(font, choices[i], x + 5, iy + 3, color, false);
            opMenuRects.add(new int[]{x, iy, w, itemH});
        }
    }

    private boolean clickOpMenu(double mx, double my) {
        if (opMenuNode == null) {
            return false;
        }
        String[] choices = opChoices(opMenuNode.typeId(), opMenuNode.type().params().get(opMenuIndex), opMenuNode);
        for (int i = 0; i < opMenuRects.size() && i < choices.length; i++) {
            int[] r = opMenuRects.get(i);
            if (mx >= r[0] && mx < r[0] + r[2] && my >= r[1] && my < r[1] + r[3]) {
                opMenuNode.setParam(opMenuIndex, opSymbol(choices[i]));
                // 模式从"旋转至"改走后，"最短路径"就不再适用，回落到正转
                if ("gearbox_control".equals(opMenuNode.typeId())
                        && "mode".equals(opMenuNode.type().params().get(opMenuIndex))) {
                    String dir = opMenuNode.param("direction");
                    if (dir.equals("shortest") || dir.startsWith(tr("cbtc.gui.dir_shortest"))) {
                        opMenuNode.setParam("direction", "forward");
                    }
                }
                closeOpMenu();
                return true;
            }
        }
        closeOpMenu();
        return true;
    }

    /** 编辑槽位里的数值（"重复…次"、"等待…秒"这类既能填也能嵌积木的位置）。 */
    private void startSlotEdit(Node n, String port) {
        paramTarget = null;
        slotEditing = n;
        slotEditPort = port;
        lastParamHit = null;
        paramBox.setValue(n.param(port));
        paramBox.setFocused(true);
        paramBox.visible = true;
    }

    private void commitParamEdit() {
        if (slotEditing != null) {
            String val = paramBox.getValue().trim();
            slotEditing.setParam(slotEditPort, val);
            slotEditing = null;
            slotEditPort = "";
        }
        if (paramTarget != null) {
            paramTarget.setParam(paramIndex, clampSoundRange(paramTarget, paramBox.getValue()));
            paramTarget = null;
        }
        paramBox.visible = false;
        paramBox.setFocused(false);
    }

    /**
     * 播放声音/朗诵/播放网络歌曲的"范围"填值按发声设备的上限夹取：
     * 此电脑看电脑装的扬声器模块（12 / 50），音响看它自己的升级槽（20 / 80）。
     * 非数字原样返回，交给服务端当 0 处理。
     */
    private String clampSoundRange(Node node, String raw) {
        NodeType type = node.type();
        if (type == null || paramIndex < 0 || paramIndex >= type.params().size()) {
            return raw;
        }
        if (!"range".equals(type.params().get(paramIndex))) {
            return raw;
        }
        if (!"play_sound".equals(type.id())
                && !"music_play".equals(type.id()) && !"music_play_index".equals(type.id())) {
            return raw;
        }
        try {
            int value = (int) Math.floor(Double.parseDouble(raw.trim()));
            int max = soundRangeLimit(node);
            return String.valueOf(Math.max(0, Math.min(max, value)));
        } catch (NumberFormatException e) {
            return raw;
        }
    }

    /** 按当前设备上限重新夹取该积木的 range 参数。 */
    private void clampNodeRange(Node node) {
        if (node == null || node.type() == null) {
            return;
        }
        int index = node.type().paramIndex("range");
        if (index < 0) {
            return;
        }
        int saved = paramIndex;
        paramIndex = index;
        node.setParam(index, clampSoundRange(node, node.param(index)));
        paramIndex = saved;
    }

    /** 积木当前选中的发声设备允许的最大范围。 */
    private int soundRangeLimit(Node node) {
        String deviceId = node.param("device").trim();
        if (deviceId.isBlank() || com.mcl.cbtc.core.NodeTypes.SOUND_DEVICE_SELF.equals(deviceId)) {
            return speakerInstalled
                    ? com.mcl.cbtc.core.vm.ProgramVM.MAX_SOUND_RANGE_SPEAKER
                    : com.mcl.cbtc.core.vm.ProgramVM.MAX_SOUND_RANGE;
        }
        for (var device : peripheralDevices) {
            if (device.id().equals(deviceId)) {
                return device.soundRange();
            }
        }
        // 设备已解绑：按电脑上限兜底，避免填出无法预期的值
        return speakerInstalled
                ? com.mcl.cbtc.core.vm.ProgramVM.MAX_SOUND_RANGE_SPEAKER
                : com.mcl.cbtc.core.vm.ProgramVM.MAX_SOUND_RANGE;
    }

    @Override
    public void removed() {
        commitParamEdit();
        // 打开说明页时本屏幕只是被替换，容器仍然保持打开，不能在这里 stopOpen。
        if (!reopening) {
            super.removed();
        }
    }

    /** 临时打开其它界面（说明页）时的标记，避免把容器关掉。 */
    private boolean reopening;

    /** 从说明页返回时置位，用来跳过重复的程序请求。 */
    private boolean fromHelp;

    /** 从说明页返回：重新挂回本屏幕，不重新发送打开容器的请求。 */
    public void reopenFromHelp() {
        reopening = true;
        fromHelp = true;
        minecraft.setScreen(this);
        fromHelp = false;
        reopening = false;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ---------------------------------------------------------------- 工具

    static int colorOf(NodeCategory category) {
        return switch (category) {
            case EVENT -> 0xFFF0B429;
            case CONTROL -> 0xFFE08A3C;
            case DATA -> 0xFF7FB069;
            case MATH -> 0xFF6E9E5B;
            case REDSTONE -> 0xFFD06A5A;
            case BROADCAST -> 0xFF6D8FC7;
            case LAN -> 0xFF8C7BC7;
            case INTERNET -> 0xFFD98F4B;
            case DISPLAY -> 0xFFA97BC7;
            case AERO -> 0xFF5EB0C7;
            case SOUND -> 0xFF4FA3A5;
        };
    }

    private static int textColorFor(int argb) {
        int r = (argb >> 16) & 0xFF;
        int g = (argb >> 8) & 0xFF;
        int b = argb & 0xFF;
        double lum = 0.299 * r + 0.587 * g + 0.114 * b;
        return lum > 140 ? 0xFF241F16 : 0xFFFDF6E8;
    }

    private static int shade(int argb, float f) {
        int a = (argb >>> 24) & 0xFF;
        int r = Math.min(255, (int) (((argb >> 16) & 0xFF) * f));
        int g = Math.min(255, (int) (((argb >> 8) & 0xFF) * f));
        int b = Math.min(255, (int) ((argb & 0xFF) * f));
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        // 背景全部在 render 里自绘
    }

    // 编程页是独立的全屏 UI，不需要物品栏/槽位。JEI 会把所有 AbstractContainerScreen 当作容器界面，
    // 在右侧叠加物品列表和搜索框盖住积木面板。这里让 JEI 拿不到有效界面尺寸（<=0 会被判为无效），
    // 使它不在本页面绘制任何覆盖层；本界面的排版和绘制均使用屏幕尺寸，不受这两个值影响。
    @Override
    public int getXSize() {
        return 0;
    }

    @Override
    public int getYSize() {
        return 0;
    }
}
