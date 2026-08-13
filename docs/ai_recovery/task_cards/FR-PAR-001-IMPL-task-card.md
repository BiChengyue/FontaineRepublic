# Task Card — FR-PAR-001-IMPL

> Draft → Authorized（Human"继续工作"指示）

## Agent Identity

- **Agent:** Codex（Designer）
- **Task ID:** FR-PAR-001-IMPL
- **Task Name:** 议会模块实现（立法流水线）
- **Request Summary:** 按 FR-PAR-001-A 实现提案/投票/法案状态机
- **Design Reference:** docs/architecture/fr-par-001-a-parliament-module-architecture.md
- **Implementation Task:** docs/development/FR-PAR-001-java-implementation-task.md
- **Human Authorization Reference:** FR-PHASE2-HUMAN-APPROVAL-01 + "继续工作"

## Scope

**Allowed:** server/parliament 全套 + 命令 + 测试。

**Forbidden:** 法律执行；守护审阅；公投；修宪；GUI/包；技术权限；新依赖。

## Acceptance

- FR-PAR-001-A §6 矩阵（除真机）全过；`parliamentFoundationTest` + `gradlew build` 全绿。
