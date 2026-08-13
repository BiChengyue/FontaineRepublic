# Task Card — FR-CMD-USER-001-IMPL

> Draft → Authorized（Human"继续工作"指示）

## Agent Identity

- **Agent:** Codex（Designer）
- **Task ID:** FR-CMD-USER-001-IMPL
- **Task Name:** 经济/公民命令与登录钩子接线
- **Request Summary:** 把已实现服务接到 `/fr money`、`/fr citizen info` 与登录钩子
- **Design Reference:** docs/development/FR-CMD-USER-001-java-implementation-task.md
- **Human Authorization Reference:** FR-PHASE2-HUMAN-APPROVAL-01 + "继续工作"

## Scope

**Allowed:** 命令贡献（money/citizen）、登录钩子、反馈、测试。

**Forbidden:** top/bank/他人余额/玩家名输入/新业务逻辑/GUI/包/新依赖。

## Acceptance

- 命令可用、输出有界；无禁用命令注册；登录钩子幂等；`gradlew build` 全绿。
