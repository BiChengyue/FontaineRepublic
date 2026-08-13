# Task Card — FR-CLIENT-001-IMPL-A（客户端展示网络面）

> Status: Authorized（服务端开发已确认完整；Human 既定顺序"服务端完成后继续
> 客户端模组"；设计 FR-CLIENT-001-A v1.1 经独立审查 FR-CLIENT-001-A-REVIEW-01）
> Task Type: 客户端阶段 A — S2C 展示网络面（协议 v2 + 消息账本 + 侧隔离 +
> 服务端发送接线 + 契约测试）
> Design Reference: docs/architecture/fr-client-001-a-client-ui-module-architecture.md
> Dependency: FR-NET-001（已实现）、FR-ECO-001/FR-ECO-002（已实现）

## Agent Identity

- **Agent:** Codex（Designer）
- **Task ID:** FR-CLIENT-001-IMPL-A
- **Task Name:** 客户端展示网络面（Stage A）

## Scope

**Allowed:** 协议 v2、生产消息账本（ID 0-2 S2C 展示包）、空表约束替换、
S2C handler 侧隔离、ClientNetworkExecutor/非权威展示缓存骨架、economy
`network` 依赖与发送接线、契约测试。

**Forbidden:** GUI/HUD/表单（Stage B）；权威 C2S 包；客户端状态存储/权限判定；
无 FR 客户端时的命令/聊天平行性破坏；新依赖（Forge 自带 DistExecutor 除外）。

## Acceptance

- `NetworkProtocol.VERSION = "2"`，谓词同步；v1 客户端被拒、absent 客户端放行；
- 消息账本：BalanceSyncPacket(0)/TransactionNotifyPacket(1)/NotificationPacket(2)
  方向 PLAY_TO_CLIENT、有界 codec、ID 唯一 append-only、freeze 单向；
- 空表强制替换为期望账本计数校验；
- S2C handler 经 `DistExecutor.safeRunWhenOn(Dist.CLIENT, ...)` 侧隔离；
  专用服务器不加载 client/ 类（源码扫描核验）；
- economy 声明 network 依赖；登录余额/待读通知、转账成功双向通知经
  presence 过滤发送；absent 客户端零影响；
- `clientPresentationFoundationTest` + `gradlew build` 全绿。
