# FR-CMD-USER-002-IMPL 派发提示词（deepseek v4 flash 实现子进程）

> 依据 tools/dispatch-PROMPT.template.md。前置：FR-DATA-003、FR-ID-001、economy 已实现。

## 1. 身份与目标

- 你是 FontaineRepublic（MC 1.20.1 / Forge 47.4.18 / Java 17）实现子进程。
- 任务 ID：FR-CMD-USER-002-IMPL；目标：`/fr money pay` 支持 UUID/玩家名/登记号。
- 只扩展目标输入；不新增业务逻辑。

## 2. 工作目录与必读文档

- 工作目录：`D:\MC\FontaineRepublic\deepseek-worktrees-v2\fr-cmd-user-002-impl`（从 develop 创建）
- 必读（按顺序）：运行手册 -> CLAUDE.md -> current_status.md ->
  `docs/development/FR-CMD-USER-002-java-implementation-task.md`（本任务细化） ->
  现有 MoneyCommand（`server/command/MoneyCommand.java`） ->
  `server/playerdata/api/PlayerDirectoryService.java`（resolveExactGameName） ->
  `server/registry/api/SubjectRegistryService.java`（resolvePlayer/resolveExactRegistryNumber） ->
  FR-ECO-001-C-ACCOUNT-ALIGN-01 §2.3

## 3. 执行约束（违反即失败）

- 只在工作树内修改；不部署/推送/SSH；不修改设计文档与台账；
- 三种输入收敛同一 SubjectId；最终再解析（suggestion 不权威）；
- UNKNOWN/RETIRED/AMBIGUOUS 统一反馈 `Player name cannot be resolved uniquely.`；
- 无前缀/模糊/枚举；无 top/bank/他人余额；不新增依赖；
- 开始时报告分支/HEAD/工作区状态。

## 4. 验收

- UUID/玩家名/登记号均收敛；歧义/未知/畸形反馈有界；
- 注入解析失败无转账；`commandFoundationTest` + `gradlew build` 全绿。

## 5. 交付格式

```text
任务：FR-CMD-USER-002-IMPL
分支：{branch}
提交：{commit}
修改文件：{列表}
测试命令与结果：{命令 + 摘要}
未解决问题：{列表}
是否有越界文件：是/否（说明）
对下一任务接口说明：{目标解析契约摘要}
```

## 6. 提交前自检

- [ ] 三输入收敛有测试；[ ] 反馈有界；[ ] 未越界；[ ] build 通过
