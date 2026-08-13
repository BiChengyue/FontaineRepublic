# FR-INST-002-B Java Implementation Task

> **Status:** Prepared — Authorized by Human"机构不设终端，划区域交互"（2026-08-13）
> **Task Type:** Feature Revision（机构访问边界：终端 -> 区域）
> **Design Input:** FR-INST-002-B（候选）
> **Dependency:** FR-INST-002-IMPL（已实现终端版）、FR-LAND-001、FR-CORE-002、
> FR-AUD-001、FR-INST-001-A/B
> **Authority Boundary:** 本文件细化实现范围；服务契约保持不变（消费者无需改）。

---

## 1. Goal

把机构访问边界从"注册终端 + 终端交互"重构为"注册区域 + 区域在场"：

- 删除 Terminal 模型/目录/命令；新增 Zone（kind: PUBLIC/OFFICIAL/SECURE；
  region 有界 3D 盒；capabilitySet）；
- Facility = Zone 容器；Zone 必须落在 Facility 的 FR-LAND parcel 内且小型化；
- 现场上下文由"终端交互"改为"玩家在 Zone 区域内"签发；
- 离开区域/换维度/登出/死亡/吊销即失效；返回不恢复；
- 最终变异边界复检不可禁用；
- `InstitutionAccessService` 接口保持兼容（gov/par/jus/eco-002 不受影响）。

## 2. 明确不实现

- 终端保留/混合；区域外机构交互；GUI/包；业务权限；新依赖。

## 3. 具体契约

### 3.1 存储 v2

```text
institution-access（StoreVersion=2）
├── Facilities: <facilityId> -> Facility（institutionType/parcelId/state/revision）
│   └── Zones: <zoneId> -> Zone（kind/region/capabilitySet/state/revision）
```

- 严格编解码 + 失败关闭 + 确定性 + 有界；无 Terminal 字段；
- 生产无数据，直接 v2；若检测到 v1（含终端）根 -> 显式迁移或失败关闭（不静默丢数据）。

### 3.2 区域在场签发

- facility/zone ACTIVE；能力匹配；玩家与 zone 同维度且在区域内 -> 签发上下文；
- 上下文绑定 player/zone/capability/time/expiry/revision；
- 有界周期校验（仅活动上下文玩家，默认 1s）+ 生命周期事件；
- 参数可配置；最终复检不可禁用。

### 3.3 命令

- `/fr admin institution facility register|suspend|activate|relocate|disable`
- `/fr admin institution zone add|remove|resize|set-kind|suspend|activate`
- 移除 terminal 命令；区域坐标来自 parcel/有界子区域（无硬编码坐标）。

## 4. 测试计划

`institutionAccessFoundationTest` 重写：

- Zone 注册/改大小/改 kind（parcel 约束、有界、单快照+门）；
- 区域内签发 / 区域外拒绝；离开失效/返回不恢复；三类工作流参数；
- 无终端引用（反射/源码守卫）；v1 根迁移或拒绝；消费者兼容（gov/par/jus/eco-002
  foundation 测试照常）；
- `gradlew build` 全绿。

## 5. 验收

对应 FR-INST-002-B §7；全部须有测试证据。

## 6. 约束

- 不改消费者业务模块；不新增依赖；服务端权威；SavedData/NBT only；
- 提交后审查（Codex）-> ROUTE TO HUMAN / RETEST / BLOCK。
