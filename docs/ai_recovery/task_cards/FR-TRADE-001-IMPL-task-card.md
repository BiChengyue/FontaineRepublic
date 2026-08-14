# Task Card — FR-TRADE-001-IMPL（通讯器交易子系统）

> Status: Authorized（Human 需求 2026-08-14）
> Task Type: 服务端交易会话 + 协议 + 交易 GUI
> Design Reference: docs/architecture/fr-trade-001-a-communicator-trade.md

## Scope

**Allowed:** `server/trade/`（TradeSession/TradeService/托管金额+物品）、
协议 v6 消息 ID 9-15（C2S 带速率策略）、`TradeScreen`、右键玩家 → 交易请求、
门禁（双方手持通讯器）、状态机/托管/交换/断线测试。

**Forbidden:** 购地（FR-LAND-CLAIM-001）；客户端权威状态；现金/市场；
新依赖（Forge 自带除外）。

## Acceptance

- 双方手持通讯器可发起/接受交易；界面实时同步双方钱+物品与同意状态；
- 双方同意 → 5s 倒计时 → 原子执行（金额互换 + 物品互换，供给/物品守恒）；
- 取消/断线/倒计时回退均全额退还、无复制无丢失；
- `tradeFoundationTest` + `gradlew build` 全绿；专用服务器不加载 client/ 类。
