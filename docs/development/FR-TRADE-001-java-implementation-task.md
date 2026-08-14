# FR-TRADE-001 Java Implementation Task

> **Status:** Prepared — Authorized
> **Task Type:** 通讯器交易子系统
> **Design Input:** docs/architecture/fr-trade-001-a-communicator-trade.md

## 1. Goal

按设计实现手持通讯器玩家间交易：请求/接受 → 交易界面（钱+物品双方实时可见）
→ 双方同意 → 5s → 原子执行；取消/断线全额退还。

## 2. 明确不实现

- 购地（FR-LAND-CLAIM-001）；现金/市场/拍卖；客户端权威；新依赖。

## 3. 契约

- 服务端主线程串行会话；状态机
  `REQUESTED→OPEN→LOCKED(5s)→EXECUTING→COMPLETED` / `CANCELLED`；
- 金额托管：出价即从本人经济账户扣至会话托管，取消/失败退还，执行互换；
- 物品托管：双方 ≤4 槽，出价从背包移入会话，取消退还（背包满掉落记录），
  执行互换；全程防复制；
- 断线/登出/关服 → 取消 + 退还；
- C2S 全部速率策略；金额/槽位有界；越权拒绝；
- 协议 v6、账本 ID 9-15（C2S 9-14 + S2C 15），freeze 单向；
- 客户端 TradeScreen 只读快照 + 提交意向；门禁双方手持通讯器。

## 4. 测试计划

- 状态机全转移；托管金额扣/退/交换守恒；物品移入/退还/交换无复制；
- 越权/速率/金额槽位越界拒绝；断线取消退还；5s 倒计时回退；
- codec 往返与边界；既有网络/经济/客户端测试同步（v6/账本）；
- `gradlew build` 全绿。

## 5. 验收

FR-TRADE-001-A §6；提交后独立审查（Codex）→ ROUTE TO HUMAN / RETEST /
BLOCK（真机双人交易随 Human）。

## 6. 约束

- 不新增依赖；服务端权威；专用服务器不加载 client/ 类。
