# FontaineRepublic 持续开发运行手册

> 本文件是每次续接工作、派发子进程、恢复上下文时**必须首先完整阅读**的文件。
> 用途：固定工作模式与提示词基线，防止上下文漂移。
> 最后更新：2026-08-13

---

## 0. 每次开工必读清单（按顺序）

1. 本文件（运行手册）
2. `CLAUDE.md`（项目规则 + 当前进度）
3. `docs/ai_recovery/current_status.md`（状态台账）
4. `docs/governance/work_journal.md`（工作日志，读最后 5 条）
5. 当前任务相关设计文档（`docs/architecture/`）
6. `git log --oneline -10` + `git status -sb`（确认分支与工作区）

---

## 1. 项目快照

- 项目：FontaineRepublic（枫丹共和国）Minecraft Forge 服务端核心 Mod
- 技术栈：MC 1.20.1 / Forge 47.4.18 / Java 17 / Gradle / Mod ID `fontainerepublic`
- 基线：架构 v2.7（冻结）、路线图 v1.1、Phase 0 技术设计 v1.1
- 当前阶段：Phase 1 基础设施实现完成；Phase 2（公民/经济）设计候选已产出、待审阅
- 代码状态：FR-CORE-001 / FR-DATA-002 / FR-NET-001 / FR-CMD-001 已完成；develop 领先 origin 两个提交未推送

---

## 2. 角色与运作规则（用户 2026-08-13 确认）

| 角色 | 职责 |
|---|---|
| Codex（本会话） | 与用户沟通、架构设计、任务拆解、审阅、派发、维护项目状态 |
| deepseek v4 flash（opencode-go 订阅） | 通过 reasonix-cli 派发的实现子进程 |
| 用户 | 只提出需求、定义制度预期、在必要时测试；是最终 Approver |

运作规则：
1. 仅在**必须真机/真实环境测试**且无法自行验证时请求用户介入。
2. 用户未及时介入时：搁置该问题，继续推进其他可做的工作。
3. 持续开发：通过应用目标机制 + 工作日志维持推进；每轮结束更新日志。
4. 自主裁量权高，但不得违背：宪法/基本法、路线图 v1.1、架构 v2.7、Phase 范围限制、CLAUDE.md 规则。
5. 工作必须文档化：状态台账、工作日志、派发记录、审查结论均留痕。

---

## 3. 开发方向摘要

宪法 v3.0 确立的国家框架 → 代码实现：

- 水神 = 最高主权者 + 服务器所有者（OP），不亲理常政；护世保全权有严格边界。
- 四大柱石：议会（立法）/ 政府（行政）/ 法院（司法）/ 中央银行（财政），分立制衡。
- 规范层级：宪法 > 宪法基本法 > 组织法 > 普通法律 > 行政规则。
- 法律状态机：DRAFT/REVIEW/VOTING/APPROVED/PUBLISHED/ACTIVE/SUSPENDED/INVALID/EXPIRED。

技术原则：服务端权威；数据先行（数据模型→逻辑→命令→GUI）；模块单向分层；只用 Minecraft 原生能力（SavedData/NBT/事件/命令）；命令优先；范围控制（禁止项见路线图）。

实现顺序（路线图推荐）：Audit → Citizen → Land → Economy → Resource → City → Government → Parliament → Justice → AI/GUI(Beta)。

用户追加方向（2026-08-13 确认）：

1. **功能开发完成后提供引导**：面向玩家的上手引导/说明（游戏内引导 + 文档），
   在功能集稳定后交付；命令层先行（/fr help 体系），可视化引导随客户端模组。
2. **最后开发带 UI 的客户端模组**（FR Client Features，Beta/GUI 阶段）：
   可视化操作（余额/土地/议会/司法等界面、HUD、通知），严格按架构 v2.7 可选客户端合约
   （仅展示/输入转发、服务端权威、无 FR 包时命令/聊天平价、DistExecutor + package 隔离）。

---

## 4. 派发手册（Dispatch Playbook）

### 4.1 工具

- CLI：`E:\Reasonix\versions\v1.25.0\reasonix-cli.exe`（如版本失效，检查 `E:\Reasonix\versions\` 下最新版）
- 模型引用：`opencode-go/deepseek-v4-flash`（opencode-go 订阅通道）
- 全局配置/密钥：`C:\Users\Fireflylover\AppData\Roaming\reasonix\`（config.toml + .env）
- 项目沙箱：根目录 `reasonix.toml`（workspace_root = D:\MC\FontaineRepublic）

### 4.2 标准派发命令（PowerShell）

```powershell
$log = 'D:\MC\FontaineRepublic\deepseek-worktrees-v2\dispatch-logs\<wave>'
$env:REASONIX_METRICS_PATH = "$log\<task>.metrics.json"
$prompt = Get-Content -LiteralPath '<TASK>-PROMPT.md' -Raw -Encoding UTF8
& 'E:\Reasonix\versions\v1.21.2\reasonix-cli.exe' run --permission-mode auto --model opencode-go/deepseek-v4-flash --max-steps 60 --output-format text -p $prompt *> "$log\<task>.out.log"
```

推荐直接使用封装运行器：

```powershell
powershell -File tools\dispatch-task.ps1 -TaskName <task> -PromptFile <TASK>-PROMPT.md
```

> 注意：
> 1. **必须加 `--output-format text -p`**：非交互环境默认 TUI 接管会卡死
>    （2026-08-13 排查确认；详见工作日志）。
> 2. 网络可达性判断必须用**沙箱外**测试（沙箱限制外网会误报）。
> 3. 历史成功波次使用 v1.21.2；CLI 版本升级需先探针验证。

### 4.3 派发纪律

- 每个任务先写 `<TASK>-PROMPT.md`：包含工作目录、必读文档、执行约束、交付格式、禁止项。
- 实现必须在隔离的 git worktree 中进行（`deepseek-worktrees-v2/<task>/`），不污染主工作区。
- 输出统一格式：会话标识 / 分支 / commit / 修改文件 / 测试命令与结果 / 未解决问题 / 是否越界。
- 日志与 metrics 落在 `deepseek-worktrees-v2/dispatch-logs/<wave>/`。

### 4.4 审查纪律

- 我（Codex）独立审查每个提交，遵循 `.reasonix/skills/mod-review/SKILL.md`（Forge 模组只读审查）。
- 结论三选一：ROUTE TO HUMAN / RETEST / BLOCK；不代替用户批准。
- 验证与修复分离：审查发现缺陷只记录 Finding，修复是独立派发任务。

---

## 5. 任务队列（Next Steps）

1. ✅ FR-PM-001/002 状态同步与持续开发基础设施（reasonix.toml、运行手册、工作日志）
2. ✅ 审阅 Phase 2 设计候选并获 Human 批准（FR-PHASE2-HUMAN-APPROVAL-01）
3. ✅ 实现：Core/ID/Bootstrap/Citizen/Land/Economy/PlayerDirectory/命令/机构边界/政府/议会
   （司法进行中）
4. ⏳ 功能完成后的玩家引导（/fr help 体系 + 文档；可视化引导随客户端）
5. ⏳ 最后：带 UI 的客户端模组（FR Client，可视化操作，Beta/GUI 阶段）
6. ⏳ 每模块完成：审查 → 用户测试（必须时）→ 合并 develop → 推送 origin
7. ⏳ 待办：develop 领先 origin（推送待用户确认）

---

## 6. 上下文恢复程序

若上下文丢失/新会话接手：
1. 读第 0 节清单。
2. 运行 `git log --oneline -10`、`git branch -a`、`git status -sb` 确认状态。
3. 读 `work_journal.md` 末尾与 `dispatch-logs/` 最新 wave。
4. 按第 5 节任务队列继续，不重复已完成工作。

---

## 7. 文档规则

- 每轮工作结束：更新 `docs/governance/work_journal.md`。
- 里程碑：更新 `CLAUDE.md` + `docs/ai_recovery/current_status.md`，保持代码=文档=git 一致。
- 派发记录：保留在 `deepseek-worktrees-v2/dispatch-logs/`。
- 设计变更：必须经用户/审阅同意后才改架构文档。
