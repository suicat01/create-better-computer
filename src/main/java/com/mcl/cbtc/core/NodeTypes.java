package com.mcl.cbtc.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 节点类型表。这里的条目是积木编程的"词汇表"，UI 的材料区与执行器都从这里取数据。
 * <p>
 * 约定：FLOW 端口用来串执行顺序；其余端口是"数据口"，每个 tick 重新求值，因此不需要手动连线求值顺序。
 * <p>
 * 目前只保留示范程序用到的积木；其余积木后续再逐个添加。
 */
public final class NodeTypes {
    private static final List<NodeType> ALL = new ArrayList<>();

    /** 声音类积木的设备参数默认值：由运行程序的这台电脑自己发声。 */
    public static final String SOUND_DEVICE_SELF = "此电脑";

    /** 当此电脑运行时：开机时触发一次。 */
    public static final NodeType ON_RUN = add("on_run", NodeCategory.EVENT,
            List.of(), List.of(new NodeType.Port("exec", PortKind.FLOW)), List.of(), List.of());

    // ---------------------------------------------------------------- 控制

    /** 重复执行：一直循环体内的积木，只能靠"退出循环"结束。 */
    public static final NodeType FOREVER = add("forever", NodeCategory.CONTROL,
            List.of(new NodeType.Port("exec", PortKind.FLOW)),
            List.of(new NodeType.Port("body", PortKind.FLOW), new NodeType.Port("done", PortKind.FLOW)),
            List.of(), List.of());

    /** 重复执行 N 次：次数既可填数字，也可嵌入"计算"等数值积木。 */
    public static final NodeType REPEAT_N = add("repeat_n", NodeCategory.CONTROL,
            List.of(new NodeType.Port("exec", PortKind.FLOW), new NodeType.Port("times", PortKind.NUMBER)),
            List.of(new NodeType.Port("body", PortKind.FLOW), new NodeType.Port("done", PortKind.FLOW)),
            List.of("times"), List.of("20"));

    /** 重复执行直到 条件成立。 */
    public static final NodeType REPEAT_UNTIL = add("repeat_until", NodeCategory.CONTROL,
            List.of(new NodeType.Port("exec", PortKind.FLOW), new NodeType.Port("cond", PortKind.BOOL)),
            List.of(new NodeType.Port("body", PortKind.FLOW), new NodeType.Port("done", PortKind.FLOW)),
            List.of(), List.of());

    /** 退出循环：立即跳出所在的最内层循环。 */
    public static final NodeType BREAK = add("break", NodeCategory.CONTROL,
            List.of(new NodeType.Port("exec", PortKind.FLOW)),
            List.of(new NodeType.Port("exec", PortKind.FLOW)), List.of(), List.of());

    /** 如果 条件：条件成立时执行空腔里的积木。 */
    public static final NodeType IF = add("if", NodeCategory.CONTROL,
            List.of(new NodeType.Port("exec", PortKind.FLOW), new NodeType.Port("cond", PortKind.BOOL)),
            List.of(new NodeType.Port("then", PortKind.FLOW), new NodeType.Port("done", PortKind.FLOW)),
            List.of("branches"), List.of("0"));

    /** 如果 条件 / 否则（可点加号追加"否则如果"分支）。 */
    public static final NodeType IF_ELSE = add("if_else", NodeCategory.CONTROL,
            List.of(new NodeType.Port("exec", PortKind.FLOW), new NodeType.Port("cond", PortKind.BOOL)),
            List.of(new NodeType.Port("then", PortKind.FLOW), new NodeType.Port("else", PortKind.FLOW),
                    new NodeType.Port("done", PortKind.FLOW)),
            List.of("branches"), List.of("0"));

    /** 保持等待直到 条件成立。 */
    public static final NodeType WAIT_UNTIL = add("wait_until", NodeCategory.CONTROL,
            List.of(new NodeType.Port("exec", PortKind.FLOW), new NodeType.Port("cond", PortKind.BOOL)),
            List.of(new NodeType.Port("exec", PortKind.FLOW)), List.of(), List.of());

    /** 当 条件：事件帽子块，条件由不成立变为成立时触发一次。 */
    public static final NodeType WHEN = add("when", NodeCategory.EVENT,
            List.of(new NodeType.Port("cond", PortKind.BOOL)),
            List.of(new NodeType.Port("exec", PortKind.FLOW)),
            List.of(), List.of());

    /** 等待 N 秒：既能填秒数，也能嵌入数值积木。 */
    public static final NodeType WAIT_SECONDS = add("wait_seconds", NodeCategory.CONTROL,
            List.of(new NodeType.Port("exec", PortKind.FLOW), new NodeType.Port("seconds", PortKind.NUMBER)),
            List.of(new NodeType.Port("exec", PortKind.FLOW)), List.of("seconds"), List.of("1"));

    // ---------------------------------------------------------------- 数据

    /** 数字常量。 */
    public static final NodeType CONST_NUMBER = add("const_number", NodeCategory.DATA,
            List.of(), List.of(new NodeType.Port("value", PortKind.NUMBER)), List.of("value"), List.of("0"));

    /** 设置变量：把 value 写进名字为 name 的变量。变量属于这台电脑，同机所有积木共用。 */
    public static final NodeType VAR_SET = add("var_set", NodeCategory.DATA,
            List.of(new NodeType.Port("exec", PortKind.FLOW), new NodeType.Port("value", PortKind.ANY)),
            List.of(new NodeType.Port("exec", PortKind.FLOW)), List.of("name"), List.of("变量1"));

    /** 读取变量：输出名为 name 的变量当前值，可作为表达式嵌进凹槽。 */
    public static final NodeType VAR_GET = add("var_get", NodeCategory.DATA,
            List.of(), List.of(new NodeType.Port("value", PortKind.ANY)), List.of("name"), List.of("变量1"));

    /** 修改硬盘变量：把 value 写进硬盘里名为 name 的变量，数据存在硬盘物品的 NBT 中。 */
    public static final NodeType DRIVE_SET = add("drive_set", NodeCategory.DATA,
            List.of(new NodeType.Port("exec", PortKind.FLOW), new NodeType.Port("value", PortKind.ANY)),
            List.of(new NodeType.Port("exec", PortKind.FLOW)), List.of("name"), List.of("变量1"));

    /** 读取硬盘变量：输出硬盘里名为 name 变量的当前值；没存过时为空文本。 */
    public static final NodeType DRIVE_GET = add("drive_get", NodeCategory.DATA,
            List.of(), List.of(new NodeType.Port("value", PortKind.ANY)), List.of("name"), List.of("变量1"));

    // ---------------------------------------------------------------- 广播

    /** 发送广播：向本机发出一个命名广播，触发所有"收到广播"积木。 */
    public static final NodeType BROADCAST_SEND = add("broadcast_send", NodeCategory.BROADCAST,
            List.of(new NodeType.Port("exec", PortKind.FLOW)),
            List.of(new NodeType.Port("exec", PortKind.FLOW)), List.of("channel"), List.of("广播1"));

    /** 收到广播时：当本机收到名为 channel 的广播就执行体内的积木。 */
    public static final NodeType BROADCAST_RECEIVE = add("broadcast_receive", NodeCategory.EVENT,
            List.of(), List.of(new NodeType.Port("exec", PortKind.FLOW), new NodeType.Port("done", PortKind.FLOW)),
            List.of("channel"), List.of("广播1"));

    /** 设置广播变量：写入本机广播变量（供其他积木读取）。 */
    public static final NodeType BC_VAR_SET = add("bc_var_set", NodeCategory.BROADCAST,
            List.of(new NodeType.Port("exec", PortKind.FLOW), new NodeType.Port("value", PortKind.ANY)),
            List.of(new NodeType.Port("exec", PortKind.FLOW)), List.of("name"), List.of("广播变量1"));

    /** 读取广播变量：输出本机广播变量的当前值。 */
    public static final NodeType BC_VAR_GET = add("bc_var_get", NodeCategory.BROADCAST,
            List.of(), List.of(new NodeType.Port("value", PortKind.ANY)), List.of("name"), List.of("广播变量1"));

    /** 收到频道信号：红石条件积木，本轮收到了无线红石信号，产出布尔。 */
    public static final NodeType REDSTONE_RECEIVED = add("redstone_received", NodeCategory.BROADCAST,
            List.of(), List.of(new NodeType.Port("value", PortKind.BOOL)),
            List.of("channel", "channel2"), List.of("", ""));

    /** 收到频道信号且满足强度条件：红石条件积木，产出布尔。 */
    public static final NodeType REDSTONE_RECEIVED_COMPARE = add("redstone_received_compare", NodeCategory.BROADCAST,
            List.of(new NodeType.Port("strength", PortKind.NUMBER)),
            List.of(new NodeType.Port("value", PortKind.BOOL)),
            List.of("channel", "channel2", "op", "strength"), List.of("", "", ">=", "1"));

    /** 收到广播：条件积木，本轮收到了指定频道的广播，产出布尔。 */
    public static final NodeType BROADCAST_RECEIVED = add("broadcast_received", NodeCategory.BROADCAST,
            List.of(), List.of(new NodeType.Port("value", PortKind.BOOL)),
            List.of("channel"), List.of("广播1"));

    /** 导航台的坐标：产出导航台自身在世界中的 X 或 Z 坐标。 */
    public static final NodeType NAV_TABLE_POS = add("nav_table_pos", NodeCategory.AERO,
            List.of(), List.of(new NodeType.Port("value", PortKind.NUMBER)),
            List.of("device", "axis"), List.of("", "X"));

    /** 导航台目标的坐标：产出导航台目标在世界中的 X 或 Z 坐标。 */
    public static final NodeType NAV_TABLE_TARGET = add("nav_table_target", NodeCategory.AERO,
            List.of(), List.of(new NodeType.Port("value", PortKind.NUMBER)),
            List.of("device", "axis"), List.of("", "X"));

    /** 目标定位模块的坐标：产出模块里写着的 X 或 Z 坐标。 */
    public static final NodeType TARGET_LOCATOR_POS = add("target_locator_pos", NodeCategory.AERO,
            List.of(), List.of(new NodeType.Port("value", PortKind.NUMBER)),
            List.of("device", "axis"), List.of("", "X"));

    /** 设置目标定位模块的坐标：把模块某一轴改成指定数值。 */
    public static final NodeType TARGET_LOCATOR_SET = add("target_locator_set", NodeCategory.AERO,
            List.of(new NodeType.Port("exec", PortKind.FLOW), new NodeType.Port("value", PortKind.NUMBER)),
            List.of(new NodeType.Port("exec", PortKind.FLOW)),
            List.of("device", "axis", "value"), List.of("", "X", "0"));

    /** 导航台离目标的直线距离（米）。 */
    public static final NodeType NAV_TABLE_DISTANCE = add("nav_table_distance", NodeCategory.AERO,
            List.of(), List.of(new NodeType.Port("value", PortKind.NUMBER)),
            List.of("device"), List.of(""));

    /** 目标在导航台的八方方位：条件积木，产出布尔。 */
    public static final NodeType NAV_BEARING_DIRECTION = add("nav_bearing_direction", NodeCategory.AERO,
            List.of(), List.of(new NodeType.Port("value", PortKind.BOOL)),
            List.of("device", "direction"), List.of("", "东"));

    /** 目标在导航台的方位角区间：条件积木，产出布尔。两个口接受角度积木。 */
    public static final NodeType NAV_BEARING_RANGE = add("nav_bearing_range", NodeCategory.AERO,
            List.of(new NodeType.Port("min", PortKind.ANGLE), new NodeType.Port("max", PortKind.ANGLE)),
            List.of(new NodeType.Port("value", PortKind.BOOL)),
            List.of("device", "min", "max"), List.of("", "-180", "180"));

    /** 目标在导航台的方位角：角度积木，产出角度（正东为 0°）。 */
    public static final NodeType NAV_BEARING_ANGLE = add("nav_bearing_angle", NodeCategory.AERO,
            List.of(), List.of(new NodeType.Port("value", PortKind.ANGLE)),
            List.of("device"), List.of(""));

    /** 目标在导航台 A→B 连线的方位（条件积木）。A 是前（目标），B 是后（辅助定位）。
     *  direction 是前 / 后（目标在线的前半还是后半），bearing 是八方（世界方向）。 */
    public static final NodeType NAV_LINE_DIRECTION = add("nav_line_direction", NodeCategory.AERO,
            List.of(), List.of(new NodeType.Port("value", PortKind.BOOL)),
            List.of("device_a", "device_b", "direction", "bearing"), List.of("", "", "前", "东"));

    /** 目标在导航台 A→B 连线的方位角区间（条件积木）。两个口接受角度积木。 */
    public static final NodeType NAV_LINE_RANGE = add("nav_line_range", NodeCategory.AERO,
            List.of(new NodeType.Port("min", PortKind.ANGLE), new NodeType.Port("max", PortKind.ANGLE)),
            List.of(new NodeType.Port("value", PortKind.BOOL)),
            List.of("device_a", "device_b", "direction", "min", "max"), List.of("", "", "前", "-180", "180"));

    /**
     * 导航台连线的目标角（角度积木）。
     * <p>
     * 选了齿轮箱时：输出"把该齿轮箱转到这个角度就正对目标"的绝对角（0~360），
     * 可以直接接「旋转至」；转到后下一轮读到的值相同，自然不会再转。
     * 没选齿轮箱时退回偏差角语义：0° = 正对目标，正值 = 需要顺时针修正。
     */
    public static final NodeType NAV_LINE_ANGLE = add("nav_line_angle", NodeCategory.AERO,
            List.of(), List.of(new NodeType.Port("value", PortKind.ANGLE)),
            List.of("device_a", "device_b", "direction", "device_gearbox"), List.of("", "", "前", ""));

    /** 当打字机按下某键时：按下立刻触发一次，按住超过 0.2 秒后继续每刻连发（短按长按都响应）。 */
    public static final NodeType TYPEWRITER_KEY = add("typewriter_key", NodeCategory.EVENT,
            List.of(),
            List.of(new NodeType.Port("exec", PortKind.FLOW)),
            List.of("device", "key"), List.of("", ""));

    /** 当打字机长按某键时：按下后前 0.2 秒不响应，满 0.2 秒起每刻触发，松开即停。 */
    public static final NodeType TYPEWRITER_LONG_PRESS = add("typewriter_long_press", NodeCategory.EVENT,
            List.of(),
            List.of(new NodeType.Port("exec", PortKind.FLOW)),
            List.of("device", "key"), List.of("", ""));

    /** 打字机按下中：该键正被按住且还没到长按阈值（0.2 秒），产出布尔。 */
    public static final NodeType TYPEWRITER_KEY_DOWN = add("typewriter_key_down", NodeCategory.AERO,
            List.of(), List.of(new NodeType.Port("value", PortKind.BOOL)),
            List.of("device", "key"), List.of("", ""));

    /** 打字机长按中：该键正被按住且已超过长按阈值（0.2 秒），产出布尔。 */
    public static final NodeType TYPEWRITER_KEY_HELD = add("typewriter_key_held", NodeCategory.AERO,
            List.of(), List.of(new NodeType.Port("value", PortKind.BOOL)),
            List.of("device", "key"), List.of("", ""));

    /**
     * 控制齿轮箱：让指定齿轮箱按方向与转速输出。
     * 模式决定这次转动怎么停：持续（一直转到被停止）/ 定角（角度）/ 点动（按住才转）/ 旋转至（绝对朝向）。
     */
    public static final NodeType GEARBOX_CONTROL = add("gearbox_control", NodeCategory.AERO,
            List.of(new NodeType.Port("exec", PortKind.FLOW),
                    new NodeType.Port("speed", PortKind.NUMBER),
                    new NodeType.Port("value", PortKind.NUMBER)),
            List.of(new NodeType.Port("exec", PortKind.FLOW)),
            List.of("device", "direction", "speed", "mode", "value"),
            List.of("", "正转", "×1", "定角", "1"));

    /** 停止齿轮箱：立即停掉指定齿轮箱正在进行的旋转（任何模式都能停下）。 */
    public static final NodeType GEARBOX_STOP = add("gearbox_stop", NodeCategory.AERO,
            List.of(new NodeType.Port("exec", PortKind.FLOW)),
            List.of(new NodeType.Port("exec", PortKind.FLOW)),
            List.of("device"), List.of(""));

    /**
     * 设置转速控制器：把智能转速控制器的输出转速与旋转方向设成指定值。
     * <p>
     * 转速填绝对值（RPM），方向用顺逆时针选择器，不需要自己带正负号。
     */
    public static final NodeType SPEED_CONTROLLER_SET = add("speed_controller_set", NodeCategory.AERO,
            List.of(new NodeType.Port("exec", PortKind.FLOW),
                    new NodeType.Port("rpm", PortKind.NUMBER)),
            List.of(new NodeType.Port("exec", PortKind.FLOW)),
            List.of("device", "rpm", "direction"), List.of("", "16", "顺时针"));

    /** 读取转速控制器的输出转速（RPM，取绝对值）。 */
    public static final NodeType SPEED_CONTROLLER_GET = add("speed_controller_get", NodeCategory.AERO,
            List.of(), List.of(new NodeType.Port("value", PortKind.NUMBER)),
            List.of("device"), List.of(""));

    // ---------------------------------------------------------------- 声音

    /** 播放声音：选择游戏内任意声音事件，按音量与传播范围播放。 */
    public static final NodeType PLAY_SOUND = add("play_sound", NodeCategory.SOUND,
            List.of(new NodeType.Port("exec", PortKind.FLOW)),
            List.of(new NodeType.Port("exec", PortKind.FLOW)),
            List.of("device", "sound", "volume", "range"),
            List.of(SOUND_DEVICE_SELF, "minecraft:block.note_block.pling", "1", "12"));

    /** 停止声音：停止本机正在播放的音效。 */
    public static final NodeType STOP_SOUND = add("stop_sound", NodeCategory.SOUND,
            List.of(new NodeType.Port("exec", PortKind.FLOW)),
            List.of(new NodeType.Port("exec", PortKind.FLOW)),
            List.of("device"), List.of(SOUND_DEVICE_SELF));

    /**
     * 默念：不发出任何声音，只在发声设备上方浮现一行小字。
     * <p>
     * 用于"想说话但不想吵到别人"的场景，附近玩家都能看到这行字。
     */
    public static final NodeType MURMUR = add("murmur", NodeCategory.SOUND,
            List.of(new NodeType.Port("exec", PortKind.FLOW), new NodeType.Port("text", PortKind.TEXT)),
            List.of(new NodeType.Port("exec", PortKind.FLOW)),
            List.of("device", "text"), List.of(SOUND_DEVICE_SELF, "你好"));

    /** 播放网络歌曲：从拓展器里的歌曲库挑一首，由指定设备发声。 */
    public static final NodeType MUSIC_PLAY = add("music_play", NodeCategory.SOUND,
            List.of(new NodeType.Port("exec", PortKind.FLOW)),
            List.of(new NodeType.Port("exec", PortKind.FLOW)),
            List.of("device", "song", "volume", "range"),
            List.of(SOUND_DEVICE_SELF, "", "1", "12"));

    /** 播放歌单里的第 N 首：歌单名就是网络音乐模块卡的 UUID。 */
    public static final NodeType MUSIC_PLAY_INDEX = add("music_play_index", NodeCategory.SOUND,
            List.of(new NodeType.Port("exec", PortKind.FLOW),
                    new NodeType.Port("index", PortKind.NUMBER)),
            List.of(new NodeType.Port("exec", PortKind.FLOW)),
            List.of("device", "playlist", "volume", "range"),
            List.of(SOUND_DEVICE_SELF, "", "1", "12"));

    /** 读取：指定设备当前播放歌曲的歌名。 */
    public static final NodeType MUSIC_SONG_NAME = add("music_song_name", NodeCategory.SOUND,
            List.of(), List.of(new NodeType.Port("value", PortKind.TEXT)),
            List.of("device"), List.of(SOUND_DEVICE_SELF));

    /** 读取：指定设备当前播放歌曲的作曲家。 */
    public static final NodeType MUSIC_SONG_ARTIST = add("music_song_artist", NodeCategory.SOUND,
            List.of(), List.of(new NodeType.Port("value", PortKind.TEXT)),
            List.of("device"), List.of(SOUND_DEVICE_SELF));

    /** 读取：指定设备当前播放歌曲的总时长（秒）。 */
    public static final NodeType MUSIC_SONG_TOTAL = add("music_song_total", NodeCategory.SOUND,
            List.of(), List.of(new NodeType.Port("value", PortKind.NUMBER)),
            List.of("device"), List.of(SOUND_DEVICE_SELF));

    /** 读取：指定设备当前播放到的秒数。 */
    public static final NodeType MUSIC_SONG_TIME = add("music_song_time", NodeCategory.SOUND,
            List.of(), List.of(new NodeType.Port("value", PortKind.NUMBER)),
            List.of("device"), List.of(SOUND_DEVICE_SELF));

    /** 读取：指定设备当前这句歌词，可在中文译文与原文之间切换。 */
    public static final NodeType MUSIC_SONG_LYRIC = add("music_song_lyric", NodeCategory.SOUND,
            List.of(), List.of(new NodeType.Port("value", PortKind.TEXT)),
            List.of("device", "lang"), List.of(SOUND_DEVICE_SELF, "中文"));

    /** 纯文本常量。 */
    public static final NodeType CONST_TEXT = add("const_text", NodeCategory.DATA,
            List.of(), List.of(new NodeType.Port("value", PortKind.TEXT)), List.of("value"), List.of("文本"));

    /** 比较：一个数 关系 另一个数，产出布尔。关系用下拉选择。 */
    public static final NodeType COMPARE = add("compare", NodeCategory.DATA,
            List.of(new NodeType.Port("a", PortKind.NUMBER), new NodeType.Port("b", PortKind.NUMBER)),
            List.of(new NodeType.Port("value", PortKind.BOOL)), List.of("op"), List.of(">"));

    /** 随机数：从数列里随机抽取一项，产出数值。 */
    public static final NodeType RANDOM_INT = add("random_int", NodeCategory.DATA,
            List.of(new NodeType.Port("list", PortKind.SEQ)),
            List.of(new NodeType.Port("value", PortKind.NUMBER)),
            List.of("list"), List.of("数列1"));

    /**
     * 计算：一个数 运算符 另一个数，产出数值。两个数都能填或嵌积木，运算符用下拉选择。
     */
    public static final NodeType MATH = add("math", NodeCategory.DATA,
            List.of(new NodeType.Port("a", PortKind.NUMBER), new NodeType.Port("b", PortKind.NUMBER)),
            List.of(new NodeType.Port("value", PortKind.NUMBER)),
            List.of("op"), List.of("+"));

    /** 逻辑：两个布尔口的与/或，产出布尔。两个口都能嵌"比较"等条件积木，从而实现两个条件判定。 */
    public static final NodeType LOGIC = add("logic", NodeCategory.DATA,
            List.of(new NodeType.Port("a", PortKind.BOOL), new NodeType.Port("b", PortKind.BOOL)),
            List.of(new NodeType.Port("value", PortKind.BOOL)), List.of("op"), List.of("and"));

    /** 估读：把数值四舍五入到指定小数位数，产出数值。 */
    public static final NodeType MATH_ROUND = add("math_round", NodeCategory.DATA,
            List.of(new NodeType.Port("value", PortKind.NUMBER),
                    new NodeType.Port("digits", PortKind.NUMBER)),
            List.of(new NodeType.Port("value", PortKind.NUMBER)),
            List.of("value", "digits"), List.of("0", "2"));

    /** 绝对值：数值的绝对值，产出数值。 */
    public static final NodeType MATH_ABS = add("math_abs", NodeCategory.DATA,
            List.of(new NodeType.Port("value", PortKind.NUMBER)),
            List.of(new NodeType.Port("value", PortKind.NUMBER)),
            List.of("value"), List.of("0"));

    /** 相反数：数值取反（正变负、负变正），产出数值。 */
    public static final NodeType MATH_NEGATE = add("math_negate", NodeCategory.DATA,
            List.of(new NodeType.Port("value", PortKind.NUMBER)),
            List.of(new NodeType.Port("value", PortKind.NUMBER)),
            List.of("value"), List.of("0"));

    /** 科学计数法：把数值写成"X乘十的N次方"形式的文本，产出文本。 */
    public static final NodeType MATH_SCIENTIFIC = add("math_scientific", NodeCategory.DATA,
            List.of(new NodeType.Port("value", PortKind.NUMBER)),
            List.of(new NodeType.Port("value", PortKind.TEXT)),
            List.of("value"), List.of("0"));

    // ---------------------------------------------------------------- 数列
    // 数列跟变量一样：积木自带一个名字，直接按名字改那条数列，不需要连线传递。
    // 本机（运行内存）/ 硬盘 / 局域网 / 互联网各一套命名空间，所以每种操作都有四块对应积木。
    // 写入的一项可以是常量，也可以是对变量的引用（那样它会跟着变量变）。

    /** 本机数列（存在电脑运行内存里，关机会清空）：在末尾加入一项。 */
    public static final NodeType SEQ_MEM_ADD = add("seq_mem_add", NodeCategory.DATA,
            List.of(new NodeType.Port("exec", PortKind.FLOW), new NodeType.Port("item", PortKind.NUMBER)),
            List.of(new NodeType.Port("exec", PortKind.FLOW)),
            List.of("name", "item"), List.of("数列1", "0"));

    /** 本机数列：删除指定的数值。 */
    public static final NodeType SEQ_MEM_REMOVE = add("seq_mem_remove", NodeCategory.DATA,
            List.of(new NodeType.Port("exec", PortKind.FLOW), new NodeType.Port("value", PortKind.NUMBER)),
            List.of(new NodeType.Port("exec", PortKind.FLOW)),
            List.of("name", "value"), List.of("数列1", "0"));

    /** 本机数列：清空全部项。 */
    public static final NodeType SEQ_MEM_CLEAR = add("seq_mem_clear", NodeCategory.DATA,
            List.of(new NodeType.Port("exec", PortKind.FLOW)),
            List.of(new NodeType.Port("exec", PortKind.FLOW)),
            List.of("name"), List.of("数列1"));

    /** 本机数列：项数。 */
    public static final NodeType SEQ_MEM_COUNT = add("seq_mem_count", NodeCategory.DATA,
            List.of(), List.of(new NodeType.Port("value", PortKind.NUMBER)),
            List.of("name"), List.of("数列1"));

    /** 本机数列：总和。 */
    public static final NodeType SEQ_MEM_SUM = add("seq_mem_sum", NodeCategory.DATA,
            List.of(), List.of(new NodeType.Port("value", PortKind.NUMBER)),
            List.of("name"), List.of("数列1"));

    /** 本机数列：平均值。 */
    public static final NodeType SEQ_MEM_AVERAGE = add("seq_mem_average", NodeCategory.DATA,
            List.of(), List.of(new NodeType.Port("value", PortKind.NUMBER)),
            List.of("name"), List.of("数列1"));

    /** 本机数列：整条数列的文本形式。 */
    public static final NodeType SEQ_MEM_TEXT = add("seq_mem_text", NodeCategory.DATA,
            List.of(), List.of(new NodeType.Port("value", PortKind.SEQ)),
            List.of("name"), List.of("数列1"));

    /** 硬盘数列：在末尾加入一项。 */
    public static final NodeType SEQ_DRIVE_ADD = add("seq_drive_add", NodeCategory.DATA,
            List.of(new NodeType.Port("exec", PortKind.FLOW), new NodeType.Port("item", PortKind.NUMBER)),
            List.of(new NodeType.Port("exec", PortKind.FLOW)),
            List.of("name", "item"), List.of("数列1", "0"));

    /** 硬盘数列：删除指定的数值。 */
    public static final NodeType SEQ_DRIVE_REMOVE = add("seq_drive_remove", NodeCategory.DATA,
            List.of(new NodeType.Port("exec", PortKind.FLOW), new NodeType.Port("value", PortKind.NUMBER)),
            List.of(new NodeType.Port("exec", PortKind.FLOW)),
            List.of("name", "value"), List.of("数列1", "0"));

    /** 硬盘数列：清空全部项。 */
    public static final NodeType SEQ_DRIVE_CLEAR = add("seq_drive_clear", NodeCategory.DATA,
            List.of(new NodeType.Port("exec", PortKind.FLOW)),
            List.of(new NodeType.Port("exec", PortKind.FLOW)),
            List.of("name"), List.of("数列1"));

    /** 硬盘数列：项数。 */
    public static final NodeType SEQ_DRIVE_COUNT = add("seq_drive_count", NodeCategory.DATA,
            List.of(), List.of(new NodeType.Port("value", PortKind.NUMBER)),
            List.of("name"), List.of("数列1"));

    /** 硬盘数列：所有项的总和。 */
    public static final NodeType SEQ_DRIVE_SUM = add("seq_drive_sum", NodeCategory.DATA,
            List.of(), List.of(new NodeType.Port("value", PortKind.NUMBER)),
            List.of("name"), List.of("数列1"));

    /** 硬盘数列：平均值。 */
    public static final NodeType SEQ_DRIVE_AVERAGE = add("seq_drive_average", NodeCategory.DATA,
            List.of(), List.of(new NodeType.Port("value", PortKind.NUMBER)),
            List.of("name"), List.of("数列1"));

    /** 硬盘数列：整条数列的文本形式。 */
    public static final NodeType SEQ_DRIVE_TEXT = add("seq_drive_text", NodeCategory.DATA,
            List.of(), List.of(new NodeType.Port("value", PortKind.SEQ)),
            List.of("name"), List.of("数列1"));

    /** 局域网数列：在末尾加入一项。 */
    public static final NodeType SEQ_LAN_ADD = add("seq_lan_add", NodeCategory.LAN,
            List.of(new NodeType.Port("exec", PortKind.FLOW), new NodeType.Port("item", PortKind.NUMBER)),
            List.of(new NodeType.Port("exec", PortKind.FLOW)),
            List.of("name", "network", "item"), List.of("数列1", "", "0"));

    /** 局域网数列：删除指定的数值。 */
    public static final NodeType SEQ_LAN_REMOVE = add("seq_lan_remove", NodeCategory.LAN,
            List.of(new NodeType.Port("exec", PortKind.FLOW), new NodeType.Port("value", PortKind.NUMBER)),
            List.of(new NodeType.Port("exec", PortKind.FLOW)),
            List.of("name", "network", "value"), List.of("数列1", "", "0"));

    /** 局域网数列：清空全部项。 */
    public static final NodeType SEQ_LAN_CLEAR = add("seq_lan_clear", NodeCategory.LAN,
            List.of(new NodeType.Port("exec", PortKind.FLOW)),
            List.of(new NodeType.Port("exec", PortKind.FLOW)),
            List.of("name", "network"), List.of("数列1", ""));

    /** 局域网数列：项数。 */
    public static final NodeType SEQ_LAN_COUNT = add("seq_lan_count", NodeCategory.LAN,
            List.of(), List.of(new NodeType.Port("value", PortKind.NUMBER)),
            List.of("name", "network"), List.of("数列1", ""));

    /** 局域网数列：总和。 */
    public static final NodeType SEQ_LAN_SUM = add("seq_lan_sum", NodeCategory.LAN,
            List.of(), List.of(new NodeType.Port("value", PortKind.NUMBER)),
            List.of("name", "network"), List.of("数列1", ""));

    /** 局域网数列：平均值。 */
    public static final NodeType SEQ_LAN_AVERAGE = add("seq_lan_average", NodeCategory.LAN,
            List.of(), List.of(new NodeType.Port("value", PortKind.NUMBER)),
            List.of("name", "network"), List.of("数列1", ""));

    /** 局域网数列：整条数列的文本形式。 */
    public static final NodeType SEQ_LAN_TEXT = add("seq_lan_text", NodeCategory.LAN,
            List.of(), List.of(new NodeType.Port("value", PortKind.SEQ)),
            List.of("name", "network"), List.of("数列1", ""));

    /** 互联网数列：在末尾加入一项。 */
    public static final NodeType SEQ_NET_ADD = add("seq_net_add", NodeCategory.INTERNET,
            List.of(new NodeType.Port("exec", PortKind.FLOW), new NodeType.Port("item", PortKind.NUMBER)),
            List.of(new NodeType.Port("exec", PortKind.FLOW)),
            List.of("name", "item"), List.of("数列1", "0"));

    /** 互联网数列：删除指定的数值。 */
    public static final NodeType SEQ_NET_REMOVE = add("seq_net_remove", NodeCategory.INTERNET,
            List.of(new NodeType.Port("exec", PortKind.FLOW), new NodeType.Port("value", PortKind.NUMBER)),
            List.of(new NodeType.Port("exec", PortKind.FLOW)),
            List.of("name", "value"), List.of("数列1", "0"));

    /** 互联网数列：清空全部项。 */
    public static final NodeType SEQ_NET_CLEAR = add("seq_net_clear", NodeCategory.INTERNET,
            List.of(new NodeType.Port("exec", PortKind.FLOW)),
            List.of(new NodeType.Port("exec", PortKind.FLOW)),
            List.of("name"), List.of("数列1"));

    /** 互联网数列：项数。 */
    public static final NodeType SEQ_NET_COUNT = add("seq_net_count", NodeCategory.INTERNET,
            List.of(), List.of(new NodeType.Port("value", PortKind.NUMBER)),
            List.of("name"), List.of("数列1"));

    /** 互联网数列：总和。 */
    public static final NodeType SEQ_NET_SUM = add("seq_net_sum", NodeCategory.INTERNET,
            List.of(), List.of(new NodeType.Port("value", PortKind.NUMBER)),
            List.of("name"), List.of("数列1"));

    /** 互联网数列：平均值。 */
    public static final NodeType SEQ_NET_AVERAGE = add("seq_net_average", NodeCategory.INTERNET,
            List.of(), List.of(new NodeType.Port("value", PortKind.NUMBER)),
            List.of("name"), List.of("数列1"));

    /** 互联网数列：整条数列的文本形式。 */
    public static final NodeType SEQ_NET_TEXT = add("seq_net_text", NodeCategory.INTERNET,
            List.of(), List.of(new NodeType.Port("value", PortKind.SEQ)),
            List.of("name"), List.of("数列1"));

    // ---------------------------------------------------------------- 固定角度

    /**
     * 角度常量：数字框 + °。数字可直接填，也可插入数值积木，产出角度类型。
     * 这是把普通数字接进角度体系的唯一入口。
     * <p>
     * 输入口叫 {@code amount}、输出口叫 {@code value}，刻意错开避免同名：
     * 同名会让布局与连线的解析互相串。
     */
    public static final NodeType ANGLE_VALUE = add("angle_value", NodeCategory.DATA,
            List.of(new NodeType.Port("amount", PortKind.ANY)),
            List.of(new NodeType.Port("value", PortKind.ANGLE)), List.of("amount"), List.of("0"));

    /** 齿轮箱角度：输出指定齿轮箱相对标定零点的当前朝向（-180~180）。 */
    public static final NodeType GEARBOX_ANGLE = add("gearbox_angle", NodeCategory.AERO,
            List.of(), List.of(new NodeType.Port("value", PortKind.ANGLE)),
            List.of("device"), List.of(""));

    /** 角度运算：两个角度做四则运算，结果按圆周归一。两个口只接受角度积木。 */
    public static final NodeType ANGLE_MATH = add("angle_math", NodeCategory.DATA,
            List.of(new NodeType.Port("a", PortKind.ANGLE), new NodeType.Port("b", PortKind.ANGLE)),
            List.of(new NodeType.Port("value", PortKind.ANGLE)),
            List.of("op"), List.of("+"));

    /** 角度比较：两个角度比大小，产出条件。等于类比较带容差。 */
    public static final NodeType ANGLE_COMPARE = add("angle_compare", NodeCategory.DATA,
            List.of(new NodeType.Port("a", PortKind.ANGLE), new NodeType.Port("b", PortKind.ANGLE)),
            List.of(new NodeType.Port("value", PortKind.BOOL)), List.of("op"), List.of("="));

    /** 拼接文本：把两个文本口接在一起；extra 记录追加的文本口数量（t1、t2…）。 */
    public static final NodeType TEXT_JOIN = add("text_join", NodeCategory.DATA,
            List.of(new NodeType.Port("a", PortKind.TEXT), new NodeType.Port("b", PortKind.TEXT)),
            List.of(new NodeType.Port("value", PortKind.TEXT)), List.of("extra"), List.of("0"));

    /**
     * 书写时间：一个数值 + 单位，产出时间点。
     * 单位可选游戏日 / 游戏时 / 游戏分 / 现实年 / 现实日 / 现实时 / 现实分 / 现实秒。
     */
    public static final NodeType TIME_WRITE = add("time_write", NodeCategory.DATA,
            List.of(new NodeType.Port("amount", PortKind.NUMBER)),
            List.of(new NodeType.Port("value", PortKind.TIME)),
            List.of("amount", "unit"), List.of("0", "游戏日"));

    /** 当前时间：按单位输出当前的游戏时间或现实时间。 */
    public static final NodeType TIME_CURRENT = add("time_current", NodeCategory.DATA,
            List.of(), List.of(new NodeType.Port("value", PortKind.TIME)),
            List.of("unit"), List.of("游戏日"));

    /** 时间运算：两个时间点做加减，结果为时间差；结果再作为时间参与运算。 */
    public static final NodeType TIME_MATH = add("time_math", NodeCategory.DATA,
            List.of(new NodeType.Port("a", PortKind.TIME), new NodeType.Port("b", PortKind.TIME)),
            List.of(new NodeType.Port("value", PortKind.TIME)),
            List.of("op"), List.of("+"));

    /** 时间比较：两个时间点比先后，产出条件。 */
    public static final NodeType TIME_COMPARE = add("time_compare", NodeCategory.DATA,
            List.of(new NodeType.Port("a", PortKind.TIME), new NodeType.Port("b", PortKind.TIME)),
            List.of(new NodeType.Port("value", PortKind.BOOL)), List.of("op"), List.of("="));

    /**
     * 发送内容到聊天框：面向全体玩家。只接一个文本口，发信目标固定为全体玩家。
     */
    public static final NodeType CHAT_SEND = add("chat_send", NodeCategory.INTERNET,
            List.of(new NodeType.Port("exec", PortKind.FLOW), new NodeType.Port("text", PortKind.TEXT)),
            List.of(new NodeType.Port("exec", PortKind.FLOW)), List.of(), List.of());

    /**
     * 私聊：以原版 /msg 的收件提示发送给指定玩家。
     * 目标填 @玩家名，留空则显示默认玩家，未选到玩家时不发送。
     */
    public static final NodeType CHAT_WHISPER = add("chat_whisper", NodeCategory.INTERNET,
            List.of(new NodeType.Port("exec", PortKind.FLOW), new NodeType.Port("text", PortKind.TEXT)),
            List.of(new NodeType.Port("exec", PortKind.FLOW)),
            List.of("target"), List.of(""));

    public static final NodeType WIRELESS_DISPLAY = add("wireless_display", NodeCategory.DISPLAY,
            List.of(new NodeType.Port("exec", PortKind.FLOW), new NodeType.Port("text", PortKind.TEXT)),
            List.of(new NodeType.Port("exec", PortKind.FLOW)),
            List.of("device", "line"), List.of("", "1"));

    /**
     * 信息获取：读取信息转发器第 N 行（显示链接器写入的行）的内容。
     * 输出 ANY：纯数字自动成为可运算的数值，其它内容原样作文本。
     */
    public static final NodeType INFO_RECEIVE = add("info_receive", NodeCategory.DISPLAY,
            List.of(), List.of(new NodeType.Port("value", PortKind.ANY)),
            List.of("device", "line"), List.of("", "1"));

    // ---------------------------------------------------------------- 仓储链接模块

    public static final NodeType WAREHOUSE_FREE_SPACE = add("warehouse_free_space", NodeCategory.REDSTONE,
            List.of(), List.of(new NodeType.Port("value", PortKind.NUMBER)), List.of(), List.of());

    public static final NodeType WAREHOUSE_CAPACITY = add("warehouse_capacity", NodeCategory.REDSTONE,
            List.of(), List.of(new NodeType.Port("value", PortKind.NUMBER)), List.of(), List.of());

    public static final NodeType WAREHOUSE_ITEM_COUNT = add("warehouse_item_count", NodeCategory.REDSTONE,
            List.of(), List.of(new NodeType.Port("value", PortKind.NUMBER)),
            List.of("item"), List.of("minecraft:iron_ingot"));

    public static final NodeType PACKAGER_SEND = add("packager_send", NodeCategory.REDSTONE,
            List.of(new NodeType.Port("exec", PortKind.FLOW),
                    new NodeType.Port("count", PortKind.NUMBER),
                    new NodeType.Port("address", PortKind.TEXT)),
            List.of(new NodeType.Port("exec", PortKind.FLOW)),
            List.of("item", "count", "address"), List.of("minecraft:iron_ingot", "1", ""));

    // ---------------------------------------------------------------- 无线红石模块

    public static final NodeType REDSTONE_RECEIVE = add("redstone_receive", NodeCategory.EVENT,
            List.of(), List.of(new NodeType.Port("exec", PortKind.FLOW)),
            List.of("channel", "channel2"), List.of("minecraft:redstone", "minecraft:redstone"));

    public static final NodeType REDSTONE_RECEIVE_COMPARE = add("redstone_receive_compare", NodeCategory.EVENT,
            List.of(new NodeType.Port("strength", PortKind.NUMBER)),
            List.of(new NodeType.Port("exec", PortKind.FLOW)),
            List.of("channel", "channel2", "op", "strength"),
            List.of("minecraft:redstone", "minecraft:redstone", ">=", "1"));

    public static final NodeType REDSTONE_SEND = add("redstone_send", NodeCategory.REDSTONE,
            List.of(new NodeType.Port("exec", PortKind.FLOW),
                    new NodeType.Port("phase", PortKind.NUMBER),
                    new NodeType.Port("gap", PortKind.NUMBER),
                    new NodeType.Port("lit", PortKind.NUMBER),
                    new NodeType.Port("strength", PortKind.NUMBER)),
            List.of(new NodeType.Port("exec", PortKind.FLOW)),
            List.of("channel", "channel2", "mode", "phase", "gap", "lit", "strength"),
            List.of("minecraft:redstone", "minecraft:redstone", "blink", "100", "3", "1", "15"));

    // ---------------------------------------------------------------- 局域网模块

    /** 当收到局域网广播时：事件帽子块，收到指定频道的局域网广播就执行一次。 */
    public static final NodeType LAN_RECEIVE = add("lan_receive", NodeCategory.EVENT,
            List.of(), List.of(new NodeType.Port("exec", PortKind.FLOW)),
            List.of("channel", "network"), List.of("广播1", ""));

    /** 条件收到局域网广播：本刻收到了指定频道的局域网广播，产出布尔。 */
    public static final NodeType LAN_RECEIVED = add("lan_received", NodeCategory.LAN,
            List.of(), List.of(new NodeType.Port("value", PortKind.BOOL)),
            List.of("channel", "network"), List.of("广播1", ""));

    /** 读取局域网广播变量：输出全局域网共享变量的当前值。 */
    public static final NodeType LAN_VAR_GET = add("lan_var_get", NodeCategory.LAN,
            List.of(), List.of(new NodeType.Port("value", PortKind.ANY)),
            List.of("name", "network"), List.of("广播变量1", ""));

    /** 设置局域网广播变量：写入全局域网共享变量。 */
    public static final NodeType LAN_VAR_SET = add("lan_var_set", NodeCategory.LAN,
            List.of(new NodeType.Port("exec", PortKind.FLOW), new NodeType.Port("value", PortKind.ANY)),
            List.of(new NodeType.Port("exec", PortKind.FLOW)),
            List.of("name", "network"), List.of("广播变量1", ""));

    /** 发送局域网广播：向所在局域网的指定频道发一次广播。 */
    public static final NodeType LAN_SEND = add("lan_send", NodeCategory.LAN,
            List.of(new NodeType.Port("exec", PortKind.FLOW)),
            List.of(new NodeType.Port("exec", PortKind.FLOW)),
            List.of("channel", "network"), List.of("广播1", ""));

    // ---------------------------------------------------------------- 互联网模块

    /** 当收到互联网广播时：事件帽子块，收到指定频道的互联网广播就执行一次。 */
    public static final NodeType NET_RECEIVE = add("net_receive", NodeCategory.EVENT,
            List.of(), List.of(new NodeType.Port("exec", PortKind.FLOW)),
            List.of("channel"), List.of("广播1"));

    /** 条件收到互联网广播：本刻收到了指定频道的互联网广播，产出布尔。 */
    public static final NodeType NET_RECEIVED = add("net_received", NodeCategory.INTERNET,
            List.of(), List.of(new NodeType.Port("value", PortKind.BOOL)),
            List.of("channel"), List.of("广播1"));

    /** 读取互联网广播变量：输出全网共享变量的当前值。 */
    public static final NodeType NET_VAR_GET = add("net_var_get", NodeCategory.INTERNET,
            List.of(), List.of(new NodeType.Port("value", PortKind.ANY)),
            List.of("name"), List.of("广播变量1"));

    /** 设置互联网广播变量：写入全网共享变量。 */
    public static final NodeType NET_VAR_SET = add("net_var_set", NodeCategory.INTERNET,
            List.of(new NodeType.Port("exec", PortKind.FLOW), new NodeType.Port("value", PortKind.ANY)),
            List.of(new NodeType.Port("exec", PortKind.FLOW)),
            List.of("name"), List.of("广播变量1"));

    /** 发送互联网广播：向互联网的指定频道发一次广播。 */
    public static final NodeType NET_SEND = add("net_send", NodeCategory.INTERNET,
            List.of(new NodeType.Port("exec", PortKind.FLOW)),
            List.of(new NodeType.Port("exec", PortKind.FLOW)),
            List.of("channel"), List.of("广播1"));

    /** 条件积木：本刻聊天框出现任意文字时成立，不限制关键词或发送者。 */
    public static final NodeType CHAT_ANY = add("chat_any", NodeCategory.INTERNET,
            List.of(), List.of(new NodeType.Port("value", PortKind.BOOL)), List.of(), List.of());

    /**
     * 开始监听：本刻听到触发词就进入监听状态，并在设定时长内保持。
     * 不限于语音或某个具体来源，任何进到聊天框的文字都能当触发词。
     * 处于监听状态时才响应后续指令；超过时长自动退出监听。
     */
    public static final NodeType CHAT_LISTEN = add("chat_listen", NodeCategory.INTERNET,
            List.of(new NodeType.Port("exec", PortKind.FLOW), new NodeType.Port("duration", PortKind.NUMBER)),
            List.of(new NodeType.Port("exec", PortKind.FLOW)),
            List.of("trigger", "duration"), List.of("小爱", "200"));

    /** 条件积木：当前是否处于监听状态（触发词出现过且未超时）。 */
    public static final NodeType CHAT_LISTENING = add("chat_listening", NodeCategory.INTERNET,
            List.of(), List.of(new NodeType.Port("value", PortKind.BOOL)), List.of(), List.of());

    /**
     * 识别聊天框内容：在"当…时"的条件口里使用，本刻有来自指定目标且包含关键词的聊天就成立。
     * 关键词留空表示只要有人说话就成立。
     */
    public static final NodeType CHAT_RECOGNIZE = add("chat_recognize", NodeCategory.INTERNET,
            List.of(), List.of(new NodeType.Port("value", PortKind.BOOL)),
            List.of("keyword", "target"), List.of("", "@a"));

    private NodeTypes() {
    }

    private static NodeType add(String id, NodeCategory category, List<NodeType.Port> in,
                                List<NodeType.Port> out, List<String> params, List<String> defaults) {
        NodeType type = new NodeType(id, category, "cbtc.node." + id, List.copyOf(in), List.copyOf(out),
                List.copyOf(params), List.copyOf(defaults));
        ALL.add(type);
        return type;
    }

    public static List<NodeType> all() {
        return ALL;
    }

    public static Optional<NodeType> byId(String id) {
        for (NodeType t : ALL) {
            if (t.id().equals(id)) {
                return Optional.of(t);
            }
        }
        return Optional.empty();
    }
}
