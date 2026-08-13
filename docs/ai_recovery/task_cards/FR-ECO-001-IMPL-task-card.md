# Task Card — FR-ECO-001-IMPL

> Draft（前置模块完成后转 Authorized）

## Agent Identity

- **Agent:** Codex（Designer）
- **Task ID:** FR-ECO-001-IMPL
- **Task Name:** 经济模块 Phase 1 实现（Economy Player Services）
- **Request Summary:** 按 FR-ECO-001-A/B/C 与 FR-ECO-001-C-ACCOUNT-ALIGN-01
  实现 SubjectId 账户、零余额开户、余额/流水、UUID 目标转账、离线通知
- **Design Reference:**
  - docs/architecture/fr-eco-001-a-economy-architecture.md
  - docs/architecture/fr-eco-001-b-account-disclosure-cash-extension-policy.md
  - docs/architecture/fr-eco-001-c-foundation-scope-emergency-authority.md
  - docs/architecture/fr-eco-001-c-account-align-01.md
  - docs/architecture/fr-id-001-a-unified-digital-subject-registry.md
- **Human Authorization Reference:** FR-PHASE2-HUMAN-APPROVAL-01（批次批准）

## Scope

**Allowed:**

- `server/economy/`：EconomyAccount（SubjectId 键）/ EconomyTransaction /
  EconomyStore / EconomyNbtCodec（严格+有界）/ EconomyDataRepository（单写入者）
- EconomyService：ensureAccount / getBalance / getRecentTransactions /
  transfer（UUID 目标，收敛到 SubjectId）；货币显示配置
- EconomyModule 注册（依赖 player-data + subject-registry）
- 离线入账通知（随转账快照提交）；收据（transaction id 提交后返回）
- `economyFoundationTest` 验证任务 + `gradlew build` 回归
- 权威转账/开户走 FR-CORE-002 `commitModuleData("economy", ...)`

**Forbidden（FR-ECO-001-C §3）:**

- `/fr money top`、他人余额、`/fr bank*`、国库界面、冻结、央行职责；
- 现金/ATM/利息/税收/市场；紧急动作 `economy.issue/reclaim`（FR-EMG 门未过）；
- 玩家名离线输入（FR-DATA-003 未实现）；GUI/权威 C2S 包；额外依赖。

## Acceptance（FR-ECO-001-C §15）

- 零余额开户/登录懒开户；UUID 在线/离线目标转账收敛同一 SubjectId；
- 原子转账快照（借记+贷记+交易+通知+修订一次）；注入保存失败无发布；
- 收据隐私（参与者可见）；分页有界；货币显示不改变数值；
- 总供给守恒与加载对账；100K 缓冲修剪不损权威；
- `economyFoundationTest` + `gradlew build` 全绿。

## Lifecycle

Draft → Human Review Pending（FR-AUD/FR-CIT 完成后）→ Authorized → 派发 → 审查。
