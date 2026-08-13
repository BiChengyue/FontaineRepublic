# Task Card — FR-CLIENT-001-IMPL

> **SUPERSEDED**：本卡已按分阶段计划取代，见
> FR-CLIENT-001-IMPL-A（网络展示面）/ FR-CLIENT-001-IMPL-B（GUI，后续起草）。
> 服务端开发已确认完整（2026-08-14）；Human 既定顺序"服务端完成后继续客户端
> 模组"已满足，Stage A 进入实施。

## Agent Identity

- **Agent:** Codex（Designer）
- **Task ID:** FR-CLIENT-001-IMPL
- **Task Name:** 客户端 UI 模组（可视化操作，首版）
- **Request Summary:** 按 FR-CLIENT-001-A 实现可选客户端：余额/流水/转账、公民卡、
  机构信息、通知、游戏内引导
- **Design Reference:** docs/architecture/fr-client-001-a-client-ui-module-architecture.md
- **Human Authorization Reference:** Human"最后开发客户端模组"（待"开始"信号）

## Scope

**Allowed:** `client/` 包（gui/hud/net）、S2C 展示包、DistExecutor 接线、测试。

**Forbidden:** 客户端权威状态/存储；权限决策；业务逻辑；无 FR 客户端时的
命令/聊天平价破坏；新依赖。

## Acceptance

- 无客户端平价保持；客户端仅展示/转发；协议不匹配拒绝；`gradlew build` 全绿。
