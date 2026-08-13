# FR-LAND-002-A — 个人用地权益有界查询（设计候选 v1.0）

> **Task ID:** FR-LAND-002-A
> **Status:** Design Candidate（待独立审查与 Human 批准；不实施）
> **Purpose:** 为客户端"我的地块"视图提供按主体限定的有界只读查询
> **Dependency:** FR-LAND-001（已实现）、FR-CLIENT-001（客户端展示面）

## 1. 背景

FR-LAND-001-A §4 明示"无批量地块列表"；B-3b 采用单值公共概况聚合（见
fr-client-001-b3 §5.1）。个人用地权益视图需要**按持有者限定的有界投影**——
只返回调用者本人持有的用地权益，不属于批量枚举。

## 2. 数据模型核对

- `LandParcel.usageRights: Map<OwnerReference, UsageRight>`（每地块至多一项）；
- `UsageRight(holder, usageType, grantedAt, expiresAt, rightRevision)`，
  `expiresAt=0` 表示无期限，`validAt(timestamp)` 判定有效性；
- 服务端按登录玩家的 SubjectId/OwnerReference 解析身份，绝不由客户端提供。

## 3. 契约

```text
List<MyUsageRightProjection> myUsageRights(
        OwnerReference holder,
        long afterParcelRevision,
        int limit);
```

- `holder` 必须是调用者本人（服务端从连接/命令来源解析，不信任客户端输入）；
- 有界：`limit ≤ 64`，仅返回 `validAt(now)` 的存续权益；
- 投影：parcelId、zoneType、region 摘要、usageType、grantedAt、expiresAt、
  rightRevision（纯只读展示字段）；
- 确定性排序（按 parcelRevision 升序、分页 afterParcelRevision 游标）；
- **不枚举他人权益**：任何越界/他人查询请求 fail closed；
- 与 economy `participantTransactions` 同理，属参与方限定的有界投影，
  在 LandFoundationTestMain 无枚举验收中按类豁免（需同步测试契约与豁免注释）。

## 4. 客户端接入（后续阶段）

- 消息账本追加 `MyLandRightsPacket(9)`（有界权益列表），协议 v6（账本 10 条）；
- `LandScreen` 增"我的地块"页签；登录或打开时服务端推送本人权益摘要；
- 发送经 `NetworkSendService.trySendToPlayer`（presence 过滤，best-effort）。

## 5. Review Gate

设计候选。实施前需：独立审查（查询边界/隐私/游标/无枚举豁免）+ Human 批准；
随客户端早间真机反馈后评估是否进入实现。
