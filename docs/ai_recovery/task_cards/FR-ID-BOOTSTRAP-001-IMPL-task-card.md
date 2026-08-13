# Task Card — FR-ID-BOOTSTRAP-001-IMPL

> Authorized（FR-ID-BOOTSTRAP-HUMAN-APPROVAL-01）

## Agent Identity

- **Agent:** Codex（Designer）
- **Task ID:** FR-ID-BOOTSTRAP-001-IMPL
- **Task Name:** 水神初始个人主体绑定实现
- **Request Summary:** 按 FR-ID-BOOTSTRAP-001-A 实现 console-only 一次性可审计绑定
- **Design Reference:** docs/architecture/fr-id-bootstrap-001-a-original-person-bootstrap.md
- **Implementation Task:** docs/development/FR-ID-BOOTSTRAP-001-java-implementation-task.md
- **Human Authorization Reference:** FR-ID-BOOTSTRAP-HUMAN-APPROVAL-01

## Scope

**Allowed:** 控制台分类器、尝试链、BootstrapState、`/fr admin bootstrap` 命令、测试。

**Forbidden:** 继任/office/官方账户/FR-EMG/GUI/包；解绑/重绑/转移；新依赖。

## Acceptance

- 矩阵（设计 §7）全过；`bootstrapFoundationTest` + `gradlew build` 全绿。

## Lifecycle

Authorized（已随 Human 批准）→ 派发 → 审查。
