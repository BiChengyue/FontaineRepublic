# FR-PAR-001 Java Implementation Task

> **Status:** Prepared — Authorized by FR-PHASE2-HUMAN-APPROVAL-01 + "继续工作"
> **Task Type:** Feature Module Implementation（议会模块）
> **Design Input:** FR-PAR-001-A（候选）
> **Dependency:** FR-INST-002、FR-CIT-001、FR-CORE-002、FR-AUD-001、FR-ID-001
> **Authority Boundary:** 本文件细化实现范围；不改变设计契约。

---

## 1. Goal

实现议会模块：提案/投票/法案 + 法律状态机（DRAFT→REVIEW→VOTING→APPROVED→PUBLISHED→
ACTIVE / REJECTED；SUSPENDED/INVALID/EXPIRED 预留）。

## 2. 明确不实现

- 法律执行；水神守护审阅（72h）；公投；修宪流水线（后续修订）；
- 技术权限映射；GUI/包；新依赖。

## 3. 具体契约

### 3.1 模型与存储

```text
parliament
├── StoreVersion / StoreRevision
├── Proposals / Votes / Bills / Transitions
```

- 规范层级校验（宪法 v3.0 §11/FR-BL-003）：宪法基本法/组织法/普通法律/行政规则；
- 阈值：普通过半数、组织法 2/3、宪法基本法 3/4（向上取整，冻结公民名单为分母）；
- 投票一人一票；投票开启时冻结名单（FR-BL-005 §2）；
- 每次状态转换记录 actor/time/trigger/before/after/revision；
- 严格编解码 + 失败关闭 + 确定性编码 + 有界限制。

### 3.2 服务

- submitProposal / openVote / castVote / closeVoteAndAdvance / bill / proposals；
- 提交/开票/投票/结票 = ONSITE_OFFICIAL_DUTY：最终变异边界校验现场上下文 +
  公民资格（FR-CIT）+ 阈值；
- 每次权威变更一个完整快照 -> `commitModuleData("parliament", ...)` -> COMMITTED 后发布。

### 3.3 命令

- `/fr parliament proposal submit|list`、`vote open|cast|close`、`bill show`；
- 经 CommandContributionRegistry 贡献；运行时解析；输出有界。

## 4. 测试计划

`src/test/java/com/fontainerepublic/server/parliament/ParliamentFoundationTestMain.java`

- 提案提交（现场上下文 + 规范层级）；开票/投票（公民资格 + 一人一票）；
- 结票阈值（1/2、2/3、3/4 向上取整）；状态机封闭枚举与非法跳转拒绝；
- 无法律执行（不触碰其他模块）；无技术权限映射；注入失败无发布；重启恢复；
- 严格编解码；无枚举；`parliamentFoundationTest` 接入 check；`gradlew build` 回归。

## 5. 验收

对应 FR-PAR-001-A §6；全部须有测试证据。

## 6. 约束

- 不改已实现模块；不新增依赖；服务端权威；SavedData/NBT only；
- 提交后审查（Codex）-> ROUTE TO HUMAN / RETEST / BLOCK。
