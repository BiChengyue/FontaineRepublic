# FR-INST-002 Java Implementation Task

> **Status:** Prepared — Authorized by FR-PHASE2-HUMAN-APPROVAL-01 + "继续工作"
> **Task Type:** Feature Module Implementation（共享机构访问边界）
> **Design Input:** FR-INST-002-A（候选）、FR-INST-001-A/B（已批准）
> **Dependency:** FR-LAND-001（空间数据）、FR-CORE-002、FR-AUD-001、FR-CIT-001、
> FR-CMD-001-A、FR-DATA-001
> **Authority Boundary:** 本文件细化实现范围；不改变已批准设计契约。

---

## 1. Goal

实现共享机构访问边界：

- 设施/终端目录（`institution-access` 命名空间；消费 FR-LAND 地块/分区）；
- 现场上下文（issueOnSiteContext / validateAtMutation / invalidateOnLeave）；
- 能力分类（REMOTE_* / ONSITE_* / EMERGENCY_RECOVERY，FR-INST-001-A §4）；
- 三类工作流默认参数（public 6 块/2 分钟/单次；official 10/60 分钟会话；
  high-risk 安全终端 6 块/30 秒/单次；FR-INST-001-B §3）；
- 出席检测（仅对有活动上下文玩家 1 秒有界轮询 + 生命周期事件）；
- 设施/终端注册与管理命令（`/fr admin institution ...`，OP 门槛仅为早期闸门）；
- 权威变更走 FR-CORE-002；审计走 FR-AUD；无硬编码坐标。

## 2. 明确不实现

- 机构业务权限（议会/政府/法院/央行模块职责）；紧急动作（FR-EMG）；
- GUI/包；建筑外观；政治/货币政策；新依赖。

## 3. 具体契约

### 3.1 模型与存储

```text
institution-access
├── StoreVersion / StoreRevision
├── Facilities: <facilityId> -> Facility（institutionType/parcelId/state/revision）
└── Terminals: <terminalId> -> Terminal（facilityId/institutionType/position/
    capabilitySet/state/integrity/revision）
```

- Facility 必须引用有效 FR-LAND parcel；终端必须位于其设施注册区域内；
- 状态机：ACTIVE / SUSPENDED / RELOCATING / DISABLED；
- 严格编解码 + 失败关闭；单写入者；COMMITTED 门控发布。

### 3.2 现场上下文

- 绑定 player/institution/facility/terminal/capability/issue-time/expiry/
  facility+terminal revision；
- validateAtMutation 在最终变异边界必查（不可禁用）；离开/维度/登出/死亡即失效；
- 返回后不恢复旧上下文；重新交互才可新发。

### 3.3 命令

- `/fr admin institution facility register|suspend|activate|relocate|disable`
- `/fr admin institution terminal register|suspend|disable`
- 注册需要有效 parcel 引用与能力集；读命令受限反馈。

## 4. 测试计划

`src/test/java/com/fontainerepublic/server/institutionaccess/InstitutionAccessFoundationTestMain.java`

- 设施/终端注册（parcel 校验/区域校验/状态机）；
- 上下文签发/校验/过期/能力不匹配；离开/返回失效；单次使用；
- 三类工作流默认参数；出席轮询边界（仅活动上下文玩家）；
- 注入失败无发布；重启上下文清空、目录恢复；严格编解码；
- 无硬编码坐标守卫；命令树存在且无越权路径；
- `institutionAccessFoundationTest` 接入 check；`gradlew build` 回归。

## 5. 验收

对应 FR-INST-002-A §7 矩阵；全部须有测试证据。

## 6. 约束

- 消费 FR-LAND 只读服务（不复制空间数据）；不改已实现模块；不新增依赖；
- 服务端权威；SavedData/NBT only；最终复检不可禁用；
- 提交后审查（Codex）-> ROUTE TO HUMAN / RETEST / BLOCK。
