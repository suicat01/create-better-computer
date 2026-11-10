package com.mcl.cbtc.core.vm;

import com.mcl.cbtc.core.Value;

/**
 * 虚拟机对外部世界的接口。核心执行器只依赖这个抽象，因此与机械动力/航空学解耦。
 */
public interface MachineHost {
    /** 写红石链接频道强度（0-15）。 */
    void setLinkStrength(int strength);

    /** 向频道广播一个值。 */
    void broadcast(String channel, Value value);

    /** 在聊天框输出一行，前缀是计算机名字。 */
    void chat(String text);

    /** 当前游戏内总天数（从世界创建算起）。 */
    long gameTotalDays();

    /** 当前游戏日内的时间，0 表示清晨 6 点，24000 为一整天。 */
    long gameTicksOfDay();

    /** 把文本送到显示链接输出行。 */
    int displayLine();

    void setDisplayText(int line, String text);

    /** 本机可读的状态，例如航空学姿态。 */
    Value query(String key);

    /**
     * 播放游戏内音效。
     *
     * @param soundId 声音事件 id
     * @param volume  音量
     * @param range   可听范围（格）
     * @param device  发声设备：NodeTypes.SOUND_DEVICE_SELF 表示本机，其余为外设 id
     */
    void playSound(String soundId, double volume, int range, String device);

    /** 本机当前允许的最大可听范围（格）：默认 12，装入扬声器升级模块后提升到 50。 */
    int maxSoundRange();

    /**
     * 指定设备允许的最大可听范围（格）。
     * <p>
     * 电脑看自己的扬声器模块，音响看它自己的升级槽，两者互不影响。
     */
    int maxSoundRange(String device);

    /** 默念：不发声，只在发声设备上方浮现一行小字。 */
    void murmur(String text, String device);

    /** 停止指定设备正在播放的声音；device 为本机或外设 id。 */
    void stopSounds(String device);

    int warehouseFreeSpace();

    int warehouseCapacity();

    int warehouseItemCount(String itemId);

    boolean sendPackage(String itemId, int count, String address);

    boolean writeWirelessDisplay(String id, int line, String text);

    /** 切换到指定双物品频率并发送信号。mode 为 steady（常亮）/ blink（高频闪烁）。 */
    void sendWirelessRedstone(String first, String second, String mode, int phase, int gap, int lit, int strength);

    /**
     * 读取导航台的坐标或距离。key 取 x / z / target_x / target_z / distance。
     * 设备不存在、不是导航台或不在线时返回 0。
     */
    double readNavTable(String deviceId, String key);

    /**
     * 读取目标定位模块的坐标。axis 取 x / z。
     * 设备不存在、不是定位模块或模块不在导航台里时返回 0。
     */
    double readTargetLocator(String deviceId, String axis);

    /** 改写目标定位模块某一轴的坐标（axis 取 x / z）；模块不在导航台里时什么也不做。 */
    void writeTargetLocator(String deviceId, String axis, double value);

    /**
     * 读取信息转发器第 N 行的内容。
     *
     * @param lineParam 行号（从 1 起），由积木里的数字输入框写入
     */
    Value infoLine(String deviceId, String lineParam);

    /**
     * 基线方位角：A（前）是导航台，其目标点是"目标"；B（后）是辅助定位外设。
     * 返回目标相对 A→B 连线的夹角（前方=0°，左=+90°，右=-90°）。
     */
    double navBaselineBearing(String deviceA, String deviceB);

    /**
     * 绝对目标角：把指定齿轮箱转到这个角度，A（导航台）就正对目标。
     * 返回相对齿轮箱零点的绝对角，可直接接「旋转至」；取不到数据时返回 NaN。
     */
    double navBaselineAbsoluteTarget(String deviceA, String deviceB, String gearboxDevice);

    /** 指定打字机当前按下的 GLFW 键码。 */
    java.util.List<Integer> pressedKeys(String deviceId);

    /**
     * 控制指定齿轮箱的输出转速。
     *
     * @param speed  目标转速（RPM），正负表示方向
     * @param mode   steady（持续）/ timed（定时）/ angle（定角）/ jog（点动）
     * @param value  定时模式为秒数，定角模式为角度，其余忽略
     */
    void writeGearbox(String deviceId, float speed, String mode, double value);

    /** 立即停掉指定齿轮箱正在进行的旋转（任何模式都能被打断）；设备不存在时什么也不做。 */
    void stopGearbox(String deviceId);

    /** 指定齿轮箱相对零点的当前角度（-180~180）；设备不存在时返回 0。 */
    double gearboxAngle(String deviceId);

    /** 设置智能转速控制器的目标转速（RPM，正负表示方向）；设备不存在时什么也不做。 */
    void setSpeedController(String deviceId, float rpm);

    /** 读取智能转速控制器的输出转速（RPM，带符号）；设备不存在时返回 0。 */
    double speedControllerSpeed(String deviceId);

    /** 读取硬盘里保存的变量；没装硬盘或没存过该变量时返回空值。 */
    Value driveVariable(String name);

    /** 把值写进硬盘的指定变量；没装硬盘时什么也不做。 */
    void setDriveVariable(String name, Value value);

    /**
     * 点动续期：本刻仍在执行这条点动积木。
     * 齿轮箱收到后刷新时间戳，一旦几个刻内没有续期就自行停转。
     */
    void keepJogging(int nodeId);

    /** 向指定局域网发送一次广播；network 为空或未加入该网时什么也不做。 */
    void sendLanBroadcast(String network, String channel);

    /** 读取指定局域网的共享广播变量；network 为空或未加入该网时返回空值。 */
    Value lanVariable(String network, String name);

    /** 写入指定局域网的共享广播变量；network 为空或未加入该网时什么也不做。 */
    void setLanVariable(String network, String name, Value value);

    /** 向互联网发送一次广播；未装互联网模块时什么也不做。 */
    void sendInternetBroadcast(String channel);

    /** 读取互联网共享广播变量；未装互联网模块时返回空值。 */
    Value internetVariable(String name);

    /** 写入互联网共享广播变量；未装互联网模块时什么也不做。 */
    void setInternetVariable(String name, Value value);

    /**
     * 按作用域读取一个变量，供数列里的"变量引用项"取值。
     *
     * @param scope   drive / lan / net
     * @param network 局域网 ID；其它作用域忽略
     */
    Value scopedVariable(String scope, String network, String name);

    /** 把文本发到聊天框；target 是积木里填的发信目标。 */
    void sendChat(String text, String target);

    /**
     * 播放网络歌曲。
     *
     * @param song    歌曲名称或歌单采样标识
     * @param volume  音量
     * @param range   可听范围（格）
     * @param device  发声设备
     * @return 是否找到了这首歌
     */
    boolean playMusic(String song, double volume, int range, String device);

    /** 拓展器里所有音乐模块卡组成的歌单（值为 0 时为全部模块的第一首）。 */
    boolean playMusicFromPlaylist(String playlist, int index, double volume, int range, String device);

    /**
     * 指定设备当前正在播放的歌曲信息。
     * <p>
     * key 取 song（歌名）/ artist（作曲家）/ total（总时长秒）/ time（已播放秒数）/ lyric（当前歌词行）/
     * lyric_original（原文歌词）。设备没有在播歌时返回空文本或 0。
     */
    Value musicState(String device, String key);
}
