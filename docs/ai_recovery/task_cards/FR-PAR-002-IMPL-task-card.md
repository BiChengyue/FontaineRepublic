# Task Card — FR-PAR-002-IMPL

> Draft → Authorized（Human"继续做完服务端暂停任务"）

## Agent Identity

- **Agent:** Codex（Designer）
- **Task ID:** FR-PAR-002-IMPL
- **Task Name:** 议会扩展实现（守护审阅/公投/修宪）
- **Request Summary:** 按 FR-PAR-002-A 扩展立法流水线
- **Design Reference:** docs/architecture/fr-par-002-a-legislative-extensions.md
- **Implementation Task:** docs/development/FR-PAR-002-java-implementation-task.md
- **Human Authorization Reference:** Human"继续做完服务端暂停的任务"（2026-08-14）

## Scope

**Allowed:** parliament 扩展（状态机/门槛/守护审阅/公投/修宪）、命令、测试。

**Forbidden:** 修宪后制度实现；法院审查逻辑；GUI/包；FR-EMG；新依赖。

## Acceptance

- FR-PAR-002-A §6 矩阵全过；`legislativeExtensionsFoundationTest` + `gradlew build` 全绿。
