# Task Card — FR-CLIENT-001-IMPL-B3b（法院 + 土地信息视图）

> Status: Authorized（B-3b 设计决策已记录：法院复用 cases()；土地为公共概况
> 聚合，符合 FR-LAND 无枚举契约）
> Task Type: 客户端阶段 B-3b — 法院/土地信息视图（协议 v5）
> Design Reference: docs/architecture/fr-client-001-b3-institution-land-views.md §5.1

## Scope

**Allowed:** 协议 v5；账本追加 `JusticeInfoPacket(7)`（复用
`JusticeService.cases()`）/ `LandInfoPacket(8)`（新增 `LandService.publicSummary()`
单个有界聚合）；`InstitutionPresentationSync` 扩展登录发送；客户端缓存/
执行器/handler/`/frclient court|land`/CourtScreen/LandScreen/投影；契约测试。

**Forbidden:** 地块枚举视图/个人权益视图（后续阶段）；权威 C2S 包；新依赖；
其他模块业务规则改动。

## Acceptance

- 协议 v5；账本 9 条（ID 0-8）；freeze 单向；有界 codec；
- 登录后 FR 客户端收到法院案件摘要与土地公共概况（presence 过滤）；
- `LandService.publicSummary()` 为单值聚合（非列表），通过无枚举验收；
- 界面读非权威缓存；专用服务器不加载 client/ 类；
- `clientStageB3bFoundationTest` + `gradlew build` 全绿。
