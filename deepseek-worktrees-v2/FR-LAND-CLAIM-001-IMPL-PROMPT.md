# FR-LAND-CLAIM-001-IMPL 派发提示词（deepseek v4 flash 实现子进程）

## 1. 身份与目标

- 你是 FontaineRepublic（MC 1.20.1 / Forge 47.4.18 / Java 17 / Gradle）实现子进程。
- 任务 ID：FR-LAND-CLAIM-001-IMPL；目标：传讯水镜购地子系统——手持传讯水镜右键
  方块 → 土地视图 → 若为无主土地可申请创建共和国地块并取得使用权。
- 工作目录：`D:\MC\FontaineRepublic\deepseek-worktrees-v2\fr-land-claim-001-impl`
  （从 develop 创建，分支 `codex/fr-land-claim-001-impl`；派发时 develop 已含
  交易/邮件，账本以派发时实际为准）。

## 2. 必读（按顺序）

- 运行手册 `docs/governance/continuous_development_ops.md`
- `CLAUDE.md`、`docs/ai_recovery/current_status.md`
- `docs/architecture/fr-land-claim-001-a-communicator-land-claim.md`（设计 v1.0）
- `docs/development/FR-LAND-001-java-implementation-task.md`（土地契约背景）
- `docs/architecture/fr-land-002-a-my-usage-rights.md`（“无批量枚举”契约背景）
- 代码：`common/network/`（协议/账本/速率策略）、`server/land/`（LandService /
  CreateParcelRequest / ParcelRegion / OwnerReference / DefaultLandService）、
  `server/trade/`（若已并入：服务/门禁/模块/运行时定位器惯例）、`client/`
  （CommunicatorInteraction / LandScreen / ClientNetworkExecutor /
  ClientViewProjection）。

## 3. 执行约束（违反即失败）

- 只在工作树内修改；不推送；不修改设计文档与台账。
- 土地状态**服务端权威**、主线程串行、FR-CORE-002 持久化门；有界、**不引入
  批量枚举**。
- 申领者须**手持传讯水镜**（服务端校验）；点击位置已被地块覆盖 → 拒绝
  （fail closed）。
- **无申领费用**（v1）；地块所有权永久为共和国，申领者仅取得使用权。
- 不得调用 `createParcel` 后再调用 `grantUsage` 并声称原子；不得以补偿删除作为
  主方案。必须由 LandRepository 构造一个包含初始使用权的完整快照并只提交一次。
- 手机 C2S 与无客户端命令都必须复核当前维度、目标加载状态、服务端方块 reach、
  完整申领区域重叠；OP 不绕过业务校验。
- 协议 v8、账本**追加到当前末尾**（邮件 v7 后为 ID 23-26：LandInspectPacket /
  LandInspectResultPacket / LandClaimPacket / LandClaimResultPacket）；
  C2S 全部带速率策略；载荷全部有界；既有测试同步。
- 不新增依赖；开始时报分支 HEAD 与工作区状态。

## 4. 实现清单

1. **LandService 新增有界精确查询与原子创建**：
   `Optional<LandParcel> parcelAt(String dimension, int x, int y, int z)`；
   仓库层已有 `findParcelAt`，公开为精确查询即可；维度为规范资源键；
   无地块 → `Optional.empty()`；不新增任何枚举/列表接口。
   - 新增单一权威操作（命名可按项目风格微调，但语义不得拆分）：
     `UsageReceipt createParcelWithUsage(UUID actor, CreateParcelRequest request,
     OwnerReference holder, long durationMillis)`；`DefaultLandService` 先完成 actor / holder
     解析和 duration 校验，再调用 Repository 单快照方法。
   - `LandRepository.createParcelWithUsage(...)` 在一个候选
     `LandStoreSnapshot` 中分配 id、构建共和国 parcel 与初始 right，并只调用一次
     `commitAndPublish`。成功时 parcelRevision=1、rightRevision=1、StoreRevision +1；
     失败全部不变，可安全重试。检查容量、ID、完整区域同维度相交；增加稳定 overlap
     错误码。现有 `createParcel` / `grantUsage` 兼容保留。

2. **server/landclaim/ 完整实现**：
   - `api/LandClaimService`：提供 `inspect` 与
     `ClaimReceipt claim(UUID actor, String dimension, int x, int y, int z)`；
   - `api/ClaimReceipt`（parcelId/region/zoneType/access/atMillis，严格校验）；
   - `DefaultLandClaimService`：手机与命令共用门禁（actor 解析 active subject、手持
     水镜、请求维度等于当前玩家维度、目标已加载且处于服务端 block reach、坐标/
     维度规范、parcelAt 已存在 → `CLAIM_ALREADY_OWNED`、完整区域相交 →
     `CLAIM_OVERLAP`）→ 单次调用 `createParcelWithUsage(..., 0)`；
   - `LandClaimModule`（仅依赖 land + network；注册消息/运行时绑定）；
   - `LandClaimRuntime`（静态定位器，参照 TradeRuntime 惯例）；
   - `ServerLandClaimPlayerAccess`（手持传讯水镜判定 + 聊天/提示反馈）。
   - 默认地块：`halfWidth=1`（3×3 水平面）、`height=8`、zoneType=RESIDENTIAL、
     access=PRIVATE；通过 `ConfigManager` 新增可配
     `land.claim.halfWidth`（[0,16]）、`land.claim.height`（[1,64]）、
     `land.claim.zoneType`（枚举名，非法回退默认）——ConfigManager 补
   `push("land")` 内追加（或新增 `push("land.claim")` 子段，与现有 land 段
   风格一致），并提供 getter。

3. **无客户端对等命令**：
   - 新增 `LandCommand` 贡献 `/fr land inspect <x> <y> <z>` 与
     `/fr land claim <x> <y> <z>`；只接受玩家源，维度从玩家当前世界取得；
   - `CommandRuntimeResolver` 执行时解析 ACTIVE `LandClaimService`，不缓存；
   - 命令调用与 C2S 完全相同的 `LandClaimService`，手持水镜、reach、加载、身份、
     重叠与 durable gate 均不可绕过；反馈走聊天，不依赖客户端包。

4. **网络**：`LandInspectPacket`（C2S，dimension≤64/x/y/z）、
   `LandClaimPacket`（C2S，同载荷）、`LandInspectResultPacket`（S2C：
   有主 → parcel 概要 id/region/zone/access；无主 → claimable=true）、
   `LandClaimResultPacket`（S2C：成功 receipt 或稳定错误码≤64）；
   注册进 `NetworkProductionMessageTable`（追加账本末尾）、
   `NetworkProtocol` 升 v8、C2S 速率策略、handler 走服务端运行时定位器。

5. **客户端**：
   - `ClientNetworkExecutor.acceptLandInspectResult` /
     `acceptLandClaimResult`（沿用 unsafeRunWhenOn 客户端侧执行惯例）；
   - `client/landclaim/ClientLandClaimSender`（C2S 发送）；
   - 新增 `LandLocationScreen`（推荐）承载“地块位置视图”，保留现有 `LandScreen`
     作为共和国土地总览；位置视图打开即发送 `LandInspectPacket`，收到结果后渲染
     有主概要或“无主 + 申请创建地块”按钮；
     点击按钮发送 `LandClaimPacket`，收到结果后展示成功/失败；
   - `CommunicatorInteraction.onRightClickBlock`：打开带位置的 LandScreen
     （携带 `event.getPos()` 与当前维度），不再只开纯总览；
   - 侧隔离：专用服务器不加载 `client/` 类。

6. **测试**：
   - 新建 `LandClaimFoundationTestMain`：单点查询命中/未命中、已有地块拒绝、完整
     区域相交拒绝、成功路径（单次 store commit、parcel/right/store revision 各
     +1、使用权无期限）、注入保存失败后零内存/持久化/revision 副作用且可重试、
     门禁（未登记/未手持/非法坐标/非法维度/错误当前维度/未加载/超 reach）fail
     closed、codec 往返与边界、速率策略注册断言；
   - build.gradle 注册 `landClaimFoundationTest`；
   - 既有测试同步：NetworkFoundationTestMain（v8、账本末尾 count 递增）、
     ClientPresentationFoundationTestMain（v8、handler 引用 +2）、
     ClientStageB3b（展示消息 count +2）等；
   - `gradlew build` 全绿；侧隔离源码扫描；**git 提交**（信息含
     FR-LAND-CLAIM-001）。
   - 命令测试覆盖两条无客户端路径、非玩家源拒绝、OP 不绕过及与 C2S 共用 service。

## 5. 交付格式

```text
任务：FR-LAND-CLAIM-001-IMPL
分支：{branch}
提交：{commit}
修改文件：{列表}
测试命令与结果：{命令 + 摘要}
未解决问题：{列表}
是否有越界文件：是/否（说明）
```

## 6. 提交前自检

- [ ] 有界单点查询（无枚举、无他人数据泄露）；
- [ ] 已覆盖地块拒绝（fail closed）；
- [ ] 手持传讯水镜门禁（服务端校验）；
- [ ] createParcelWithUsage 单快照/单 durable commit、无期限使用权；
- [ ] 完整区域相交拒绝；错误维度/未加载/超 reach 拒绝；
- [ ] `/fr land inspect` / `/fr land claim` 无客户端对等且无 OP 绕过；
- [ ] 无申领费用；所有权仍归共和国；
- [ ] 协议 v8、账本追加到当前末尾、C2S 速率策略；
- [ ] 测试同步、`landClaimFoundationTest` 注册并通过；
- [ ] `gradlew build` 通过；[ ] 已提交。
