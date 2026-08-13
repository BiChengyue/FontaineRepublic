# Task Card — FR-CMD-GUIDE-001-IMPL

> Draft → Authorized（Human"提供引导"指示）

## Agent Identity

- **Agent:** Codex（Designer）
- **Task ID:** FR-CMD-GUIDE-001-IMPL
- **Task Name:** 游戏内引导（/fr help 分模块）
- **Request Summary:** 扩展 /fr help 为分模块玩家引导
- **Implementation Task:** docs/development/FR-CMD-GUIDE-001-java-implementation-task.md
- **Human Authorization Reference:** Human"功能开发完成后提供引导"（2026-08-13）

## Scope

**Allowed:** help 树扩展、i18n、测试。

**Forbidden:** 新业务逻辑/GUI/新依赖。

## Acceptance

- `/fr help <模块>` 有界可用；`gradlew build` 全绿。
