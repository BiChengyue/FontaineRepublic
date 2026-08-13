# Task Card — FR-CORE-002-IMPL

> Draft（待 Human 授权后转为 Authorized）
> 依据 `docs/ai_recovery/templates/task_card_template.md`

---

## Agent Identity

- **Agent:** Codex（Designer）
- **Task ID:** FR-CORE-002-IMPL
- **Task Name:** 核心持久化确认门实现（Durable Commit Gate）
- **Request Summary:** 按 FR-CORE-002-A 实现 `DataManager.commitModuleData` 可确认落盘
- **Design Reference:** `docs/architecture/fr-core-002-a-durable-commit-gate.md`
- **Human Authorization Reference:** （待 Human 确认设计批后填写）

## Project Context Snapshot

- 基线：架构 v2.7 / 路线图 v1.1 / Phase 0 设计 v1.1；FR-CORE-001/FR-DATA-002/FR-NET-001/FR-CMD-001 已审查通过
- 当前 HEAD 构建基线：`gradlew build` BUILD SUCCESSFUL（tmp/full-build-20260813.log）
- 前置：FR-CORE-002-A 架构批准（触及 Core 接口，需 Human 确认）

## Scope

**Allowed:**

- `DataManager` / `ModSavedData` 增加可确认提交路径（原子整根写入 + fsync + 替换）
- 世界身份校验、孤儿临时文件清理、停服语义、失败码
- 注入式存储适配器 + 临时目录集成测试（沿用 PlayerDataRepository 测试模式）
- Gradle 验证任务（如 `durableCommitTest`）+ 构建回归

**Forbidden:**

- 业务模块实现（Audit/Citizen/Land/Economy 等）
- WAL、分片文件、外部框架、JSON/数据库存储
- 客户端权威逻辑、GUI、业务包
- 改动 FR-CORE-002-A 已定的契约边界

## Acceptance（对应 FR-CORE-002-A §8 验收矩阵）

- COMMITTED / FAILED / UNINITIALIZED / STOPPING 各路径正确
- 注入失败时内存与已发布文件不变；崩溃窗口恢复正确（临时清理/旧态保留/新态生效）
- 跨世界根文件被拒；损坏根文件 fail closed
- 确定性编码；autosave 与 commit 不产生分歧
- `gradlew build` 通过

## Lifecycle

| 状态 | 当前 |
|---|---|
| Draft | ✅（本文档） |
| Human Review Pending | 等待设计批确认 |
| Authorized | 待 Human |
| Assigned → Accepted → Completed → Archived | 未开始 |

## Dispatch 备注（网络恢复后）

```powershell
powershell -File tools\dispatch-task.ps1 -TaskName fr-core-002-impl -PromptFile deepseek-worktrees-v2\FR-CORE-002-IMPL-PROMPT.md
```

审查：Codex 独立复核（mod-review 纪律）→ ROUTE TO HUMAN / RETEST / BLOCK。
