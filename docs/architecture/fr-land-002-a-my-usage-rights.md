# FR-LAND-002-A — 个人用地权益有界查询（设计候选 v1.2）

> **Task ID:** FR-LAND-002-A
> **Status:** Human 产品方向已确认；待实现门与持久化基线恢复；不授权立即实现
> **Purpose:** 为安装客户端模组的玩家提供传讯水镜“我的地块”有界只读查询
> **Dependencies:** FR-LAND-001、FR-LAND-CLAIM-001、FR-ITEM-001、
> FR-CLIENT-001、FR-NET-001
> **Rebased Baseline:** `develop@b213b99`（含 FR-LAND-CLAIM-001，协议 v8，
> 消息 ID 0..26）

---

## 1. 目标与边界

玩家应能查看自己当前有效的共和国土地使用权，包括地块位置、规划类型和权益
期限。该功能是个人信息查询，不改变土地所有权、使用权或规划状态。

本设计只允许：

- 已认证玩家查询**本人**当前有效的使用权；
- 安装客户端模组的玩家通过传讯水镜按需查看；
- 服务端使用有界分页、速率限制和严格网络载荷校验。

本设计禁止：

- 枚举全服地块或他人权益；
- 客户端提交目标 UUID、主体号、名字或 OwnerReference；
- 通过该查询修改、续期、撤销、转让或出售权益；
- 把客户端缓存当作土地权威；
- 绕过传讯水镜持有门、身份门或 LandService；
- 通过 `/fr land mine` 或其他玩家命令取得该便利查询；
- 扩展企业/机构土地持有类型。

---

## 2. 当前事实与身份模型

### 2.1 土地权威

- `LandRepository` 是 `land` SavedData 命名空间唯一写入者；
- `LandParcel.usageRights` 的权威键为 `OwnerReference`；
- Alpha 当前只接受 `OwnerReferenceKind.PLAYER_UUID`；
- 玩家查询边界由服务端登录身份得到 UUID，再调用
  `OwnerReference.forPlayer(playerId)`；
- `SubjectId` 不存入土地权益，也不得与 `OwnerReference` 混用；
- 本功能不需要数据迁移或 Land NBT schema 升级。

主体登记册仍用于确认玩家具有可解析的有效主体，但它不替换土地存储中的
`PLAYER_UUID` 持有人键。

### 2.2 既有 HolderIndex

Land NBT 已包含由权威地块派生的 `HolderIndex`，Codec 在加载时严格校验它与
`Parcels` 一致，但当前 Repository 不保留可查询的内存镜像。

实施时 Repository 应在加载和每次成功发布快照后，从权威 `parcels` 构建：

```text
OwnerReference -> sorted canonical ParcelId keys
```

该内存索引：

- 是可重建的派生数据，不是第二权威来源；
- 只在成功 durable commit 后随新快照一起发布；
- 不改变当前 NBT 格式；
- 不允许通过公共 API 读取完整 Map；
- 查询成本受单个持有人地块上限和页大小约束，不扫描全服地块。

---

## 3. 服务契约

### 3.1 投影

```text
MyUsageRightProjection {
  parcelId
  dimension
  region(minX, minY, minZ, maxX, maxY, maxZ)
  zoneType
  usageType
  grantedAt
  expiresAt
  rightRevision
  parcelRevision
}

MyUsageRightsPage {
  storeRevision
  entries (bounded)
  nextAfterParcelId (optional)
  hasMore
  generatedAt
}
```

投影不包含其他持有人、审计记录、违规报告、内部索引或权限缓存。

### 3.2 API

推荐公共服务方法：

```text
MyUsageRightsPage myUsageRights(
    UUID authenticatedPlayerId,
    Optional<ParcelId> afterParcelId,
    long expectedStoreRevision,
    int limit);
```

规则：

1. `authenticatedPlayerId` 只能来自服务端连接或玩家命令来源；
2. API 没有任意目标参数，因此不能查询其他玩家；
3. 服务端转换为 `OwnerReference.forPlayer(authenticatedPlayerId)` 并执行既有
   玩家记录/有效主体检查；失败时 fail closed；
4. 只返回 `UsageRight.validAt(serverNow)` 为真的权益；
5. 默认 `limit=12`，硬上限 `32`，越界输入拒绝而不是静默放大；
6. 返回 `MyUsageRightsPage` 记录，而不是裸 `List`、`Map`、数组或 `Stream`；
7. LandService 与 Repository 不暴露全量集合，也不提供 holder 参数的任意查询。

该方法是“认证参与方限定的有界页面”，不是公共地块枚举。现有
`LandFoundationTestMain.testNoEnumerationApi()` 应继续拒绝集合返回值；新增页面
记录作为唯一明确允许的本人投影，并增加反射测试，确保没有目标 holder 参数。

---

## 4. 排序、游标与一致性

### 4.1 稳定排序

页面按 `ParcelId.canonicalKey()` 升序。`afterParcelId` 是排他游标：下一页只返回
canonical key 严格大于游标的地块。

Repository 沿派生 HolderIndex 顺序检查地块，`nextAfterParcelId` 表示本页**最后
检查**的地块，而不只是最后返回的有效权益。因此即使索引中存在已过期权益，
过滤它们也不会改变底层游标顺序、造成后续有效权益漏项；每次查询最多检查该
持有人受硬上限约束的索引，不回退为全服地块扫描。

禁止使用 `parcelRevision` 作为游标。它是每地块独立修订号，多个地块可同时为
Revision 1，使用它分页会漏项或重复。

### 4.2 StoreRevision 一致性

- 第一页请求使用 `expectedStoreRevision=0`；
- 服务端返回查询时的 `storeRevision`；
- 后续页必须回传该 revision；
- 如果当前 StoreRevision 与预期不同，服务端返回 `RESET_REQUIRED`，不返回条目；
- 客户端或命令调用方从第一页重新查询；
- 查询不会锁住 Land 存储，也不会阻止正常变更。

这样可避免分页期间授权、撤销或新申领导致跨快照拼接。

---

## 5. 传讯水镜客户端流程

### 5.1 按需查询

本阶段采用**打开页面时按需请求**，不在登录时推送全部个人地块：

1. 玩家手持传讯水镜打开 LandScreen；
2. 选择“我的地块”；
3. 客户端发送第一页请求；
4. 翻页时发送带游标和 StoreRevision 的下一页请求；
5. 关闭界面或断线时清空临时分页状态。

理由：个人页面并非每次登录都需要；按需请求减少无用载荷，并能自然处理分页与
刷新。客户端缓存仅用于展示，不是权威。

### 5.2 服务端门控

每次请求必须重新验证：

- 发送者存在且是 `ServerPlayer`；
- 玩家当前手持注册的传讯水镜；
- 玩家记录与有效主体可解析；
- Land runtime 可用；
- 请求速率、游标、页大小和载荷合法。

本功能是远程个人信息服务，不要求进入国家机构建筑；它不能办理土地审批、转让、
规划或司法流程。

### 5.3 响应关联

请求与响应均携带正数 `requestId`。客户端只接受当前页面请求的相同 requestId，
旧响应不得覆盖新页面。响应同时携带 `storeRevision` 和 `generatedAt`；低于当前
缓存 revision 的响应丢弃。同 revision 下，旧 requestId/旧 generatedAt 不得回退
界面。

---

## 6. 命令与无客户端边界

Human 已确认本功能是传讯水镜客户端提供的便利型个人信息视图，当前阶段**不提供**
`/fr land mine`，也不注册其他等价玩家查询命令。命令树、控制台、RCON 与命令方块
均不得通过本任务新增入口读取个人用地权益。

这不改变服务器权威原则：客户端仍只能向服务端请求本人页面，所有身份、持有门、
分页、限流和 LandService 校验均在服务端完成。它也不把土地申领、审批、续期、撤销、
转让或争议处理变成客户端专属能力；这些国家业务不属于本只读便利视图，并继续由各自
批准的标准交互或现场工作流承载。

未安装客户端模组的玩家不会获得“我的地块”聚合查询，但其既有土地权利与办理国家
业务的资格不受影响。未来若要增加无客户端查询，必须单独获得 Human 产品决策并重新
审查隐私、输出边界与命令账本，不能从本设计推定授权。

---

## 7. 网络账本与载荷

当前生产协议为 v8，消息账本 ID 0..26。FR-LAND-002 新增两条消息会改变线协议，
因此必须升级到 **v9**：

| ID | 消息 | 方向 | 说明 |
|---:|---|---|---|
| 27 | `MyLandRightsRequestPacket` | PLAY_TO_SERVER | requestId、afterParcelId、expectedStoreRevision、limit |
| 28 | `MyLandRightsPagePacket` | PLAY_TO_CLIENT | status、requestId、storeRevision、≤32 entries、next cursor、hasMore、generatedAt |

生产消息总数：29（ID 0..28）。账本保持只追加，不重排、不复用旧 ID。

严格边界：

- `dimension` 最多 64 字符，并按 canonical ResourceLocation 校验；
- `ParcelId` 必须是 canonical UUID；
- 条目最多 32；
- 所有计数先验证再分配；
- 总编码载荷不得超过 `NetworkPayloadLimits.MAX_PAYLOAD_BYTES`（32 KiB）；
- 非法枚举、负数时间、非法区域、倒置坐标或多余数据全部拒绝；
- C2S 使用独立 `MY_LAND_QUERY_POLICY`，建议与 land inspect 同级：短时突发 2、
  持续约 10 次/秒；限流只防滥用，不构成业务授权。

响应状态闭合为：`OK`、`RESET_REQUIRED`、`INVALID_REQUEST`、`UNAVAILABLE`。
非 `OK` 响应不得携带权益条目。

---

## 8. 失败与隐私语义

- 不存在权益：返回空的 `OK` 页面；
- Land/身份服务不可用：`UNAVAILABLE`，无条目；
- 非法游标或页大小：`INVALID_REQUEST`，无条目；
- revision 漂移：`RESET_REQUIRED`，无条目；
- 玩家未手持传讯水镜：统一拒绝，不泄露是否存在权益；
- 任何失败不修改 Land 数据、不递增 revision、不写审计交易；
- 日志只记录稳定错误码与玩家 UUID 的安全投影，不输出完整私人地块列表。

---

## 9. 验证要求

实施前后的自动化验证至少包括：

1. 同一玩家多个地块均为 parcelRevision 1 时分页不漏项、不重复；
2. 排序严格按 canonical ParcelId，游标为排他语义；
3. 下一页前 StoreRevision 改变时返回 RESET_REQUIRED；
4. 只返回登录玩家的 PLAYER_UUID 权益，客户端无法指定目标；
5. 已过期权益不返回，无期限权益返回；
6. HolderIndex 在加载、申领、授权、续期、撤销后与 Parcels 一致；
7. durable commit 失败时索引、地块与 revision 均不变化；
8. 页大小 0、负数、33、非法 UUID 游标和畸形包被拒绝；
9. 编码载荷始终 ≤32 KiB，恶意计数不会提前分配大集合；
10. requestId/revision 关联阻止旧响应覆盖新页面；
11. 命令树不存在 `/fr land mine` 或其他由本任务新增的等价查询入口；
12. 未手持传讯水镜时 C2S 被拒绝；
13. NetworkProtocol=v9、消息数 29、ID 27/28 方向和限流断言通过；
14. 现有“无公共批量地块 API”反射验收继续通过；
15. Dedicated Server 启动无客户端类加载错误。

---

## 10. 非目标

本任务不实现：

- 过期/撤销权益历史；
- 地图、领地边框或导航标记；
- 企业、机构或职位持有土地；
- 查看他人地块；
- 管理员批量导出；
- 续期、撤销、转让、租赁、买卖、抵押或税收；
- 土地审批、规划、法院或政府业务；
- 新的 Land NBT schema；
- 客户端权威缓存。

---

## 11. 实施分层建议

若 Human 批准，建议拆为一个实现任务、一次普通独立审查：

1. Land 内存 HolderIndex + `MyUsageRightsPage` 有界服务；
2. 协议 v9、ID 27/28、严格 Codec 与限流；
3. 客户端 LandScreen“我的地块”页签、请求关联与分页；
4. 命令树负向断言，确保未注册 `/fr land mine` 或等价入口；
5. Foundation tests、全量 build、Dedicated Server 冒烟；
6. Human 真机查看与翻页验收。

实现可交给 DSH；Coordinator 只复核隐私边界、分页一致性、网络版本与服务端门控。

---

## 12. Human 裁决

Human 已确认：

1. “我的地块”作为下一项土地便利功能进入后续实现；
2. 仅安装客户端模组的玩家通过手机端查看，命令端不提供；
3. 当前页面只展示有效权益，不在本阶段加入过期/撤销历史。

该裁决不越过当前 Core 持久化重启缺陷的实施阻塞。按需请求、PLAYER_UUID-only、
协议 v9 和稳定游标属于现有架构下的技术选择，不建议再开放多套实现分支。

---

## 13. Review Gate

本文件是设计候选，不构成 Human 批准，不授权修改源码、网络账本或命令树。
应先由独立 Reviewer 核对：隐私边界、HolderIndex 一致性、游标与 StoreRevision、
协议 v9/ID 27–28、命令入口禁令和无公共枚举保证；通过后再交 Human 裁决。
