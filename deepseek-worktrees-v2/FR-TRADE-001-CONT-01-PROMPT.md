# FR-TRADE-001-CONT-01 派发提示词（deepseek v4 flash 续作子进程）

## 1. 身份与目标

- 你是 FontaineRepublic（MC 1.20.1 / Forge 47.4.18 / Java 17）实现子进程。
- 任务 ID：FR-TRADE-001-CONT-01；目标：完成通讯器交易子系统剩余实现。
- 工作树 `D:\MC\FontaineRepublic\deepseek-worktrees-v2\fr-trade-001-impl`
  （分支 codex/fr-trade-001-impl）已有未提交改动（协议 v6、C2S 包、
  TradeStateSyncPacket、economy escrowTransfer），**继续使用并补齐**。

## 2. 必读（按顺序）

- 运行手册 -> CLAUDE.md -> current_status.md
- `docs/architecture/fr-trade-001-a-communicator-trade.md`（**§6.1 交易税**）
- `docs/development/FR-TRADE-001-java-implementation-task.md`
- 工作树现有改动：`common/network/`（v6、账本 9-15）、`common/trade/`（包）、
  `server/trade/model/`（TradePhase/TradeSession）、
  `server/economy/`（escrowTransfer）

## 3. 设计变更（Human 确认，覆盖旧 escrow 出价扣款模型）

### 3.1 出价不扣款（废除 offer 时扣款）

- 交易会话只保存**意向**：每方出价金额（long）、物品槽索引（≤4，含
  inventoryIndex；-1 表示取回）；**不预先从账户扣钱、不移出背包物品**；
- 取消/断线/关服：直接丢弃会话（无需退还——钱与物品从未离开账户/背包）；
- 执行时**原子复核 + 结算**。

### 3.2 执行结算（多腿原子）

- economy 新增 `executeTradeSettlement`（或等价）：
  - 单快照内完成：A→B（A 出价）、B→A（B 出价，若 B 出钱）、
    每个出钱方→国库（税额）；
  - 税额 = `floor(出价 × taxRatePercent / 100)`，taxRatePercent=5
    （可配置 `trade.taxRatePercent`）；只对**出钱方**收税；
  - 出钱方余额须 ≥ 出价 + 税，否则整笔拒绝（fail closed）；
  - supply 守恒（税入国库，总供给不变）；
  - 全部走 FR-CORE-002 持久化门；无通知/无 cooldown 耦合（沿用 escrowTransfer
    语义，可改造/复用其实现）。
- 物品：执行时逐槽校验（该槽仍在发件方背包且未变）→ 移入对方背包；
  任一项校验失败 → 整笔拒绝；背包满 → 拒绝并提示（不做掉落）。

### 3.3 状态机

`REQUESTED → OPEN → LOCKED(双方同意，5s 倒计时) → EXECUTING → COMPLETED`；
任意时刻 `CANCELLED`（取消/断线/关服/校验失败）；LOCKED 中任一方取消同意 →
回 OPEN；倒计时由服务端 tick 驱动（如 5s）。

## 4. 剩余实现清单

1. **Economy**：`executeTradeSettlement`（多腿原子 + 税），并保留/调整
   escrowTransfer 或由新方法替代（更新 EconomyService/Default/
   PresentationAware 透传）；
2. **server/trade/**：`TradeService`（request/respond/offerMoney/offerItem/
   agree/cancel/tick/execute/playerDisconnected/shutdown；主线程串行；
   双方须手持通讯器，执行时复核）、`TradeModule`（依赖 economy+network，
   tick 驱动 + logout 钩子）、`TradeRuntime`（静态定位）、
   `ServerTradePlayerAccess`（背包/手持/聊天）；
3. **FontaineRepublic**：注册 TradeModule + bind + 登录/登出钩子；
   onServerStopping 中**在 DataManager.beginShutdown() 之前**取消全部会话；
4. **客户端**：`ClientNetworkExecutor.acceptTradeStateSync`、
   `ClientTradeCache`、`TradeScreen`（双方槽位/金额/同意/倒计时/取消）、
   `ClientTradeSender`（C2S 发送）、`CommunicatorInteraction` 右键玩家 →
   发送 `TradeRequestPacket`（替代转账表单）；注册/侧隔离正确；
5. **测试同步**（编译必改）：
   - NetworkFoundationTestMain（v6、账本 0..15、C2S 断言）、
   - ClientPresentationFoundationTestMain（v6、unsafeRunWhenOn 引用 9→10）、
   - ClientStageB3a/B3b（v6、count 9→16）、
   - CommandFoundationTestMain mock 与 FakeEconomyService 补
     `executeTradeSettlement`（若接口增方法）；
6. **TradeFoundationTestMain**：状态机全转移、税计算（含 floor/边界）、
   多腿结算守恒、物品校验/移动、越权/速率/金额/槽位越界、断线取消、
   5s 倒计时回退、codec 往返；build.gradle 注册 `tradeFoundationTest`；
7. `gradlew build` 全绿；侧隔离源码扫描（专用服务器不加载 client/ 类）。

## 5. 约束

- 只在工作树内修改；不推送/SSH；不修改设计文档与台账；
- 服务端权威、主线程串行；出价不扣款；执行原子复核；
- 不新增依赖；开始时报分支/HEAD/工作区状态。

## 6. 交付格式

```text
任务：FR-TRADE-001-CONT-01
分支：{branch}
提交：{commit}
修改文件：{列表}
测试命令与结果：{命令 + 摘要}
未解决问题：{列表}
是否有越界文件：是/否（说明）
```

## 7. 提交前自检

- [ ] 出价不扣款；[ ] 执行多腿原子结算 + 5% 税；[ ] 物品复核防复制；
  [ ] 断线/关服仅丢弃会话（无残留钱物）；[ ] 测试同步；[ ] build 通过
