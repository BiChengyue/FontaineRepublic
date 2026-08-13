# Task Card — FR-AUD-001-IMPL

> Draft（FR-CORE-002 完成后转 Authorized）

## Agent Identity

- **Agent:** Codex（Designer）
- **Task ID:** FR-AUD-001-IMPL
- **Task Name:** 审计模块实现（Audit Module）
- **Request Summary:** 按 FR-AUD-001-A 实现 append-only 审计模块（命名空间 `audit`）
- **Design Reference:** `docs/architecture/fr-aud-001-a-audit-module-architecture.md`
- **Human Authorization Reference:** FR-PHASE2-HUMAN-APPROVAL-01（批次批准；本卡随 FR-CORE-002 完成转正）

## Scope

**Allowed:**

- `server/audit/`：AuditEntry / AuditCategory / AuditStore / AuditNbtCodec /
  AuditRepository（单写入者）/ AuditService（record、recordAuthoritative、getEntry、page）
- 模块注册（IModule，依赖 player-data；优先顺序在 FR-CORE-002 之后）
- 段式摘要链、分类强制、边界分页、确定性编码
- `auditFoundationTest` 验证任务 + `gradlew build` 回归
- 权威条目走 FR-CORE-002 `commitModuleData("audit", ...)`

**Forbidden:**

- 业务模块数据、FR-EMG 紧急日志替代、GUI、包、删除/更新条目、外部依赖

## Acceptance（FR-AUD-001-A §8）

- append-only（无 update/delete API）；段摘要链篡改检测；
- recordAuthoritative 注入失败 -> 无收据、无修订变化；
- 分类强制（未分类拒绝；秘密仅摘要）；分页有界；
- 重启恢复最后提交段；孤儿 tmp 清理；FR-EMG 隔离；
- `gradlew build` 通过。

## Lifecycle

Draft（当前）→ Human Review Pending（FR-CORE-002 完成后）→ Authorized → 派发 → 审查。
