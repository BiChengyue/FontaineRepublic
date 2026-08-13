# FontaineRepublic Legislative Extensions Architecture v1.0

> **Task ID:** FR-PAR-002-A
> **Status:** Design Candidate — Pending Independent Review and Human Approval
> **Project:** FontaineRepublic
> **Platform:** Minecraft Forge 1.20.1 / Forge 47.4.18 / Java 17
> **Purpose:** Guardian review, referendums, and the constitutional amendment
> pipeline (FR-PAR-001 extensions)
> **Dependency:** FR-PAR-001（立法流水线已实现）、FR-CIT-001（公投名册）、
> FR-INST-002-B（区域在场）、FR-AUD-001、FR-CORE-002、FR-CMD-001-A
> **Implementation Status:** Not authorized

---

## 1. Purpose

FR-PAR-001 implements the core pipeline (DRAFT -> REVIEW -> VOTING -> APPROVED
-> PUBLISHED -> ACTIVE / REJECTED). This design adds the constitution's
higher-order steps (FR-CON v3.0 §13-15; FR-BL-001 §9; FR-BL-003 §6-9;
FR-BL-005 §8-10):

1. **水神守护审阅**（Guardian Review）：普通法律/基本法呈交水神；
2. **公投**（Referendum）：第一层修正案需公民公投；
3. **修宪流水线**（Amendment pipeline）：提案 -> 法院审查 -> 议会 -> 公投 ->
   水神共同制宪同意。

---

## 2. Normative Rules（宪法/基本法）

| 层级 | 门槛 |
|---|---|
| 普通法律 | 议会过半数；水神 72h 守护审阅（超时=批准；可依宪法条款/程序错误退回一次；议会 2/3 再通过则生效） |
| 宪法基本法 | 最高法院层级+合宪性审查；议会 3/4；水神 7 日一次守护审阅（退回后议会 4/5 再通过生效） |
| 组织法 | 最高法院层级+合宪性审查；议会 2/3；水神无否决权（仅守护意见） |
| 第一层修正案 | 提案（水神/≥1/3 议员/≥1/3 公民）；最高法院 14 日审查（可延 7 日）；议会 4/5；
  公民公投（参与 ≥ 冻结名册 2/3、赞成 ≥ 有效票 2/3）；水神共同制宪同意（7 日超时=同意，明示拒绝=失败） |

公投分母：程序开始时冻结公民名单（FR-BL-005 §2）；弃权计入参与不计有效。

---

## 3. State Machine Extensions

```text
（FR-PAR-001 现有）VOTING -> APPROVED
新增：
VOTING -> GUARDIAN_REVIEW -> APPROVED | GUARDIAN_RETURNED -> VOTING(override)
VOTING -> COURT_REVIEW（宪法基本法/组织法）-> VOTING/GUARDIAN_REVIEW
VOTING -> REFERENDUM_OPEN -> REFERENDUM_CLOSED -> APPROVED | REJECTED
AMENDMENT: PROPOSED -> COURT_REVIEW -> PARLIAMENT_VOTE -> REFERENDUM ->
           GUARDIAN_CONSENT -> APPROVED -> PUBLISHED
```

每次转换记录 actor/time/trigger/before/after/revision（沿用 FR-PAR-001
TransitionRecord）。

---

## 4. 水神守护审阅（Guardian Review）

- 触发：法案到达 GUARDIAN_REVIEW 后，水神（经控制台或水神玩家，见下）在期限内
  批准 / 退回一次 / 超时视为批准；
- 退回必须附具体宪法条款或程序错误依据（有界文本）；
- 退回后议会按层级门槛再通过（普通 2/3、基本法 4/5）则进入公布；
- 水神涉利益时回避，由最高法院完成程序+合宪性审查（FR-BL-003 §8）。

实现建议：守护审阅命令走**水神玩家在场**（议会公投/审阅区）或**真实本地控制台**
（类似 bootstrap 分类器）；具体交互通道待 Human 确认。

---

## 5. 公投（Referendum）

- 冻结公民名单（投票开启时快照，FR-CIT 服务读取）；
- 公民经 `/fr parliament referendum vote <proposal> <for|against|abstain>`（现场公众区）
  投票，一人一票；
- 结票：参与 ≥ 2/3 名册、赞成 ≥ 2/3 有效票 -> APPROVED，否则 REJECTED；
- 弃权计入参与不计有效。

---

## 6. Acceptance Matrix

| Test | Expected |
|---|---|
| 守护审阅时限/超时/退回/再通过 | 按层级门槛；72h/7d；退回一次 |
| 法院审查（基本法/组织法） | 层级+合宪性；14d 审查 |
| 公投门槛 | 参与 2/3、赞成 2/3、一人一票、冻结名册 |
| 修宪流水线 | 完整顺序 + 各阶段门槛 + 水神同意/拒绝/超时 |
| 状态机封闭 | 无非法跳转；转换留痕 |
| 水神回避 | 涉利益时法院替代审阅 |
| 现场门控 | 公投/守护审阅按区域在场（FR-INST-002-B） |
| 注入失败/重启/严格编解码 | 标准 |

---

## 7. Non-Goals

- 修宪后的制度实现（由对应模块手动执行）；GUI/包；紧急状态（FR-EMG）；
  技术权限；implementation。

## 8. Review Gate

Design candidate only. Does not constitute architecture approval, Human
Approval, or implementation authorization. Independent review requested.
