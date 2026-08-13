# FR-ECO-001 Java Implementation Task

> **Status:** Prepared — Authorized by FR-PHASE2-HUMAN-APPROVAL-01
> **Task Type:** Feature Module Implementation (Phase 1 player services)
> **Design Input:** FR-ECO-001-A/B/C + FR-ECO-001-C-ACCOUNT-ALIGN-01 + FR-ID-001-A
> **Dependency:** FR-CORE-002、FR-ID-001（subject registry）、FR-DATA-001、FR-CMD-001
> **Authority Boundary:** 本文件细化实现范围；不改变已批准设计契约。

---

## 1. Goal

实现经济 Phase 1 玩家服务：

- 账户主键 = `SubjectId`（UUID 经 FR-ID 解析；登记号为唯一公开路由号）；
- 零余额懒开户；登录/已知 UUID 幂等开户；
- 本人余额/流水（有界分页）；UUID 在线/离线目标转账（可选 memo）；收据；
- 离线入账通知（随转账快照提交，登录聊天回退）；
- 总供给守恒 + 加载对账；货币显示可配置。

## 2. 明确不实现（FR-ECO-001-C §3）

- `/fr money top`、他人余额、`/fr bank*`、国库界面、冻结、央行职责；
- 现金/ATM/利息/税收/市场；紧急动作 `economy.issue/reclaim`（FR-EMG 门未过）；
- 玩家名离线输入（FR-DATA-003 未实现）；GUI/权威 C2S 包；新依赖。

## 3. 具体契约

### 3.1 模型与存储

```java
// server/economy/model
record EconomyAccount(SubjectId subjectId, long balance, long accountRevision,
                      long createdAt, long lastTransactionId) {}
record EconomyTransaction(long transactionId, long timestamp, SubjectId from,
                          SubjectId to, long amount, TransactionType type,
                          String reason) {}   // 参与者为 SubjectId，非游戏名
```

```text
fontainerepublic.dat
└── modules
    └── economy
        ├── StoreVersion / StoreRevision / TreasuryBalance / NextTransactionId
        ├── Accounts: <SubjectId> -> account
        ├── Transactions: <id> -> transaction   // 有界（100K，最旧修剪）
        └── PendingNotifications: <SubjectId> -> bounded summaries
```

- 余额非负、上限 Long.MAX_VALUE/2；金额 > 0；memo ≤128 字符、规范化；
- 严格编解码（declared fields only、确定性、失败关闭、容量/字节上限）。

### 3.2 服务

```java
// server/economy/api/EconomyService（概念）
interface EconomyService {
    EconomyAccount ensureAccount(SubjectId subjectId);       // 幂等
    long getBalance(SubjectId subjectId);
    Page<EconomyTransaction> getRecentTransactions(SubjectId subjectId, long afterId, int limit);
    TransferReceipt transfer(SubjectId from, SubjectId to, long amount, String memo);
    List<NotificationSummary> pendingNotifications(SubjectId subjectId);  // 登录时
    void acknowledgeNotification(SubjectId subjectId, long notificationId);
}
```

### 3.3 转账原子快照

一个完整替换快照必须同时包含：借记源账户、贷记目标账户、一条 TRANSFER 交易、
nextTransactionId、受影响修订、离线目标 PENDING 通知（如需）、总供给守恒校验。

提交：`DataManager.commitModuleData("economy", ...)`；仅 `COMMITTED` 后发布
（余额/交易/收据/通知/聊天均在其后）。

### 3.4 供给守恒

```text
Digital Money Supply = sum(accounts) + treasuryBalance
```

加载与每次变异对账；越界/不一致拒绝。普通转账守恒；无普通发钞路径。

## 4. 测试计划

`src/test/java/com/fontainerepublic/server/economy/EconomyFoundationTestMain.java`

- 零余额开户/懒开户/幂等；UUID 在线/离线目标收敛同一 SubjectId；
- memo 规范化与边界；原子转账（注入保存失败无发布）；cooldown；陈旧修订拒绝；
- 收据隐私（参与者可见）；分页有界；离线通知随快照提交 + 登录回退；
- 总供给守恒与加载对账；100K 修剪不损权威；货币显示不改数值；
- 无被禁界面（top/bank/他人余额/GUI/权威 C2S）；
- `economyFoundationTest` 接入 check；`gradlew build` 回归。

## 5. 验收映射

对应 FR-ECO-001-C §15；全部须有测试证据。

## 6. 约束

- 不改已实现模块行为；不新增依赖；服务端权威；SavedData/NBT only；
- 账户主键 SubjectId；登记号唯一公开路由号；
- 提交后审查（Codex）-> ROUTE TO HUMAN / RETEST / BLOCK。
