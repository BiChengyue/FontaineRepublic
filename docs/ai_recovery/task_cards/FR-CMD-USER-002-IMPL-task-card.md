# Task Card — FR-CMD-USER-002-IMPL

> Draft → Authorized（Human"继续工作"指示）

## Agent Identity

- **Agent:** Codex（Designer）
- **Task ID:** FR-CMD-USER-002-IMPL
- **Task Name:** 转账目标输入扩展（UUID/玩家名/登记号）
- **Request Summary:** `/fr money pay` 支持三种目标输入并收敛 SubjectId
- **Design Reference:** docs/development/FR-CMD-USER-002-java-implementation-task.md
- **Human Authorization Reference:** FR-PHASE2-HUMAN-APPROVAL-01 + "继续工作"

## Scope

**Allowed:** 目标输入识别、目录/登记号解析接线、反馈、测试。

**Forbidden:** 模糊/枚举；top/bank/他人余额；新业务逻辑；GUI/包；新依赖。

## Acceptance

- 三输入收敛；歧义/未知统一反馈；无枚举；`gradlew build` 全绿。
