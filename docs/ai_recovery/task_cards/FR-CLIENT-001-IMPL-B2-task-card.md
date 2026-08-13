# Task Card — FR-CLIENT-001-IMPL-B2（公民卡 + 历史视图）

> Status: Authorized（Stage A/B-1 已并入 develop；客户端阶段继续）
> Task Type: 客户端阶段 B-2 — 公民卡 + 交易历史视图（协议 v3，账本追加）
> Design Reference: docs/architecture/fr-client-001-a-client-ui-module-architecture.md v1.1
> Dependency: FR-CLIENT-001-IMPL-A/B（已并入）、FR-CIT-001、FR-ECO-001

## Agent Identity

- **Agent:** Codex（Designer）
- **Task ID:** FR-CLIENT-001-IMPL-B2
- **Task Name:** 公民卡 + 历史视图（Stage B-2）

## Scope

**Allowed:**

- 协议 v3；消息账本追加 `CitizenInfoPacket(3)`（登记号/公民状态/政治等级/入籍
  时间）与 `TransactionHistorySyncPacket(4)`（有界历史页）；
- 公民模块：声明 `network` 依赖、绑定发送、登录后发送公民卡信息
  （PresentationAwareCitizenService 装饰器，展示失败不影响业务）；
- economy：`syncAccount` 后追加发送第一页历史（扩展
  `EconomyPresentationNotifier.syncHistory`）；
- 客户端：展示缓存增历史字段、执行器/handler 增两个 accept、`/frclient
  citizen|history` 命令、CitizenScreen/HistoryScreen、投影；
- 契约测试与完整构建。

**Forbidden:** 机构/土地信息视图（Stage B-3，需新公共信息查询面）；权威 C2S
包；新依赖；服务端业务规则改动。

## Acceptance

- 协议 v3；账本 5 条（ID 0-4）；freeze 单向；有界 codec；
- 登录后 FR 客户端收到公民卡与历史第一页（presence 过滤、absent 零影响）；
- 公民/历史界面读非权威缓存；专用服务器不加载 client/ 类；
- `clientStageB2FoundationTest` + `gradlew build` 全绿。
