# Task Card — FR-EMG-001-IMPL

> Draft → Authorized（Human"继续做完服务端暂停的任务"）

## Agent Identity

- **Agent:** Codex（Designer）
- **Task ID:** FR-EMG-001-IMPL
- **Task Name:** 共享紧急权限基础设施实现
- **Request Summary:** 按 FR-EMG-001-A 实现演员验证/preview-confirm/日志/命令/配置
- **Design Reference:** docs/architecture/fr-emg-001-a-hydro-archon-emergency-authority.md
- **Implementation Task:** docs/development/FR-EMG-001-java-implementation-task.md
- **Human Authorization Reference:** Human"继续做完服务端暂停的任务"（2026-08-14）

## Scope

**Allowed:** server/emergency 共享基础设施 + 命令适配 + 测试。

**Forbidden:** 业务动作本体；GUI/包；任意数据编辑；新依赖。

## Acceptance

- FR-EMG-001-A 门（除业务目录/真机）全过；`emergencyFoundationTest` + `gradlew build` 全绿。
