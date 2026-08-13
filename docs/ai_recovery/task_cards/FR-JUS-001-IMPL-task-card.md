# Task Card — FR-JUS-001-IMPL

> Draft → Authorized（Human"继续工作"指示）

## Agent Identity

- **Agent:** Codex（Designer）
- **Task ID:** FR-JUS-001-IMPL
- **Task Name:** 司法模块实现（案件/证据/裁判）
- **Request Summary:** 按 FR-JUS-001-A 实现司法流水线
- **Design Reference:** docs/architecture/fr-jus-001-a-justice-module-architecture.md
- **Implementation Task:** docs/development/FR-JUS-001-java-implementation-task.md
- **Human Authorization Reference:** FR-PHASE2-HUMAN-APPROVAL-01 + "继续工作"

## Scope

**Allowed:** server/justice 全套 + 命令 + 测试。

**Forbidden:** 裁判执行；违宪审查；AI 枢机；GUI/包；技术权限；新依赖。

## Acceptance

- FR-JUS-001-A §6 矩阵（除真机）全过；`justiceFoundationTest` + `gradlew build` 全绿。
