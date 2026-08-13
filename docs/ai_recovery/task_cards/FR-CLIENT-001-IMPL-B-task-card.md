# Task Card — FR-CLIENT-001-IMPL-B（客户端 GUI/HUD）

> Status: Prepared（依赖 Stage A 并入 develop 后派发）
> Task Type: 客户端阶段 B — 可视化界面（展示/输入转发）
> Design Reference: docs/architecture/fr-client-001-a-client-ui-module-architecture.md v1.1
> Dependency: FR-CLIENT-001-IMPL-A（S2C 展示网络面）、FR-CMD-001（命令路径）

## Agent Identity

- **Agent:** Codex（Designer）
- **Task ID:** FR-CLIENT-001-IMPL-B
- **Task Name:** 客户端 GUI/HUD/表单（Stage B）

## Scope

**Allowed:** balance card/HUD、transfer 表单（/fr 命令路径提交）、citizen 卡、
通知 HUD、交易历史视图（需追加 S2C 历史页消息）、游戏内引导页；
必要时追加 `CitizenInfoPacket(3)` / `TransactionHistorySyncPacket(4)`。

**Forbidden:** 客户端权威状态/存储/权限判定；权威 C2S 包；新依赖；
紧急动作客户端界面；服务端业务逻辑改动（除必要的展示消息与查询投影）。

## Acceptance

- 界面仅读非权威展示缓存；表单经命令路径提交并由服务端校验；
- 无 FR 客户端时全部功能可用（chat 兜底不变）；
- 专用服务器不加载 client/ 类（源码扫描）；
- `gradlew build` 全绿；`clientGuiFoundationTest`（如适用）通过。
