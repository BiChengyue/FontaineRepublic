# FR-TRADE-001-A — 通讯器交易子系统（设计候选 v1.0）

> **Task ID:** FR-TRADE-001-A
> **Status:** Design Candidate — 实施中（Human 需求 2026-08-14）
> **Purpose:** 手持通讯器的两名玩家之间进行钱+物品的安全实时交易

## 1. 需求（Human）

- 手持通讯器的玩家可向另一手持通讯器的玩家发起交易；
- 对方同意后打开交易界面；双方可随意放入钱和物品并实时看到对方状态；
- 双方均同意后进入 5 秒确认，随后执行交易；
- 执行前任何一方可随时取消同意/取消交易。

## 2. 权威与安全原则

- 交易状态**服务端权威**；客户端只展示与提交意向；
- 金额用经济账户**托管扣减**：出价时从本人账户扣至交易托管，取消/失败即
  退还，执行时交换托管——杜绝双花；
- 物品**移入服务端托管槽**（双方各 ≤4 格）：出价时从背包移出至会话，取消
  退还（背包满则掉落并记录），执行时原子交换；服务端主线程串行，杜绝复制；
- 会话生命周期：`REQUESTED → OPEN → LOCKED(5s) → EXECUTING → COMPLETED`，
  任意时刻 `CANCELLED`（取消/超时/断线/关服）；
- 断线/登出/关服 → 取消会话并全额退还；
- C2S 全部有速率策略；金额有界；槽位有界。

## 3. 网络面（协议 v6，账本追加 ID 9-14）

| ID | 方向 | 消息 | 载荷（有界） |
|---:|---|---|---|
| 9 | C2S | `TradeRequestPacket` | target UUID（规范）|
| 10 | C2S | `TradeRespondPacket` | sessionId(long>0)、accept(bool) |
| 11 | C2S | `TradeOfferMoneyPacket` | sessionId、amount(long≥0，覆盖式出价) |
| 12 | C2S | `TradeOfferItemPacket` | sessionId、slot(0..3)、来源背包格 index |
| 13 | C2S | `TradeAgreePacket` | sessionId、agree(bool) |
| 14 | C2S | `TradeCancelPacket` | sessionId |
| 15 | S2C | `TradeStateSyncPacket` | session 快照（双方 money/items/agree/phase/countdown）|

（S2C 亦可复用已有 display 注册路径；C2S 首次引入，需带速率策略。）

## 4. 服务端

- `server/trade/`：`TradeSession`（不可变状态模型）、`TradeService`（主线程
  串行：request/respond/offerMoney/offerItem/agree/cancel/tick/execute）、
  `TradeRepository`（可选持久化；会话可仅内存，执行结果走经济/背包提交）；
- 出价金额经 `EconomyService` 托管（新增 escrow 接口或直接调用仓库）；
- 执行：两方托管金额互换（经济账户原子双写）+ 物品槽互换（背包原子交换）；
- 5 秒倒计时由服务端 tick 驱动；倒计时中任何一方取消同意 → 回 OPEN。

## 5. 客户端

- `client/gui/trade/TradeScreen`：双方槽位 + 金额输入 + 同意/取消按钮 +
  实时状态与倒计时；读 S2C 快照；
- 右键玩家（手持通讯器）→ 发送 `TradeRequestPacket`；收到请求 →
  聊天/屏幕确认；
- 门禁：发起与执行时双方均须手持通讯器。

## 6. 测试

- 状态机全转移（含取消/断线/倒计时回退）；
- 托管金额扣/退/交换守恒；物品移入/退还/交换无复制；
- C2S 速率、越权（非会话成员）、金额/槽位越界拒绝；
- codec 往返与边界；`gradlew build` 全绿。

## 6.1 交易税（Human 需求 2026-08-14）

- **所有交易按次收税，双方各收**：执行时从双方各扣一笔固定税额（默认 1
  Mora，可配置 `trade.taxPerSide`），入国库（supply 守恒：税额来自玩家余额，
  国库增加，总供给不变）；
- 税额在同意倒计时开始时冻结显示，执行时校验双方余额足以支付税 + 出价；
- 任一方余额不足以支付税 → 执行拒绝并全额退还（fail closed）；
- 税额不参与交换（不进入对方托管）；审计随交易记录。

## 7. 分阶段

- **FR-TRADE-001-IMPL**：服务端会话 + 托管 + 执行 + 协议 + TradeScreen +
  右键请求；一次派发（较大，Reviewer 兜底）。
- **FR-LAND-CLAIM-001**：右键无主方块 → 创建共和国地块并取得使用权
  （后续独立任务）。
- **交易税**随 FR-TRADE-001 一并实现（本设计 §6.1）。

## 8. Review Gate

设计候选；实施后独立审查（Codex）→ ROUTE TO HUMAN / RETEST / BLOCK
（真机双人交易核验随 Human）。
