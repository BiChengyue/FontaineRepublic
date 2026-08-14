# FontaineRepublic 派发工具

- `dispatch-task.ps1` — 子进程派发运行器。默认使用 DeepSeek Harness headless 引擎（`-Engine dsh`，opencode-go / deepseek-v4-flash）；`-Engine reasonix` 回退到旧 reasonix-cli。
- `dispatch-PROMPT.template.md` — 实现子进程提示词模板（防上下文漂移）。
- `dsh-sub-settings.yaml` — dsh 子进程工作域（DSH_HOME）配置模板。

用法：

```powershell
# 1) 按模板写 <TASK>-PROMPT.md（含工作目录、必读文档、约束、交付格式）
# 2) 确保目标 worktree 已存在且隔离
powershell -File tools\dispatch-task.ps1 -TaskName tXX-xxx -PromptFile <TASK>-PROMPT.md
```

日志与指标输出到 `deepseek-worktrees-v2\dispatch-logs\<wave>\`。
网络依赖：`opencode.ai` 可达（全局配置代理就绪）。2026-08-13 起网络不可达，已搁置待恢复。

dsh 引擎说明：

```powershell
# 默认（dsh headless，模型走 DSH_HOME 配置 = opencode-go/deepseek-v4-flash）
powershell -File tools\dispatch-task.ps1 -TaskName tXX-xxx -PromptFile <TASK>-PROMPT.md -Worktree <worktree路径>

# 回退到旧 reasonix-cli
powershell -File tools\dispatch-task.ps1 -TaskName tXX-xxx -PromptFile <TASK>-PROMPT.md -Engine reasonix
```

dsh 引擎的 DSH_HOME 默认在 `.codex_tmp\dsh-home`（首次运行自动从 `dsh-sub-settings.yaml` 生成），
秘钥读取自 reasonix 全局 `.env` 的 `OPENCODE_GO_API_KEY`，不落仓库。
