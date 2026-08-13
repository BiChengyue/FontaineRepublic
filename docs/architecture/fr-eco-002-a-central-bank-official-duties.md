# FontaineRepublic Central Bank Official Duties Architecture v1.0

> **Task ID:** FR-ECO-002-A
> **Status:** Design Candidate — Pending Independent Review and Human Approval
> **Project:** FontaineRepublic
> **Platform:** Minecraft Forge 1.20.1 / Forge 47.4.18 / Java 17
> **Purpose:** Unblock Central Bank official duties (`/fr bank`) with on-site
> gating; propose defaults for FR-ECO-001-A §14 open questions
> **Dependency:** FR-ECO-001-A/B/C（已实现玩家服务）、FR-INST-002（已实现）、
> FR-EMG-001-A（紧急动作保持门控）、FR-CORE-002、FR-AUD-001、FR-ID-001
> **Implementation Status:** Not authorized

---

## 1. Purpose

FR-ECO-001-A §6.4 gates Central Bank mutation commands on the institution
access contract — now implemented (FR-INST-002). This design defines the bank
surface and proposes defaults for the open questions, **each default marked
for Human confirmation**.

---

## 2. Proposed Defaults（Human 拍板项）

| Open question | Proposed default | Status |
|---|---|---|
| 1. 个人存取款价值移动语义 | 纯账目（个人账户 <-> 国库记账）；无现金/ATM（FR-ECO-001-B §2） | 待确认 |
| 2. 国库信息公开 | 总额公开（`/fr bank balance` 只读）；明细受限+审计（FR-ECO-001-B §4） | 待确认 |
| 3. 现场上下文单次性 | 官方职责每次变异单次使用（对齐 FR-INST-001-B 公众工作流默认） | 待确认 |
| 4. 显示权限 | 本人余额仅本人；国库总额全服可见；他人余额永不可见 | 待确认 |

---

## 3. Surfaces

```text
/fr bank balance              -> 国库总额（只读、公开）
/fr bank deposit <player> <amount>   -> ONSITE_OFFICIAL_DUTY（发钞，增加供给）
/fr bank withdraw <player> <amount>  -> ONSITE_OFFICIAL_DUTY（回收，减少供给）
/fr bank freeze|unfreeze <player>    -> ONSITE_OFFICIAL_DUTY
```

- 所有官方变异：注册央行设施 + 注册终端 + 有效现场上下文（FR-INST-002
  `validateAtMutation(ONSITE_OFFICIAL_DUTY)` 最终边界复检）；
- OP 权限仅为早期闸门，不满足现场规则；
- `economy.issue`/`economy.reclaim` 紧急动作保持 FR-EMG 门控（不改动）。

---

## 4. Semantics

- `deposit`（发钞）：目标账户余额 +amount，国库 -amount？——不：deposit 是系统向
  玩家发放（国库减少，供给增加）；`withdraw` 是回收（玩家减少，供给减少）；
  总供给 = sum(accounts) + treasury 恒等保持不变式（FR-ECO-001-C §7）；
- 每次官方变异一个完整快照 -> `commitModuleData("economy", ...)` -> COMMITTED 后发布；
- 审计：FR-AUD 记录官方操作（类别 FINANCE，actor=现场官员）；
- 无现金/ATM/利息/市场；无其他玩家余额展示。

---

## 5. Acceptance Matrix

| Test | Expected |
|---|---|
| /fr bank balance 只读公开 | 总额可见；无明细 |
| deposit/withdraw 现场门控 | 无上下文拒绝；单次使用 |
| 供给守恒 | 发钞/回收后恒等式成立 |
| 冻结/解冻 | 现场门控；冻结账户拒绝转账/提取 |
| 紧急动作隔离 | issue/reclaim 仍走 FR-EMG |
| 注入失败无发布 | 标准原子性 |
| 无现金/ATM/他人余额 | 源码/反射守卫 |

---

## 6. Non-Goals

- 现金/ATM/利息/市场；紧急发钞回收（FR-EMG）；货币政策；GUI/包；
  implementation。

## 7. Review Gate

Design candidate only. Does not constitute architecture approval, Human
Approval, or implementation authorization. Independent review requested.
