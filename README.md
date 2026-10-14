# 维度之家 · Dimension Home

> 一个属于你自己的虚空空岛维度。
> 用**平滑石**搭一扇门、打火石点燃，就能进入纯虚空的空岛；
> 再走回来时，你会回到**出发的那个维度、原来那扇门**旁边。

**English: [README_en.txt](README_en.txt)**

[![Minecraft](https://img.shields.io/badge/Minecraft-1.20.1-62B47A?style=flat-square)](https://www.minecraft.net/)
[![Forge](https://img.shields.io/badge/Forge-47.x-DFA037?style=flat-square)](https://files.minecraftforge.net/)
[![License](https://img.shields.io/badge/License-MIT-blue?style=flat-square)](LICENSE)
[![Release](https://img.shields.io/github/v/release/GNX001/dimensionhome?style=flat-square)](https://github.com/GNX001/dimensionhome/releases)

---

## 这是什么

任何维度（**主世界 / 下界 / 末地**）都能搭一扇平滑石门。点燃后进入一个纯虚空的空岛维度
「维度之家：空岛」。空岛里只有**一块与门等宽的落脚平台**和**唯一一扇**返回门，除此之外空无一物。

这不是一个「资源维度」，而是一个**干净的私人空间**：坐标 1:1、有昼夜、光照正常，
适合用来建家、做红石、隔离测试，或者单纯找一个没人打扰的地方。

## 核心特性

| 特性 | 说明 |
| --- | --- |
| 坐标缩放 | **1:1** —— 主世界走 100 格，空岛也是 100 格（**不是**下界式 8:1 缩放） |
| 门颜色 | **金色 `#FFD700`**（门方块、传送时的屏幕蒙版、门口粒子三处统一） |
| 昼夜 | 有昼夜交替，与主世界同步流逝 |
| 天空光照 | 有（`has_skylight = true`） |
| 地形 | **纯虚空** —— 不生成浮空岛、矿物、树木、结构、村庄 |
| 落脚平台 | 与门（含左右门框）**等宽**，并沿门的法线方向前后各延伸 **2 格** |
| 返回逻辑 | 回到**出发的维度**、**原来那扇门**旁边（下界进就从下界出，末地进就从末地出） |
| 门框架方块 | `minecraft:smooth_stone`（平滑石），打火石点燃 |
| 生物生成 | 同主世界（会刷怪，但**不触发袭击**） |
| 床 | 可以用（`bed_works = true`） |
| 重生锚 | 不能使用（`respawn_anchor_works = false`） |
| 实体通行 | **玩家、怪物、动物、掉落物都能穿过门** |

## 空岛规则

空岛是一片纯虚空。为了既保持「只有一扇门」的设计，又**不会因为门被拆掉而被困**，
空岛上的点燃规则与主世界不同：

```
                    可点燃区域（水平 ±12 格，竖直 y=99 ~ 122）

        ┌───────────────────────────────────────────┐
        │                                           │
        │        ┌─ 唯一的返回门（默认 2×3）─┐         │
        │        │                          │         │
        │   ═════╧══════════════════════════╧═════    │  y=99 落脚平台
        │        （与门等宽，前后各延伸 2 格）          │
        │                                           │
        └───────────────────────────────────────────┘
                          水平 ±12 格
```

- **区域内**可以随时点燃**任意形状**的门框（宽 2~21、高 3~21），但**同一时间只允许存在一扇门**
- 区域内**已有一扇门**时，点燃第二扇会被**拒绝**
- **区域之外**点燃会被**拒绝**
- 空岛的**第一扇门**会按「第一次有人进门时那扇来源门」的**形状与朝向自动复刻**
- 所以：**门被拆掉也不会被困** —— 在区域内重新搭一个门框、点燃即可

> 如果连打火石和平滑石都掉进虚空了，空岛内可以执行 `/dimensionhome home` 直接逃生。

## 安装

### 前置要求

| 项目 | 要求 |
| --- | --- |
| Minecraft | **1.20.1** |
| Forge | **47.x**（已在 47.4.10 / 47.4.26 实测） |
| Java | **17** |

### 必需前置 MOD ⚠️

**[Custom Portal API Reforged 1.0.2](https://www.curseforge.com/minecraft/mc-mods/custom-portal-api-reforged)**
（MOD ID：`cpapireforged`，文件名 `customportalapi-reforged-1.0.2.jar`）

> **这是必需前置，不是可选项。** 缺少它游戏会在启动时直接崩溃，并在日志里报：
> `Missing or unsupported mandatory dependencies: Mod ID: 'cpapireforged'`

### 安装步骤

1. 到 [Releases](https://github.com/GNX001/dimensionhome/releases) 下载 `dimensionhome-1.0.0.jar`
2. 把**两个 jar 一起**放进游戏的 `mods\` 文件夹：
   - `dimensionhome-1.0.0.jar`
   - `customportalapi-reforged-1.0.2.jar`
3. 完全重启游戏（回到主菜单无效）
4. 在「Mods」列表里应同时看到 **Dimension Home** 与 **Custom Portal API ReForged**

多人服务器同理：两个 jar 都要放进服务器的 `mods\` 文件夹。

## 怎么玩

1. 主世界挖石头烧成**平滑石**（`smooth_stone`），搭一个门框，内腔至少 **2 格宽 × 3 格高**
2. 拿**打火石**对着门洞内侧底部右键 → 门洞被一层**金色**填满
3. 站进去约 **4 秒**（创造模式约 1 秒）→ 抵达空岛
4. 空岛上有唯一一扇返回门，走进去就回到你出发的地方

## 内置自检命令

本 MOD 带了两条自检命令，用于**可复现地**核对所有规则，不必靠肉眼观察：

```
/dimensionhome selftest    # 维度参数 / 纯虚空 / 地标几何 / 点燃规则 / 注册状态（40 项）
/dimensionhome tptest      # 传送全链路：玩家 + 掉落物各跑一遍完整往返（26 项）
/dimensionhome home        # 兜底逃生（只在空岛维度内有效）
```

需要 OP 或单人作弊权限。

## 从源码构建

```bat
git clone https://github.com/GNX001/dimensionhome.git
cd dimensionhome
set JAVA_HOME=<你的 JDK 17 路径>
gradlew.bat build
```

产物：`build\libs\dimensionhome-1.0.0.jar`

> **`gradle.properties` 里的 `org.gradle.daemon=false` 不要改成 `true`。**
> ForgeGradle 在守护进程模式下会因工具链服务提前关闭而报
> `Project services has been closed` 并构建失败。

开发环境直接运行：

```bat
gradlew.bat runClient    :: 启动带 MOD 的客户端
gradlew.bat runServer    :: 启动带 MOD 的服务器
```

## 项目结构

```
dimensionhome/
├─ README.md                        中文说明（本文件）
├─ README_en.txt                    英文说明（纯文本）
├─ LICENSE                          MIT
├─ publish.ps1                      提交并推送的辅助脚本
├─ src/main/java/com/dimensionhome/
│  ├─ DHConfig.java                 全局常量（唯一事实来源）
│  ├─ DimensionHome.java            主类（MOD 入口 + 传送门注册）
│  ├─ DHModEvents.java              mod 事件总线（能力注册、传送门注册）
│  ├─ DHEvents.java                 游戏事件总线（能力挂载 / 克隆 / tick / 服务器启动）
│  ├─ DHSelfTest.java               selftest | tptest | home 命令
│  ├─ capability/
│  │  └─ PortalOriginCapability.java  记录来源维度与门坐标（玩家死亡后持久化）
│  ├─ portal/
│  │  └─ DHTeleportHandler.java     传送接管：落点控制、来源记录、非玩家实体路径
│  └─ world/
│     ├─ VoidChunkGenerator.java    纯虚空区块生成器
│     ├─ SkyGateShape.java          门形状 / 足迹 / 平台与落点几何推导
│     ├─ SkyGateGeometry.java       轴向换算、区域判定、区域内门扫描
│     ├─ SkyGateData.java           首门形状的 SavedData（只写一次）
│     ├─ SkyGuardPortalFrameTester.java  区域内放行 / 区域外禁燃 / 唯一门
│     └─ SkyLandmarkBuilder.java    落脚平台 + 锚点门建造 / 擦除 / 测量
├─ src/main/resources/
│  ├─ META-INF/mods.toml            MOD 元信息与依赖声明
│  ├─ README.txt                    jar 内自带的中文说明
│  ├─ assets/dimensionhome/lang/    中英文语言文件
│  └─ data/dimensionhome/           维度类型 + LevelStem 数据包
└─ docs/
   ├─ 使用说明.md                    完整中文使用说明（安装 / 玩法 / 验收 / FAQ）
   ├─ RELEASE_NOTES_v1.0.0.md        v1.0.0 发布说明
   └─ 开发文档.txt                    最初的开发文档
```

## 技术要点

几个实现上值得记录的坑，供同类 MOD 参考：

- **禁止在空岛内点燃**不能靠 Forge 事件拦截 —— 打火石点燃走的是 CPA 自己的
  `AbstractFireMixin`（直接注入 `BaseFireBlock.onPlace`），事件 `cancel()` 无效。
  因此改用**自定义 `PortalFrameTester`**，在 `getNewPortal` 里返回
  `Optional.empty()` —— 这是唯一有效的拦点。
- **返回原来的门**无法用 CPA 的 `returnDimID` 表达（它只支持一个固定返回维度），
  因此通过 CPA 的 `registerBeforeTPEvent` 返回 `CANCEL_TP` 完全接管传送，
  自己记录来源维度与坐标，再做精确落点。
- **非玩家实体**不能走 `ServerPlayer.teleportTo`，需要
  `changeDimension(ServerLevel, ITeleporter)`；且自定义 `ITeleporter` **必须覆写
  `getPortalInfo`** —— Forge 的默认实现只在 `isVanilla()` 为真时使用原版算法，
  自写传送器恒为 `false`，会把实体**当前坐标**当落点，导致实体原地不动。
- **冷却要在 `changeDimension` 之前设置**，因为 `Entity.restoreFrom` 会把
  `portalCooldown` 复制给新实体实例。

## 已验证

在**真实的 Forge 1.20.1 生产服务器**（官方 installer 安装，非开发环境）上完成验证：

- ✅ 服务器正常启动，MOD 加载无报错，地标自动建造
- ✅ `/dimensionhome selftest` — **40/40 项通过**
- ✅ `/dimensionhome tptest` — **26/26 项通过**（玩家 + 掉落物各跑一遍完整往返）
- ✅ 主世界点燃平滑石门 → 正常生成传送门
- ✅ 空岛区域**之外**点燃 → 正确被拒绝
- ✅ 空岛区域**之内但已有门**时点燃 → 正确被拒绝（唯一门规则成立）
- ✅ 空岛区域**之内、门被拆掉之后**点燃 → **成功生成门**（「拆门被困」已修复）
- ✅ 首门形状复刻：能测出来源门形状、只记一次、按形状重建后外框完整且无新旧重叠
- ✅ 落脚平台按门形状铺设：与门（含两侧门框）等宽，沿门法线前后各延伸 2 格
- ✅ 到达落点动态计算：默认 2 格宽的门 → `(0.5, 100.0, 0.5)`
- ✅ 传送门染色为金色 `#FFD700`（运行时读回 `link.colorID` 断言确认）
- ✅ **非玩家实体**（掉落物 / 怪物 / 动物）随门进出，来源随 NBT 存活，返回原位置

**版本兼容性**：已在 **Forge 47.4.10** 与 **47.4.26** 上分别完整验证通过。

## 许可

[MIT](LICENSE) © 2026 GNX001
