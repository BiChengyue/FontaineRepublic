# FR-CLIENT-001-B Java Implementation Task（Stage B — 占位，Stage A 落地后细化）

> **Status:** Prepared（依赖 FR-CLIENT-001-IMPL-A 并入 develop 后细化并派发）
> **Task Type:** 客户端 GUI/HUD/表单
> **Design Input:** FR-CLIENT-001-A v1.1 §3/§4/§7

## 1. Goal

在 Stage A 的 S2C 展示网络面与客户端非权威展示缓存之上实现：

- balance card / HUD（余额与货币展示，读 `ClientPresentationCache`）；
- transfer 表单（经 `/fr money transfer` 命令路径提交，服务端校验）；
- citizen 卡（身份/登记号/状态展示）；
- 通知 HUD（待读入账摘要，读 `NotificationPacket` 缓存）；
- 交易历史视图（Stage A 未含历史页消息时，本阶段追加
  `TransactionHistorySyncPacket` 与服务端有界查询投影）；
- 游戏内引导页（链接 `/fr help` 与玩家指南）。

## 2. 明确不实现

- 客户端权威状态/存储/权限判定；权威 C2S 包；新依赖；
- 紧急动作客户端界面；土地/机构深度视图（后续阶段）。

## 3. 契约

- 全部界面位于 `com.fontainerepublic.client.*`，经 DistExecutor 客户端初始化
  （FMLClientSetupEvent 或等价机制）；
- 表单提交失败仅展示服务端有界反馈，不本地落账；
- 展示缓存内存级、登出清理；GUI 每次打开读取最新缓存；
- 需要新展示消息时，按 FR-CLIENT-001-A §4.2 账本追加（append-only）并按需
  评估协议版本。

## 4. 验收

FR-CLIENT-001-A v1.1 §5 矩阵；`gradlew build` 全绿；无越界；提交后独立审查
（Codex）→ ROUTE TO HUMAN / RETEST / BLOCK。
