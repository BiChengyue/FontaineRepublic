# Task Card — FR-ID-001-IMPL

> Draft（FR-CORE-002 完成后转 Authorized）

## Agent Identity

- **Agent:** Codex（Designer）
- **Task ID:** FR-ID-001-IMPL
- **Task Name:** 统一数字主体登记册实现
- **Request Summary:** 按 FR-ID-001-A 实现主体登记核心（号码/索引/懒开户/精确查询）
- **Design Reference:** docs/architecture/fr-id-001-a-unified-digital-subject-registry.md
- **Implementation Task:** docs/development/FR-ID-001-java-implementation-task.md
- **Human Authorization Reference:** FR-PHASE2-HUMAN-APPROVAL-01

## Scope

**Allowed:** registry 核心（见实现任务文档 §1）；固定号保留；MOD 97；严格编解码；
懒式 ensure；精确查询；`subjectRegistryFoundationTest`。

**Forbidden:** 水神初始个人绑定（bootstrap，独立设计）；企业/机构/城市适配器；
公民/余额/权限；紧急动作；GUI/包；额外依赖。

## Acceptance

- 验收矩阵（FR-ID §19）除 bootstrap 项全过；bootstrap 项标 BLOCKED；
- `subjectRegistryFoundationTest` + `gradlew build` 全绿。

## Lifecycle

Draft → Authorized（已随批次）→ 派发 → 审查。
