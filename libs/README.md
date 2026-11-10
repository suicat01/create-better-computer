# libs 说明

本仓库不包含任何第三方模组文件。构建前请按下表把依赖按**完全一致的文件名**放入 `libs/`（`aeronautics/` 为其子目录）。

| 文件 | 必需 | 来源 |
| --- | --- | --- |
| `create-1.21.1-6.0.10.jar` | 是 | Create 官方发布页 https://modrinth.com/mod/create |
| `ponder-neoforge-1.0.82+mc1.21.1.jar` | 是 | Create 官方 Maven https://maven.createmod.net |
| `flywheel-neoforge-1.21.1-1.0.6.jar` | 是 | Flywheel 官方发布页 https://modrinth.com/mod/flywheel |
| `flywheel-neoforge-api-1.21.1-1.0.6.jar` | 是 | Flywheel 官方发布页 https://modrinth.com/mod/flywheel （发行包内的 api jar） |
| `Registrate-MC1.21-1.3.0+67.jar` | 是 | Registrate 官方仓库 https://github.com/tterrag1098/Registrate |
| `jna-5.14.0.jar` | 是 | JNA 官方仓库 https://github.com/java-native-access/jna |
| `jna-platform-5.14.0.jar` | 是 | JNA 官方仓库 https://github.com/java-native-access/jna |
| `jei-1.21.1-neoforge-19.39.0.372.jar` | 是（编译） | JEI 官方发布页 https://modrinth.com/mod/jei |
| `curios-neoforge-9.5.1+1.21.1.jar` | 是（编译） | Curios 官方发布页 https://modrinth.com/mod/curios |
| `sable-neoforge-1.21.1-2.0.6.jar` | 是 | Sable 官方发布页 https://modrinth.com/mod/sable |
| `sable-companion-common-1.21.1-1.6.0.jar` | 是 | 从 Sable 的 jar 内解出（内嵌依赖） |
| `netmusic-1.4.0-neoforge+mc1.21.1.jar` | 否（软依赖） | 网络音乐机的作者发布页 |

`aeronautics/` 子目录需要下列三个文件，均来自 Create Aeronautics 官方发布页 https://modrinth.com/mod/create-aeronautics （该整合包以嵌套 jar 形式分发，解开后取出）：

```
aeronautics/dev.eriksonn.aeronautics.aeronautics-neoforge-1.21.1-1.3.2.jar
aeronautics/dev.ryanhcode.offroad.offroad-neoforge-1.21.1-1.3.2.jar
aeronautics/dev.simulated_team.simulated.simulated-neoforge-1.21.1-1.3.2.jar
```

缺少可选的软依赖不会影响编译；缺少上表中标为“是”的文件会导致构建失败。
