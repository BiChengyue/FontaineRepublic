# FR-CLIENT-001-B2 Java Implementation Task（Stage B-2）

> **Status:** Prepared — Authorized
> **Task Type:** 公民卡 + 交易历史视图
> **Design Input:** FR-CLIENT-001-A v1.1 §4/§7
> **Dependency:** FR-CLIENT-001-IMPL-A/B（已并入）、FR-CIT-001、FR-ECO-001

## 1. Goal

在 Stage A/B-1 基础上追加：

1. 协议 v3 + 消息账本追加：

| ID | 消息 | 方向 | 载荷（有界） |
|---:|---|---|---|
| 3 | `CitizenInfoPacket` | PLAY_TO_CLIENT | registryNumber(≤32)、citizenStatus(≤32)、citizenRank(≤32)、firstCitizenAt(>0)、at(>0) |
| 4 | `TransactionHistorySyncPacket` | PLAY_TO_CLIENT | entries(≤128)[(transactionId>0, direction 0/1, amount>0, counterpartyDigest hex≤64, memo≤128 可空, at>0)]、nextAfterId(≥0)、hasMore(bool)、at(>0) |

2. 公民模块发送接线：`requiredDependencies` 增 `network`；`bindServices` 增
   `NetworkSendService`；`PresentationAwareCitizenService` 装饰器在
   `ensureCitizen` 成功后经 `CitizenPresentationNotifier` 发送
   `CitizenInfoPacket`（登记号经已绑定的 SubjectRegistryService 解析）；
   展示异常吞咽，业务结果不变。
3. economy 历史发送：`EconomyPresentationNotifier` 增
   `syncHistory(UUID playerId, EconomyPage<EconomyTransaction> page)`；
   `PresentationAwareEconomyService.ensureAccountForPlayer` 在 syncAccount
   后追加发送第一页历史（经 delegate.getRecentTransactions）。
4. 客户端：`ClientPresentationCache` 增历史字段与 `setHistory/clear`；
   `ClientNetworkExecutor`/`DisplayMessageHandlers` 增两个 accept/handler；
   `ClientManager` 增 `/frclient citizen|history`；`CitizenScreen`、
   `HistoryScreen`；`ClientViewProjection` 增 citizen/history 投影。

## 2. 明确不实现

- 机构/土地信息视图（Stage B-3）；权威 C2S 包；分页翻页（首发仅第一页）；
- 新依赖；服务端业务规则改动。

## 3. 契约

- 消息类/handler 位于 common/network/display，侧隔离与 Stage A 相同；
- 发送全经 `NetworkSendService.trySendToPlayer`；absent/离线/异常 best-effort；
- 客户端缓存非权威、登出清理；界面只读缓存；
- 专用服务器不加载 client/ 类（FMLClientSetupEvent 模式不变）。

## 4. 测试计划

`src/test/java/.../client/ClientStageB2FoundationTestMain.java`

- 两个新 codec 往返与边界（超长/负值/hex/列表上限/空列表）；
- 账本：5 条、ID 0-4 升序、PLAY_TO_CLIENT、freeze 单向；
- 侧隔离源码扫描（display 无 client import；server/ 不引用 client/）；
- 展示缓存历史字段语义（set/clear/有界）；
- 公民/历史投影渲染；
- 发送接线：公民装饰器与 history notifier 的 absent/抛错不影响业务
  （fake 注入）；
- `gradlew build` 全绿。

## 5. 验收

FR-CLIENT-001-A v1.1 §5；提交后独立审查（Codex）→ ROUTE TO HUMAN /
RETEST / BLOCK（真机目视核验随 Stage C）。

## 6. 约束

- 不新增依赖；服务端权威；专用服务器不加载 client/ 类；
- 协议升版按 FR-NET-001-A §6.4（消息表变更 → v3）。
