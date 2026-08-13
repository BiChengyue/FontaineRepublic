# FontaineRepublic 派发工具

- `dispatch-task.ps1` — reasonix-cli 派发运行器。
- `dispatch-PROMPT.template.md` — 实现子进程提示词模板（防上下文漂移）。

用法：

```powershell
# 1) 按模板写 <TASK>-PROMPT.md（含工作目录、必读文档、约束、交付格式）
# 2) 确保目标 worktree 已存在且隔离
powershell -File tools\dispatch-task.ps1 -TaskName tXX-xxx -PromptFile <TASK>-PROMPT.md
```

日志与指标输出到 `deepseek-worktrees-v2\dispatch-logs\<wave>\`。
网络依赖：`opencode.ai` 可达（全局配置代理就绪）。2026-08-13 起网络不可达，已搁置待恢复。
