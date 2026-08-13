# FontaineRepublic Zone-Based Institution Access Revision v1.0

> **Task ID:** FR-INST-002-B
> **Status:** Design Candidate — Pending Independent Review and Human Approval
> **Project:** FontaineRepublic
> **Platform:** Minecraft Forge 1.20.1 / Forge 47.4.18 / Java 17
> **Purpose:** Replace institution **terminals** with registered **zones**:
> interaction with an institution's functions is possible only inside its
> small registered area
> **Supersedes:** FR-INST-002-A（终端目录/终端交互）中的终端部分；保留其余契约
> **Dependency:** FR-LAND-001（区域空间数据）、FR-CORE-002、FR-AUD-001、
> FR-INST-001-A/B（能力分类与工作流语义不变）
> **Implementation Status:** Not authorized

---

## 1. 变更说明（Human 2026-08-13 指示）

各机构**不设终端**：改为在机构设施内划出一片**小区域**（zone），玩家只有
在该区域内才能与相应功能交互。原"注册终端 + 终端交互"模型废弃。

保留不变：

- 能力分类（REMOTE_* / ONSITE_* / EMERGENCY_RECOVERY，FR-INST-001-A §4）；
- 三类工作流语义（公众/公务/高风险，FR-INST-001-B §3，参数适配区域制）；
- 服务端权威、现场上下文短生命周期、离开失效、最终变异边界复检；
- 现场官方职责 = 官方操作必须持有效现场上下文。

---

## 2. 新数据模型（命名空间 `institution-access` v2）

```text
institution-access
├── StoreVersion: 2
├── StoreRevision
├── Facilities: <facilityId> -> Facility
│   ├── institutionType, parcelId (FR-LAND), lifecycle state, revision
│   └── Zones: <zoneId> -> Zone
│       ├── kind: PUBLIC | OFFICIAL | SECURE
│       ├── region: bounded 3D box (dimension + min/max coords, from parcel)
│       ├── capabilitySet
│       └── lifecycle state, revision
```

- **无 Terminal 实体**：删除终端目录/终端位置/终端完整性模型；
- Facility = 一个或多个 Zone 的容器；Zone 区域必须落在 Facility 的 FR-LAND
  parcel 内且**小型化**（建议默认 ≤ 例如 16×16×8，可配置）；
- Zone kind 决定工作流：PUBLIC（公众业务）、OFFICIAL（公务工作区）、
  SECURE（高风险安全区）。

---

## 3. 现场上下文（On-Site Context）改为"区域在场"

```java
// InstitutionAccessService（概念，接口保持兼容）
OnSiteContext issueOnSiteContext(UUID playerId, ZoneId zoneId, CapabilityClass c, Duration ttl);
ValidationResult validateAtMutation(OnSiteContext ctx, CapabilityClass c, long now);
```

签发条件（替代"终端交互"）：

1. Facility 与 Zone 均 ACTIVE；
2. Zone 属于该 Facility 且能力集匹配；
3. 玩家与 Zone 同维度，且**位于 Zone 区域内**（服务端位置校验）；
4. 上下文绑定 player/zone/capability/issue-time/expiry/zone+facility revision。

失效条件（不变语义，来源改为区域）：

- 离开 Zone 区域（移动出界）、换维度、登出、死亡、服务端停止、facility/zone
  吊销或修订 -> 立即失效；
- 返回区域不恢复旧上下文；需重新进入（重新签发）；
- 最终变异边界复检不可禁用。

---

## 4. 工作流默认参数（FR-INST-001-B 适配）

| 工作流 | 区域 | 默认参数 |
|---|---|---|
| 公众业务 | PUBLIC zone | 在场即签发；上下文 2 分钟、单次使用 |
| 公务 | OFFICIAL zone | 会话 10/60 分钟；仅有效操作刷新空闲 |
| 高风险 | SECURE zone | 30 秒单次授权；绑定具体动作与参数 |

现场检测：仅对有活动上下文的玩家做有界周期校验（默认 1 秒）+ 生命周期事件。

---

## 5. 命令面

```text
/fr admin institution facility register|suspend|activate|relocate|disable
/fr admin institution zone add|remove|resize|set-kind|suspend|activate
```

（原 `terminal register|suspend|disable` 移除；区域注册引用 FR-LAND parcel 与
区域坐标，无硬编码坐标——坐标来自 parcel 区域或显式有界子区域。）

---

## 6. 影响面

| 消费者 | 影响 |
|---|---|
| FR-GOV / FR-PAR / FR-JUS / FR-ECO-002 | 仅消费 `InstitutionAccessService` 接口；
  上下文来源从终端改为区域在场，**服务契约不变**，无需业务改动 |
| FR-INST-002-IMPL（已实现） | 重构：Terminal 模型/目录/命令移除，Zone 模型/目录/
  在场检测接入；StoreVersion 1 -> 2（迁移或全新，生产无数据） |
| 测试 | institutionAccessFoundationTest 重写终端相关用例为区域用例 |
| 玩家引导 | /fr help institution 文案更新 |

---

## 7. Acceptance Matrix

| Test | Expected |
|---|---|
| Zone 注册/改大小/改 kind | parcel 约束 + 有界；单快照 + 门 |
| 区域在场签发 | 同维度 + 区域内才可签发；区域外拒绝 |
| 离开失效/返回不恢复 | 移动出界即失效；重新进入需重签 |
| 三类工作流参数 | 公众/公务/高风险按默认 |
| 无终端引用 | 源码/反射守卫（terminal 移除） |
| 消费者兼容 | gov/par/jus/eco-002 现场门控照常 |
| 无硬编码坐标 | 区域来自 parcel/显式有界子区域 |
| 最终复检不可禁用 | 不变 |

---

## 8. Non-Goals

- 终端保留/混合模型；区域外任何形式的机构交互；GUI/包；业务权限；
  实现（另行授权）。

## 9. Review Gate

Design candidate only. Does not constitute architecture approval, Human
Approval, or implementation authorization. Independent review requested.
