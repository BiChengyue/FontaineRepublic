# FR-CMD-USER-001-IMPL 派发提示词（deepseek v4 flash 实现子进程）

> 依据 tools/dispatch-PROMPT.template.md。前置：economy/citizen 已实现。

## 1. 身份与目标

- 你是 FontaineRepublic（MC 1.20.1 / Forge 47.4.18 / Java 17）实现子进程。
- 任务 ID：FR-CMD-USER-001-IMPL；目标：接线 `/fr money`、`/fr citizen info` 与登录钩子。
- 只接线已批准命令面；不新增业务逻辑。

## 2. 工作目录与必读文档

- 工作目录：`D:\MC\FontaineRepublic\deepseek-worktrees-v2\fr-cmd-user-001-impl`（从 develop 创建）
- 必读（按顺序）：运行手册 -> CLAUDE.md -> current_status.md ->
  `docs/development/FR-CMD-USER-001-java-implementation-task.md`（本任务细化） ->
  现有命令实现（`server/command/`：CommandBootstrap/FRCommand/FrameworkAdminCommand/
  CommandContributionRegistry/CommandRuntimeResolver） ->
  economy/citizen 服务（`server/economy/`、`server/citizen/`） ->
  FR-ECO-001-A §6 命令面约束

## 3. 执行约束（违反即失败）

- 只在工作树内修改；不部署/推送/SSH；不修改设计文档与台账；
- **无禁用命令**：top/bank/他人余额/国库/冻结/现金/发钞一律不注册；
- 玩家名输入禁用（UUID 目标）；memo 规范化 ≤128；
- 执行时经 CommandRuntimeResolver 取当前 ACTIVE 服务；不可用给标准反馈；
- 登录钩子幂等（失败仅日志）；不读 NBT；不新增依赖；
- 开始时报告分支/HEAD/工作区状态。

## 4. 验收

- `/fr money balance|pay|history`、`/fr citizen info` 可用且输出有界；
- 无禁用命令注册（反射/源码守卫）；登录钩子幂等；
- `gradlew build` 全绿。

## 5. 交付格式

```text
任务：FR-CMD-USER-001-IMPL
分支：{branch}
提交：{commit}
修改文件：{列表}
测试命令与结果：{命令 + 摘要}
未解决问题：{列表}
是否有越界文件：是/否（说明）
对下一任务接口说明：{命令/钩子契约摘要}
```

## 6. 提交前自检

- [ ] 无禁用命令；[ ] 输出有界；[ ] 未越界；[ ] build 通过
