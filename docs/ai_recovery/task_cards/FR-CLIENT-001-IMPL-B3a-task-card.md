# Task Card — FR-CLIENT-001-IMPL-B3a（政府 + 议会信息视图）

> Status: Authorized（客户端阶段继续；复用既有公开投影，无模块边界改动）
> Task Type: 客户端阶段 B-3a — 政府/议会信息视图（协议 v4）
> Design Reference: docs/architecture/fr-client-001-b3-institution-land-views.md
> Dependency: FR-CLIENT-001-IMPL-A/B/B2（已并入）

## Scope

**Allowed:** 协议 v4；账本追加 `GovernmentInfoPacket(5)` /
`ParliamentInfoPacket(6)`；`InstitutionPresentationSync`（server 侧轻量
组装，登录时经 NetworkSendService 发送，复用 `GovernmentService.ministries()`
与 `ParliamentService.proposals()`）；客户端缓存/执行器/handler/
`/frclient government|parliament`/GovernmentScreen/ParliamentScreen/投影；
契约测试。

**Forbidden:** 法院/土地视图（B-3b）；模块定义改动；权威 C2S 包；新依赖；
服务端业务规则改动。

## Acceptance

- 协议 v4；账本 7 条（ID 0-6）；freeze 单向；有界 codec；
- 登录后 FR 客户端收到政府/议会公开摘要（presence 过滤、absent 零影响）；
- 界面读非权威缓存；专用服务器不加载 client/ 类；
- `clientStageB3aFoundationTest` + `gradlew build` 全绿。
