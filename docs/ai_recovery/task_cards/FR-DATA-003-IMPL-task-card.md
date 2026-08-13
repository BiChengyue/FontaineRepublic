# Task Card — FR-DATA-003-IMPL

> Draft（经济模块完成后转 Authorized；Human"继续工作"指示）

## Agent Identity

- **Agent:** Codex（Designer）
- **Task ID:** FR-DATA-003-IMPL
- **Task Name:** 安全玩家目录实现（Safe Player Directory）
- **Request Summary:** 按 FR-DATA-003-A 在 player-data 内实现精确姓名目录
- **Design Reference:** docs/architecture/fr-data-003-a-safe-player-directory.md
- **Implementation Task:** docs/development/FR-DATA-003-java-implementation-task.md
- **Human Authorization Reference:** FR-PHASE2-HUMAN-APPROVAL-01（批次）

## Scope

**Allowed:** Directory 段（v2 StoreVersion）、迁移、解析服务、永久歧义、
改名原子快照、`playerDirectoryFoundationTest`。

**Forbidden:** 主体/余额/公民字段；模糊/枚举；外部查询；覆盖/清除歧义；GUI/包；新依赖。

## Acceptance

- FR-DATA-003-A §15 矩阵（除真机）全过；`playerDirectoryFoundationTest` + `gradlew build` 全绿。

## Lifecycle

Draft → Authorized（随"继续工作"）→ 派发 → 审查。
