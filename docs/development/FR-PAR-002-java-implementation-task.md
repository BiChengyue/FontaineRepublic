# FR-PAR-002 Java Implementation Task

> **Status:** Prepared — Authorized by Human"继续做完服务端暂停的任务"
> **Task Type:** Feature Extension（议会扩展：守护审阅/公投/修宪）
> **Design Input:** FR-PAR-002-A（候选）
> **Dependency:** FR-PAR-001（已实现）、FR-CIT-001、FR-INST-002-B（区域在场）、
> FR-CORE-002、FR-AUD-001、FR-CMD-001-A
> **Authority Boundary:** 本文件细化实现范围；守护审阅交互通道待 Human 确认
> （默认：水神玩家在场 + 控制台，见设计 §4）。

---

## 1. Goal

在 FR-PAR-001 立法流水线上增加：

- 水神守护审阅（普通法 72h / 基本法 7d；退回一次；议会再通过门槛）；
- 宪法基本法/组织法的最高法院层级+合宪性审查（14d）；
- 公投（冻结名册、参与 2/3、赞成 2/3、一人一票）；
- 第一层修正案流水线（提案 -> 法院审查 -> 议会 4/5 -> 公投 -> 水神共同制宪同意）。

## 2. 明确不实现

- 修宪后的制度实现；GUI/包；紧急状态（FR-EMG）；技术权限；新依赖。

## 3. 具体契约

### 3.1 状态机扩展

```text
VOTING -> GUARDIAN_REVIEW -> APPROVED | GUARDIAN_RETURNED -> VOTING(override)
VOTING -> COURT_REVIEW -> VOTING/GUARDIAN_REVIEW
VOTING -> REFERENDUM_OPEN -> REFERENDUM_CLOSED -> APPROVED | REJECTED
AMENDMENT: PROPOSED -> COURT_REVIEW -> PARLIAMENT_VOTE -> REFERENDUM ->
           GUARDIAN_CONSENT -> APPROVED -> PUBLISHED
```

- 每次转换记录 actor/time/trigger/before/after/revision；
- 封闭枚举 + 非法跳转拒绝。

### 3.2 门槛

- 普通法守护审阅 72h 超时=批准；退回后议会 2/3 再通过生效；
- 基本法守护审阅 7d；退回后议会 4/5 再通过生效；
- 法院审查 14d（可延 7d）；组织法议会 2/3、基本法 3/4；
- 公投参与 ≥ 冻结名册 2/3、赞成 ≥ 有效票 2/3；弃权计参与不计有效；
- 修宪：议会 4/5 -> 公投 -> 水神同意 7d（超时=同意，明示拒绝=失败）。

### 3.3 交互通道

- 守护审阅/共同制宪同意：水神玩家在场（议会区域）+ 真实本地控制台（分类器复用
  bootstrap 模式）；默认两通道均实现，Human 可后续收敛；
- 公投投票：公民在议会公众区现场投票（FR-INST-002-B）；
- 法院审查：预留 Justice 服务接口（本任务只记录状态与时限，不实现审查逻辑）。

## 4. 测试计划

`src/test/java/com/fontainerepublic/server/parliament/LegislativeExtensionsFoundationTestMain.java`

- 守护审阅时限/超时/退回/再通过（普通/基本法分门槛）；
- 法院审查时限与状态推进；公投名册冻结/参与/赞成门槛/一人一票；
- 修宪流水线完整顺序与各阶段门槛；水神同意/拒绝/超时；
- 状态机封闭；注入失败无发布；重启恢复；严格编解码；
- 现场门控（公投/守护审阅区域在场）；无越权（不实现审查/执行）；
- `legislativeExtensionsFoundationTest` 接入 check；`gradlew build` 回归。

## 5. 验收

对应 FR-PAR-002-A §6；全部须有测试证据。

## 6. 约束

- 不改 FR-PAR-001 已实现行为；不新增依赖；服务端权威；SavedData/NBT only；
- 提交后审查（Codex）-> ROUTE TO HUMAN / RETEST / BLOCK。
