# FR-EMG-ECO-001 Java Implementation Task

> **Status:** Prepared — Authorized by Human"继续做完服务端暂停的任务" + 默认值已确认
> **Task Type:** Emergency Business Action Catalogue（economy.issue / economy.reclaim）
> **Design Input:** FR-ECO-001-C §9-12（目录/快照/收据）、FR-EMG-001-A（共享基础设施）
> **Dependency:** FR-EMG-001（已实现）、FR-ECO-001（玩家服务已实现）、FR-CORE-002、
> FR-ID-001、FR-AUD-001
> **Authority Boundary:** 本文件细化实现范围；不改变 FR-EMG/FR-ECO 契约。

---

## 1. Goal

把 Economy 的两个紧急动作注册进 FR-EMG 共享基础设施：

- `economy.issue`（紧急发钞：目标账户 +amount、总供给 +amount）；
- `economy.reclaim`（紧急回收：目标账户 -amount、总供给 -amount）。

按 FR-ECO-001-C §9-12：描述符（module=economy、action、version=1、PLAYER_UUID、
amount 参数 schema、允许类别）、side-effect-free prepare、经确认信封的 mutation
（一个快照含余额/供给/交易/成功收据/离线通知）、收据提供方。

## 2. 明确不实现

- 普通官方发钞/回收（FR-ECO-002 现场职责）；现金/ATM/利息/市场；GUI/包；
  FR-EMG 共享基础设施复刻；新依赖。

## 3. 具体契约

### 3.1 提供方（economy 侧）

```java
// server/economy/emergency/EconomyEmergencyProvider.java（概念）
implements EmergencyActionProvider {
    descriptor(): economy.issue / economy.reclaim（version 1, PLAYER_UUID, amount）
    preview(request, envelope): EconomyEmergencyPlan（余额/供给前后、修订绑定）
    mutate(plan, envelope): 一个快照（账户+供给+交易+成功收据+离线通知）-> COMMITTED 后发布
}
```

- prepare 无副作用；amount > 0；issue 可懒开户；reclaim 需余额充足；
- 供给恒等式 `sum(accounts)+treasury` 守恒/增减正确；
- 收据含 shared attempt/action id（FR-ECO-001-C §13 分类）。

### 3.2 注册

- 在 Mod 入口注册到 FR-EMG `EmergencyActionRegistry`（冻结前）；
- 收据提供方经 FR-EMG 对账接口暴露（watermark/分页）。

## 4. 测试计划

`src/test/java/.../economy/EconomyEmergencyFoundationTestMain.java`

- 描述符（id/version/目标类型/schema/类别）；
- issue/reclaim prepare（溢出/余额不足/懒开户）；mutation 单快照 + 供给守恒；
- 经确认信封（actor/category/reason/参数/摘要）校验；篡改拒绝；
- 注入失败无发布；收据与通知；重启恢复；
- 无 FR-EMG 复刻（actor/console/token 守卫）；`gradlew build` 回归。

## 5. 验收

对应 FR-ECO-001-C §15.2（紧急提供方测试）+ FR-EMG 门（除真机）；全部须有测试证据。

## 6. 约束

- 不改 FR-EMG/FR-ECO 已实现行为；不新增依赖；服务端权威；
- 提交后审查（Codex）-> ROUTE TO HUMAN / RETEST / BLOCK。
