# FontaineRepublic 接任工单（Takeover Ticket）— 2026-08-14

> 状态：**已接管并恢复持续开发**（后续 Human 指令覆盖阶段中止；
> FR-LAND-CLAIM-001 已于 `develop@2a2354d` 完成）
> 交接方：Codex（root，架构/审查/派发）
> 接收方：接任 AI（读取本工单后从"恢复指引"开始）
> 生成时间：2026-08-14 11:40

> **接管更新（2026-08-14 下午）：** 接任 Coordinator 已读取本工单，从
> `develop@35e1f8b` 完成预检、DSH 隔离实现、独立复审、全量构建与快进合并。
> 当前协议 v8、消息 27 条（ID 0..26）；本工单中的“FR-LAND-CLAIM-001 未派发”
> 与“协议 v7”描述仅保留为交接时历史快照，不再代表当前状态。

---

## 0. Agent Handoff（按项目交接协议 §4.1）

```
Task ID: FR-OVERALL-TAKEOVER-2026-08-14
Task Owner: Human
Human-confirmed Implementer: 未指定（沿用 dsh/reasonix 子进程派发模式）
Human-confirmed Reviewer: 未指定（沿用 root 独立审查 Level 1-2）
Current Implementer: 接任 AI
Human Authorization Reference: Human 会话指令（2026-08-14："本阶段结束后中止任务，生成接任工单"）
Previous Role: Codex（root）
Current Role: 接任 AI

Allowed Action:
  - 读取仓库文档与代码、继续按既定工作流派发实现/审查/合并
  - 在 Human 决策点暂停并请求 Human
  - 使用 tools/dispatch-task.ps1（dsh 引擎默认）派发子进程

Forbidden Action:
  - 未经 Human 确认改变设计方向 / 政策（税、邮费、紧急权限、区域制等）
  - 删除或重写已合并模块、破坏性 git 操作（reset/checkout -- 需先确认）
  - 提交或外发任何 API 秘钥（OPENCODE_GO_API_KEY / DEEPSEEK_API_KEY）

Next Step: 见 §9 恢复指引；待办见 §6。
```

---

## 1. 项目概况

- **项目**：FontaineRepublic — 我的世界 1.20.1 / Forge 47.4.18 / Java 17 / Gradle 8.5 模组。
- **定位**：服务器端权威的"共和国内政系统"——公民、土地、经济（央行/国库）、
  政府/议会/司法、紧急权限、机构现场区域制、传讯水镜（手机）与客户端 UI。
- **仓库**：`D:\MC\FontaineRepublic`，主分支 `develop`，模块开发在
  `deepseek-worktrees-v2\<module>-impl`（git worktree，分支 `codex/<module>-impl`）。
- **协作模式**：root AI 负责需求沟通、架构、审查、合并；实现由 **deepseek-v4-flash
  子进程**完成（此前 reasonix-cli + opencode-go；2026-08-14 起切换为
  **DeepSeek Harness（dsh）headless**，同为 opencode-go 订阅）。

## 2. 当前进度快照（develop HEAD = `c40c476`）

### 已合并进 develop 的模块

| 模块 | 内容 | 分支提交 | 合并进 develop |
|---|---|---|---|
| FR-CORE-001 | 核心框架运行时基线 | `0d22129` | Phase 0 |
| FR-DATA-002 | 玩家身份基础设施（UUID/SavedData） | `4d2876f` | Phase 0 |
| FR-NET-001 | 网络基础（协议版本/限流/消息表） | `e3e8da0` | Phase 0 |
| FR-CMD-001 | 命令基础（/fr 根命令） | `f9e1806` | Phase 0 |
| FR-CORE-002 | 持久化提交门（fsync+原子替换） | `3813648` | 2026-08-13 |
| FR-AUD-001 | 追加式审计模块 | `2ffdd1d` | 2026-08-13 |
| FR-ID-001 | 统一主体登记册（公开号 TT-NNNNNN-CC） | `2b46fd6` | 2026-08-13 |
| FR-ID-BOOTSTRAP-001 | 原始人绑定 | `0896a1a` | 2026-08-13 |
| FR-CIT-001 | 公民模块 | `7adcd42` | 2026-08-13 |
| FR-LAND-001 | 土地模块（共和国地块/使用权） | `d9cc20c` | 2026-08-13 |
| FR-ECO-001 | 经济模块（账户/转账/国库/央行） | `b09704d` | 2026-08-13 |
| FR-DATA-003 | 安全玩家目录（名→UUID→主体→账户） | `f3c34a3` | 2026-08-13 |
| FR-CMD-USER-001/002 | 玩家命令（余额/转账/按 UUID/登记号） | `5959b0c` / `931c220` | 2026-08-13 |
| FR-INST-002(+B) | 机构现场访问边界（设施/终端/区域制） | `adee38f` / `0e4a7cd` | 2026-08-13/14 |
| FR-GOV-001 | 政府（部门/职位/职务任命） | `7c439c9` | 2026-08-13 |
| FR-PAR-001/002 | 议会（提案/表决/法案）及扩展 | `8985fda` / `32f7d64` | 2026-08-13 |
| FR-JUS-001 | 司法（案件/证据/判决，含土地收件） | `566ea07` | 2026-08-13 |
| FR-EMG-001 / -CMD / -ECO | 水神紧急权限/命令/紧急经济目录 | `f8a8569` / `28bc3c3` / `db2704b` | 2026-08-14 |
| FR-ITEM-001 | 传讯水镜物品 + UI 门禁 + 右键交互 | `fc4e8f1` | `a5fcf77` |
| FR-CLIENT-001 A/B-1/B-2/B-3a/B-3b | 客户端 UI（10 个 /frclient 视图、HUD、协议 v5） | `68f5746`/`4cdc95d`/`84bb477`/`d8c83e2`/`61cc505` | 2026-08-14 |
| **FR-TRADE-001** | **传讯水镜交易子系统（意向模型/5%税/物品防复制，协议 v6 账本 9-15）** | `3c4b36f` | `9872723` |
| **FR-MAIL-001** | **传讯水镜邮箱子系统（邮费/附件/机构广播，协议 v7 账本 16-22）** | `7118cf2` | `c40c476` |

### 协议/账本演进（接任 AI 必须沿用）

- 协议版本目前 **v7**；消息表 23 条，账本 ID 0..22：
  `0-8` 客户端展示（A/B 阶段）、`9-15` 交易（C2S 9-14 + S2C 15）、
  `16-22` 邮件（16 Send / 17 ListRequest / 18 Read / 19 Delete /
  20 MailboxSync / 21 MailAlert / 22 MailBroadcast）。
- **FR-LAND-CLAIM-001 派发时账本应为 v8、ID 23-26**（LandInspect /
  LandInspectResult / LandClaim / LandClaimResult）——提示词已写明。
- 所有 C2S 包必须带速率策略；载荷全部有界；新消息同步更新
  `NetworkFoundationTestMain` 的 v/数量/方向分组断言。

## 3. 已实现功能摘要（含使用限制）

详细玩家指引见 `docs/guide/player-guide.md` 与 `docs/guide/feature-summary.md`。
要点：

- **身份**：进服自动建档；寻址支持 UUID、精确名、登记号。
- **经济**：`/fr money` 余额/转账；国库与央行；紧急发钞/回收（水神）。
- **机构**：政府/议会/司法/央行均为物理地点，**须在指定区域内**才能交互
  （区域制 FR-INST-002；无独立终端）。
- **传讯水镜**：无合成表，由水神垄断发放；**只有背包/手持水镜**才可使用
  客户端 UI 与交易/邮件；未装客户端者不受影响。
- **交易**（FR-TRADE-001）：双方须**手持水镜**；意向出价不扣款、不挪物品；
  双同意后 5 秒 LOCKED 倒计时执行；执行时原子复核（余额、物品源槽防复制、
  背包满拒绝）；**只对出钱方收 5% 税**（floor，入国库）；断线/关服直接取消。
- **邮件**（FR-MAIL-001）：个人/机构互寄（gov:government、gov:ministry:<id>、
  parliament、court、bank）；**邮费 10/封 + 附件费 100/个**（发件人扣，入国库；
  机构免费）；金额附件发送不扣款、**领取时原子到账**；物品附件领取入背包
  （满则留待领）；**机构一键向全体公民广播**（授权+冷却）；HUD/聊天新邮件
  提醒（背包含水镜时）。
- **客户端 UI**：10 个 `/frclient` 视图（money/citizen/history/notifications/
  guide/government/parliament/court/land/主菜单）；HUD 可调位置（FR-HUD）。

## 4. 待办（按优先级，未授权勿自行扩展范围）

1. **FR-LAND-CLAIM-001**（提示词已备、未派发）：
   `deepseek-worktrees-v2/FR-LAND-CLAIM-001-IMPL-PROMPT.md`；
   设计 `docs/architecture/fr-land-claim-001-a-communicator-land-claim.md`；
   从 develop 新建 worktree `fr-land-claim-001-impl`（分支
   `codex/fr-land-claim-001-impl`）后派发；协议 v8、账本 23-26。
2. **Level 3 真机核验**（需 Human）：清单
   `docs/development/LEVEL3-RUNTIME-VERIFICATION-CHECKLIST.md`
   §4.6 服务器面（控制台启动、/fr admin emergency 预览/确认/检查、央行现场、
   崩溃窗口）+ §4.7 客户端面（10 个视图、交易/邮件真机流程、无模组平行）。
3. **待 Human 决策**：
   - 紧急发钞/回收是否允许作用于冻结账户（当前按设计允许，break-glass）；
   - "我的地块"权益视图（FR-LAND-002-A 设计候选已备）是否进入实现；
   - 早间真机核验的时间安排。
4. **推送**：develop 落后 origin（此前约定"先审查后可以推送"）；确认后
   `git push origin develop`。
5. **清理**：仓库根与 worktree 有大量未跟踪/遗留目录（.reasonix、.codex_tmp、
   credential-stage、各 worktree、`.gradle-cache` 等），待 Human 确认后清理。

## 5. 派发工具链（接任 AI 必读）

- **入口**：`tools/dispatch-task.ps1`
  - 默认引擎 `-Engine dsh`（DeepSeek Harness headless，opencode-go /
    deepseek-v4-flash）；`-Engine reasonix` 回退旧 CLI。
  - 参数：`-TaskName <ID> -PromptFile <PROMPT.md> [-Worktree <worktree路径>]
    [-Wave <wave>] [-MaxSteps] [-Engine]`。
  - 日志：`deepseek-worktrees-v2\dispatch-logs\<wave>\<Task>.out.log`。
  - dsh 引擎自动：定位 CLI（`C:\Users\Fireflylover\.dsh\profiles\node_modules\
    @deepseek-ai\dsh\lib\bin.js`）、DSH_HOME 默认 `.codex_tmp\dsh-home`
    （settings 模板 `tools/dsh-sub-settings.yaml`）、从
    `%APPDATA%\reasonix\.env` 读 `OPENCODE_GO_API_KEY` 注入环境。
- **dsh 现状**：Web UI 运行于 `http://127.0.0.1:3080`（`~/.dsh`，用户自己的
  会话默认 deepseek-v4-pro）；子进程 DSH_HOME 独立（默认 flash）。
- **秘钥**：`OPENCODE_GO_API_KEY`（opencode.ai，验证可用）与
  `DEEPSEEK_API_KEY` 均存于 `%APPDATA%\reasonix\.env`；dsh 的
  `~/.dsh/.credentials.yaml` 含同一把 opencode-go 秘钥。**不得提交/外发**。
- **已知坑（重要）**：
  - dsh 子进程**无法写共享 `.git`**（workspace-write 沙箱；headless 无审批
    通道）。已尝试 `permissionPresets: danger-full-access` 仍被拒（邮件轮
    复现）。当前 workaround：**root 代为 `git add` + `git commit`**。
  - 直接用 npm 安装 `@deepseek-ai/dsh` 在 Node 24 下会因 koffi 原生编译失败；
    本机已装好（pnpm），勿重装。
  - opencode.ai 网络可达性曾波动（2026-08-13 不可达）；失败先查网络。

## 6. 关键文档索引

- 运行手册：`docs/governance/continuous_development_ops.md`
- 台账/状态：`docs/governance/work_journal.md`、
  `docs/ai_recovery/current_status.md`、`docs/ai_recovery/decision_log.md`
- 架构设计：`docs/architecture/*.md`（fr-trade-001-a、fr-mail-001-a、
  fr-land-claim-001-a、fr-item-001-a、fr-inst-002-b、fr-emg-001-a 等）
- 实现任务书：`docs/development/FR-*-java-implementation-task.md`
- 任务卡：`docs/ai_recovery/task_cards/FR-*-task-card.md`
- 审查/审计报告：`docs/ai_recovery/evidence/*REVIEW*/`、`*AUDIT-REPORT*`
- 模板：`docs/ai_recovery/templates/`（task_card/audit_report/evidence/
  implementation_report/approval_record）
- 交接协议：`docs/ai_recovery/ai_agent_handoff_protocol_design.md`
- 治理：`docs/governance/ai_role_protocol.md`、`ai_workflow_lessons.md`
- 测试清单：`docs/development/LEVEL3-RUNTIME-VERIFICATION-CHECKLIST.md`

## 7. 工作流约定（沿用）

1. 派发实现：写 `<TASK>-PROMPT.md`（模板 `tools/dispatch-PROMPT.template.md`）
   → `dispatch-task.ps1`（worktree 内修改，不推送）。
2. 独立审查：root 做 Level 1-2（构建全绿 + 关键安全路径 + 越界扫描），
   报告存 `docs/ai_recovery/evidence/`。
3. 合并：审查通过 → `git merge --no-ff codex/<branch>` 进 develop。
4. 文档：更新 `work_journal.md`、`current_status.md`、任务卡。
5. 测试约定：plain-JVM `*FoundationTestMain`（build.gradle 注册 JavaExec 并入
   check）；Forge 生命周期/网络发送面用专用服务器冒烟（Level 3）；client/
   类仅经 `FMLClientSetupEvent` / `unsafeRunWhenOn` 触达（侧隔离）。

## 8. 最新提交链（develop）

```
c40c476 merge: communicator mailbox subsystem into develop (FR-MAIL-001)
7118cf2 feat(mail): ... (FR-MAIL-001)
9872723 merge: communicator trade subsystem into develop (FR-TRADE-001)
3c4b36f feat(trade): ... (FR-TRADE-001)
6b30840 docs(governance): record FR-TRADE-001 completion via dsh engine and merge
d92e8a8 chore(tools): dsh subprocess preset danger-full-access (self-commit capability)
06a70b0 chore(tools): dispatch default engine -> dsh headless (opencode-go/deepseek-v4-flash)
```

## 9. 恢复指引（接任 AI 第一步）

1. 读 `CLAUDE.md` → `docs/governance/continuous_development_ops.md` →
   `docs/ai_recovery/current_status.md` → `docs/governance/work_journal.md`
   （时间倒序）。
2. `git status` / `git log --oneline -15` / `git worktree list` 确认基线
   （develop = `c40c476`）。
3. 检查运行中进程：dsh Web UI（:3080）、Gradle 守护、遗留子进程
   （`.codex_tmp\*-dispatch.pid` / `*-build.pid`）。
4. 如需继续开发：从 §4 待办开始（先 FR-LAND-CLAIM-001）；有 Human 决策点时
   暂停并请求 Human，不要擅自定政策。
5. 提交时若涉及子进程产物：先跑 `gradlew build` 独立复核再合并；dsh 子进程
   不能自行提交时由 root/接任 AI 代提交。
