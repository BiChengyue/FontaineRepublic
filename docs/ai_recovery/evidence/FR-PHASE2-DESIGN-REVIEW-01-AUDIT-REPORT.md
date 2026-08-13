# Audit Report — FR-PHASE2-DESIGN-REVIEW-01

> 独立架构审查报告（Design-Candidate Review）
> 按 `docs/ai_recovery/templates/audit_report_template.md` 结构。
> 本报告只做审阅，不代表 Human Approval，不授权任何实现。

---

## Agent Identity

- **Agent:** Codex
- **Role:** Reviewer（独立审阅，未参与设计候选撰写）
- **Task ID:** FR-PHASE2-DESIGN-REVIEW-01
- **Task Name:** Phase 2 设计候选独立架构审查
- **Request Summary:** 审阅 2026-08-02 产出的 Phase 2 设计候选，给出批准建议与优先级
- **Context Source:** 运行手册 `docs/governance/continuous_development_ops.md` 任务队列第 2 项

---

## Project Context Snapshot

- **Branch:** codex/fr-inst-001-a-design
- **Audited commits:** `6e7a731`（设计候选冻结提交）；FR-DATA-003-A 为未跟踪文件
- **Working Tree:** 设计分支含历史未提交改动（治理/模板类，非本审查范围）
- **Phase:** Phase 1 基础设施完成；Phase 2 设计候选待审

---

## Audit Report

**Review type:** Pre-review（设计候选，非实现）

**Files reviewed:**

| 设计 | 状态（文档自述） |
|---|---|
| FR-ID-001-A 统一数字主体登记册 | Design Candidate — Pending Review |
| FR-DATA-003-A 安全玩家目录 | Architecture Candidate — Pending Review（未提交） |
| FR-ECO-001-A 经济架构 | Design Candidate（头部元数据滞后，见 F-004） |
| FR-ECO-001-B 账户披露/现金扩展 | Approved Design Record |
| FR-ECO-001-C 经济基础范围+紧急目录 | Design Candidate — Pending Review |
| FR-INST-001-A 机构实体交互边界 | Design Candidate — Pending Review |
| FR-INST-001-B 机构会话/分区策略 | Approved Design Record |
| FR-EMG-001-A 水神紧急权限 | Architecture Design — Pending Review（FIX-01） |

**Evidence sources consulted:**

1. 上述 8 份设计文档全文。
2. `docs/architecture/architecture.md` §5.1–§5.5 及核心原则（服务端权威/模块分层/客户端合约）。
3. `docs/roadmap/development_roadmap.docx`（v1.1）与 `docs/design/phase0_technical_design.docx`（v1.1）。
4. `deliverables` 宪法 v3.0 与六部基本法（方向源头，核对制度映射）。
5. 源码核验：`core/DataManager.java`、`core/ModSavedData.java`（持久化能力）。
6. FR-EMG 文档 SHA-256 与 FR-ECO-001-C 引用比对。
7. `docs/ai_recovery/approval_records/FR-GOV-V2-HUMAN-APPROVAL-01.md`（治理记录）。

**Repository-observable facts:**

| 事实 | 核验方式 | 结果 |
|---|---|---|
| `DataManager.putModuleData` 返回 void、仅 `setDirty()` | 源码阅读 | 属实 |
| `ModSavedData` 无同步落盘确认 | 源码阅读 | 属实 |
| FR-EMG 当前文件哈希 = FR-ECO-001-C 引用 | `Get-FileHash -Algorithm SHA256` | 一致：`59B74C...E4CB` |
| FR-DATA-003-A 未提交 | `git status` | 未跟踪 |
| 批准记录 FR-GOV-V2-HUMAN-APPROVAL-01 | 文件阅读 | 仍为 Draft，未 Human Confirmed |

---

## Findings

### F-001 — 账户主键冲突（FR-ECO vs FR-ID）
- **Severity:** Blocker（实现前必须解决）
- **Status:** Open
- **Description:** FR-ECO-001-A §2.2 / §3.1 与 FR-ECO-001-C §2.3 以 Minecraft UUID 作为账户主键；
  FR-ID-001-A §5 要求个人账户以 `SubjectId` 为主键、公开登记号为唯一收款路由号，
  并在 §21.1 预告需要 `FR-ECO-001-C-ACCOUNT-ALIGN` 修订。两份设计若同时实现会冲突。
- **Evidence:** fr-eco-001-a §2.2/§3.1；fr-eco-001-c §2.3；fr-id-001-a §5、§21.1。
- **Recommendation:** 先批准 FR-ID-001-A，再产出 `FR-ECO-001-C-ACCOUNT-ALIGN`
  （账户主键改为 SubjectId、登记号为唯一公开号码），之后才允许 Economy 实现设计。

### F-002 — 核心持久化确认门缺失（跨 FR-ID / FR-DATA-003 / FR-EMG / ECO 紧急）
- **Severity:** Blocker
- **Status:** Open
- **Description:** 三份设计（FR-ID §9、FR-DATA-003 §9、FR-EMG §11.4）均要求
  "可确认的持久化提交（durable commit/flush 或 WAL）"。源码核验确认当前
  `DataManager`/`ModSavedData` 只有内存替换 + `setDirty()`，无法证明崩溃后落盘成功。
- **Evidence:** DataManager.java（`putModuleData`/`saveAll` 返回 void）；ModSavedData.java（`setDirty()`）。
- **Recommendation:** 下一优先级架构任务 = 设计核心持久化门（FR-CORE-002），
  统一被 FR-ID / FR-DATA-003 / FR-EMG / Economy 紧急动作消费；门未批准前不得授权相关实现。

### F-003 — 审计模块设计缺失（FR-AUD-001）
- **Severity:** Major
- **Status:** Open
- **Description:** 路线图 Phase 2 = Audit；FR-ECO 把审计钩子标为可选/未来；
  FR-EMG 依赖共享审计索引。当前无任何设计文档定义 Audit 模块（append-only、命名空间、容量、归档）。
- **Evidence:** roadmap v1.1 Phase 2；fr-eco-001-a §8.2；fr-emg-001-a §10。
- **Recommendation:** 与持久化门并行设计 FR-AUD-001（append-only 审计、边界、与 FR-EMG 收据段的关系）。

### F-004 — FR-ECO-001-A 头部状态滞后
- **Severity:** Minor
- **Status:** Open
- **Description:** FR-ECO-001-A 头部仍写 "Design Candidate — Pending Human Review"，
  而 FR-ECO-001-B §7.1 已自记"已批准"。元数据与状态不一致。
- **Evidence:** fr-eco-001-a 头部；fr-eco-001-b §7.1。
- **Recommendation:** 元数据修正任务 + 正式批准记录（Human Confirmed）。

### F-005 — 架构 v2.7 "已批准"缺少 Human-Confirmed 记录工件
- **Severity:** Minor
- **Status:** Open
- **Description:** 状态台账记载架构 v2.7 已获人工批准，但 approval_records 下唯一记录
  仍为 Draft（未 Human Confirmed）。
- **Evidence:** FR-GOV-V2-HUMAN-APPROVAL-01.md。
- **Recommendation:** 治理卫生：用户确认后补 Human-Confirmed 记录；不阻塞本次设计审阅。

### F-006 — FR-DATA-003-A 未入库
- **Severity:** Minor
- **Status:** Open（本审查随附提交解决）
- **Description:** FR-DATA-003-A 是完整设计候选但从未提交，git 历史无法追溯。
- **Recommendation:** 随本审查报告一并提交至设计分支。

### F-007 — 机构实现依赖未来空间所有权模型
- **Severity:** Suggestion
- **Status:** Deferred（文档已自认）
- **Description:** FR-INST-001-A/B 的设施/子区域实现依赖 Land/City 空间契约；硬编码坐标被明确禁止。
- **Recommendation:** 机构工作流（含央行现场职责）排到空间契约之后；远程个人服务不受影响。

### F-008 — FR-ECO-001-A 开放问题需在央行/国库界面前解决
- **Severity:** Suggestion
- **Status:** Deferred
- **Description:** 价值移动语义、国库信息披露、显示权限、现场上下文单次使用等开放问题，
  只影响央行/国库/排行等后续界面，不影响 Phase 1 个人服务。
- **Recommendation:** 个人服务先实现；上述问题在对应界面设计时解决。

---

## Severity Summary

| Severity | Count |
|---|---|
| Blocker | 2 |
| Major | 1 |
| Minor | 3 |
| Suggestion | 2 |
| **Total** | **8** |

---

## Consistency Matrix（设计方向一致性）

| 检查项 | 结果 | 说明 |
|---|---|---|
| 服务端权威 | PASS | 全部设计客户端/手机/命令均为投影，服务端最终裁决 |
| 单一写入者 + Service-only 访问 | PASS | 全部设计禁止跨模块 NBT/Repository 访问 |
| SavedData-only 持久化 | PASS | 无 JSON/数据库/Capability 权威存储 |
| 无 GUI/网络权威化 | PASS | S2C 仅展示；C2S 不具权威；无客户端缓存权威 |
| Phase 范围控制 | PASS | 均为设计候选，未授权实现；禁止项保持禁止 |
| FR-EMG ↔ FR-ECO-001-C 身份一致 | PASS | SHA-256 匹配 |
| FR-INST ↔ architecture.md 5.x 修正 | PASS | 引用章节存在且语义一致 |
| 宪法/基本法映射 | PASS | 水神紧急权限、四柱石、法律状态机均有对应设计 |

---

## Per-Document Recommendation

| 设计 | 建议 | 条件 |
|---|---|---|
| FR-ID-001-A | **ROUTE TO HUMAN（批准）** | 架构批准；实现被持久化门（F-002）阻断 |
| FR-DATA-003-A | **ROUTE TO HUMAN（批准）** | 接受迁移历史盲区；实现被持久化门阻断 |
| FR-INST-001-A/B | **ROUTE TO HUMAN（批准）** | 边界修正；实现依赖未来空间契约 |
| FR-ECO-001-A | **ROUTE TO HUMAN（批准）** | 附条件：先解决 F-001 账户对齐 + F-004 元数据 |
| FR-ECO-001-B | **ROUTE TO HUMAN（批准）** | 已确认决策记录，无冲突 |
| FR-ECO-001-C | **ROUTE TO HUMAN（批准）** | 附条件：紧急动作保持被 FR-EMG 门阻断 |
| FR-EMG-001-A | **ROUTE TO HUMAN（批准）** | 架构批准；4 个实现门（持久化/命令/审计/配置）未过 |

**Overall:** CONDITIONAL PASS → 批量提请 Human 确认。
批准仅代表架构方向一致；**不授权任何实现**。

---

## Recommended Sequencing（下一批架构任务）

1. **FR-CORE-002 持久化确认门**（WAL 或 durable commit/flush）— 解锁 FR-ID / FR-DATA-003 / FR-EMG / 经济紧急。
2. **FR-AUD-001 审计模块** — 补齐路线图 Phase 2 缺口，供经济与 FR-EMG 消费。
3. **FR-ECO-001-C-ACCOUNT-ALIGN** — 账户主键改为 SubjectId。
4. 之后才进入实现设计：Audit → PlayerData 目录 → 主体登记册 → Economy 个人服务
   （央行/紧急动作继续等待空间契约与 FR-EMG 门）。

---

## Governance Verification

| 原则 | 结果 | 说明 |
|---|---|---|
| Human Authority | Pass | 本报告不含批准 |
| Review ≠ Approval | Pass | 结论为 ROUTE TO HUMAN / 条件 |
| Implementer ≠ Reviewer | Pass | Codex 未参与设计候选撰写 |
| Evidence Source Classification | Pass | 区分源码核验事实与文档自述 |

## Compliance

**Overall Compliance:** Conditional Pass

**Recommendation:**
- 8 份设计候选方向一致，无 Phase/原则违反；
- 两个 Blocker（F-001 账户对齐、F-002 持久化门）必须在任何实现设计前解决；
- 建议用户确认本批架构批准，并批准下一批架构任务（FR-CORE-002、FR-AUD-001、FR-ECO-001-C-ALIGN）。

## Final Statement

本报告为独立设计审阅。不构成 Human Approval；Reviewer 未修改任何设计文档；
等待 Human 确认；实现仍需独立授权。
