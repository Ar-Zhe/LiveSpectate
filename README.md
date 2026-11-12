# LiveSpectate — Minecraft 1.21.1 / NeoForge 服务端旁观（附体）模组

把 Bukkit 时代的 **StreamCamera**（`StreamCamera-18.0.0-Release.jar`）的"相机玩家 / 焦点玩家"思路，
做成一个 NeoForge 1.21.1 的服务端模组，并且**新增了"定时自动切换玩家"**功能：
一个管理员账号可以定时在多个在线玩家之间自动轮换旁观视角，方便无人值守直播。

* 侧别：**服务端**（专服 / 局域网主机都可用）。玩家不需要安装任何客户端模组，也不需要资源包。
* 加载器：NeoForge `21.1.248`（Minecraft `1.21.1`）
* 权限：所有命令都要求 **OP 等级 2**
* 主命令：`/livespectate`（别名 `/livespec`）
* 图形界面：**按 F9 打开控制面板**（相机账号也装了本模组时可用，按键可在「选项 → 按键控制」里改）

> 📖 **第一次用请先看 [教程.md](教程.md)** —— 从"装上去"到"挂机直播"的分步操作、OBS 设置、
> 按场景调配置、常见问题速查；本文档（README）是完整的命令/配置参考手册。

---

## 1. 命令一览

| 命令 | 说明 |
| --- | --- |
| `/livespectate <玩家>` | 附体到指定玩家。执行者不是旁观模式时会先保存原状态并切换为旁观模式。 |
| `/livespectate stop` | 停止自动轮换 **并**退出附体，恢复执行者原来的维度、坐标、朝向和游戏模式。 |
| `/livespectate reload` | 重载 `config/livespectate-common.toml`。 |
| `/livespectate status` | 显示当前附体目标与自动轮换状态（控制台执行时显示全部会话总览）。 |
| `/livespectate auto start [间隔秒]` | 开始定时自动轮换。不填间隔时使用配置里的 `auto-default-interval-seconds`。 |
| `/livespectate auto stop` | 停止自动轮换，**保持当前附体状态**，不恢复执行者原状态。 |
| `/livespectate auto status` | 显示是否开启、间隔、当前目标、下一个目标。 |
| `/livespectate auto next` | 立即切换到下一个目标。 |
| `/livespectate help` | 命令帮助。 |

### Tab 补全

* `/livespectate ` → 在线玩家名 + `stop` / `reload` / `status` / `auto` / `help`
* `/livespectate auto ` → `start` / `stop` / `status` / `next`
* `/livespectate auto start ` → `10` / `15` / `30` / `60` / `120` / `300`

### 图形控制面板（F9）

相机账号**也装了本模组**时，按 **F9** 打开控制面板，不用再敲命令：

```
┌ LiveSpectate 控制面板 ──────────────────────────────────────────┐
│ 附体目标：Steve          在线玩家 (12)          [开始自动轮换]  │
│ 自动轮换：开启 · 30s     下一个：Alex           间隔 [30] 秒    │
│                                                [停止自动轮换]  │
│ ┌───────────────────────┐                      [下一个目标]    │
│ │ Steve        旁观  当前│                      [退出附体]      │
│ │ Alex         生存     │                      [重载配置]      │
│ │ Herobrine    创造     │                                     │
│ │ …（可滚动，点击即附体）│                                     │
│ └───────────────────────┘                                     │
│ 左键点击玩家名即可附体 · 需要 OP 等级 2        [刷新] [关闭]    │
└────────────────────────────────────────────────────────────────┘
```

面板内容与行为：

| 区域 | 功能 |
| --- | --- |
| 状态区 | 当前附体目标、自动轮换开关与间隔、下一个目标（由服务端实时同步） |
| 玩家列表 | 在线玩家（按名字排序），带模式标签；**左键点击即附体**；当前目标标绿并显示"◀ 当前" |
| 操作按钮 | 开始/停止自动轮换、下一个目标、退出附体、重载配置、刷新状态、关闭 |
| 间隔输入框 | 只接受 1–86400 的数字，会记住上次填的值 |
| 提示行 | 未收到服务端状态时会变红提示"服务器也需要安装本模组" |

设计要点：

* 面板上的每个按钮都只是把 **`/livespectate ...` 命令**发给服务端，所以权限校验、目标筛选、
  异常处理全部复用命令那一套，面板不会绕过任何规则（非 OP 使用会收到正常权限报错）。
* 状态是客户端按 payload 主动请求、服务端按请求者身份回发自己的状态，不做全局广播。
* 打开面板**不会暂停游戏**（单人当机位时尤其重要），按 ESC 或再按 F9 关闭。
* 专用服务端不会加载任何客户端类：客户端代码都在 `com.livespectate.client` 下，
  状态分发口 `StatusDispatcher` 由客户端初始化时注入。

---

## 2. 配置文件 `config/livespectate-common.toml`

首次启动服务器后自动生成：

```toml
#消息语言 / message language: zh_cn 或 en_us
message-language = "zh_cn"

#管理员退出游戏时是否立刻恢复其原游戏模式。
restore-on-logout = true

#自动轮换默认间隔（秒）。/livespectate auto start 不带参数时使用。
auto-default-interval-seconds = 30

#自动轮换目标选择模式：SEQUENTIAL（按在线列表顺序循环）或 RANDOM（随机）。
auto-mode = "SEQUENTIAL"

#自动轮换是否可以把执行者自己作为目标。
auto-include-self = false

#自动轮换是否排除旁观模式玩家。
auto-exclude-spectators = true

#自动轮换是否排除创造模式玩家。
auto-exclude-creative = false

#自动轮换需要排除的玩家名列表，例如 ["Steve", "Alex"]。
auto-exclude-names = []

#没有任何有效目标时：true = 自动停止轮换并提示；false = 保持等待。
auto-stop-when-no-targets = true

#执行 /livespectate <玩家> 时是否停止该管理员的自动轮换，避免手动与自动冲突。
auto-manual-overrides-auto = true

#自动轮换是否允许跟随目标切换维度。false 时只在"锚定维度"（相机账号进入附体前所在的维度）内选择目标。
auto-cross-dimension-follow = true

#是否把相机账号自身的朝向同步成目标的朝向（切换目标时立即复位、之后跟随目标转头）。
camera-lock-rotation = true

#朝向同步的检查间隔（tick）。只在偏差超过 12° 时才发包。
camera-lock-interval-ticks = 10
```

除了需求中列出的 9 个 `auto-*` 键，额外增加了 `message-language`（内置中英双语消息，
不依赖客户端资源包）、`restore-on-logout`（防止掉线/重启后管理员卡在旁观模式）
和 `camera-lock-*`（配合越肩视角等第三方相机模组，见 §3.3）。

修改文件后在游戏里执行 `/livespectate reload` 即可生效（所有配置值都是使用时惰性读取的）。

---

## 3. 自动轮换逻辑

1. `/livespectate auto start [秒]`
   * 执行者当前不是旁观模式时，先保存原状态（维度 / 坐标 / 朝向 / 游戏模式），再切到 `SPECTATOR`。
   * **立即**挑选一个有效目标并附体。
   * 为该管理员单独启动一份定时器状态（`Map<UUID, AutoSpectateState>`），每 N 秒切换一次。
   * 如果当前一个有效目标都没有：
     * `auto-stop-when-no-targets = true`：不开轮换，直接提示（不会把执行者切成旁观）。
     * `auto-stop-when-no-targets = false`：登记状态进入等待，有玩家出现后自动接上。

2. 目标选择规则
   * `SEQUENTIAL`（默认）：按**每次切换时重新获取**的在线玩家列表顺序循环。
   * `RANDOM`：随机选择，并尽量避免与当前目标相同。
   * 默认排除执行者自己（`auto-include-self = false`）。
   * 可选排除旁观玩家、创造玩家、`auto-exclude-names` 名单中的玩家。
   * 可选只在同维度内选目标（`auto-cross-dimension-follow = false`）。此模式下的"锚定维度"是
     相机账号**进入附体前**所在的维度（通常是主世界）；当前目标一旦跑到别的维度就会被判定为失效，
     立刻换回锚定维度里的其他玩家，而不是跟着穿维度。
   * 当前目标离线 / 死亡 / 失效时，不等间隔立即跳到下一个有效目标（带 1 秒冷却，避免抖动）。

3. 切换时只做一件事
   * 调用 `ServerPlayer#setCamera(Entity)`——这就是 1.21.1 里原版 `/spectate` 命令使用的等价方法。
   * **不**传送目标玩家、**不**修改目标玩家游戏模式、**不**给目标玩家发任何消息。
   * 自动跟随跨维度由 `setCamera` 自身的维度传送完成。

4. 定时器
   * 使用 NeoForge `TickEvent.ServerTickEvent.Post`，在服务器主线程上做 tick 累加。
   * 只有到达切换时间（或目标失效）时才调用一次 `setCamera`，**绝不每 tick 调用**。
   * 不使用 `java.util.Timer`，不开异步线程。
   * 每个管理员一份独立状态，互不影响。

5. 异常处理

   | 情况 | 行为 |
   | --- | --- |
   | 目标离线 / 死亡 / 失效 | 跳过该玩家，选下一个有效目标 |
   | 没有任何有效目标 | 按 `auto-stop-when-no-targets` 自动停止并提示，或保持等待 |
   | 执行者退出游戏 | 停止其自动轮换、清理状态，并（`restore-on-logout = true` 时）恢复原游戏模式 |
   | 执行者被改成其它游戏模式 | 检测到后自动停止轮换并提示 |
   | 服务器关闭 | 停止所有自动轮换，并尝试恢复所有正在附体的管理员 |
   | 手动 `/livespectate <玩家>` | 默认停止该管理员的自动轮换（`auto-manual-overrides-auto`） |

---

## 3.3 第三人称相机朝向（含越肩视角 Shoulder Surfing）

模组在**切换目标时会把相机账号自身的朝向**同步成目标的朝向，之后每
`camera-lock-interval-ticks` 检查一次（偏差超过 12° 才发包），所以镜头始终是
**从目标后脑勺朝前看**，并且会跟着目标转头。

**为什么必须这么做（坑）**：

| 相机方案 | 摄像机角度取自 | 只调 `setCamera(目标)` 会怎样 |
| --- | --- | --- |
| 原版第三人称（F5） | 摄像机实体（就是目标） | ✅ 一直正常，会跟着目标转身 |
| 越肩视角 Shoulder Surfing（`decoupled_camera = true`，默认） | **相机账号自己的朝向**（解耦保存，只跟鼠标/传送走） | ❌ 切换目标后镜头停在上一次的方向 → "人都换了还朝着旧方向，只能正面拍脸" |

第一个目标往往"看着正常"，只是因为它的朝向刚好等于相机账号当时的朝向。

**搭配越肩视角的推荐设置**（`config/shouldersurfing-client.toml`）：

```toml
[camera]
decoupled_camera = false        # 关键：不关的话相机角度不受玩家朝向驱动
orient_camera_on_teleport = true
camera_transition_speed_multiplier = 0.5   # 默认 0.25 偏慢，切换目标时镜头"飘"太久
```

* `decoupled_camera = false` 之后，SSR 的相机角度跟随"玩家朝向"，
  而本模组已经把相机账号的朝向锁定成目标朝向 → 镜头永远在目标后脑勺 ✅
* 想临时手动观察（自由转头）时，把 `camera-lock-rotation` 设为 `false` 即可。
* 用原版 F5 第三人称则**不需要任何配置**，模组的朝向同步只是让两边行为一致。

---

## 4. 与 StreamCamera 插件的对应关系

参考的两份材料：`StreamCamera-18.0.0-Release.jar`（Spigot 1.12.2 插件，**只有编译后的 class，
没有附带源码**，因此按插件描述符 `plugin.yml`、类名、以及官方 Wiki 的行为说明来还原语义）
和 `wiki.7z`（lxStd Wiki 页面存档）。

| StreamCamera（Bukkit 插件） | LiveSpectate（本模组） |
| --- | --- |
| 相机玩家 Camera Player（`/actcamera`，强制旁观并锁定） | 执行者：`/livespectate auto start` 自动保存状态并切旁观 |
| 焦点玩家 Focus Player（`/setfocus`、`/focuscam`） | 附体目标：`/livespectate <玩家>` |
| 移交焦点 `/migratecam` | `/livespectate auto next` / 自动轮换 |
| 关闭相机与焦点 `/shutcam` | `/livespectate stop`（并且额外恢复原状态） |
| 配置热重载 `/camreload` | `/livespectate reload` |
| 查看概况 `/seefocus` | `/livespectate status`、`/livespectate auto status` |
| 无人值守：焦点掉线自动随机换人、焦点为空时新玩家自动接上 | 目标失效立即换人 + `auto-stop-when-no-targets=false` 的等待模式 |
| 焦点投票 `/votefocus`、`/agreevote`、`/seevote` | **未实现**（属于社区玩法，和"定时轮换"目标无关） |
| 拒绝被围观 `/dontlookme` | 用 `auto-exclude-names` 名单静态实现 |
| Web 面板（`Web/Admin.html` 等） | **未实现**（服务端 mod 不内置 HTTP 服务） |

相比原插件，本模组刻意去掉了"每 3 秒强制刷新旁观"这类 workaround：
NeoForge/原版 1.21.1 的 `setCamera` 本身就会处理跨维度与传送，
所以只在真正需要切换时才发一次摄像机包。

---

## 5. 从源码构建

需要 **JDK 21** 和网络（首次构建会下载 Minecraft / NeoForge 依赖）。

```bash
# Windows
gradlew.bat build

# Linux / macOS
./gradlew build
```

产物（两个 jar 都会一起生成）：

| 文件 | 内容 | 用在哪 |
| --- | --- | --- |
| `livespectate-neoforge-1.21.1-1.1.1.jar` | 完整版：命令 + 附体 + 轮换 + 相机朝向锁定 + **F9 面板**（客户端按键/界面/状态同步） | 想让相机账号按 F9 开面板时 |
| `livespectate-neoforge-1.21.1-1.1.1-server.jar` | **纯服务端精简版**：剥离 `com.livespectate.client` 与 `com.livespectate.network`，只有命令、附体、轮换、相机朝向锁定 | 专用服务端 / 不想让客户端多出任何界面和按键 |

两个 jar 的 `modId` 相同，**只能二选一**放进 `mods/`（同时装会被判定为重复模组）。
精简版不注册任何自定义 payload，也不会给客户端发任何东西 —— 对客户端完全无感，
是"只要功能、不要面板"时该用的那个。

开发环境运行：

```bash
gradlew.bat runServer     # 启动带模组的测试服务端
gradlew.bat runClient
gradlew.bat serverJar     # 只生成服务端精简版
```

> 说明：仓库里的 `build.gradle` 使用 [ModDevGradle](https://github.com/neoforged/ModDevGradle) 2.0.144
> 与 NeoForge `21.1.248`。若使用离线环境，请先把 Gradle 依赖缓存准备好。

安装：把 jar 丢进服务端 `mods/` 目录即可。**只需装服务端**，玩家不需要装任何东西
（完整版的 F9 面板才需要客户端也装）。

### 已经验证过的内容

本仓库的代码在 Minecraft 1.21.1 / NeoForge 21.1.248 的**真实专用服务端**上跑过冒烟测试：

* 服务端正常启动到 `Done (0.567s)!`，模组列表里出现 `LiveSpectate 1.0.0 (livespectate)`；
* F9 控制面板（1.1.0 新增）：客户端加载模组无异常（模组列表、资源包 `mod/livespectate`、
  按键与 payload 注册全部正常）。**面板的屏幕渲染尚未由作者实机截图确认**，
  布局是照 352×250 的面板尺寸手算的，第一次上手请留意；有问题发张截图即可修。
* 相机朝向锁定（1.1.1）：用 mineflayer 无头机器人实测，服务端读到的 Rotation 与目标完全一致 ——
  附体朝 yaw=0 的目标后 `AdminA=[0.0f,0.0f]`；目标自己转头到 90°、180° 后
  `AdminA=[90.0f,0.0f]` / `[-180.0f,0.0f]`，即"切换即复位 + 持续跟随"。
* 原版客户端兼容（1.1.1）：机器人（原版协议、未装模组）可以正常进服 ——
  测试中还发现并修掉了 1.1.0 把状态通道注册成"必需"、会导致**没装模组的玩家被踢**的问题（已改为 optional）。
* 首次启动生成的 `config/livespectate-common.toml` 与需求中的 9 个 `auto-*` 键**完全一致**
  （默认值 `30 / "SEQUENTIAL" / false / true / false / [] / true / true / true`）；
* 通过 RCON 执行命令验证命令树与 Tab 补全之外的执行路径：

  | 输入 | 结果 |
  | --- | --- |
  | `livespectate status` | 控制台总览：`LiveSpectate 状态：0 个附体会话，0 个自动轮换` |
  | `livespectate reload` | `配置已重载 (config/livespectate-common.toml)。` |
  | `livespectate help` | 完整输出 8 行帮助 |
  | `livespectate auto status/start/next/stop` | 正确进入玩家分支（控制台执行 → `A player is required...`） |
  | `livespectate bogusplayer` | 正确进入 `<玩家>` 参数分支 |
  | `stop` | 服务器干净关闭，无任何异常 |

  （需要真实玩家客户端的附体/切换路径依赖的是已用 `javap` 对着 1.21.1 实际字节码核对过的
  `ServerPlayer#setCamera` / `setGameMode` / `teleportTo`，行为与原版 `/spectate` 一致。）


---

## 6. 代码结构

```
src/main/java/com/livespectate/
├── LiveSpectate.java              模组入口，注册配置
├── Config.java                    livespectate-common.toml 的 ModConfigSpec 定义
├── AutoMode.java                  SEQUENTIAL / RANDOM
├── SpectateSession.java           一次附体会话保存的原始状态
├── AutoSpectateState.java         单个管理员的自动轮换状态（tick 累加器）
├── SpectateManager.java           附体/解除附体、会话与轮换状态的 Map
├── AutoRotationManager.java       ServerTickEvent.Post 驱动的轮换逻辑
├── command/LiveSpectateCommand.java  命令树 + Tab 补全
├── event/SpectateEvents.java      命令注册、tick、玩家退出、服务器关闭
├── network/                       F9 面板的状态同步（两端都会注册）
│   ├── SpectateStatusPayload.java 服务端 -> 客户端：状态快照
│   ├── StatusRequestPayload.java  客户端 -> 服务端：请求快照
│   ├── LiveSpectateNetwork.java   payload 注册 + 快照打包
│   └── StatusDispatcher.java      分发口（专用服务端不会碰到客户端类）
├── client/                        仅客户端加载（value = Dist.CLIENT）
│   ├── LiveSpectateKeys.java      F9 按键注册 + 客户端初始化
│   ├── LiveSpectateClient.java    客户端 tick：按 F9 开面板
│   ├── LiveSpectateScreen.java    F9 控制面板本体
│   ├── ClientSpectateState.java   本地缓存的状态快照
│   ├── ClientText.java            面板文案（中/英跟随客户端语言）
│   └── StatusDispatcherBridge.java 把状态缓存接到分发口
└── util/
    ├── TargetSelector.java        每次重新取在线列表并按规则筛选目标
    └── Lang.java                  内置 zh_cn / en_us 消息
src/main/resources/
├── META-INF/neoforge.mods.toml
└── assets/livespectate/lang/{zh_cn,en_us}.json   按键名称/分类
```

---

## 7. 已知限制

* 附体会话状态保存在内存中（`Map<UUID, SpectateSession>`）。正常关服、正常退服都会被正确恢复；
  只有服务器被强杀（kill -9 / 断电）才可能残留，此时管理员会以旁观模式重新进服，
  用 `/livespectate stop` 或直接切回游戏模式即可。
* `reload` 走的是 FML 的 `ConfigTracker.loadConfigs(COMMON, configDir)`，
  它会重新读取 `config/` 下**所有** COMMON 类型配置（这是 NeoForge 提供的公开热重载入口）。
* 命令全部要求 OP 等级 2，没有做 LuckPerms 之类的细粒度权限节点。
* 附体刚开始的 3 秒内，模组会持续补发摄像机包（见附录）。这期间玩家按 Shift（原版"退出旁观"操作）
  也会被重新附体；自动轮换本来就是"锁住机位"的语义，所以没有特殊处理。

## 8. 许可证

MIT

---

## 附录：附体是怎么实现的（以及 1.21.1 的一个原版坑）

附体用的是 **1.21.1 里和原版 `/spectate`、旁观模式右键生物完全相同的方法**：

```java
serverPlayer.setCamera(targetEntity);   // 1.21.1 里没有 setSpectatorTarget，就是这个
```

它会做三件事：把旁观者传送到目标身上、`ChunkMap#move` 刷新区块/实体跟踪、
给客户端发一个 `ClientboundSetCameraPacket`。之后服务端每 tick 都会在
`ServerPlayer#tick()` 里把旁观者的位置同步到目标身上，客户端则直接以目标实体为摄像机
—— 这就是"跟着跑、连续跟拍"的来源。

### 坑：第一个摄像机包会被客户端丢掉

原版发送顺序是（`ServerPlayer#setCamera`）：

```java
this.teleportTo(serverlevel, ...);            // 传送
this.serverLevel().getChunkSource().move(this); // 刷新跟踪
this.connection.send(new ClientboundSetCameraPacket(this.camera)); // 立刻发摄像机包
```

而 `ChunkMap#move` 是**先跑实体配对、后更新玩家的区块跟踪视图**，
实体配对又被 `isChunkTracked(player, chunkX, chunkZ)` 卡住：

```java
boolean flag = d1 <= d2 && this.entity.broadcastToPlayer(player)
               && ChunkMap.this.isChunkTracked(player, ...);   // ← 此刻还是旧视野，false
```

所以当目标玩家原本**不在相机账号的视野范围内**时（自动轮换经常如此：相机账号在世界中心，
目标在 1000 格外的地图角落），这一 tick 根本发不出目标实体的 spawn 包。
客户端收到摄像机包后执行：

```java
// ClientPacketListener#handleSetCamera
Entity entity = packet.getEntity(this.level);
if (entity != null) this.minecraft.setCameraEntity(entity);   // ← 实体不在 → 什么都不做
```

于是这个包被**静默丢弃**，摄像机永远没挂上去。表现出来就是：

* 位置包到了 → 相机账号"嗖"地瞬移到目标那儿；
* 摄像机没挂上 → **视界留在原地不动**，直到下一次轮换再次瞬移。

### 修复

1. **跨维度先换维度再附体**：`setCamera` 在跨维度时会走 6 参数 `teleportTo`，
   而那个重载内部会 `setCamera(this)` 把刚设好的摄像机重置掉。所以现在先
   `teleportTo(targetLevel, ...)` 把相机账号搬过去，再在同维度下调 `setCamera`。
2. **补发摄像机包**：附体后 3 秒（60 tick）内每 tick 补发一次
   `ClientboundSetCameraPacket`。`setCamera` 在相机已指向同一目标时不会再发包，所以这里直接发原包。
   等到区块/实体跟踪追上（通常 2~3 tick），客户端就能解析并挂上摄像机。
   自动轮换中目标中途跨维度时，也会立刻重新走一遍附体流程。

**实测**（两个真实协议客户端，相机账号在 -2000,-2000、目标在世界出生点，相距 2800 格）：

```
camera client knows target entity before attach? false
total packets = 61            <- 补发窗口 60 tick + 首次
first packet resolvable by the client? false      <- 原版就是这样丢的
first USABLE packet index = 2 (ms=138)            <- 补发第 3 个包就挂上了
RESULT: RACE REPRODUCED and FIXED
```

