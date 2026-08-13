# FR-CLIENT-001-B3b Java Implementation Task

> **Status:** Prepared — Authorized
> **Task Type:** 法院/土地信息视图（协议 v5）
> **Design Input:** docs/architecture/fr-client-001-b3-institution-land-views.md §5.1

## 1. Goal

1. 协议 v5；账本追加：

| ID | 消息 | 方向 | 载荷（有界） |
|---:|---|---|---|
| 7 | `JusticeInfoPacket` | PLAY_TO_CLIENT | cases(≤128)[(id≤64, stage≤32, summary≤128)]、at(>0) |
| 8 | `LandInfoPacket` | PLAY_TO_CLIENT | parcelCount(≥0)、totalArea(≥0)、zones(≤16)[(zone≤32, count≥0, area≥0)]、storeRevision(≥0)、at(>0) |

2. `LandService.publicSummary()`：单个有界只读聚合（不可变 `LandSummary`
   记录：parcelCount/totalArea/zone 分布/storeRevision）；实现为遍历内部
   地块计算聚合，**返回单值不返回列表**，通过 LandFoundationTestMain 无枚举
   验收（必要时同步该测试的豁免注释）。
3. `InstitutionPresentationSync` 扩展：登录时一并发送法院案件摘要（复用
   `JusticeService.cases(0, ≤128)`）与土地公共概况；best-effort 不变。
4. 客户端：缓存增 court/land 快照；执行器/handler 两个 accept；
   `/frclient court|land`；`CourtScreen`/`LandScreen`；投影（空态占位）。

## 2. 明确不实现

- 地块枚举/个人权益视图；权威 C2S 包；新依赖；其他模块业务规则改动。

## 3. 契约

- 消息类/handler 位于 common/network/display，侧隔离同前；
- 登录发送经 presence 过滤；业务结果不变；
- 客户端缓存非权威、登出清理。

## 4. 测试计划

`src/test/java/.../client/ClientStageB3bFoundationTestMain.java`

- 两个新 codec 往返与边界（超长/负值/空列表/数量上限）；
- 账本 9 条 ID 0-8、PLAY_TO_CLIENT、freeze 单向（同步既有网络测试）；
- 展示缓存语义、投影渲染（含空态）；
- 登录发送接线：fake sendService + 空投影 → 零发送零异常；
- LandSummary 聚合正确性（含空仓库与分布）；
- 侧隔离源码扫描；
- `gradlew build` 全绿。

## 5. 验收

FR-CLIENT-001-B3 §5.1；提交后独立审查（Codex）→ ROUTE TO HUMAN /
RETEST / BLOCK。

## 6. 约束

- 不新增依赖；服务端权威；专用服务器不加载 client/ 类；
- 协议升版按 FR-NET-001-A §6.4（消息表变更 → v5）。
