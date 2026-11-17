# WOL 唤醒

局域网远程开机工具。手机上点一下，把家里或办公室的电脑叫醒，并实时告诉你它是否真的起来了。

Android 原生应用，Java + 传统 View 体系，无第三方网络库，扁平化设计。

---

## 功能

| 功能 | 说明 |
|---|---|
| 设备列表 | 卡片式管理多台电脑，显示名称、IP、MAC |
| 一键唤醒 | 点击「唤醒」向目标网卡广播魔术包（Magic Packet） |
| 唤醒确认 | 发出包后按 1.5 秒间隔持续 ping，确认真正开机才提示成功 |
| 在线状态 | 绿点=在线（附 ping 延迟）、灰点=离线、琥珀点=唤醒中 |
| 全部检测 | 概览条一键检测所有设备，并发执行 |
| 增删改 | 支持新增、编辑、删除设备；MAC 自动规范化并查重 |
| 本地持久化 | 设备列表存于 SharedPreferences，不依赖网络与账号 |

## 界面

三屏结构：

1. **主界面** —— 概览条（在线/离线台数 + 全部检测）、设备卡片列表、右下角添加按钮
2. **添加 / 编辑** —— 名称、IP、MAC、端口、广播地址、唤醒检测时长
3. **设备详情** —— 中央延迟圆环、完整参数表、大号唤醒按钮

视觉稿见 [`docs/design/ui-design.html`](docs/design/ui-design.html)（浏览器直接打开即可），
图标方案见 [`docs/design/icon-options.html`](docs/design/icon-options.html) 与
[`docs/design/icon-c-colors.html`](docs/design/icon-c-colors.html)。

## 快速开始

### 第一步：准备被唤醒的电脑

这一步比 App 本身更重要，绝大多数「唤不醒」都源于此处未配置。

1. **BIOS / UEFI** 中开启 `Wake on LAN`（不同主板叫法不同，通常位于 Power Management 或 Advanced 下）
2. **Windows 网卡设置**（设备管理器 → 网络适配器 → 属性）：
   - 「电源管理」标签：勾选「允许此设备唤醒计算机」与「只允许幻数据包唤醒计算机」
   - 「高级」标签：开启「唤醒魔术包 / Wake on Magic Packet」
3. **关闭快速启动**（控制面板 → 电源选项 → 选择电源按钮的功能 → 取消「启用快速启动」），否则关机后网卡可能断电
4. **记录网卡 MAC 地址**：`ipconfig /all` 中的「物理地址」
5. **建议使用有线连接**：无线网卡在关机后通常不再供电，无法接收魔术包

### 第二步：配置 App

1. 手机连上**与电脑相同的 Wi-Fi**
2. 点右下角 `+`，填写：
   - 设备名称：随意，如「客厅主机」
   - IP 地址：电脑在局域网中的地址，如 `192.168.1.100`（建议在路由器上设为静态分配）
   - MAC 地址：上一步记录的值，支持 `AA:BB:CC:DD:EE:FF`、`AA-BB-CC-DD-EE-FF`、`AABBCCDDEEFF` 三种写法
   - 端口：默认 `9`，少数环境需要 `7`
   - 广播地址：默认 `255.255.255.255`；若路由器不转发全局广播，填子网广播地址（如 `192.168.1.255`）
   - 唤醒检测时长：默认 60 秒，超时视为失败
3. 保存后回到列表，点「唤醒」

## 技术实现

### 魔术包

WOL 的唤醒信号是一段固定结构的 UDP 报文，共 **102 字节**：

```
[ 6 字节 0xFF ] + [ 目标 MAC 重复 16 次，共 96 字节 ]
```

网卡在关机状态下仍由待机电源供电并监听链路层广播，一旦收到以 6 个 `0xFF` 开头的序列，
即校验后续 16 次 MAC 是否与自己匹配，匹配则触发开机。

发送时依次尝试三个目标，提高复杂网络下的成功率：

1. 用户配置的广播地址
2. `255.255.255.255`（应对配置写错）
3. 设备单播 IP（部分路由器过滤广播，但 ARP 表仍有效）

每个目标重复发送 3 次，规避 UDP 丢包。

### 在线检测

Android 普通应用**没有 ICMP 权限**，`InetAddress.isReachable()` 在多数机型上会退化为
对 TCP 7 端口的探测，而该端口通常关闭，结果并不可靠。因此采用双重策略：

1. 先试 `isReachable()`（800ms）
2. 失败则依次 TCP 连接 `445 / 139 / 135 / 3389 / 22 / 80`，单端口超时 260ms

Windows 默认开启 445、139、135，Linux/NAS 通常有 22，命中任意一个即判定在线。

### 状态机

```
OFFLINE ──点唤醒──> WAKING ──ping 通──> ONLINE ──再次检测──> ONLINE
   ↑                   │
   └──── 超时/发包失败 ──┘
```

唤醒不是「发了包就算成功」，而是等到真正 ping 通才提示 —— 这是本工具与普通 WOL App 的主要区别。

### 并发模型

- `DeviceScanner` 内部 4 线程池，所有网络操作在后台执行
- 回调统一 `Handler.post` 切回主线程，界面层不接触线程切换
- 状态刷新走 `notifyItemChanged`，并关闭条目变更动画避免闪烁

### 数据存储

设备数量在几十条量级，直接用 `SharedPreferences` 存一段 JSON，省掉 Room/SQLite 依赖。
运行时状态（`state` / `latencyMs`）不落盘，每次进入页面重新检测。

## 项目结构

```
app/src/main/java/goha/win/wol/
├── MainActivity.java           主界面：列表、概览、全部检测
├── DeviceEditActivity.java     添加 / 编辑设备
├── DeviceDetailActivity.java   详情：延迟圆环 + 参数表
├── model/Device.java           设备实体、状态枚举、MAC/IP 校验
├── data/DeviceStore.java       SharedPreferences + JSON 持久化
├── net/WolSender.java          魔术包组装与广播发送
├── net/PingChecker.java        ICMP + TCP 双重探测
├── net/DeviceScanner.java      并发调度、唤醒后轮询
├── adapter/DeviceAdapter.java  列表适配器
├── ui/DeviceStateUi.java       状态色与文案的唯一来源
└── util/ViewUtils.java         dp、圆角、圆形背景
```

## 构建

环境：Android Studio（AGP 9.5.1 / Gradle 9.6 / JDK 20），`compileSdk 37`、`minSdk 24`。

```bash
# 调试包
./gradlew assembleDebug

# 产物
app/build/outputs/apk/debug/app-debug.apk
```

国内网络环境下依赖可能拉取缓慢，可在 `settings.gradle.kts` 的仓库列表中前置镜像地址。

## 测试

```bash
./gradlew testDebugUnitTest
```

覆盖 7 个用例，集中在最容易写错又最难在真机上察觉的地方：

- MAC 规范化：冒号、短横线、无分隔符、点分、含空格五种写法
- 非法输入拒绝：位数不足、超长、非法字符、null
- IPv4 校验：越界值、缺段、空段、非数字
- **魔术包结构**：长度 102、前 6 字节全 `0xFF`、MAC 重复 16 次
- 默认值与副标题拼接

## 设计

### 配色

主色为**柔靛 `#3F4A7E`**（低饱和深靛）。理由：绿（在线）与灰（离线）已被状态语义占用，
主色必须与两者都能清晰区分；而高饱和的紫蓝在大面积纯色平铺时会显得发亮、跳眼。

| 用途 | 色值 |
|---|---|
| 主色 | `#3F4A7E` |
| 背景 / 卡片 / 描边 | `#F7F8FC` / `#FFFFFF` / `#E7EAF2` |
| 在线 | `#10B981`（底 `#E6F7F0`） |
| 离线 | `#A0A7B4`（底 `#F0F2F6`） |
| 唤醒中 | `#F0A202`（底 `#FEF5E3`） |

扁平化：不使用阴影，靠 1dp 描边与色阶分层。

### 图标

字母 **W**（Wake / WOL 首字母）+ 底部弧线（唤起的波纹），柔靛底色。
图形整体缩至 0.9，确保落在 72dp 安全区内，适配圆形、方形、squircle 等各家遮罩。

- `ic_launcher_foreground.xml` / `ic_launcher_background.xml`：自适应图标
- `ic_launcher_monochrome.xml`：Android 13+ 主题图标
- `mipmap-anydpi/`：API 24–25 的降级矢量版本

## 已知限制

- **必须在同一广播域内**：跨网段需要路由器支持定向广播，App 未提供该配置
- **电脑建议有线连接**：无线网卡关机后大多不再接收魔术包
- **端口探测有盲区**：若目标主机关闭全部六个常见端口且 ICMP 不通，可能误判为离线
- **路由器 AP 隔离**：部分公共/访客网络禁止客户端互访，此时广播无法送达
- 关闭了深色模式，App 固定浅色主题

## 后续计划

- [ ] 设备分组与拖拽排序
- [ ] 定时唤醒（例如工作日 8:55 自动开机）
- [ ] 桌面小组件：不解锁手机直接唤醒
- [ ] 配置导入 / 导出，便于换机迁移
- [ ] 唤醒历史记录

## 许可

个人项目，未声明许可证。
