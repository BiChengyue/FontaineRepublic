# FR-JUS-001 Java Implementation Task

> **Status:** Prepared — Authorized by FR-PHASE2-HUMAN-APPROVAL-01 + "继续工作"
> **Task Type:** Feature Module Implementation（司法模块）
> **Design Input:** FR-JUS-001-A（候选）
> **Dependency:** FR-INST-002、FR-CIT-001、FR-CORE-002、FR-AUD-001、FR-LAND-001
> **Authority Boundary:** 本文件细化实现范围；不改变设计契约。

---

## 1. Goal

实现司法模块：案件/证据/裁判 + 司法流水线 + Land 违规举报自动立案入口。

## 2. 明确不实现

- 裁判执行；违宪审查流水线（未来）；AI/谕示裁定枢机（Beta）；GUI/包；
- 技术权限映射；新依赖。

## 3. 具体契约

### 3.1 模型与存储

```text
justice
├── StoreVersion / StoreRevision
├── Cases / Evidence / Verdicts
```

- 流水线：DRAFT→FILED→ADMITTED→HEARING→VERDICT_PENDING→VERDICTED / REJECTED；
  VERDICTED→REVIEW_REQUESTED→REVIEWED→FINAL；
- 证据 append-only（受理后不静默移除）；裁判不可变；
- 每次状态转换记录 actor/time/trigger/before/after/revision；
- 严格编解码 + 失败关闭 + 确定性编码 + 有界限制。

### 3.2 服务

- fileCase / submitEvidence = ONSITE_PUBLIC_SERVICE；
- acceptCase / issueVerdict = ONSITE_OFFICIAL_DUTY；
- 最终变异边界校验现场上下文 + standing + 证据可采性；
- Land ViolationReport 立案 = 有界 intake；
- 每次权威变更一个完整快照 -> `commitModuleData("justice", ...)` -> COMMITTED 后发布。

### 3.3 命令

- `/fr court case file|list|show`、`evidence submit|list`、`verdict issue|show`、
  `review request|decide`；
- 经 CommandContributionRegistry 贡献；运行时解析；输出有界。

## 4. 测试计划

`src/test/java/com/fontainerepublic/server/justice/JusticeFoundationTestMain.java`

- 立案/证据（现场门控 + append-only）；受理/裁判（现场官方职责 + 绑定记录）；
- Land 举报立案 intake；流水线封闭枚举 + 非法跳转拒绝；复核路径；
- 独立性（无其他模块指示裁判）；无裁判执行；注入失败无发布；重启恢复；
- 严格编解码；无枚举；`justiceFoundationTest` 接入 check；`gradlew build` 回归。

## 5. 验收

对应 FR-JUS-001-A §6；全部须有测试证据。

## 6. 约束

- 不改已实现模块；不新增依赖；服务端权威；SavedData/NBT only；
- 提交后审查（Codex）-> ROUTE TO HUMAN / RETEST / BLOCK。
