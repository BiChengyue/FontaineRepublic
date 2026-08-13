# Task Card — FR-LAND-001-IMPL

> Draft → Authorized（依据 Human"可以继续工作"指示 + 路线图 v1.1 Phase 4-6 范围）

## Agent Identity

- **Agent:** Codex（Designer）
- **Task ID:** FR-LAND-001-IMPL
- **Task Name:** 土地模块实现（Land Module）
- **Request Summary:** 按 FR-LAND-001-A 实现共和国土地制度基础
- **Design Reference:** docs/architecture/fr-land-001-a-land-module-architecture.md
- **Human Authorization Reference:** FR-PHASE2-HUMAN-APPROVAL-01（批次）+ Human"可以继续工作"

## Scope

**Allowed:**

- `server/land/`：LandParcel / UsageRight / ZoneType / LandAccess / ViolationReport /
  LandStore / LandNbtCodec（严格）/ LandRepository（单写入者）/ LandService
  （createParcel / grantUsage / renewUsage / revokeUsage / setZoneType / setAccess /
  createViolationReport / getParcel）+ PermissionResolver（canBuild/canBreak/canInteract）
- 事件：BuildEventHandler / InteractionEventHandler（BlockEntity）
- 权威变更走 FR-CORE-002 `commitModuleData("land", ...)`
- `landFoundationTest` + `gradlew build` 回归

**Forbidden:**

- 所有权转移/出售/租赁/拍卖（LandMarket Beta）；自动合规判定；司法判决；
- 经济交易；rank/OP 硬编码绕过；硬编码坐标；GUI/包/客户端权威；额外依赖。

## Acceptance（FR-LAND-001-A §7）

- ownership 恒 REPUBLIC 不可变；grant/renew/revoke 单快照+门；holder 经服务解析；
- zone/access 权威变更；违规举报只读入口；PermissionResolver 配置驱动、rank 无绕过；
- 无硬编码坐标；无自动合规；严格编解码；重启恢复；无枚举 API；
- `landFoundationTest` + `gradlew build` 全绿。

## Lifecycle

Draft（当前）→ 设计确认（依据路线图范围与 Human 指示）→ 派发 → 审查。
