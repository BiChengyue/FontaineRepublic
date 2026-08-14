# Task Card — FR-ITEM-001-IMPL（便携通讯器物品 + UI 门禁 + 右键转账）

> Status: Authorized（Human 需求 2026-08-14）
> Task Type: 新物品 + 客户端交互
> Design Reference: docs/architecture/fr-item-001-a-communicator.md

## Scope

**Allowed:** `DeferredRegister<Item>` 注册 `fontainerepublic:communicator`
（显示名"便携通讯器"，临时 vanilla 纹理）；物品模型/本地化；
客户端 `/frclient` UI 门禁（手持判定）；右键玩家 → 转账表单（预填目标）；
右键方块 → 土地视图；门禁/交互纯函数测试。

**Forbidden:** 购地/地块申请（后续 FR-LAND-CLAIM-001）；权威 C2S 包；
客户端权威状态；新依赖（Forge 自带除外）。

## Acceptance

- `/give @s fontainerepublic:communicator` 可获得物品；
- 未手持时 `/frclient` 提示且不打开；手持时正常打开；
- 手持右键玩家打开转账表单且目标预填；右键方块打开土地视图；
- `clientItemFoundationTest`（或并入既有测试）+ `gradlew build` 全绿；
- 专用服务器不加载 client/ 类。
