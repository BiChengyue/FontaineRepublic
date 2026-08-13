# Task Card — FR-GOV-001-IMPL

> Draft → Authorized（Human"继续工作"指示）

## Agent Identity

- **Agent:** Codex（Designer）
- **Task ID:** FR-GOV-001-IMPL
- **Task Name:** 政府模块实现（职位/部门/任命）
- **Request Summary:** 按 FR-GOV-001-A 实现政府模块
- **Design Reference:** docs/architecture/fr-gov-001-a-government-module-architecture.md
- **Implementation Task:** docs/development/FR-GOV-001-java-implementation-task.md
- **Human Authorization Reference:** FR-PHASE2-HUMAN-APPROVAL-01 + "继续工作"

## Scope

**Allowed:** server/government 全套 + 命令 + 测试。

**Forbidden:** 立法/司法/财政权力；选举；GUI/包；技术权限；新依赖。

## Acceptance

- FR-GOV-001-A §6 矩阵（除真机）全过；`governmentFoundationTest` + `gradlew build` 全绿。
