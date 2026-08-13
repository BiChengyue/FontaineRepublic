# FR-CMD-USER-001 Java Implementation Task

> **Status:** Prepared — Authorized by FR-PHASE2-HUMAN-APPROVAL-01
> **Task Type:** Command/Lifecycle Wiring（无新业务逻辑）
> **Design Input:** FR-ECO-001-A §6/§8.2、FR-ECO-001-B/C、FR-CIT-001-A §5、FR-CMD-001-A
> **Dependency:** 已实现模块（economy/citizen/registry/player-data/core）
> **Authority Boundary:** 只接线已批准命令面；不新增业务逻辑。

---

## 1. Goal

把已实现服务接到玩家可用入口：

- `/fr money balance`（本人余额）、`/fr money pay <uuid> <amount> [memo]`
  （UUID 目标；玩家名输入保持禁用）、`/fr money history [page]`（本人流水）；
- `/fr citizen info`（本人公民状态/等级，只读）；
- 登录钩子：`ensureAccount`（经济）+ `ensureCitizen`（公民）幂等预开户；
- 统一经 CommandContributionRegistry 贡献（top-level `money` / `citizen`）；
- 运行时经 CommandRuntimeResolver 解析当前服务；反馈走 CommandFeedback。

## 2. 明确不实现

- `/fr money top`、他人余额、`/fr bank*`、国库、冻结、现金、发钞；
- 玩家名输入（FR-DATA-003 未上线前）；GUI/包；新业务策略；新依赖。

## 3. 具体契约

### 3.1 命令面

```text
/fr money balance                 -> 本人余额（无参数）
/fr money pay <uuid> <amount> [memo] -> 转账（金额 long > 0；memo ≤128 规范化）
/fr money history [page]          -> 本人流水（有界分页；参与者可见）
/fr citizen info                  -> 本人 status + rank（只读）
```

- 执行时从 CommandRuntimeResolver 取当前 ACTIVE 服务；不可用 -> 标准反馈；
- 最终解析：UUID -> FR-ID subject -> economy/citizen 服务（不读 NBT）；
- cooldown 仅作滥用控制，不替代修订校验；
- 无 top/bank/他人余额路径。

### 3.2 登录钩子

- `onPlayerLoggedIn`：PlayerData 就绪后调用 `ensureAccount` / `ensureCitizen`
  （幂等；失败仅记录日志，不影响登录）。

### 3.3 测试

- `commandFoundationTest` 扩展：命令树存在/参数校验/服务不可用反馈；
- 登录钩子调用幂等性（注入测试）；
- `gradlew build` 回归（全部 foundation 测试）。

## 4. 验收

- `/fr money balance/pay/history`、`/fr citizen info` 可用且输出有界；
- 无禁用命令（top/bank/他人余额）注册（反射/源码守卫）；
- 登录钩子幂等；`gradlew build` 全绿。

## 5. 约束

- 不改已实现模块行为；不新增依赖；服务端权威；
- 提交后审查（Codex）-> ROUTE TO HUMAN / RETEST / BLOCK。
