# Task Card — FR-INST-002-IMPL

> Draft → Authorized（Human"继续工作"指示；设计候选 FR-INST-002-A 已备）

## Agent Identity

- **Agent:** Codex（Designer）
- **Task ID:** FR-INST-002-IMPL
- **Task Name:** 共享机构访问边界实现
- **Request Summary:** 按 FR-INST-002-A 实现设施/终端目录与现场上下文
- **Design Reference:** docs/architecture/fr-inst-002-a-shared-institution-access-boundary.md
- **Implementation Task:** docs/development/FR-INST-002-java-implementation-task.md
- **Human Authorization Reference:** FR-PHASE2-HUMAN-APPROVAL-01 + "继续工作"

## Scope

**Allowed:** institution-access 模块（设施/终端/上下文/命令/测试）。

**Forbidden:** 机构业务权限；FR-EMG；GUI/包；硬编码坐标；新依赖。

## Acceptance

- FR-INST-002-A §7 矩阵（除真机）全过；`institutionAccessFoundationTest` + `gradlew build` 全绿。
