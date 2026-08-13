# FR-CLIENT-001-B3a Java Implementation Task

> **Status:** Prepared — Authorized
> **Task Type:** 政府/议会信息视图（协议 v4）
> **Design Input:** docs/architecture/fr-client-001-b3-institution-land-views.md

## 1. Goal

1. 协议 v4；账本追加：

| ID | 消息 | 方向 | 载荷（有界） |
|---:|---|---|---|
| 5 | `GovernmentInfoPacket` | PLAY_TO_CLIENT | ministries(≤64)[(id≤64, name≤64, positionCount int≥0)]、at(>0) |
| 6 | `ParliamentInfoPacket` | PLAY_TO_CLIENT | proposals(≤128)[(id≤64, stage≤32, normLevel≤32, title≤128)]、at(>0) |

2. `InstitutionPresentationSync`（server 侧，非模块）：登录时解析
   `GovernmentService.ministries()` 与 `ParliamentService.proposals(0, ≤128)`
   组装两个包，经 `NetworkSendService.trySendToPlayer` 发送；absent/离线/
   异常 best-effort；不改 government/parliament 模块定义。
3. 客户端：缓存增 government/parliament 快照；执行器/handler 增两个 accept；
   `/frclient government|parliament`；`GovernmentScreen`/`ParliamentScreen`；
   `ClientViewProjection` 增投影（空态占位）。

## 2. 明确不实现

- 法院/土地视图（B-3b）；分页/详情（首发为摘要快照）；权威 C2S 包；新依赖。

## 3. 契约

- 消息类/handler 位于 common/network/display，侧隔离同 Stage A；
- 登录发送经 presence 过滤；业务结果不变；
- 客户端缓存非权威、登出清理。

## 4. 测试计划

`src/test/java/.../client/ClientStageB3aFoundationTestMain.java`

- 两个新 codec 往返与边界（超长/负值/空列表/数量上限）；
- 账本 7 条 ID 0-6、PLAY_TO_CLIENT、freeze 单向（同步既有网络测试）；
- 展示缓存语义、投影渲染（含空态）；
- 登录发送接线：fake sendService + 空投影 → 零发送零异常；
- 侧隔离源码扫描；
- `gradlew build` 全绿。

## 5. 验收

FR-CLIENT-001-B3 设计 §6；提交后独立审查（Codex）→ ROUTE TO HUMAN /
RETEST / BLOCK。

## 6. 约束

- 不新增依赖；服务端权威；专用服务器不加载 client/ 类；
- 协议升版按 FR-NET-001-A §6.4（消息表变更 → v4）。
