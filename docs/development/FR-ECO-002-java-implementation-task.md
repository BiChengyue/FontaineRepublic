# FR-ECO-002 Java Implementation Task

> **Status:** Prepared — Authorized by Human"现在就可以做"（默认值确认后转 Authorized）
> **Task Type:** Feature Module Implementation（央行现场职责）
> **Design Input:** FR-ECO-002-A（候选，含建议默认值）
> **Dependency:** FR-ECO-001（玩家服务已实现）、FR-INST-002（已实现）、FR-CORE-002、
> FR-AUD-001、FR-ID-001、FR-CMD-001-A
> **Authority Boundary:** 本文件细化实现范围；默认值以 Human 确认为准。

---

## 1. Goal

实现央行现场职责：

- `/fr bank balance`（国库总额，只读公开）；
- `/fr bank deposit <player> <amount>`（发钞：国库减少、目标账户增加、供给 +amount）；
- `/fr bank withdraw <player> <amount>`（回收：目标账户减少、国库增加、供给 -amount）；
- `/fr bank freeze|unfreeze <player>`（冻结/解冻）。

## 2. 明确不实现

- 现金/ATM/利息/市场；紧急 issue/reclaim（FR-EMG）；他人余额展示；GUI/包；新依赖。

## 3. 具体契约

### 3.1 现场门控

- 所有官方变异（deposit/withdraw/freeze/unfreeze）必须是
  `ONSITE_OFFICIAL_DUTY`：`InstitutionAccessService.validateAtMutation(context,
  ONSITE_OFFICIAL_DUTY, now)` 在最终变异边界复检；上下文单次使用；
- OP 权限仅为早期闸门（`.requires(LEVEL_2)`），不满足现场规则；
- balance 为只读公开展示，无变异。

### 3.2 供给守恒

```text
Digital Money Supply = sum(accounts) + treasury
deposit:   account +amount, treasury -amount, supply +amount
withdraw:  account -amount, treasury +amount, supply -amount
```

- 余额非负、上限 Long.MAX_VALUE/2；amount > 0；withdraw 需账户余额充足；
- 每次官方变异一个完整快照 -> `commitModuleData("economy", ...)` -> COMMITTED 后发布；
- 失败无任何发布（余额/国库/供给/收据）。

### 3.3 冻结语义

- 冻结账户：拒绝转账/提取（ordinary transfer/withdraw），余额可见；
- 解冻恢复；冻结状态存于 EconomyAccount（新字段或现有字段扩展，明确 schema）；
- 官方 deposit 可对冻结账户？——按默认值：deposit 允许（不涉及扣减），withdraw 拒绝；
  此条随默认值确认。

### 3.4 审计

- 每笔官方操作写 FR-AUD（actor=现场官员 UUID，category=FINANCE，含金额/目标/类型）；
- 紧急动作隔离：不实现 issue/reclaim。

## 4. 测试计划

`src/test/java/com/fontainerepublic/server/economy/CentralBankFoundationTestMain.java`

- balance 只读公开；deposit/withdraw/freeze 现场门控（无上下文拒绝、单次使用）；
- 供给守恒（deposit/withdraw 后恒等式成立）；withdraw 余额不足拒绝；
- 冻结拒绝转账/提取、解冻恢复；注入失败无发布；重启恢复；
- 无现金/ATM/他人余额/紧急动作（反射/源码守卫）；
- `centralBankFoundationTest` 接入 check；`gradlew build` 回归（含 economyFoundationTest）。

## 5. 验收

对应 FR-ECO-002-A §5 矩阵；全部须有测试证据。

## 6. 约束

- 不改已实现模块行为；不新增依赖；服务端权威；SavedData/NBT only；
- 提交后审查（Codex）-> ROUTE TO HUMAN / RETEST / BLOCK。
