# 更新日志

## 1.1.3

> 正式版：新增瓦列里中士、大力神引擎与相关合成，并补齐智能炮座系列与动力臂的联动。

### 新增

- **瓦列里中士**：可编程火炮装填动力臂，外观、传动与动画一比一复刻机械动力动力臂。
  - 右键打开五行指令界面，每行可设为「填充」或「留空」，留空即终止序列；
  - 填充物品种类自动读取 CBC 的弹药与发射药标签（含发射药包、药筒）；
  - 沿用动力臂的交互点绑定：手持中士右键火炮底座选点，放下方块即绑定，蹲下 + 扳手可重绑；
  - 从中士正下方容器取弹，只对装有速射炮闩的火炮装填；
  - 支持连续装填多段发射药，上限为炮管物理容量；
  - 炮闩开合动画与音效随手臂动作同步，弹药在关闩的一瞬间进膛；
  - 可同时绑定多门火炮，遵循动力臂的分配方法：优先第一个 / 轮流 / 强制轮流；
  - 应力影响为 256x RPM，物品提示、护目镜、思索动画与 7 种语言全部就绪。
- **大力神引擎**：序列组装新产物。工业铁块 → 辊压 → 安装大齿轮 → 安装小齿轮 → 安装蒸汽引擎 → 安装超级烈焰棒 → 安装下界合金锭 → 安装辊压机，重复 9 次；产出概率 60% 大力神引擎、20% 工业铁块、20% 下界合金块。
- **瓦列里中士工作台配方**：有序合成，安山合金 ×3 + 下界合金锭 ×1 + 大力神引擎 ×1 + 工业铁块 ×1。

### 兼容与联动

- 智能炮座、智能固定炮座现在可以被原版动力臂与瓦列里中士识别为装填目标。
- 智能炮座系列支持漏斗、管道等物品传输方式输入弹药。

---

## 1.1.3 (English)

> Release: adds Sergeant Valery, the Hercules Engine, their recipes, and full Mechanical Arm integration for the smart cannon mounts.

### Added

- **Sergeant Valery**: a programmable cannon-loading arm that faithfully recreates the Create Mechanical Arm in appearance, kinetics and animation.
  - Right-click to open a five-row program UI: each row can be set to "Fill" or "Blank"; a blank row terminates the sequence;
  - Filler items are read directly from CBC's projectile and propellant tags (including powder charges and cartridges);
  - Uses the Mechanical Arm's interaction-point binding: right-click a cannon mount while holding it to select the point, then place the block to bind; crouch + wrench to rebind;
  - Pulls ammo from the container directly beneath it, and only loads cannons fitted with a quick-firing breech;
  - Supports loading multiple propellant charges up to the physical capacity of the barrel;
  - Breech open/close animation and sounds are synced with the arm's motion; ammunition enters at the instant the breech closes;
  - Multiple cannon mounts can be bound at once and are served following Create's distribution modes: Prefer First / Round Robin / Forced Round Robin;
  - Stress impact is 256x RPM, with item tooltip, goggles overlay, ponder scene and 7 languages included.
- **Hercules Engine**: a new Sequenced Assembly product. Industrial Iron Block → Pressing → Deploy Large Cogwheel → Deploy Cogwheel → Deploy Steam Engine → Deploy Super Blaze Rod → Deploy Netherite Ingot → Deploy Mechanical Press, looped 9 times; results are 60% Hercules Engine, 20% Industrial Iron Block and 20% Netherite Block.
- **Sergeant Valery crafting recipe**: shaped crafting with 3× Andesite Alloy, 1× Netherite Ingot, 1× Hercules Engine and 1× Industrial Iron Block.

### Compatibility & Integration

- Smart Cannon Mount and Smart Fixed Cannon Mount are now recognized as loading targets by both the vanilla Mechanical Arm and Sergeant Valery.
- Smart Cannon Mounts now accept ammo from hoppers and other item transport.

---

## 1.1.2.2-beta

> 测试版（Beta）：建议先在备份存档中验证后再用于正式存档。
> 改进：瞄准积木改成「瞄准目标」凹槽、新增无线红石 / 线缆开关、C 型积木恢复每刻重判。

### 新增

- **瞄准目标凹槽**：「火控单位瞄准」不再自带准星开关，改为一个「瞄准目标」端口，可嵌入三种瞄准目标积木：
  - **准星处**：取瞄准组件佩戴者的准星位置，隔玻璃、隔方块都不再丢目标，第三人称同样跟随；
  - **水平垂直角度**：直接写角度（水平正南 0°、向西为正，±180°；垂直抬头为正，±90°），两槽可填可嵌，空槽按 0°；
  - **准星偏移角度**：在准星位置基础上叠加水平 / 垂直偏移，适合做提前量或固定偏置。
- **无线红石开关 / 线缆开关**：把一组频道或一个线缆序列号**持续**置为开 / 关，执行一次后由电脑每刻保持，直到同一条积木关掉或关机；与一次性的「发送频道信号 / 发送线缆信号」区分开。
- **频道选择器搜索框**：红石频道选择器顶部可输入物品 ID 或名称过滤；装了 Just Enough Characters 时支持拼音（`hsf`、`hongshifen` 都能搜到「红石粉」）。

### 修复

- **隔墙无法瞄准**：旧逻辑会对方块做射线检测，隔着玻璃或方块时拿不到准星点；现在直接取准星位置，不再受方块阻挡。
- **自己发的无线红石收不到**：Create 6 的 `updateNetworkOf` 会跳过发信方自身，本机订阅的接收端因此收不到自己发的信号；现在发信后由电脑自己补一次接收。
- **发送顶掉接收频道**：发信与接收共用频道时互相覆盖；现在按每个频道（红蓝频率对）分别记录强度，发信不再取消接收订阅。
- **事件积木开 / 关各触发一次**：「当收到频道信号」原本信号熄灭也会触发；现在没有强度条件的事件只响应有信号的上升沿，带强度条件的仍可判断 `强度 <= 0` 检测断开。
- **C 型积木只跑一次**：「如果…那么 / 如果…否则」接在入口后跑完就结束；现在没接 `done` 时会在原地每刻重判，分支跑完也回到条件积木，可以反复使用。
- **旧存档积木变成未知类型**：旧版 `signal_switch` 载入时自动拆成「无线红石开关 / 线缆开关」并搬移连线。
- **FTB 侧栏按钮压在编程界面上**：装了 FTB 任务 / 地图 / 队伍时，把编程界面加入 FTB Library 侧栏黑名单。
- **材料区重名**：「如果…否则」「当收到广播」「收到广播（条件）」与其他积木重名，已改成互不相同的名字。

### 措辞统一

- 全部「频率」改为「频道」，角度轴统一为「垂直」；无线红石仍严格遵循 #1 红 / #2 蓝。

### 兼容性

- 旧存档的瞄准积木自动迁移成「火控单位瞄准 + 准星处」，旧 `signal_switch` 自动拆分。
- 参数与存档结构未变，可直接覆盖升级。

---

## 1.1.2.2-beta (English)

> Beta release: please validate in a backed-up world before using it on a main save.
> Changes: aiming is now driven by an "aim target" socket, wireless redstone / cable switch blocks were added, and C-shaped blocks re-evaluate every tick again.

### Added

- **Aim target socket**: "Fire control aim" no longer carries its own crosshair toggle. It exposes a single "aim target" port that accepts three target blocks:
  - **Crosshair**: uses the sighting component wearer's crosshair position; it no longer loses the target through glass or blocks and works in third person as well;
  - **Yaw / pitch angles**: write angles directly (yaw follows Minecraft, south = 0°, west positive, ±180°; pitch positive up, ±90°). Both sockets accept typed numbers or blocks, empty means 0°;
  - **Crosshair offset angles**: adds a yaw / pitch offset on top of the crosshair position, handy for lead or a fixed bias.
- **Wireless redstone switch / cable switch**: hold a channel pair or a cable serial **continuously** on or off. Execute once and the computer keeps it until the same block turns it off or the computer shuts down, distinct from the one-shot "send channel signal / send cable signal".
- **Channel picker search**: the wireless channel picker has a search box for item IDs or names, with pinyin support when Just Enough Characters is installed (`hsf` and `hongshifen` both find Redstone Dust).

### Fixed

- **Aiming blocked by walls**: the old path ray-traced blocks and found no crosshair point behind glass or blocks; it now reads the crosshair position directly.
- **Not receiving your own wireless redstone**: Create 6's `updateNetworkOf` skips the sender, so a locally subscribed receiver never saw its own signal; the computer now mirrors the send into its own receive path.
- **Sending cancelling the receive channel**: sending and receiving on the same channel overwrote each other; strengths are now tracked per frequency pair, so sending no longer drops the receive subscription.
- **Event blocks firing on both edges**: "when a channel signal is received" used to fire on signal loss too; blocks without a strength condition now only fire on the rising edge, while strength-conditioned ones can still test `strength <= 0`.
- **C-shaped blocks running only once**: "if / if-else" ended after a single pass; without a `done` connection they now re-evaluate in place every tick, including after a branch finishes, so they can be reused.
- **Legacy blocks becoming unknown types**: old `signal_switch` blocks are converted into "wireless redstone switch / cable switch" on load, with their wires moved over.
- **FTB sidebar buttons covering the programming screen**: the screen is now registered in FTB Library's sidebar blacklist.
- **Duplicate names in the palette**: "if-else", "when broadcast received" and "broadcast received (condition)" no longer share names with other blocks.

### Wording

- All player-facing "frequency" wording is now "channel", and angle axes are consistently "vertical"; wireless redstone still follows the native #1 red / #2 blue rule.

### Compatibility

- Existing aim blocks migrate to "fire control aim + crosshair", and legacy `signal_switch` blocks are split automatically.
- No parameter or save-format changes; drop-in upgrade.

---

## 1.1.2.1-beta

> 测试版（Beta）：建议先在备份存档中验证后再用于正式存档。
> 改进：外设绑定改为识别方块自身的持久身份，物理结构组装 / 拆卸 / 移动后不必重新绑定。

### 改进

- **外设绑定改为方块身份 UUID**：CB编程齿轮箱、智能转速控制器、CB音响、讲解石、CB打字机、信息转发器、结构图解传感器会把自己的持久 UUID 存进方块实体，外设绑定模块卡记的是这个身份而不是坐标。物理结构组装、拆卸、被推着飞走再落地后，绑定始终指向同一台方块；随方块物品拆下再放回也会保留身份
- **丢失的绑定自行修复**：身份索引命中时直接把绑定校正到方块此刻的位置；只有命中失败才回落到坐标去找，找到后先核对候选方块的身份，避免「就近找回」把绑定指到旁边另一台同型号外设上

### 兼容性

- 旧存档的绑定无需重绑：第一次读取时会把方块自己的 UUID 自动补写进绑定记录
- 其它模组的外设（导航台、哨戒动力臂、CBC 智能炮座、应力 / 转速表、雷达网络控制器）没有地方保存 UUID，仍按坐标定位
- 不涉及存档结构变动，可直接覆盖升级

---

## 1.1.2.1-beta (English)

> Beta release: please validate in a backed-up world before using it on a main save.
> Improvement: peripheral bindings are now keyed by each block's persistent identity, so nothing needs to be rebound after a physics structure is assembled, disassembled or moved.

### Improvements

- **Peripheral bindings keyed by block UUID**: the CB gearbox, smart speed controller, CB speaker, lecture stone, CB typewriter, info transmitter and diagram sensor now persist their own UUID inside the block entity, and the peripheral card stores that identity instead of a coordinate. Bindings keep pointing at the same block through physics-structure assembly, disassembly, and being carried away and landed; picking a device up as an item and placing it back preserves its identity as well
- **Lost bindings heal themselves**: when the identity index hits, the binding is corrected to the block's current position directly; only when that fails does it fall back to coordinates, and any candidate found that way is checked against the recorded identity first, so a "repair" can no longer silently latch onto an identical device placed next door

### Compatibility

- Existing bindings need no rebinding: the block's own UUID is written into the record automatically on its first read
- Peripherals from other mods (nav tables, sentry arms, CBC smart mounts, stress / speed gauges, radar network controllers) have nowhere to store a UUID and stay coordinate-based
- No save-format changes; drop-in upgrade

---

## 1.1.2

> 修复：导航台/准星连线角的方位角算错、齿轮箱跟随越转越偏、偶尔卡住不转；「旋转至」跟随更跟手。

### 修复

- **导航台连线角首值错误**：物理结构刚组装完、投影还没算好时，导航台返回的自身坐标是结构存储区坐标（上千万量级），被当成世界坐标参与计算，第一条指令直接算歪；现在这类值一律作废，改按结构内坐标投影到世界后再算
- **齿轮箱越转越偏**：闭环此前假定「账面角增大 = 平台方位角增大」，遇到齿轮组反向装配的装置会变成正反馈，偏差每轮翻倍、平台绕着整圈乱转；现在用上一次的账面角变化与实际方位角变化实测判断同向/反向，换装置也不会反
- **偶尔卡住不转**：输入轴转速为 0 时发起角度指令，原版按时长折算会除零溢出成负数，序列永远跑不完，之后所有指令都被「还没跑完」挡掉；现在轴不转时不发起指令，并加入 40 刻看门狗强制复位，已卡住的存档也能自行恢复
- **外设绑定塌到同一坐标**：物理结构组装/拆卸后多台外设可能被就近找回成同一块方块；现在定位会排除已被占用的坐标，必要时扩大搜索半径
- **无效角度驱动齿轮箱**：外设未加载或连线两点重合时算出的角度不再下发，齿轮箱不会追着无效值乱转

### 改进

- **「旋转至」改为连续跟随**：不再把一次转向切成一段一段（每段结束轴都要停一下再接下一段，动作会断成一节一节），改成轴持续转动、每刻只决定换不换向和何时停，动作连贯，同时能及时跟上准星与移动目标
- **准星连线角的外设选择**：前、后两个槽都可选任意已绑定外设（含导航台、目标定位模块），不再被误排除
- **方向参数显示**：统一存英文稳定值并本地化显示，旧存档里的「前/后」值继续可用

### 说明

- 不涉及存档结构变动，可直接覆盖升级；已卡住的齿轮箱无需拆除重放

---

## 1.1.2 (English)

> Fixes wrong nav-table / sight-line bearings, runaway gearbox tracking and occasional stalls; "rotate to" now follows moving targets more closely.

### Fixes

- **Wrong first bearing for nav-table lines**: right after a physics structure is assembled, the projected position is not ready yet and the nav table reports its storage-area coordinate (tens of millions of blocks away), which was used as a world coordinate and produced a bogus first command. Such values are now discarded and the position is re-projected from the in-structure coordinate
- **Gearbox drifting away from the target**: the control loop assumed "larger book angle = larger platform bearing". With a reversed gear train this turns into positive feedback, doubling the error every step and spinning the platform in full circles. The mod now derives the direction empirically from the previous book-angle change versus the measured bearing change, so it stays correct on any assembly
- **Occasional freeze**: starting an angle command while the input shaft is stopped makes vanilla divide by zero, overflow the instruction duration into a negative number, so the sequence never finishes and every later command is rejected as "still busy". Commands are no longer started without rotation input, and a 40-tick watchdog force-resets a stalled sequence, so already-stuck worlds recover on their own
- **Device bindings collapsing onto the same block**: after assembling or disassembling a physics structure, several devices could be resolved to one block. Bindings now exclude already-claimed coordinates and widen the search radius when needed
- **Invalid angles no longer drive the gearbox**: when a peripheral is unloaded or the two line points coincide, no command is sent

### Improvements

- **"Rotate to" is now continuous tracking**: a turn is no longer split into segments (each segment had to finish and idle the shaft before the next one started, which made the motion stutter). The shaft keeps rotating and every tick only decides whether to reverse and when to stop, so the motion stays smooth while still following a crosshair or a moving target closely
- **Peripheral selection for sight-line angles**: both the front and back slots now accept any bound peripheral, including nav tables and target locators
- **Direction parameter display**: stored with stable English keys and localized on screen, while legacy Chinese values in old saves keep working

### Notes

- No save-format changes; drop-in upgrade. Gearboxes that are already stuck do not need to be broken and replaced

---

## 1.1.1

> 紧急修复：智能炮座装在物理结构上（飞行器、移动平台等）时无法正常瞄准。

### 修复

- **物理结构上无法瞄准**：智能炮座被组装进物理结构后，方块实体坐标位于结构存储区，而前方目标坐标属于世界坐标系，两者直接相减得到的方向完全错误；现在会先把目标点换算到结构自身坐标系，再计算偏航与俯仰，炮管可正常指向目标
- **智能固定炮座**：同样按结构坐标系计算偏航/俯仰，滚轮角度不再随结构移动而偏移
- **火控击杀的可达判定**：距离与视线检测改用投影后的世界坐标计算起点，物理结构上的火控单位不再误判目标不可达而一直等待

### 说明

- 只影响物理结构上的炮座与火控单位，普通方块上的瞄准行为与之前完全一致
- 不涉及存档结构变动，可直接覆盖升级

---

## 1.1.0

> 大更新：新增「火控单位」外设体系与两张智能炮座方块，把哨戒动力臂完整接入 CB 电脑，并加入枪式遥控器、瞄准组件与色彩修复附魔。

### 兼容性声明

**哨戒动力臂属于非官方兼容。** Create: Sentry Mechanical Arm 官方版本目前尚未发布 1.21.1 版本，本模组对该动力臂的接管（瞄准、开火、换弹、弹药读取）基于社区在 1.21.1 上的非官方移植版本实现。待上游发布官方 1.21.1 版本后，本模组会跟进适配官方接口；在官方版本发布前，这部分功能请以上游社区版本为准。

### 新增

- **智能炮座 / 智能固定炮座**：复刻 Create Big Cannons 的火炮底座，一比一沿用其组装、红石开火、俯仰范围与固定炮座滚轮设定；使用改色贴图，底座没有传动轴，底面为完整平面
- **火控单位**：哨戒动力臂与两张智能炮座统一为「火控单位」外设，可被外设绑定模块卡绑定
- **火控积木**：火控单位瞄准、火控单位可用弹药；瞄准与弹药读取同时兼容哨戒动力臂和炮座
- **哨戒动力臂接管**：瞄准射击、换弹、可用弹药、弹匣弹量四组积木，绑定后动力臂停止自动索敌
- **枪式遥控器**：手持右键进入控制模式，鼠标左右键像键盘一样供积木读取，Esc 或切换物品退出；工作台绑定，5×5 动力合成
- **全能枪式遥控器**：沿用枪式遥控器的外观，控制模式下鼠标左右键、两个侧键与整个键盘都可作为按键；序列组装配方（枪式遥控器 → 烈焰人计算芯片 → CB打字机 → 集成电路 → 辊压），工作台绑定
- **枪炮手的瞄准组件**：取景器式 HUD、缩放、雷达目标框与屏幕外箭头；序列组装配方（目标定位模块 → 集成电路 → 望远镜 → 精密构件 → 皮革 → 黄铜板）
- **瞄准组件锁定**：佩戴瞄准组件时，准星对准生物按鼠标中键即可锁定，并分配递增的锁定序号；雷达投影中，锁定目标的框、名称与血量变为绿色并显示 `#序号`
- **锁定积木**：清除瞄准组件的所有锁定目标、清除瞄准组件的上一个锁定目标、火控单位瞄准并击杀瞄准组件的所有目标（排序方式：锁定先后 / 按名字首字母 / 最高最大血量优先）
- **智能炮座序列组装配方**：CBC 火炮底座 / 固定火炮底座 → 黄铜块 → 切割 → 辊压 → 集成电路 → 烈焰人计算芯片 → 超级烈焰棒 ×2
- **智能炮座思索**：两张智能炮座新增 7 种语言的思索说明
- **色彩修复附魔**：瞄准组件的专属附魔，附魔后取景器保留彩色画面

### 改进

- **设置转速控制器**改为外设专属积木，未绑定外设时不显示
- **锁定优先于手动指令**：火控击杀任务进行期间，同一火控单位的瞄准与开火积木暂时让位；目标无法攻击时保持瞄准等待，锁定目标清空后自动恢复
- 7 种语言的积木名称与提示同步更新

### 说明

- 智能炮座依赖 Create Big Cannons；未安装时相关方块、物品与积木不会注册，其余功能照常可用
- 智能炮座保留红石开火与红石组装；「开火」积木只面向哨戒动力臂
- 瞄准组件的锁定表保存在服务端内存中，世界重启后清空
- 升级可直接覆盖，旧存档与旧绑定记录兼容

---

## 1.0.2

### 新增

- **高级显示链接器**：界面、目标绑定、写入行等行为与机械动力原版显示链接器完全一致，但**每刻自动刷新**，不需要接高频红石信号（无序合成：显示链接器 + 集成电路）
- **「次方根」积木**：给定次数与数值求根，负数只有奇次根有解
- **「书写计时」积木**：单位只有刻 / 秒 / 分，输出「数值 + 单位」的原样时长，不折算成刻
- **「计算」积木新增「取模」**

### 改进

- **带单位的数值**：数值可以携带单位参与运算，乘除会得到复合单位（如 `RPM·s`、`RPM/s`），平方得到 `²`
- **时间 / 角度运算兼容带单位数值**：按写入时的单位回算结果（如 5 游戏分 ÷ 2 = 2.5 游戏分），不会被悄悄换算成刻
- **「等待」「红石发送时长」**：可以直接吃带单位的时长，在接收端再换算成刻
- 玩家手册与 7 种语言的积木帮助文本同步更新

### 说明

- 高级显示链接器的灯泡为模型内的静态贴图，不会像原版那样在每次刷新时闪烁；刷新本身与接高频红石的原版一致
- 升级可直接覆盖，旧存档兼容

---

## 1.0.1

### 新增

- 日语（ja_jp）
- 韩语（ko_kr）
- 俄语（ru_ru）
- 繁体中文 · 台湾（zh_tw）
- 繁体中文 · 香港（zh_hk）

### 说明

本版本只补充本地化，玩法与 1.0.0 完全一致，可直接覆盖升级。

---

## 1.0.0

首个正式版本。

- **CB电脑**：积木编程，共 11 类积木（事件、控制、数据、运算、机械动力、广播、局域网、互联网、显示、航空学、声音）
- **CB拓展器**：6 个模块槽，9 种模块卡与硬盘
- **机械动力联动**：CB编程齿轮箱、智能转速控制器、无线红石收发、仓储查询与打包机发货
- **通信**：本机广播、局域网、全服互联网、聊天框收发
- **显示与声音**：无线显示器、CB音响、讲解石、网络音乐
- **航空学联动**：导航台坐标、距离与方位
- **配套玩法**：捏塑创生之手、超级烈焰人、磁石挂件与绑定指南针
