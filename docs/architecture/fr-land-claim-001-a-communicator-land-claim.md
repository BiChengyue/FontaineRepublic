# FR-LAND-CLAIM-001-A — 传讯水镜购地（共和国地块申领）设计候选 v1.0

> **Task ID:** FR-LAND-CLAIM-001-A
> **Status:** Design Aligned — Human 已授权持续推进；实施须通过预检与独立审查
> **Purpose:** 手持传讯水镜右键无主方块 → 在土地视图中申请创建共和国地块并取得使用权
> **Dependency:** FR-LAND-001（已实现）、FR-ITEM-001（已实现）、FR-CLIENT-001（已实现）

## 1. 需求（Human 2026-08-14）

- **购地 = 创建共和国地块并取得使用权**（Human 明确，覆盖“购买/转账购地”类歧义）。
- 手持传讯水镜右键方块 → 打开土地视图；若该处**无主**（无地块覆盖）→ 可在视图中
  申请在该处创建共和国地块并取得使用权。
- 有主地块 → 视图只读展示该地块概要，不提供申领入口。

## 2. 权威与安全原则

- 地块创建与初始使用权授予由 **FR-LAND-001 土地模块权威管理**。现有
  `createParcel` + `grantUsage` 是两个独立 durable commit，不能组成原子申领；
  本任务必须新增 Land-owned `createParcelWithUsage`，在一个完整候选快照中同时
  创建共和国地块和初始使用权，只调用一次 FR-CORE-002 持久化门，提交成功后才
  发布内存状态。
- 申领动作**服务端权威**：客户端仅提交意向（C2S 有界载荷 + 速率策略）；
  位置与维度由服务端从消息载荷校验，绝不信任客户端口头声明之外的内容。
- 门禁（全部服务端复核，fail closed）：
  - 申领者须为已登记玩家（PlayerData → FR-ID active subject）；
  - 申领者须**手持传讯水镜**（主/副手，`CommunicatorGate` 语义在服务端等价实现）；
  - 点击位置已被地块覆盖，或待创建的完整区域与任一同维度地块相交 → 拒绝
    （`CLAIM_ALREADY_OWNED` / `CLAIM_OVERLAP`）；
  - 请求维度必须等于玩家当前维度，目标方块必须已加载且位于服务端计算的方块
    交互距离内；客户端不能借 C2S 或命令申领远处、其他维度或未加载位置；
  - 地块数量/容量上限由土地模块既有约束承接。
- **无申领费用**（Human 定义“购地=创建地块+取得使用权”，未要求付费）；预留可配置
  费用旋钮 `land.claim.fee`（默认 0）供后续财政政策，v1 不实现扣费。
- 地块所有权永久为共和国（FR-LAND-001 契约），申领者仅取得**使用权**。

## 3. 数据与契约

### 3.1 土地模块新增：有界精确单点查询（非枚举）

```text
Optional<LandParcel> parcelAt(String dimension, int x, int y, int z);
```

- 仓库层已有 `findParcelAt(dimension, x, y, z)`；仅在 `LandService` 公开为
  **精确单点查询**，不引入任何批量枚举、不泄露他人地块明细以外的信息。
- 边界：dimension 为规范资源键；坐标为 int 方块坐标；无地块 → `Optional.empty()`。

### 3.2 申领契约（独立轻模块 `server/landclaim/`）

```text
ClaimReceipt claim(UUID actor, String dimension, int x, int y, int z);
```

- 解析 actor → active subject（未登记/无效 → fail closed）；
- 通过服务端在线玩家事实复核：当前维度、目标已加载、方块交互距离、主/副手水镜；
- `parcelAt` 已有地块 → 拒绝（`CLAIM_ALREADY_OWNED`，无副作用）；构造完整区域后
  再检查同维度区域相交，存在任何相交 → `CLAIM_OVERLAP`；
- 以点击方块为中心构建默认地块：
  - 水平半宽 `halfWidth`（可配 `land.claim.halfWidth`，[0,16]，默认 1 →
    3×3 水平面）；
  - 垂直范围 `[y, y + height - 1]`（可配 `land.claim.height`，[1,64]，默认 8），
    钳制在世界高度界内；
  - zoneType 默认 `RESIDENTIAL`（可配 `land.claim.zoneType` 枚举）；
  - access 默认 `PRIVATE`（需有效使用权——与“购地取得使用权”语义一致，
    非 PUBLIC 开放）。
- 调用土地模块新增的单一权威操作：
  `createParcelWithUsage(actor, request, OwnerReference.forPlayer(actor), 0)`；
  Repository 在同一个 `LandStoreSnapshot` 中构造 parcel 与永久 usage right，并仅
  durable commit 一次。成功结果为 `parcelRevision=1`、`rightRevision=1`、
  `storeRevision +1`；验证、编码、容量或 durable commit 任一失败时，地块、使用权
  与全部 revision 均不变化，不允许“先创建后补偿删除”；
- 返回 `ClaimReceipt(parcelId, region, zoneType, access, atMillis)`。

### 3.3 模块职责

- `server/landclaim/`：`LandClaimService`（`inspect` + `claim` 契约）、
  `DefaultLandClaimService`（统一执行手机与命令的服务端门禁）、`LandClaimModule`
  （仅依赖 land + network；注册运行时服务，登入/登出无状态）、
  `ServerLandClaimPlayerAccess`（在线玩家、当前维度、加载状态、服务端 reach、手持
  水镜与聊天反馈）。PlayerData/FR-ID 验证由 LandService 继续权威承接，不建立
  landclaim → registry 的重复依赖。
- 不修改 FR-LAND-001 的“无批量枚举”契约；不新增依赖。

### 3.4 无客户端对等入口

- 新增 `/fr land inspect <x> <y> <z>` 与 `/fr land claim <x> <y> <z>`；维度始终
  从命令执行玩家当前世界取得，不接受调用者提供维度。
- 两条命令与 C2S handler 必须调用同一个 `LandClaimService`，执行相同的 active
  subject、手持水镜、加载状态、交互距离、区域重叠和 durable commit 校验；命令
  不是后门，也不提供 OP 绕过。
- 非玩家命令源、远距坐标、其他维度或未加载方块均 fail closed。命令仅以聊天
  输出同等结果，不需要客户端自定义包。

## 4. 网络面（协议 v8，账本追加在邮件 v7 之后）

| ID | 方向 | 消息 | 载荷（有界） |
|---:|---|---|---|
| 23 | C2S | `LandInspectPacket` | dimension(≤64)、x、y、z（int） |
| 24 | S2C | `LandInspectResultPacket` | 有主：parcel 概要（id/region/zone/access）；无主：claimable=true |
| 25 | C2S | `LandClaimPacket` | dimension(≤64)、x、y、z（int） |
| 26 | S2C | `LandClaimResultPacket` | 成功：receipt；失败：稳定错误码（≤64） |

> 注：ID 以派发时 develop 实际账本末尾为准（邮件 v7 后为 23-26），派发提示词会
> 写明“追加到当前账本末尾”，不硬编码旧编号。

- C2S 全部带速率策略；载荷全部有界；方向/连接活性由既有网络基础框架强制。
- 客户端增加“地块位置视图”（可使用独立 `LandLocationScreen`，保留现有共和国
  土地总览 `LandScreen`）：打开时发送 `LandInspectPacket`，
  收到结果后渲染概要或“无主 + 申请创建地块”按钮；点击按钮发送
  `LandClaimPacket`，收到结果后展示成功/失败。

## 5. 测试

- 仓库/服务：单点查询有界、点击处已有地块拒绝、完整区域相交拒绝、申领成功路径
  （单快照/单 durable commit，parcel/right/store revision 各 +1，使用权无期限）、
  保存失败后零副作用与可安全重试、门禁（未登记/未手持/错误维度/未加载/超出
  reach/越界坐标）fail closed；
- codec 往返与载荷边界（dimension/坐标/错误码）；
- 速率策略注册；侧隔离（专用服务器不加载 client/ 类）；
- `/fr land inspect` / `/fr land claim` 无客户端对等，且命令与 C2S 走同一 service；
- `landClaimFoundationTest` + `gradlew build` 全绿；
- 真机：右键无主方块 → 申请 → 获得使用权并可建造；右键有主方块 → 只读概要。

## 6. Review Gate

实施后独立审查（Codex）→ ROUTE TO HUMAN / RETEST / BLOCK；真机购地核验随 Human。
