# FR-ITEM-001-A — 便携通讯器（客户端可视化入口物品）设计 v1.0

> **Task ID:** FR-ITEM-001-A
> **Status:** Design Candidate — 实施中（Human 需求 2026-08-14）
> **Purpose:** 为客户端可视化 UI 提供世界观内入口物品，并支持右键快捷交互

## 1. 需求（Human）

- 新增一个物品"手机/通讯器"（或更符合世界观的东西）；
- **只有安装客户端且手持该物品的玩家**才能使用客户端可视化 UI；
- 手持物品右键玩家 → 便捷转账；右键方块 → 便捷"购地"等。

## 2. 设计决策

### 2.1 物品

- 注册名：`fontainerepublic:communicator`，显示名"便携通讯器"（枫丹风格，
  可改）；临时使用 vanilla `item/clock` 纹理占位，后续可替换自制贴图；
- 物品为普通 `Item`（无方块/实体），可堆叠 1；通过 `/give @s
  fontainerepublic:communicator` 获取（后续可加合成/发放规则）；
- 注册在 common（双方都知道该物品），经 Forge `DeferredRegister` 挂到
  mod 事件总线（FMLCommonSetup 前）。

### 2.2 UI 门禁（客户端）

- 打开 `/frclient` 任意界面时，客户端检查本地玩家主手/副手是否持有
  `communicator`；未持有则聊天提示"需要手持便携通讯器"，不打开界面；
- 该检查是 **UX 门禁，非权威**：所有动作仍经服务端命令校验；恶意客户端
  绕过只影响自身界面，不获得任何权限/状态变更能力。

### 2.3 右键交互（客户端监听）

- 右键玩家（`PlayerInteractEvent.EntityInteract`，CLIENT 侧）：若手持
  communicator，打开转账表单并预填目标玩家名（提交 `/fr money pay
  <目标> <金额>`，服务端解析）；
- 右键方块（`PlayerInteractEvent.RightClickBlock`，CLIENT 侧）：若手持
  communicator，打开土地视图（当前只读概况）；**购地/申请地块**为后续
  任务（FR-LAND-CLAIM-001，需服务端 createParcel+grantUsage 命令）。

## 3. 权威边界

- 物品只是 UI 入口；余额/转账/土地等一切状态仍服务端权威；
- 右键交互只负责打开界面/预填表单，不触发任何服务端副作用；
- 无 FR 客户端/未手持物品不影响任何命令功能（no-client parity 保持）。

## 2.4 获取方式（Human 需求 2026-08-14）

- **不设合成表**；由**水神垄断**：仅服务器管理员（水神/控制台）通过
  `/give @s fontainerepublic:communicator` 发放；
- 显示名"传讯水镜"（Message Water Mirror）；注册名 `communicator` 不变；
- 后续可提供受控发放命令（仅水神/控制台），本设计不新增。

## 4. 测试

- 物品注册 id/名称/堆叠；门禁判定纯函数（手持/未手持）；
- 右键目标捕获（右键玩家 → 目标名；右键方块 → 坐标）；
- `gradlew build` 全绿；专用服务器不加载 client/ 类。

## 5. Review Gate

设计候选；实施后独立审查（Codex）→ ROUTE TO HUMAN / RETEST / BLOCK。
