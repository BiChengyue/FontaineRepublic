# FontaineRepublic Client UI Module Architecture v1.1

> **Task ID:** FR-CLIENT-001-A
> **Revision:** FR-CLIENT-001-A-REVIEW-01（独立审查修正：网络展示面落地 + 分阶段）
> **Status:** Design Candidate — Independently Reviewed（Pending Human Approval）
> **Project:** FontaineRepublic
> **Platform:** Minecraft Forge 1.20.1 / Forge 47.4.18 / Java 17
> **Purpose:** Optional client mod for visual operations (FR Client Features)
> **Dependency:** FR-NET-001-A（channel/protocol）、已实现服务端模块、FR-CMD-001-A
> **Implementation Status:** 分阶段实现（服务端开发已确认完整；Human 既定顺序
>  "服务端完成后继续客户端模组"）

---

## 1. Purpose

Human directive (2026-08-13): finally develop the UI-enabled client mod for
visual operations. This design defines the optional client as a **presentation
and input-forwarding layer** per architecture v2.7 §5: it never decides state,
never stores authoritative data, and never bypasses server validation.

First-release scope (visual operations for implemented features):

- Economy: balance card, transaction history view, transfer form;
- Citizen: identity/rank card;
- Land: parcel/usage info view (region read-only);
- Government/Parliament/Court: public info views and status;
- Notifications: incoming transfer / case updates / bill milestones;
- Guidance: in-client help linking to `/fr help` and the player guide.

## 2. Normative Boundaries

| Concern | Authority |
|---|---|
| All state | Server |
| Client | Display, input forwarding, optimistic presentation only |
| Mutations | Re-enter the same server command/Service paths |
| Presence/facility | Never asserted by client (FR-INST-002) |
| No-client parity | Preserved; commands/chat remain fully functional |

Rules (architecture v2.7 §5):

- Client cache is optimistic display only; never authority;
- Every mutation submits via the same server path (commands or C2S request
  packets that carry operation+params only — server resolves identity/context);
- No authoritative C2S packet; no client-side permission/business decisions;
- S2C sync is non-authoritative presentation (FR-NET-001-A);
- `client/` package isolated via DistExecutor + package rules; server never
  imports client classes;
- Protocol version mismatch rejected at handshake (existing channel).

## 3. Client Structure

```text
com.fontainerepublic.client/
├── ClientManager.java            # DistExecutor-init; registries
├── gui/
│   ├── money/                    # balance/history/transfer screens
│   ├── citizen/                  # identity card
│   ├── land/                     # parcel info (read-only)
│   ├── institution/              # government/parliament/court info
│   └── guide/                    # in-client help
├── hud/                          # balance/notification HUD
└── net/                          # ClientNetworkExecutor + presentation cache
```

Screens are thin views over server-provided projections; forms submit through
the same command/Service entry points (or approved C2S request packets that
carry no authority).

## 4. Network Presentation Surface（v1.1 修正）

### 4.1 现状核对（FR-CLIENT-001-A-REVIEW-01）

现有网络基线（FR-NET-001 实现）：

- `NetworkProtocol.VERSION = "1"`；client 谓词仅接受精确版本，server 谓词
  额外接受 `ABSENT.version()`；
- `NetworkProductionMessageTable` 注册零消息，且 `NetworkBootstrap`
  `registerProductionMessagesAndFreeze()` 强制 `messageCount == 0`；
- 仅存在 `ServerNetworkDispatcher`：`PLAY_TO_CLIENT` 消息到达接收侧时经
  `context.enqueueWork` 后在接收侧主线程调用 `spec.handler().handle(...)`；
- `NetworkSendService`（presence 过滤的 S2C 发送门面）已实现但尚无调用方。

客户端展示面因此必须：

1. 向 `NetworkProductionMessageTable` 追加 S2C 展示消息（ID 从 0 起，
   append-only，符合 FR-NET-001-A §7.1 预留）；首次发布仅注册 economy 展示
   消息（0-2），公民/机构/土地信息随后续阶段追加；
2. 将 `NetworkProtocol.VERSION` 提升为 `"2"`（消息表变更 = 不兼容变更，
   FR-NET-001-A §6.4）；`serverAccepts`/`clientAccepts` 同步使用新版本；
3. 移除 `NetworkBootstrap` 的“必须为空”强制（该约束是 FR-NET-001 基线占位；
   架构 §7.1 已预期首个生产消息获得 ID 0）；保留 freeze 与重复/越界校验；
4. S2C handler 采用 `DistExecutor.safeRunWhenOn(Dist.CLIENT, ...)` 侧隔离：
   专用服务器永不求值该 supplier，从而不加载 `client/` 类（FR-NET-001-A
   §9.3）；handler 本体留在 common 包，仅以 lambda 引用客户端执行器；
5. 服务端发送钩子由功能模块持有：economy 模块声明 `network` 运行时依赖，
   通过运行时解析的 `NetworkSendService` 发送；发送前 presence 过滤，absent
   客户端零影响（no-client parity）。

### 4.2 首次发布消息账本（append-only）

| ID | 消息 | 方向 | 载荷（有界） | 触发 |
|---:|---|---|---|---|
| 0 | `BalanceSyncPacket` | PLAY_TO_CLIENT | balance(long)、货币名/符号(字符串≤32)、at(long)、seq(long) | 登录/余额变更后 |
| 1 | `TransactionNotifyPacket` | PLAY_TO_CLIENT | transactionId(long)、direction(byte)、amount(long)、对方摘要(≤64 hex)、memo(可空≤128)、at(long) | 本人参与的转账成功后 |
| 2 | `NotificationPacket` | PLAY_TO_CLIENT | count(int≤128)+[(notificationId, amount, memo?)] | 登录后交付待读通知摘要 |

后续阶段追加：`CitizenInfoPacket`(3)、`InstitutionInfoPacket`(4)、
`LandInfoPacket`(5) —— 每次追加按 §6.4 决定是否提升协议版本。

### 4.3 侧隔离与无客户端兼容

- 消息类与 handler 位于 `common/` 或 `server/`，不得 import `net.minecraft.client.*`
  或 `com.fontainerepublic.client.*`；
- S2C handler 内 `DistExecutor.safeRunWhenOn(Dist.CLIENT, () -> () ->
  ClientNetworkExecutor.accept(msg, ctx))`；专用服务器上 supplier 不求值，
  client 类不加载；
- **Forge 限制（v1.1 修正）：** `DistExecutor.safeRunWhenOn` 的 safe-referent
  校验只接受 Minecraft/client 包作为 referent，mod 自有客户端类会触发
  "Unsafe Referent usage" 并导致加载失败。因此：
  - S2C 展示 handler（common 包，服务端永不执行该路径）仍可用 safe 变体；
  - 主入口客户端初始化改用 `FMLClientSetupEvent` 监听器（仅物理客户端触发），
    监听器方法体引用 `ClientManager` 惰性解析，专用服务器不加载 client/ 类；
- 发送端每次 `trySendToPlayer`；`REMOTE_CHANNEL_ABSENT`/`CONNECTION_NOT_LIVE`
  为非异常结果，业务照常（命令/聊天为兜底）；
- 客户端展示缓存仅内存、非权威、随连接/登出清空。

## 5. Acceptance Matrix

| Test | Expected |
|---|---|
| No-client parity | 无客户端时全部功能可用，无 FR 包发送 |
| Client cache non-authoritative | 客户端缓存不参与任何判定 |
| Mutation revalidation | 表单/请求仍经服务端校验 |
| No presence spoof | 客户端不能断言现场上下文 |
| Package isolation | 专用服务器不加载 client/ 类；server/ 不引用 client/ |
| Handshake mismatch | 协议 v2 与 v1/absent 按谓词处理：v1 拒绝、absent 放行 |
| S2C presentation only | 展示包仅携带展示数据，无权威字段 |
| Guidance present | 游戏内引导 + 玩家指南链接 |
| 消息账本 | ID 0 起 append-only；重复/越界注册拒绝；freeze 单向 |

## 6. Non-Goals

- 客户端存储/权威、权限判定、业务逻辑、完整地图/渲染覆盖层（后续修订）；
- 权威 C2S 包（首发无 C2S 业务包；表单走命令路径）；
- 紧急动作（FR-EMG）客户端展示（管理员专属，控制台路径）；
- 实施（分阶段实现中）。

## 7. Staging（v1.1 修正）

### Stage A — 网络展示面（FR-CLIENT-001-IMPL-A）

- 协议 v2 + 消息账本（ID 0-2）+ 空表约束替换；
- S2C handler 侧隔离（DistExecutor.safe）+ ClientNetworkExecutor 骨架与
  非权威展示缓存；
- economy 模块 `network` 依赖 + 运行时发送接线（登录余额/待读通知、转账
  成功双向通知）；
- 契约测试（编解码边界、账本唯一性、谓词、侧隔离静态核验）+ 完整构建。

### Stage B — GUI/HUD（FR-CLIENT-001-IMPL-B）

- balance card/HUD、history 视图、transfer 表单（命令路径提交）；
- citizen 卡；机构/土地信息视图（依赖后续展示消息或登录快照）；
- 游戏内引导页。

### Stage C — 验证与打磨

- 真机客户端连服验证（Human）；无客户端平行验证；崩溃窗口；性能与边界打磨。

## 8. Review Gate

独立审查（FR-CLIENT-001-A-REVIEW-01）已完成：确认 v1.1 修正覆盖网络基线
冲突、协议版本、侧隔离、发送接线与分阶段。设计候选仍需 Human 批准后才算
正式生效；实现按用户既定顺序分阶段推进。
