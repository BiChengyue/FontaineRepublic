# Task Card — FR-INST-002-B-IMPL

> Draft → Authorized（Human"划区域交互"指示）

## Agent Identity

- **Agent:** Codex（Designer）
- **Task ID:** FR-INST-002-B-IMPL
- **Task Name:** 机构访问边界区域制重构
- **Request Summary:** 终端模型替换为区域（zone）模型；在场签发上下文
- **Design Reference:** docs/architecture/fr-inst-002-b-zone-based-institution-access.md
- **Implementation Task:** docs/development/FR-INST-002-B-java-implementation-task.md
- **Human Authorization Reference:** Human"各机构不设终端，划区域交互"（2026-08-13）

## Scope

**Allowed:** institution-access 模块重构（Zone 模型/在场/命令/测试）、HelpCommand 文案。

**Forbidden:** 终端保留；消费者业务改动；GUI/包；新依赖。

## Acceptance

- FR-INST-002-B §7 矩阵全过；消费者 foundation 测试照常；`gradlew build` 全绿。
