# Task Card — FR-EMG-ECO-001-IMPL

> Draft → Authorized（Human"继续做完服务端暂停的任务"）

## Agent Identity

- **Agent:** Codex（Designer）
- **Task ID:** FR-EMG-ECO-001-IMPL
- **Task Name:** 紧急业务动作目录（economy.issue/reclaim）
- **Request Summary:** 把经济紧急发钞/回收提供方接入 FR-EMG
- **Design Reference:** FR-ECO-001-C §9-12 + FR-EMG-001-A
- **Implementation Task:** docs/development/FR-EMG-ECO-001-java-implementation-task.md
- **Human Authorization Reference:** Human"继续做完服务端暂停的任务"（2026-08-14）

## Scope

**Allowed:** economy emergency provider + FR-EMG 注册 + 测试。

**Forbidden:** 普通官方发钞；FR-EMG 复刻；GUI/包；新依赖。

## Acceptance

- FR-ECO-001-C §15.2 矩阵全过；`economyEmergencyFoundationTest` + `gradlew build` 全绿。
