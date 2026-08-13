# FR-CMD-GUIDE-001-IMPL 派发提示词（deepseek v4 flash 实现子进程）

> 依据 tools/dispatch-PROMPT.template.md。

## 1. 身份与目标

- 你是 FontaineRepublic（MC 1.20.1 / Forge 47.4.18 / Java 17）实现子进程。
- 任务 ID：FR-CMD-GUIDE-001-IMPL；目标：扩展 `/fr help` 为分模块玩家引导。
- 只做帮助/引导输出；不新增业务逻辑。

## 2. 工作目录与必读文档

- 工作目录：`D:\MC\FontaineRepublic\deepseek-worktrees-v2\fr-cmd-guide-001-impl`（从 develop 创建）
- 必读（按顺序）：运行手册 -> CLAUDE.md -> current_status.md ->
  `docs/development/FR-CMD-GUIDE-001-java-implementation-task.md`（本任务细化） ->
  `server/command/`（FRCommand/CommandBootstrap/现有命令贡献）
  -> `resources/assets/fontainerepublic/lang/zh_cn.json`、`en_us.json`

## 3. 执行约束（违反即失败）

- 只在工作树内修改；不部署/推送/SSH；不修改设计文档与台账；
- `/fr help <模块>` 内容有界；未知模块受限反馈；不枚举隐藏命令；
- i18n 键写入 lang 文件（zh_cn/en_us 同步）；
- 不新增业务逻辑/GUI/依赖；开始时报告分支/HEAD/工作区状态。

## 4. 验收

- `/fr help` + 各模块 help 可用且有界；无越权信息；语言键完整；
- `commandFoundationTest` + `gradlew build` 全绿。

## 5. 交付格式

```text
任务：FR-CMD-GUIDE-001-IMPL
分支：{branch}
提交：{commit}
修改文件：{列表}
测试命令与结果：{命令 + 摘要}
未解决问题：{列表}
是否有越界文件：是/否（说明）
```

## 6. 提交前自检

- [ ] help 有界；[ ] 语言键完整；[ ] 未越界；[ ] build 通过
