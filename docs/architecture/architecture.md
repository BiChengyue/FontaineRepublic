# FontaineRepublic Architecture

> **Revision:** v2.7
> **Status:** Architecture Frozen — Approved Baseline: v2.7
> **Related Task:** FR-ARCH-001-DESIGN-09
> **Review State:** Approved — Human Approval Granted

版本：

Alpha 0.1

Minecraft:

1.20.1

Mod Loader:

Forge

项目名称：

FontaineRepublic

中文名称：

枫丹共和国核心系统

---

# 1. 项目定位

FontaineRepublic 是一个 Minecraft Forge 服务器核心 Mod。

目标：

为枫丹共和国服务器提供国家运行框架。

Mod 不只是提供单一玩法，而是管理：

- 玩家身份

- 土地归属

- 国家经济

- 城市规划

- 政府制度

- 司法系统

- AI辅助治理

---

# 2. 核心设计原则

## 2.1 服务端权威

所有核心数据必须由服务器管理。

包括：

- 金钱
- 土地
- 身份
- 职位
- 投票
- 案件

数据流程：

Client → Network Packet → Server Logic → Data Storage

客户端只负责：
- 显示
- 输入
- 请求

禁止客户端决定游戏状态。

---

## 2.2 模块化设计

系统按照功能划分模块。

模块之间：
- 保持独立
- 使用明确接口通信
- 禁止直接修改其他模块内部数据

---

## 2.3 数据优先

开发顺序：

数据模型 → 业务逻辑 → 网络同步 → GUI

禁止先开发界面，再设计数据。

---

## 2.4 包依赖规则

### 允许的依赖方向

```
client → network → service → data
                ↘          ↗
               common ←---+
```

具体规则：
- `common` 不依赖任何其他层，只包含共享 DTO、网络包定义、标识符、侧安全的常量。**不**包含服务接口或业务逻辑
- `network` 依赖 `common`，负责序列化和传输
- `service`（服务端应用服务）依赖 `common` 和 `data`，实现业务逻辑。服务接口定义在此层
- `client` 依赖 `network` 和 `common`，禁止接触 `service` 或 `data`
- `data` 依赖 `common` 和 `core`(DataManager/ModSavedData)

### 禁止的依赖方向

| 禁止依赖 | 原因 |
|----------|------|
| `server` → `client` | 服务器绝对不能引用客户端类 |
| `data` → `network` | 数据层不关心网络传输 |
| `data` → `service` | 数据层不包含业务逻辑 |
| `service` → `client` | 业务逻辑不依赖显示层 |

违反这些依赖规则视为架构违规，必须在 Code Review 阶段拒绝。

---

# 3. 总体架构

## 3.1 三层模型

FontaineRepublic 采用 Common / Server / Client 三层架构：

```
com.fontainerepublic
├── common/     # 共享层：DTO / packet payload、网络包定义、常量
├── server/     # 服务端层：业务服务、权威数据、命令处理
└── client/     # 客户端层：GUI、HUD、本地只读缓存
```

| 层 | 职责 | 权威性 |
|----|------|--------|
| common | DTO / packet payload、包结构、常量 | 无状态契约 |
| server | 所有业务逻辑、权限校验、数据持久化 | **全部权威数据** |
| client | 显示、输入、本地缓存 | 非权威，只读镜像 |

**权威性说明：** 权威领域模型（authoritative domain models）和持久化模型（persistence models）统一位于 `server/data` 层。`common` 层不持有任何权威游戏状态，仅包含无状态的数据传输契约（DTO / packet payload）。

## 3.2 模块架构

```
FontaineRepublic
├── Core (已完成基础框架)
├── Network Layer (Phase 0 Step 4)
│
├── Feature Modules (Phase 1+)
│   ├── Citizen     — 身份系统
│   ├── Land        — 土地系统
│   ├── Economy     — 经济系统
│   ├── Audit       — 审计日志
│   ├── Government  — 政府系统
│   ├── Parliament  — 议会系统
│   ├── Justice     — 司法系统
│   └── AI Advisor  — AI 辅助
│
└── Client Systems
    ├── Screens     — GUI 界面
    ├── Overlays    — HUD 叠加层
    └── Cache       — 只读客户端缓存
```

## 3.3 模块契约

### 模块声明

每个 Feature Module 实现 `IModule` 接口，并声明：

- **Module ID**: 唯一标识，如 `"economy"`, `"citizen"`
- **Required Dependencies**: 必需的模块 ID 列表，如 `["core"]`, `["citizen"]`
- **Optional Dependencies**: 可选的模块 ID 列表，如 `["audit"]`
- **Priority**: 同层内排序用，不作为依赖解析依据

```java
public class EconomyModule implements IModule {
    @Override public String getName() { return "economy"; }
    @Override public List<String> getRequiredDependencies() { return List.of("core", "citizen"); }
    @Override public List<String> getOptionalDependencies() { return List.of("audit"); }
    @Override public int getPriority() { return 30; }

    @Override
    public void init() {
        // 初始化服务、注册处理器
    }
}
```

### 初始化顺序

```
依赖顺序优先 → 优先级其次 → 稳定断平
```

1. CoreManager 收集所有已注册模块的依赖声明
2. 按拓扑排序：被依赖的模块先初始化
3. Priority 仅用于同层模块间的初始化顺序
4. 同 Priority 的模块按注册顺序初始化（稳定断平）

### 关闭顺序

按初始化顺序的逆序关闭（先初始化的后关闭，确保依赖方先于被依赖方关闭）。

### 失败处理

| 失败场景 | 行为 |
|----------|------|
| 重复 Module ID | 注册时拒绝，日志记录冲突 |
| 缺少必需依赖 | 注册时拒绝，列出缺失的模块 ID |
| 依赖循环 | 拓扑排序时检测，拒绝注册 |
| 初始化异常 | 模块 init() 抛出异常时标记为失败，记录错误。依赖该模块的其他模块不可用。不依赖该失败模块的独立模块继续初始化 |
| 必需依赖模块初始化失败 | 依赖该模块的模块标记为不可用（在它们的 init() 被调用前跳过） |

### 传播规则

- 如果模块 A 初始化失败，且模块 B 声明了 A 为必需依赖，则 B 的初始化被跳过（无论 B 的 init() 是否会被调用）
- 如果模块 B 声明了 A 为可选依赖，B 仅在运行时检测 A 是否可用并相应降级
- 独立模块（不依赖失败模块）不受影响，正常初始化
- 所有失败和跳过记录在日志中，不阻止服务器启动

所有模块通过 CoreManager 注册，生命周期由 CoreManager 管理。



---

# 4. 模块说明

## Core

核心框架。

负责：

- Mod初始化

- 配置管理

- 数据加载

- 全局事件

---

## Citizen

居民身份系统。

负责：

- 玩家身份

- 国家成员信息

- 身份等级

身份：

- GOD

- KING

- COUNCIL

- CITIZEN

注意：

身份不等于权限。

---

## Land

土地管理系统。

最高优先级模块之一。

负责：

- Chunk归属

- 区域规划

- 建筑权限

- 建设许可

区域类型：

- CAPITAL

- RESIDENTIAL

- COMMERCIAL

- INDUSTRIAL

- PORT

- NATURE

---

## Audit

审计日志系统。

记录：

- 土地变化

- 金钱变化

- 交易

- 政府行为

- 司法记录

采用：

只追加记录。

禁止随意删除历史。

---

## Economy

经济系统。

负责：

- 玩家账户

- 国家财政

- 交易

- 资源回收

- 动态价格

价格模型：

基础价格

×

供需倍率

×

政策倍率

AI只提供分析，不直接控制经济。

---

## Resource

资源系统。

负责：

- 国家库存

- 资源统计

- 建设需求

---

## City

城市系统。

负责：

- 城市区域

- 建筑登记

- 公共设施

---

## Government

政府系统。

负责：

- 行政机构

- 官员

- 部门

---

## Parliament

议会系统。

负责：

- 提案

- 投票

- 国家工程审批

---

## Justice

司法系统。

负责：

- 案件管理

- AI辅助审理

- 上诉流程

---

## AI

AI辅助系统。

用途：

- 市场分析

- 司法分析

- 政策建议

AI不得直接修改国家数据。

---

## Client

客户端显示层。

负责：

- GUI

- HUD

- 信息显示

不保存核心数据。

---

## 4.2 服务层设计

### 服务层原则

```
# 客户端路径（有 FR Client Mod）
Client GUI
    ↓
C2S Packet
    ↓
Server Packet Handler
    ↓
Server Application Service
    ↓
Data Layer

# 命令路径（无 FR Client Mod）
Command
    ↓
Server Application Service
    ↓
Data Layer
```

**核心约束：**
- 客户端（GUI 或命令）**绝不直接访问**服务端服务对象
- 客户端**绝不持有**权威状态
- GUI 和命令共享完全相同的服务端应用服务

### 层内容定义

```
# common 层（两侧共享）
- DTO（数据传输对象）
- 网络包定义（Packet 类本身，含序列化）
- 标识符（Module ID, Phase ID）
- 侧安全的常量（两侧一致的纯数据，如枚举、配置键）

# server 层（仅服务端）
- 应用 API（服务接口，如 IEconomyService）
- 服务实现（业务逻辑）
- 数据访问对象
- 命令处理

# client 层（仅客户端）
- 屏幕
- HUD
- 只读缓存
```

### 依赖规则

- `client` 层禁止依赖 `server` 层的任何类
- 网络通信是客户端与服务端之间唯一的调用路径
- 服务接口定义在 `server` 层，不在 `common` 层共享
- `common` 层仅包含两侧一致的类型定义

### 服务定义

每个 Feature Module 定义一个显式服务接口（server/api/ 层）和实现（server/service/ 层）：

```java
// server/api/ — 服务端接口定义
public interface IEconomyService {
    long getBalance(UUID player);
    boolean transfer(UUID from, UUID to, long amount);
}

// server/service/ — 服务端实现，含所有校验逻辑
public class EconomyService implements IEconomyService {
    private final EconomyData data;
    private final PermissionService permission;

    @Override
    public boolean transfer(UUID from, UUID to, long amount) {
        // 1. 校验：发送方余额足够
        // 2. 校验：发送方有转账权限
        // 3. 执行：扣款 + 入账
        // 4. 审计：记录
        return true;
    }
}
```

### 服务初始化

服务实例在 Feature Module 的 `init()` 中构造：

```java
public class EconomyModule implements IModule {
    @Override public void init() {
        EconomyData data = new EconomyData();   // 通过 DataManager 加载
        EconomyService service = new EconomyService(data, permissionService);
        PacketHandler.registerEconomyPackets(service);
    }
}
```

### 禁止：全局 ServiceRegistry

不采用全局 ServiceRegistry 模式。服务通过模块级引用管理。跨模块访问通过服务接口（server/api/）和显式模块依赖实现。

### 服务间依赖

```
EconomyService → PermissionService, AuditService
LandService    → PermissionService, CitizenService, EconomyService
GovernmentService → CitizenService, EconomyService, LandService
```

---

# 5. 可选客户端合约

## 5.1 核心合约

FontaineRepublic 区分两个组件：**Server Core**（必需）和 **FR Client Features**（可选增强）。

### Server Core（始终必需）

以下组件始终在服务端运行，不依赖任何客户端安装：

- Forge 1.20.1 服务端
- 服务端业务逻辑
- 权威数据存储
- 命令系统（`/fr`）
- 服务端校验

### FR Client Features（可选增强）

仅当玩家安装 FR Client Mod 时可用：

- GUI 界面（屏幕、菜单、表单）
- HUD 叠加层（余额显示、通知、土地边框渲染）
- 信息可视化（图表、地图）
- 便捷交互（快捷按钮）
- 客户端只读缓存

**核心约束：** 未安装 FR Client Mod 的玩家**不得发送**任何 FR 自定义网络包。所有核心功能通过命令和标准 Minecraft 交互完成。

## 5.2 客户端状态

服务端必须处理三种客户端状态：

| 类型 | 描述 | C2S 包 | S2C 包 | 交互方式 |
|------|------|--------|--------|----------|
| **兼容客户端** | Forge 1.20.1 + FR Client Mod 匹配 | 允许全部 | 发送全部 | GUI + 命令 |
| **缺失可选客户端** | Forge 1.20.1，无 FR Client Mod | 不发送 FR 包 | 不发送 FR S2C 包 | 仅命令 + 聊天 |
| **不兼容客户端** | 协议版本不匹配 | 连接时拒绝 | 连接时拒绝 | 无法连接 |

### 状态 A: FR Client Features 可用

玩家安装 FR Client Mod，服务端检测到可选功能可用。

- 允许发送 FR 自定义 C2S 包
- 接收 FR 自定义 S2C 同步包
- 显示 GUI 界面

### 状态 B: FR Client Features 不可用

玩家使用原版 Forge 客户端，无 FR Client Mod。

- 不得发送 FR 自定义 C2S 包
- 不接收 FR 自定义 S2C 包
- 所有交互通过命令（`/fr`）和聊天消息完成
- 服务端自动将同步输出切换到聊天消息或命令返回

### 状态 C: 不兼容

Forge 版本或 FR Mod 协议版本不匹配，连接时拒绝。

## 5.3 能力检测

服务端通过以下机制检测客户端能力：

- Mod 版本在 Forge 握手阶段传递
- `PacketHandler` 根据 `PROTOCOL_VERSION` 识别不兼容客户端
- 可选功能通过 `SimpleChannel.isRemotePresent()` 检测
- 检测结果在玩家连接期间缓存

## 5.4 开发原则

1. 所有功能模块必须先实现命令交互，再实现 GUI
2. GUI 必须调用与命令相同的服务端 API，不得创建独立逻辑路径
3. 未安装 Client Mod 的玩家使用命令完成所有操作
4. 服务端的权威校验不受客户端连接方式影响
5. 发送 S2C 包前必须校验接收方客户端是否支持
6. 命令路径必须始终可用（无论客户端状态如何）

## 5.5 部署模型

### 统一 Mod JAR

FontaineRepublic 采用 **单一 Universal JAR** 部署。不区分服务端专用和客户端专用构建产物。

物理类分离配合编译期约束，防止 Dedicated Server 加载客户端代码：

```
fontainerepublic-1.20.1-0.1.0.jar
├── com/fontainerepublic/common/     # 两侧加载：DTO、包定义、常量、编解码器
├── com/fontainerepublic/server/     # 仅服务端：业务服务、命令、数据层、包处理器
└── com/fontainerepublic/client/     # 仅客户端：屏幕、HUD、渲染器、客户端事件处理器
```

### 客户端类隔离规则

| 机制 | 用途 | 局限性 |
|------|------|--------|
| **物理包分离** | 编译期确保 `server/` 不导入 `client/` 的类 | 仅防止编译期泄漏。运行时仍需 DistExecutor |
| **`DistExecutor`** | 运行时条件执行：仅当当前环境匹配时执行 lambda | 正确的客户端代码分发方式。所有客户端注册必须通过 `DistExecutor.unsafeRunWhenOn(Dist.CLIENT, ...)` |
| **客户端注册隔离** | 所有客户端事件处理器（`FMLClientSetupEvent`、屏幕注册、按键绑定）在客户端初始化类中注册，不混入公共事件总线 | 配合 `DistExecutor` 确保注册不触发服务端 |

**核心规则：** 服务端代码永远不得直接引用客户端类，包括在死代码路径或注释导入中。编译期和运行时双重防护。

> **关于 `@OnlyIn`：** `@OnlyIn(Dist.CLIENT)` 不提供运行时隔离。它是 Forge 类加载器的加载提示，不影响已加载类的引用路径。服务端代码引用客户端类仍会崩溃，即使被 `@OnlyIn` 标注。不要依赖 `@OnlyIn` 作为安全机制。

### Mod 加载显示测试

Mod 加载显示测试（display test）控制 Forge Mod 层面的版本兼容性声明，与网络协议版本无关。

推荐策略：

- `mods.toml`：`displayTest="IGNORE_SERVER_VERSION"` 或 `displayTest="NONE"`
- 搭配 `IExtensionPoint.DisplayTest`：注册自定义版本匹配逻辑

```java
ModLoadingContext.get().registerExtensionPoint(IExtensionPoint.DisplayTest.class,
    () -> new IExtensionPoint.DisplayTest(
        () -> VERSION,
        (remoteVersion, isNetwork) -> true  // 由 SimpleChannel 负责网络协议校验
    ));
```

> **注意：** `IExtensionPoint.DisplayTest` 的兼容性测试只应判断 Mod 版本兼容性。网络协议版本的匹配由 `SimpleChannel` 的 accepted predicates 处理。不要在 `DisplayTest` 中使用 `NetworkRegistry.ABSENT` —— `ABSENT` 专用于网络通道的 accepted predicate。

### SimpleChannel 网络兼容性

`NetworkRegistry.newSimpleChannel()` 通过三个元素声明网络协议兼容性：

| 元素 | 配置 | 说明 |
|------|------|------|
| **protocol version supplier** | `() -> PROTOCOL_VERSION` | 声明本端的网络协议版本号 |
| **client accepted predicate** | `version -> version.equals(PROTOCOL_VERSION)` | 客户端侧：只接受匹配的服务端协议版本。拒绝不兼容服务端 |
| **server accepted predicate** | `version -> version.equals(PROTOCOL_VERSION) \|\| version.equals(NetworkRegistry.ABSENT.toString())` | 服务端侧：接受匹配版本或未安装 Mod 的客户端。拒绝已安装但版本不兼容的客户端 |

说明：

- `NetworkRegistry.ABSENT` 是 Forge 定义的常量字符串 `"ABSENT"`。当远端未安装 Mod 时，该值作为协议版本传入 accepted predicate
- 服务器 predicate **必须**显式接受 `ABSENT` 以允许未安装 FR Client 的玩家连接
- 客户端 predicate **不**需要接受 `ABSENT` —— 如果服务端未安装 FR Mod，客户端侧的 SimpleChannel 根本不会被注册

配置示例：

```java
NetworkRegistry.newSimpleChannel(
    CHANNEL_NAME,
    () -> PROTOCOL_VERSION,                               // protocol version supplier
    version -> version.equals(PROTOCOL_VERSION),           // client predicate: accept exact match only
    version -> version.equals(PROTOCOL_VERSION)            // server predicate: accept exact match
               || version.equals(NetworkRegistry.ABSENT.toString())  // or absent client
);
```

### 运行时能力检测

握手完成后，通过 `SimpleChannel.isRemotePresent()` 运行时检测远端是否安装 FR Client Mod：

| 方法 | 触发时机 | 用途 |
|------|----------|------|
| **`SimpleChannel.isRemotePresent()`** | 连接建立后，整个生命周期可用 | S2C 包发送过滤；可选客户端功能开关 |

检测结果在玩家连接期间可缓存。发送 S2C 包前必须通过此方法确认接收方支持。

### 客户端状态与连接规则

三种客户端状态由握手结果决定：

| 状态 | 握手结果 | 连接 | 功能 |
|------|----------|------|------|
| **匹配 FR 客户端** | Server predicate 接收匹配的协议版本；`isRemotePresent() = true` | 允许 | 完整 FR 功能：GUI + S2C 同步 + 命令 |
| **服务端 FR + 客户端无 FR** | Server predicate 接收 `ABSENT`；`isRemotePresent() = false` | 允许 | 仅核心功能：命令 + 聊天，无 FR S2C 包 |
| **已安装不兼容客户端** | Server predicate 拒绝（非匹配版本、非 `ABSENT`） | 拒绝 | 连接终止，Forge 显示版本不匹配信息 |

**规则：**
- 匹配 FR 客户端：正常双向通信，C2S 包允许，S2C 包发送全部
- 服务端 FR + 无 FR 客户端：连接允许，客户端增强禁用，不发送 FR S2C 包，不接收 FR C2S 包
- 已安装不兼容客户端：server predicate 拒绝时终止连接。不接受退回到未安装模式

### 专用服务器行为

Dedicated Server 仅加载 `common/` 和 `server/` 包。`client/` 包的类在物理上存在但从未被引用或加载。所有客户端特定注册（屏幕、按键、客户端事件）通过 `DistExecutor` 隔离：

```java
// 正确：客户端注册通过 DistExecutor 隔离
public FontaineRepublic() {
    DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
        () -> ClientRegistration::register);
}

// 错误：直接调用客户端类（即使被 @OnlyIn 包裹）
// @OnlyIn(Dist.CLIENT)  // 仍会在某些加载场景崩溃
// public void registerScreens() { ... }
```

### 无 FR Client 兼容性

未安装 FR Client Mod 的客户端玩家：

- **可以连接和游玩**所有核心游戏功能
- **不发送**任何 FR 自定义 C2S 网络包
- **不接收**任何 FR 自定义 S2C 同步包
- 所有交互通过命令（`/fr`）和聊天消息完成
- 服务端通过握手结果（`Channel.isRemotePresent()`）识别此类玩家

### S2C 发送过滤规则

| 规则 | 说明 |
|------|------|
| **发送前检测** | 使用 `SimpleChannel.isRemotePresent()` 检测接收方是否安装 FR Client Mod |
| **登录阶段** | 全量同步仅发送给支持 FR Client 的玩家；不支持时使用聊天消息输出初始状态 |
| **操作响应** | 操作成功时，对无 FR Client 的玩家使用 `Player.sendSystemMessage()` 返回结果，不发送 S2C 包 |
| **广播事件** | 土地认领、政府变更等公开事件：对有 FR Client 的玩家走 S2C 广播；对无 FR Client 的玩家走聊天广播 |
| **状态更新** | 余额变动等增量更新仅在 FR Client 可用时推送 S2C 同步包 |

### 验收条件

以下条件必须在 FR Client Optional Contract 实现中得到满足：

| 条件 | 验证方式 |
|------|----------|
| Dedicated Server 正常启动，不加载任何 `client/` 包中的类 | 服务器启动日志无 ClassNotFoundException |
| 未安装 FR Client Mod 的玩家可以连接服务器 | 握手通过，进入游戏，可用 `/fr` 命令 |
| 未安装 FR Client Mod 的玩家不接收任何 FR S2C 包 | `Channel.isRemotePresent()` 返回 false 时无 S2C 包发送 |
| 已安装 FR Client Mod 的玩家接收完整 S2C 同步 | 登录全量同步，事件驱动增量同步 |
| 协议版本不匹配的客户端在握手阶段被拒绝 | Forge 显示版本不匹配错误，连接终止 |

---



# 6. 网络架构

## 6.1 通道配置

使用 Forge SimpleChannel，每个 Mod 一个通道：

- 协议版本号管理（拒绝版本不匹配的客户端）
- 按递增索引注册消息类型（禁止插入已有索引）

## 6.2 包设计模式

每个 Feature Module 定义自己的 Request/Response 包。**包类（数据 + 编解码）在 `common` 层，处理器在 `server` 层**：

```
# common 层 — 包定义（仅数据 + 编解码）
com.fontainerepublic.common.network.packets
├── economy/
│   ├── EconomyTransferPacket.java     # 转账请求 (C2S) — data + codec only
│   └── EconomyBalanceSyncPacket.java  # 余额同步 (S2C) — data + codec only
├── land/
│   ├── LandClaimPacket.java           # 认领土地 (C2S)
│   └── LandOwnershipSyncPacket.java   # 所有权同步 (S2C)
└── citizen/
    ├── CitizenInfoRequestPacket.java  # 信息请求 (C2S)
    └── CitizenProfileSyncPacket.java  # 资料同步 (S2C)

# server 层 — 包处理器（业务逻辑）
com.fontainerepublic.server.network.handlers
├── economy/
│   └── EconomyTransferHandler.java   # EconomyTransferPacket → EconomyService
├── land/
│   └── LandClaimHandler.java
└── citizen/
    └── CitizenInfoRequestHandler.java
```

## 6.3 包处理流程

### 处理器契约

每个 C2S 包的服务端处理器必须遵循两阶段流程：

#### 阶段一：网络线程（传输校验）

执行于网络 IO 线程。仅做快速结构性校验，不接触游戏状态：

```
接收包
    ↓
校验方向 (确保是 C2S，拒绝 S2C 错位)
    ↓
校验发送者 (ctx.get().getSender() 不为 null)
    ↓
结构性负载校验 (参数结构合法，如目标非空)
    ↓
入队服务端线程 (ctx.get().enqueueWork)
    ↓
标记包已处理 (ctx.get().setPacketHandled(true))
```

此阶段**不得**：
- 访问游戏世界状态
- 执行业务校验（权限、余额、所有权）
- 调用 Service 层方法
- 修改任何数据

#### 阶段二：服务端线程（业务处理）

执行于服务端主线程。所有业务校验和操作在此阶段执行：

```
入队 lambda 开始
    ↓
调用 Service 层方法 (EconomyService.transfer(...))
    ↓
Service 内部统一校验: 权限 → 所有权 → 余额 → 规则约束
    ↓
执行业务操作
    ↓
同步结果 — 检测客户端能力
    ├── FR Client 可用 → 发送 S2C 同步包
    └── FR Client 不可用 → Player.sendSystemMessage()
    ↓
lambda 结束
```

所有代码路径（包括失败路径）必须最终调用 `ctx.get().setPacketHandled(true)`。

### 两阶段责任表

| 阶段 | 线程 | 校验类型 | 可访问状态 | 失败处理 |
|------|------|----------|-----------|----------|
| 一：网络线程 | Netty IO 线程 | 方向、发送者、结构 | 仅包字段 | 静默拒绝，不入队 |
| 二：服务端线程 | Server 主线程 | 权限、所有权、状态、业务 | 完整游戏状态 | 静默拒绝，可记录审计日志 |

### 核心规则

| 规则 | 说明 |
|------|------|
| **不信任客户端** | 服务端对所有客户端发送的数据在服务端线程重新校验。业务校验不可因网络线程已检查结构而省略 |
| **服务端线程执行** | 所有写入数据的操作必须在服务端主线程执行 |
| **失败处理** | 任意校验阶段失败则静默拒绝，不向客户端返回内部错误细节。关键业务失败可记录审计日志 |
| **条件同步** | 同步前检查接收客户端是否支持可选 FR Client 功能。不支持时使用聊天消息替代 S2C 包 |
| **全路径标记** | 所有代码路径（成功、失败、异常）必须最终调用 `setPacketHandled(true)` |

## 两阶段处理器模式

包类（common 层）仅包含数据定义和编解码，不包含处理器逻辑：

```java
// common/network/packets/economy/EconomyTransferPacket.java
// 仅数据 + 编解码。无服务端类引用，无业务逻辑。
public class EconomyTransferPacket {
    private final UUID target;
    private final long amount;

    public EconomyTransferPacket(UUID target, long amount) {
        this.target = target;
        this.amount = amount;
    }

    // Forge codec-based serialization (1.20.1)
    public static final Codec<EconomyTransferPacket> CODEC = RecordCodecBuilder.create(inst ->
        inst.group(
            UUIDUtil.CODEC.fieldOf("target").forGetter(EconomyTransferPacket::getTarget),
            Codec.LONG.fieldOf("amount").forGetter(EconomyTransferPacket::getAmount)
        ).apply(inst, EconomyTransferPacket::new)
    );

    public UUID getTarget() { return target; }
    public long getAmount() { return amount; }
}
```

处理器类（server 层）通过模块级服务引用调用业务服务：

```java
// server/network/handlers/economy/EconomyTransferHandler.java
// 服务端处理器。通过模块级引用持有服务实例，非全局静态访问。
public class EconomyTransferHandler {
    private final IEconomyService economyService;

    public EconomyTransferHandler(IEconomyService economyService) {
        this.economyService = economyService;
    }

    public void handle(EconomyTransferPacket packet, Supplier<NetworkEvent.Context> ctx) {
        // === 阶段一：网络线程 — 传输校验 ===

        // 1. 校验方向
        if (ctx.get().getDirection() != NetworkDirection.PLAY_TO_SERVER) {
            ctx.get().setPacketHandled(true);
            return;
        }

        // 2. 校验发送者
        ServerPlayer player = ctx.get().getSender();
        if (player == null) {
            ctx.get().setPacketHandled(true);
            return;
        }

        // 3. 传输校验完成，入队服务端线程
        ctx.get().enqueueWork(() -> {
            // === 阶段二：服务端线程 — 调用业务服务 ===

            // 业务校验（权限、余额、所有权）在 Service 内部统一执行
            // 通过模块级服务引用调用，非全局静态访问
            economyService.transfer(player.getUUID(), packet.getTarget(), packet.getAmount());
        });
        ctx.get().setPacketHandled(true);
    }
}
```

模块初始化时将服务实例传递给处理器：

```java
// server/module/EconomyModule.java — 模块初始化
public class EconomyModule implements IModule {
    private IEconomyService economyService;

    @Override public void init() {
        EconomyData data = new EconomyData();
        this.economyService = new EconomyService(data, permissionService);

        // 注册处理器，传递服务引用
        CHANNEL.registerMessage(
            EconomyTransferPacket.class,
            EconomyTransferPacket.CODEC,
            new EconomyTransferHandler(economyService)::handle,
            NetworkDirection.PLAY_TO_SERVER
        );
    }
}
```

## 6.4 同步策略

| 策略 | 时机 | 范围 |
|------|------|------|
| 全量同步 | 玩家加入服务器 | 个人资料、余额、土地列表 |
| 增量同步 | 状态变更后 | 仅发送变化的字段 |
| 广播 | 公开事件 | 土地认领、政府变更 — 通知所有在线玩家 |
| 请求-响应 | 客户端按需请求 | 交易历史等不常用数据 |

## 6.5 安全规则

- 服务端不信任任何客户端发送的数据，所有字段在服务端重新校验
- 速率限制：记录每个玩家的包频率，超过阈值则忽略
- 操作幂等性或可回滚

## 6.6 校验分离

### 传输校验（网络层）

负责于包到达服务端应用服务之前，在网络层（Packet Handler）完成：

| 校验项 | 说明 |
|--------|------|
| **包方向** | 确认包方向合法（C2S 包必须在 PLAY_TO_SERVER 方向接收） |
| **包大小** | 拒绝超出协议限制的包负载 |
| **速率限制** | 记录每个玩家的包频率，超过阈值时静默丢弃 |
| **连接状态** | 确认发送者在线且连接有效 |

传输校验失败时静默拒绝，不返回内部细节。

### 业务校验（服务层）

负责于服务端应用服务中，在 Service 层完成：

| 校验项 | 说明 |
|--------|------|
| **权限** | 发送者是否拥有执行该操作的权限 |
| **所有权** | 发送者是否拥有操作目标的所有权 |
| **金额** | 操作金额是否为正数 |
| **余额** | 操作所需资金是否充足 |
| **游戏规则** | 操作是否符合当前游戏规则和状态 |

业务校验属于服务端应用服务的职责。网络层（Packet Handler）和命令层（Command Layer）**不得重复实现**业务校验逻辑。网络层和命令层调用 Service 层方法，由 Service 层统一执行业务校验。

### 分层示例

```
# 网络层（Packet Handler）— 仅传输校验
# 不执行PermissionService.canTransfer()、不检查余额
PacketHandler.validateDirection(ctx);       // 传输：包方向
PacketHandler.validateRateLimit(player);    // 传输：速率限制
EconomyService.transfer(from, to, amount);  // 业务：委托给 Service

# 命令层（Command）— 仅参数解析和语法校验
# 不执行PermissionService.canTransfer()、不检查余额
Command.parseArgs(context);                 // 语法：参数解析
Command.validateSyntax(player, target, amount); // 语法：参数格式
EconomyService.transfer(from, to, amount);  // 业务：委托给 Service

# 服务层（Service）— 统一业务校验 + 业务操作
# 权限、余额、所有权、游戏规则全部在 Service 内部完成
EconomyService.transfer(from, to, amount) {
    validatePermission(from);       // 业务：权限
    validateOwnership(from, to);    // 业务：所有权
    validateBalance(from, amount);  // 业务：余额
    validateAmount(amount);         // 业务：金额为正
    validateGameRules(from, amount);// 业务：游戏规则
    executeTransfer(from, to, amount); // 执行
}
```

---



# 7. 命令层架构

## 7.1 命令即第一代用户界面

在 FontaineRepublic Client Mod 开发完成前，命令是所有玩家的官方交互方式。

命令不是临时调试工具。命令是第一代用户界面。后续开发的 Client GUI 必须调用与命令相同的服务端 API，不得创建独立的逻辑路径。

## 7.2 命令流程

```
玩家输入
    ↓
命令解析 (CommandLayer) — 仅参数解析和语法校验
    ↓
调用 Service 层方法 (EconomyService.transfer(...))
    ↓
Service 内部统一校验: 权限 → 余额 → 所有权 → 规则约束
    ↓
执行业务操作
    ↓
数据层 (DataManager → ModSavedData)
```

**命令层仅负责参数解析和命令分发。** 所有业务校验（权限、余额、所有权、游戏规则）属于 Service 层，命令层不得重复实现这些规则。命令层调用 Service 接口即可，Service 内部统一执行业务校验。

## 7.3 命令规范

所有命令以 `/fr` 为根命令，按功能划分子命令：

```
/fr citizen info [player]        — 查询身份信息
/fr citizen list                  — 在线居民列表

/fr money balance [player]       — 查询余额
/fr money transfer <目标> <金额>   — 转账

/fr bank deposit <金额>           — 存款
/fr bank withdraw <金额>          — 取款

/fr land info                    — 当前地块信息
/fr land claim                   — 认领地块

/fr admin reload                 — 重载配置
/fr admin save                   — 强制保存
```

## 7.4 包结构

```
server/command/
├── FRCommand.java                # 根命令注册 (/fr)
├── CitizenCommand.java           # 身份子命令
├── EconomyCommand.java           # 经济子命令
├── LandCommand.java              # 土地子命令
└── AdminCommand.java             # 管理命令
```

## 7.5 命令与 GUI 的关系

| 维度 | 命令 | GUI |
|------|------|-----|
| 适用客户端 | 任何 Forge 客户端 | 仅安装 FR Client Mod |
| 后端 API | 调用 Service 层 | 调用相同的 Service 层 |
| 数据同步 | 显示返回结果 | S2C 包实时同步 |
| 开发阶段 | Phase 1+ 每个模块的首个交互接口 | Phase 8 集中开发 |

命令和 GUI 共享完全相同的服务端校验逻辑。GUI 不得引入命令路径中不存在的校验。

---

# 8. 数据存储原则

## 8.1 基础框架

采用 Minecraft SavedData (ModSavedData) + NBT。

现有框架提供：
- `DataManager.getModuleData(key)` — 读取模块数据
- `DataManager.putModuleData(key, tag)` — 写入模块数据

## 8.2 数据模型版本管理

每个模块数据包含 `schemaVersion` 字段：

```java
CompoundTag moduleTag = new CompoundTag();
moduleTag.putInt("schemaVersion", 1);
moduleTag.put("data", actualData);
```

当前版本：`1`

版本号变更场景：
- `schemaVersion` 不变：向后兼容的字段添加（新字段有默认值）
- `schemaVersion` +1：破坏性变更（重命名字段、类型变更、结构重组）

## 8.3 数据迁移策略

### 完整迁移流程

```
加载数据
    ↓
检查 schemaVersion
    ↓
需要迁移?
    ├── 否 → 正常使用
    └── 是
         ↓
    执行逐版本迁移 (v → v+1 → v+2 → ...)
         ↓
    校验迁移后数据完整性
         ↓
    ✅ 迁移已验证 (Migration validated)
         ↓
    写入 DataManager (putModuleData)
         ↓
    ✅ 已应用于内存 (Applied in memory)
         ↓
    标记 setDirty()
         ↓
    ✅ 已标记脏数据 (Marked dirty)
         ↓
    ✅ 可被持久化 (Eligible for persistence)
         ↓
    Minecraft 保存周期触发写入
         ↓
    ✅ 已由 Minecraft 持久化 (Persisted by Minecraft save cycle)
```

**状态定义：**

| 状态 | 含义 | 可恢复性 |
|------|------|----------|
| Migration validated | 迁移步骤执行完毕，校验通过 | 仍在内存中，可丢弃回退 |
| Applied in memory | 数据已写入 DataManager，可被读取 | 内存状态，未持久化 |
| Marked dirty | DataManager 已标记脏，等待保存 | 崩溃后丢失 |
| Eligible for persistence | 数据已准备好等待 Minecraft 保存周期写入 | 写入前崩溃需重新迁移 |
| Persisted by Minecraft save cycle | Minecraft 已将数据写入磁盘 | 崩溃后可恢复 |

迁移不提供数据库级别的事务保证。迁移步骤应在数据写入前验证完整性，但不保障磁盘写入瞬间的原子性。如果迁移写回后但在磁盘保存前服务器崩溃，下次启动时将从上一个已持久化的数据版本重新加载并重新执行迁移。

### 可变数据所有权模型

所有模块数据修改**必须**通过 `putModuleData()` 完成。模块读取数据，创建防御性副本后修改并写回：

```java
// 通过 DataManager API 写入
CompoundTag tag = DataManager.getModuleData(MODULE_KEY).copy();  // 防御性副本
tag.putLong("balance", newBalance);
tag.putString("status", "active");
DataManager.putModuleData(MODULE_KEY, tag);  // 显式写回
```

`DataManager.putModuleData()` 自动执行：
1. 用新 NBT 替换内存中的模块数据
2. 标记 `setDirty()` 等待 Minecraft 保存周期持久化

`DataManager.getModuleData()` 当前返回内部 `CompoundTag` 的直接引用。直接修改可绕过校验路径，且若未来实现防御性副本则修改会丢失。因此所有模块数据写入**必须**遵循 copy → modify → putModuleData 模式。

#### 所有权规则

| 操作 | 规则 |
|------|------|
| **读取** | 允许直接读取返回的 `CompoundTag`。模块可安全读取所有字段 |
| **写入** | 先 `copy()` 创建防御性副本，修改副本，调用 `putModuleData()` 写回 |
| **持有引用** | 后续 `putModuleData` 写入新 NBT 时，旧引用指向已被替换的旧数据 |
| **跨模块共享** | 模块**不得**将自己的 `getModuleData` 返回的 `CompoundTag` 引用传递给其他模块。每个模块只能通过自己的 key 访问自己的数据 |

**核心原则：** 模块对其自身 key 的 NBT 具有读写所有权。对其他模块 key 的 NBT 无任何访问权。`DataManager` 仅提供按 key 的存取，不实现跨模块数据共享。

### 迁移实现

```java
public void load() {
    CompoundTag tag = DataManager.getModuleData(DATA_KEY);
    int version = tag.getInt("schemaVersion");

    if (version < CURRENT_VERSION) {
        try {
            tag = migrate(tag, version, CURRENT_VERSION);
            // 校验迁移后数据
            if (!validateMigratedData(tag)) {
                LOGGER.error("[Data] Migration validation failed for {}", DATA_KEY);
                return; // 保留原数据，不写入
            }
            // 写回内存并标记脏数据
            DataManager.putModuleData(DATA_KEY, tag);
            LOGGER.info("[Data] Migration {}: v{} → v{} applied; pending Minecraft persistence", DATA_KEY, version, CURRENT_VERSION);
        } catch (Exception e) {
            LOGGER.error("[Data] Migration {}: v{} → v{} failed: {}", DATA_KEY, version, CURRENT_VERSION, e);
            // 失败时不修改原始数据
        }
    }
}

private CompoundTag migrate(CompoundTag old, int fromVersion, int toVersion) {
    CompoundTag working = old.copy();
    for (int v = fromVersion; v < toVersion; v++) {
        working = migrateStep(working, v, v + 1);
        if (working == null) throw new RuntimeException("Migration step " + v + "→" + (v+1) + " returned null");
    }
    return working;
}
```

### 失败处理

| 场景 | 行为 |
|------|------|
| 迁移步骤抛出异常 | 记录错误日志，保留原始数据，不写回 |
| 迁移后校验失败 | 记录错误日志，保留原始数据，不写回 |
| 写回内存后崩溃 | 已标记脏数据但未写入磁盘。下次启动从上一个持久化版本重新加载并执行迁移 |
| 数据完全损坏 | 管理员手动恢复备份（备份策略见下） |

### 备份与恢复

- 重要迁移前，建议管理员手动备份 `world/data/fontainerepublic.dat`
- 迁移本身不自动创建备份（不引入额外 I/O 延迟）
- 如果迁移失败，可回滚到备份文件后重新启动服务器

## 8.4 模块数据隔离

```
ModSavedData (DataManager)
├── "economy"    → EconomyData (schemaVersion, balances, treasury)
├── "citizen"    → CitizenData (schemaVersion, profiles, roles)
├── "land"       → LandData (schemaVersion, claims, regions)
├── "government" → GovernmentData (schemaVersion, structure, officials)
└── "audit"      → AuditData (schemaVersion, log entries)
```

每个模块的数据完全隔离：
- 模块只能读写自己的 key
- 跨模块数据访问通过 API 接口，不直接操作其他模块的 NBT
- `schemaVersion` 按模块独立管理

## 8.5 存储拆分考量

当前所有数据存储在单个 `ModSavedData` 文件中。当以下可测量指标表明性能受影响时，考虑拆分为每个 UUID 一个独立文件：

### 衡量指标

| 指标 | 说明 | 警告阈值 |
|------|------|----------|
| **文件大小** | `fontainerepublic.dat` 物理大小 | 持续增长，不可控 |
| **加载时间** | 服务器启动时 SavedData 反序列化耗时 | > 500ms |
| **保存时间** | 服务器停止时 CompoundTag 序列化耗时 | > 500ms |
| **Tick 影响** | `setDirty()` 后续保存操作的 tick 耗时 | 引起可感知的卡顿 |

### 拆分策略

1. 将 Player 相关数据（玩家资料、余额）拆分为每个 UUID 一个独立 `PlayerDataStorage` 文件
2. 全局数据（经济总量、土地登记、政府结构）保留在主文件
3. 此迁移在 Alpha 阶段不做，仅在性能数据驱动下实施

---

# 9. 开发路线图

## 9.1 开发阶段总览

```
Phase 0: Core Framework
    ↓
Phase 0.5: Audit Foundation
    ↓
Phase 1: Citizen & Identity
    ↓
Phase 2: Economy
    ↓
Phase 3: Land & City
    ↓
Phase 4: Infrastructure & Transportation
    ↓
Phase 5: Government
    ↓
Phase 6: Justice
    ↓
Phase 7: Industry & Society
    ↓
Phase 8: Client Enhancement
```

**阶段依赖原则**：每个阶段在其前一阶段完成后方可开始。跳过阶段会导致缺失依赖数据或服务。

## 9.2 Phase 0 — Core Framework

### 剩余任务

**Step 4: Network Foundation**
- 创建 PacketHandler (SimpleChannel 注册，版本管理)
- 定义基础网络包结构
- 实现玩家加入时的全量同步流程
- 验证：服务端启动、包收发正常

**Step 5: Command Framework**
- 创建 `server/command/` 基础命令结构 (`/fr`)
- 注册管理员调试命令（查询状态、查看数据等）

**Step 6: Permission Framework (Framework Only)**
- 权限框架基础结构
- OP / Bootstrap 权限（基于 Minecraft OP 的开机权限）
- UUID 基础的身份校验
- 系统级权限（管理员 vs 普通玩家）
- 权限检查 API 接口定义

**注意：** Phase 0 的权限框架仅提供基础的系统级权限。公民身份、角色、社交权限、政府权限由 Phase 1 引入，在 Citizen 模块存在之前这些功能不可用。

## 9.3 Phase 0.5 — Audit Foundation

在进入功能模块之前建立审计基础框架。

### 最低契约

Phase 0.5 Audit Foundation **必须**提供：

| 组件 | 说明 | 位置 |
|------|------|------|
| **`AuditEvent`** | 审计事件数据结构。包含事件类型、时间戳、关联玩家、事件数据等字段 | `common/` |
| **`AuditData`** | 审计数据存储。基于 ModSavedData 的只追加事件日志 | `server/data/` |
| **`AuditService.record()`** | 审计记录服务接口。系统模块调用此接口写入审计事件 | `server/api/` |

### 延迟范围

以下功能在 Phase 0.5 **不实现**，延迟到对应功能模块阶段：

| 延迟项 | 目标阶段 | 原因 |
|--------|----------|------|
| 审计报告生成 | Phase 5+ | 需要 Government 定义报告格式 |
| 审计查询命令 | Phase 5+ | 命令层开发在功能模块阶段 |
| 审计 GUI | Phase 8 | Client Enhancement 阶段集中实现 GUI |
| 审计数据分析 | Phase 5+ | 需要 Government 和 Justice 定义分析需求 |

### 集成规则

- `AuditService.record()` 是同步调用，**必须**在服务端主线程调用
- 事件数据使用 NBT（`CompoundTag`）编码，存储在 `ModSavedData` 的 `"audit"` key 下
- 审计日志是只追加结构，禁止删除或修改已记录的事件
- 功能模块在各自的 Phase 中接入 `AuditService`，Phase 0.5 仅提供框架

### 阶段依赖

- Audit Foundation **不依赖**任何 Feature Module
- Feature Module **可选依赖** Audit Foundation

---

## 9.4 Phase 1 — Citizen & Identity

第一步实现完整的 Feature Module，验证整个架构通路。

- CitizenData（玩家资料、角色、身份等级）
- CitizenService（身份管理）
- CitizenCommand (`/fr citizen`)
- 玩家加入/离开时的数据加载与保存
- 网络同步：首次加入全量同步

## 9.5 Phase 2 — Economy

以经济系统作为首条完整功能线，验证模块间协作。

- EconomyData（玩家账户、国家财政）
- EconomyService（转账、交易、动态价格）
- EconomyCommand (`/fr money`, `/fr bank`)
- EconomyTransferPacket / EconomyBalanceSyncPacket
- AuditService 接入（所有交易记录审计日志）

## 9.6 Phase 3 — Land & City

土地系统依赖经济（认领需要资金）和身份（需要公民身份）。

- LandData（Chunk 归属、区域规划）
- LandService（认领、权限、转让）
- LandCommand (`/fr land`)
- LandClaimPacket / LandOwnershipSyncPacket
- City 数据模型（城市区域、公共设施）

## 9.7 Phase 4 — Infrastructure & Transportation

基础设施需要已有的空间结构（Land）和经济资源（Economy）支撑。

- 道路网络
- 交通系统
- 公共设施管理
- 资源运输

## 9.8 Phase 5 — Government

政府系统需要已有的身份、经济、土地和基础设施数据。

- GovernmentData（行政机构、部门、官员）
- GovernmentService
- GovernmentCommand
- 官员任命与管理

## 9.9 Phase 6 — Justice

司法系统需要政府结构支持。

- JusticeData（案件、审理记录）
- JusticeService（案件管理、审理流程）
- 上诉流程
- AI 辅助分析（建议，不直接裁决）

## 9.10 Phase 7 — Industry & Society

在基础设施和经济系统之上构建社会系统。

- 资源生产与消耗
- 商业活动
- 社会发展指标
- Parliament 议会与提案系统

## 9.11 Phase 8 — Client Enhancement

各功能模块的 GUI 集中开发。所有 GUI 调用已有 Service 层 API。

- 经济界面（钱包、交易记录）
- 土地界面（地图、地块信息）
- 政府界面（组织结构、官员管理）
- HUD 叠加（余额、通知、土地边框）

## 9.12 开发纪律

1. 每个 Feature Module 必须按 **数据模型 → 业务逻辑 → 命令 → 网络同步 → GUI** 顺序开发
2. 未声明的依赖模块未完成时，不得开始开发该模块
3. 每个 Phase 完成时必须通过 Build Verification
4. 命令接口必须在 GUI 之前完成
5. 跨阶段跳过需要架构审查批准

---

# 10. 禁止事项

开发过程中禁止：

1. GUI先行

2. 客户端保存核心数据

3. 模块直接修改其他模块内部数据

4. 为未来需求引入不必要复杂架构

5. 未经过架构审查直接增加大型系统

---

# 11. 治理流程

## 11.1 开发工作流

```
设计 (Architecture Design)
    ↓
Human-approved Task Card
    ↓
实现 (Implementation)
    ↓
构建验证 / 运行时验证
    ↓
Implementation Report
    ↓
独立审查 (Independent Review)
    ↓
修复 / 重新审查 (Fix / Re-review)
    ↓
Human Approval
    ↓
合并 (Merge)
```

## 11.2 角色职责

| 角色 | 职责 |
|------|------|
| **审阅者 (Reviewer)** | 验证、建议、识别风险。产生 Audit Report 作为审阅建议 |
| **Human** | 批准、授权、决定阶段转换。产生 Approval Record 作为最终决定 |

## 11.3 关键规则

### 审阅建议 ≠ Human 批准

- Reviewer 验证实现正确性、架构一致性和证据分类准确性
- 审阅结果（Audit Report）是建议性的，**不是**批准
- Reviewer 使用 "审阅建议"、"推荐" 等措辞，不使用 "批准"
- Human 拥有最终决定权

### Human 批准是最终权限

- Architecture Review 不激活变更
- Reviewer 通过审阅不等同于批准合并
- 只有 Human 可以批准合并、授权部署、确定争议项

### 工作流约束

- Implementation Report 必须区分 Repository Observable Evidence 和 Implementer Secondary Claims
- Review 由独立 Reviewer（非 Implementer）执行
- Fix/Re-review 循环在 Human Approval 之前完成

