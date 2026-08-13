# Task Card — FR-ECO-002-IMPL

> Draft（Human 拍板开放问题默认值后转 Authorized）

## Agent Identity

- **Agent:** Codex（Designer）
- **Task ID:** FR-ECO-002-IMPL
- **Task Name:** 央行现场职责实现（/fr bank）
- **Request Summary:** 按 FR-ECO-002-A 实现 bank balance/deposit/withdraw/freeze
- **Design Reference:** docs/architecture/fr-eco-002-a-central-bank-official-duties.md
- **Human Authorization Reference:** Human"现在就可以做"（2026-08-13，待默认值确认）

## Scope

**Allowed:** `/fr bank` 命令（现场门控）、供给守恒发钞/回收、冻结/解冻、测试。

**Forbidden:** 现金/ATM/利息/市场；紧急 issue/reclaim（FR-EMG）；他人余额；GUI/包；新依赖。

## Acceptance

- FR-ECO-002-A §5 矩阵（除真机）全过；`economyFoundationTest` 扩展 + `gradlew build` 全绿。
