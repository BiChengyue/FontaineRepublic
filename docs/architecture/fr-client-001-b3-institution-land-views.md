# FR-CLIENT-001-B3 — 机构/土地信息视图（设计候选 v1.0）

> **Task ID:** FR-CLIENT-001-B3
> **Status:** Design Candidate（准备中；等待 Stage A/B-1/B-2 真机反馈后实施）
> **Design Input:** FR-CLIENT-001-A v1.1 §4.2（后续阶段追加展示消息）

## 1. 目标

为首版客户端补上机构与土地信息视图（只读展示）：

- 政府：部门/职位公开摘要；
- 议会：提案公开摘要；
- 法院：案件公开摘要；
- 土地：地块公开信息（只读）。

## 2. 现状核对（FR-CLIENT-001-A-REVIEW-01 结论）

- `GovernmentService.ministries()` / `positionsByMinistry()` 已存在公开投影；
- `ParliamentService.proposals(afterSeq, limit)` 已存在公开投影；
- `JusticeService.cases(afterSeq, limit)` 已存在公开投影；
- `LandService` 仅有 `getParcel(ParcelId)`（按 id），**无区域/列表公开投影**——
  地块视图需要新增有界 `parcelsInRegion` 只读投影（模块 API 新增，需单独
  设计审查）。

## 3. 消息账本追加（协议 v4，ID 5-8）

| ID | 消息 | 方向 | 载荷（有界） |
|---:|---|---|---|
| 5 | `GovernmentInfoPacket` | PLAY_TO_CLIENT | ministries(≤64)[(id≤64, name≤64, positionCount int≥0)]、at(>0) |
| 6 | `ParliamentInfoPacket` | PLAY_TO_CLIENT | proposals(≤128)[(id≤64, stage≤32, normLevel≤32, title≤128)]、at(>0) |
| 7 | `JusticeInfoPacket` | PLAY_TO_CLIENT | cases(≤128)[(id≤64, stage≤32, summary≤128)]、at(>0) |
| 8 | `LandInfoPacket` | PLAY_TO_CLIENT | parcels(≤64)[(id≤64, zone≤32, ownerDigest≤64, size>0)]、at(>0) |

机构包为**登录快照**：登录时服务端组装一次发送（机构/议会/法院数据变化不频繁；
变更实时推送留待后续）。土地包依赖新的只读投影。

## 4. 接线

- 政府/议会/法院：复用现有公开投影，经一个轻量 `InstitutionPresentationSync`
  （server 侧，非模块）在登录时经 `NetworkSendService` 发送；无需改动
  government/parliament/justice 模块定义；
- 土地：`LandService` 新增有界 `parcelsInRegion` 只读投影（B-3b 范围）；
- 客户端：缓存/执行器/handler/命令/界面/投影，同 Stage B-2 模式；
- 协议 v4、账本 7/9 条（按阶段）。

## 5. 分阶段

- **B-3a**：政府 + 议会（ID 5-6，协议 v4，账本 7 条；复用现有投影）；
- **B-3b**：法院 + 土地（ID 7-8，账本 9 条）。

## 5.1 B-3b 设计决策（FR-LAND 契约对齐）

- 法院：复用 `JusticeService.cases(afterSeq, limit)` 有界分页投影，无需新增
  API；
- 土地：LandService 明示"无批量地块列表"（FR-LAND-001-A §4），且
  LandFoundationTestMain 有"无批量枚举 API"验收。因此**不做地块枚举视图**，
  改为**共和国土地公共概况**：新增单个有界只读方法
  `LandSummary publicSummary()`（返回不可变聚合记录：parcelCount、totalArea、
  各 ZoneType 计数/面积分布、storeRevision）——非列表、非枚举，符合契约；
- 玩家个人用地权益视图（"我的地块"）需新增按主体限定的有界查询，留待后续
  阶段单独设计（见 docs/architecture/fr-land-002-a-my-usage-rights.md 设计候选）；
- 协议 v5、账本 9 条（ID 0-8）。

## 6. Review Gate

设计候选；B-3a 复用既有公开投影、无模块边界改动，可按 Stage A/B-2 同模式
实施；B-3b 的土地投影为模块 API 新增，实施前需独立审查。
