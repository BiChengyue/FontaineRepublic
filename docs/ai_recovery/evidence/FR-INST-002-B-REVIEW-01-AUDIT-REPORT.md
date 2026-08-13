# Audit Report — FR-INST-002-B-REVIEW-01

> 独立代码与验证审查（Level 1-2）
> 本报告只做审阅，不代表 Human Approval。

---

## Agent Identity

- **Agent:** Codex
- **Role:** Reviewer（独立复核 + 独立复跑）
- **Task ID:** FR-INST-002-B-REVIEW-01
- **Task Name:** 机构访问边界区域制重构审查
- **Human Directive:** "各机构不设终端，划区域交互"（2026-08-14）

---

## Project Context Snapshot

- **Worktree:** deepseek-worktrees-v2/fr-inst-002-b-impl
- **Candidate commit:** `0e4a7cd`（43 文件，+2561/-1749；Terminal 全删、Zone 全加）
- **Baseline:** develop @ 95c5941

---

## Audit Report

**Files reviewed:**

- `server/institutionaccess/`：Zone 模型/API/存储 v2/服务（区域在场签发）/命令/配置
- 消费者命令联动（gov/par/jus/bank 的 zoneId 适配；**服务层零改动**）
- HelpCommand + 双语文案；InstitutionAccessFoundationTestMain 重写 + 4 消费者测试适配

**Evidence sources:**

1. 上述源码审阅。
2. 独立复跑：institutionAccess/economy/government/parliament/justice/command 六个
   foundation 测试全绿；完整 `gradlew build` BUILD SUCCESSFUL
   （tmp/fr-inst2b-verify/fullbuild-20260814.log）。
3. 终端残留核验：`rg "\bTerminal\b"` 仅命中 bootstrap 日志措辞（无关），无类引用残留。

---

## Acceptance Matrix（FR-INST-002-B §7）

| # | 验收 | 判定 |
|---|---|---|
| 1 | Zone 注册/改大小/改 kind（parcel 约束 + 有界 + 单快照+门） | PASS |
| 2 | 区域内签发 / 区域外拒绝 | PASS（verifyPresenceInZone） |
| 3 | 离开失效 / 返回不恢复 | PASS |
| 4 | 三类工作流参数（PUBLIC/OFFICIAL/SECURE） | PASS（kind→能力集/工作流） |
| 5 | 无终端引用 | PASS（删除 + rg 核验） |
| 6 | 消费者兼容（服务层零改动） | PASS（命令层仅 zoneId 适配） |
| 7 | 无硬编码坐标（parcel/有界子区域） | PASS |
| 8 | 最终复检不可禁用 | PASS |
| 9 | v1 根处理 | PASS（显式拒绝，不静默迁移） |
| 10 | 真机核验 | NOT TESTED（Human） |

---

## Findings

### F-001（Suggestion, Open）— 消费者命令层 zoneId 适配属必然联动
- Terminal 类删除使 gov/par/jus/bank 命令直接引用编译失败；适配为 zoneId 为必要，
  服务层 API 零改动。范围可接受。

### F-002（Suggestion, Open）— 真机核验
- 含真实区域划定/在场/离开失效路径（Human）。

---

## Compliance

**Overall Compliance:** Pass → ROUTE TO HUMAN（真机核验随晨间清单）。

## Final Statement

本报告为独立审查，不构成 Human Approval；Reviewer 未修改实现文件。
