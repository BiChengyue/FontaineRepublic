# Task Card — FR-CIT-001-IMPL

> Draft → Authorized（依据 Human"可以继续工作"指示 + 路线图 v1.1 Phase 3 范围）

## Agent Identity

- **Agent:** Codex（Designer）
- **Task ID:** FR-CIT-001-IMPL
- **Task Name:** 公民模块实现（Citizen Module）
- **Request Summary:** 按 FR-CIT-001-A 实现公民身份/政治等级基础设施
- **Design Reference:** docs/architecture/fr-cit-001-a-citizen-module-architecture.md
- **Human Authorization Reference:** FR-PHASE2-HUMAN-APPROVAL-01（批次）+ Human"可以继续工作"（2026-08-13）

## Scope

**Allowed:**

- `server/citizen/`：CitizenRecord / CitizenRank / CitizenStatus / CitizenStore /
  CitizenNbtCodec（严格）/ CitizenRepository（单写入者）/ CitizenService
  （ensureCitizen / getCitizen / setRank / setStatus）
- 懒式开户链：PlayerData -> SubjectRegistryService.ensurePlayerSubject -> ensureCitizen
- 默认 status=CITIZEN、rank=CITIZEN；rank 永不等于技术权限（GOD 非 OP）
- 权威变更走 FR-CORE-002 `commitModuleData("citizen", ...)`
- `citizenFoundationTest` + `gradlew build` 回归

**Forbidden:**

- 余额/土地/案件/职位/权限/登记号字段；投票/议会/任命/司法资格策略；
- GUI/包/客户端权威；rank 到 OP 的映射；额外依赖。

## Acceptance（FR-CIT-001-A §7）

- 懒开户幂等；无 subject 的 citizen 拒绝；rank/status 变更单快照 + 门；
- rank 无技术效果（测试断言）；GOD 非 OP；严格编解码；重启恢复；
- 无枚举 API；`citizenFoundationTest` + `gradlew build` 全绿。

## Lifecycle

Draft（当前）→ 设计确认（Codex 依据路线图范围与 Human 指示）→ 派发 → 审查。
