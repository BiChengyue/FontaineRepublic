# FR-CLIENT-001-A Java Implementation Task（Stage A）

> **Status:** Prepared — Authorized（服务端开发完整；设计 v1.1 审查通过）
> **Task Type:** 客户端展示网络面
> **Design Input:** FR-CLIENT-001-A v1.1 §4/§7；FR-NET-001-A §6/§7/§9/§11
> **Dependency:** FR-NET-001（已实现）、FR-ECO-001/FR-ECO-002（已实现）

## 1. Goal

建立客户端展示所需的 S2C 网络面，暂不实现 GUI：

1. 协议版本提升至 `"2"`（`NetworkProtocol.VERSION` 与谓词同步）；
2. `NetworkProductionMessageTable` 注册 3 个 S2C 展示消息（ID 0-2）；
3. `NetworkBootstrap` 空表强制替换为期望账本计数校验；
4. S2C handler 侧隔离（`DistExecutor.safeRunWhenOn(Dist.CLIENT, ...)`）；
5. `com.fontainerepublic.client.net`：`ClientNetworkExecutor` + 非权威展示缓存
   骨架（内存、登出清理；本阶段仅缓存数据结构，无 GUI）；
6. economy 模块声明 `network` 依赖，绑定 `NetworkSendService`，在登录与
   余额变更/转账成功后发送展示包（presence 过滤；absent 客户端零影响）。

## 2. 明确不实现

- GUI/HUD/表单/引导页（Stage B）；
- 权威 C2S 包；客户端权限/业务判定；客户端持久化；
- 公民/机构/土地展示包（后续阶段）；
- 新依赖。

## 3. 具体契约

### 3.1 消息账本（common/network/display）

| ID | 消息 | 方向 | 载荷（有界） |
|---:|---|---|---|
| 0 | `BalanceSyncPacket` | PLAY_TO_CLIENT | balance(long≥0)、currencyName(≤32)、currencySymbol(≤8)、at(long>0)、seq(long>0) |
| 1 | `TransactionNotifyPacket` | PLAY_TO_CLIENT | transactionId(long>0)、direction(byte 0=in/1=out)、amount(long>0)、counterpartyDigest(hex≤64)、memo(可空≤128)、at(long>0) |
| 2 | `NotificationPacket` | PLAY_TO_CLIENT | count(int 0..128)+[(notificationId(long>0)、amount(long>0)、memo(可空≤128))] |

- codec 用 `FriendlyByteBuf`，全部 `readUtf(max)` 与显式边界；拒绝负值/越界；
- 消息类为 common 包，不得 import `net.minecraft.client.*` 或
  `com.fontainerepublic.client.*`；
- handler 位于 common，内部 `DistExecutor.safeRunWhenOn(Dist.CLIENT, () -> () ->
  ClientNetworkExecutor.accept(message, context))`。

### 3.2 网络接线

- `NetworkProductionMessageTable.registerAll` 注册上述 3 条；
- `NetworkBootstrap.registerProductionMessagesAndFreeze` 期望计数 = 3；
- 协议 v2；client 谓词仅接受 "2"；server 谓词接受 "2" 与 ABSENT；
- 发送经 `NetworkSendService.trySendToPlayer`；`REMOTE_CHANNEL_ABSENT` 与
  `CONNECTION_NOT_LIVE` 为非异常结果。

### 3.3 economy 发送接线

- `EconomyModule` 依赖新增 `network`（FR-NET-001-A §13.4：收发消息的模块声明
  network 运行时依赖）；
- `EconomyModule.bindServices` 增补 `NetworkSendService` 绑定；运行时解析当前
  ACTIVE send service，不缓存跨运行实例；
- 触发点：
  - 登录后：BalanceSync（余额+当前 seq）+ NotificationPacket（待读通知摘要）；
  - 本人参与的转账成功：双方各自 BalanceSync + TransactionNotify；
  - 其余余额变更（紧急动作/央行现场）：目标玩家在线时 BalanceSync。
- 所有发送失败/absent 均不影响业务结果（no-client parity）。

## 4. 测试计划

`src/test/java/.../client/ClientPresentationFoundationTestMain.java`

- 协议谓词矩阵（v2/v1/absent/vanilla）；
- 三个消息 codec 往返与边界（超长字符串/负值/空列表拒绝）；
- 账本唯一性、方向、ID 0-2、freeze 单向；
- 侧隔离源码扫描：common/network/display 无 client import；
  client/net 类仅在 DistExecutor supplier 中被引用；
- 发送接线：absent/presence 过滤下业务结果不变（fake sender 断言）；
- `gradlew build` 全绿。

## 5. 验收

FR-CLIENT-001-A v1.1 §5 矩阵（网络面部分）；无越界（无 GUI/无 C2S 包）；
提交后独立审查（Codex）→ ROUTE TO HUMAN / RETEST / BLOCK。

## 6. 约束

- 不新增依赖；服务端权威；专用服务器不加载 client/ 类；
- 不改 FR-NET-001 既有测试预期之外的行为（协议版本与空表约束为既定变更）。
