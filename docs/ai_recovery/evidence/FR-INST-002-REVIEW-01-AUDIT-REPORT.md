# Audit Report — FR-INST-002-REVIEW-01

> 独立代码与验证审查（Level 1-2）
> 按 `docs/ai_recovery/templates/audit_report_template.md` 结构。
> 本报告只做审阅，不代表 Human Approval。

---

## Agent Identity

- **Agent:** Codex
- **Role:** Reviewer（独立复核 + 独立复跑，未参与实现）
- **Task ID:** FR-INST-002-REVIEW-01
- **Task Name:** 共享机构访问边界实现审查
- **Context Source:** 派发 wave `fr-inst-002-r2-20260813`（首次派发瞬态失败，重试成功）

---

## Project Context Snapshot

- **Worktree:** deepseek-worktrees-v2/fr-inst-002-impl（分支 codex/fr-inst-002-impl）
- **Candidate commit:** `adee38f`（42 文件，+6478/-1）
- **Baseline:** develop @ b9d4394
- **Working Tree:** 审查后干净

---

## Audit Report

**Files reviewed:**

- `server/institutionaccess/` 全部：api（InstitutionAccessService/OnSiteContext/
  Facility/Terminal 注册与收据）、model（CapabilityClass/Facility/Terminal/WorkflowKind/
  InstitutionType）、persistence（Codec/Repository/Store/Limits）、event（PresenceMonitor/
  LifecycleEventHandlers）、service（DefaultInstitutionAccessService/OnSiteContextRegistry/
  ActorResolver/IntegrityDigest/InstitutionAccessConfig）、InstitutionAdminCommand、Module
- `test/.../InstitutionAccessFoundationTestMain.java`（16 项验收测试）

**Evidence sources:**

1. 上述源码审阅。
2. 独立复跑：`institutionAccessFoundationTest` -> `[FR-INST-002] Institution access
   foundation validation passed`；完整 `gradlew build` -> BUILD SUCCESSFUL
   （tmp/fr-inst2-verify/fullbuild-20260813.log）。

---

## Acceptance Matrix（FR-INST-002-A §7）

| # | 验收 | 判定 | 证据 |
|---|---|---|---|
| 1 | 设施/终端注册（parcel 校验） | PASS | testFacilityRegistrationParcelValidation / Terminal |
| 2 | 设施状态机 | PASS | testFacilityStateMachine |
| 3 | 上下文签发/校验/过期/能力 | PASS | testIssueContextValidation / ValidateAtMutation |
| 4 | 单次使用与失败策略 | PASS | testSingleUseAndFailurePolicy |
| 5 | 三类工作流默认参数 | PASS | testWorkflowDefaults |
| 6 | 离开失效/返回不恢复 | PASS | testLeaveInvalidatesNoRestore |
| 7 | 出席轮询边界 | PASS | testPresenceBoundary |
| 8 | 注入失败无发布 | PASS | testInjectionFailureNoPublish |
| 9 | 重启上下文清空 | PASS | testRestartClearsContexts |
| 10 | 严格编解码 | PASS | testStrictDeterministicCodec / CorruptFailClosed |
| 11 | 无硬编码坐标 | PASS | testNoHardcodedCoordinates |
| 12 | 命令树无越权 | PASS | testCommandTreeNoEscalation |
| 13 | 真机运行时验证 | NOT TESTED | 待 Level 3 |

---

## Findings

### F-001（Minor, Open）— 首次派发瞬态失败
- 首次派发会话归档无产出（~1.5 分钟退出）；重试成功。未复现，未发现配置/提示词问题。

### F-002（Suggestion, Open）— 机构业务权限未接线
- 本边界不包含议会/政府/法院/央行业务权限（属各机构模块）；预留消费接口。

### F-003（Suggestion, Open）— Level 3 真机验证
- 含真实设施/终端交互、上下文失效、出席检测路径。

---

## Severity Summary

| Severity | Count |
|---|---|
| Blocker | 0 |
| Major | 0 |
| Minor | 1 |
| Suggestion | 2 |
| **Total** | **3** |

---

## Governance Verification

| 原则 | 结果 |
|---|---|
| Human Authority | Pass（本报告非批准） |
| Review ≠ Approval | Pass（ROUTE TO HUMAN） |
| Implementer ≠ Reviewer | Pass（独立复跑） |
| 范围控制 | Pass（仅 institution-access + 注册/命令接线） |

## Compliance

**Overall Compliance:** Pass

**Recommendation:** ROUTE TO HUMAN；Level 3 真机验证列入运行时阶段。

## Final Statement

本报告为独立审查，不构成 Human Approval；Reviewer 未修改实现文件。
