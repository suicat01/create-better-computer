# Create: Better Computer (CBTC)

一个基于 NeoForge 1.21.1 的机械动力（Create）附属模组：用积木搭程序的 **CB电脑**，附带拓展模块、外设绑定、无线红石、局域网 / 互联网通信、齿轮箱控制、无线显示、网络音乐与航空学联动。

版本：1.0.2

## 能做什么

- **积木编程**：事件、控制、数据、运算、机械动力、广播、局域网、互联网、显示、航空学、声音共 11 类积木，支持拖拽拼接、分支、复制粘贴。
- **控制机械动力**：CB编程齿轮箱、智能转速控制器、无线红石收发信、仓储查询与打包机发货。
- **联网**：局域网内多台电脑通信，全服共享的互联网频道与聊天框收发。
- **扩展外设**：打字机、音响、讲解石、导航台、定位模块、结构图解传感器、信息转发器。
- **配套玩法**：捏塑创生之手把生物捏成计算机零件，烈焰蛋糕把烈焰人变成超级烈焰人。

## 安装

1. 安装 NeoForge 21.1.228+（Minecraft 1.21.1）与 Java 21。
2. 把前置模组放进 `mods/`：**Create 6.0.10**、**Create Aeronautics 1.3.2**、**Sable 2.0.6**。
3. 把 `cbtc-1.0.2.jar` 放进 `mods/`。
4. 可选：JEI（查看捏塑与配方）、Curios（磁石挂件放任意饰品槽）、网络音乐机（网络歌曲积木）。

## 文档

完整玩法、方块 / 物品图鉴、全部积木说明、合成与生产链、常见问题：

**[玩家手册](docs/玩家手册.md)**

## 构建

本仓库不附带任何第三方模组文件，`libs/` 默认为空。构建前请按 [libs/README.md](libs/README.md) 的清单，把前置依赖按同名文件放入 `libs/`。

```bash
# Windows
gradlew.bat build

# macOS / Linux
./gradlew build
```

构建产物位于 `build/libs/cbtc-1.0.2.jar`。

## 目录结构

```
src/main/java        # 模组源码
src/main/resources   # 资源文件（assets / data）
src/main/templates   # neoforge.mods.toml 模板
docs/                # 玩家手册
libs/                # 构建所需的前置依赖，需自行下载，见 libs/README.md
local-maven/         # DevLaunch 本地仓库
```

## 许可证

MIT
