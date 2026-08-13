# FR-GOV-001-IMPL 派发提示词（deepseek v4 flash 实现子进程）

> 依据 tools/dispatch-PROMPT.template.md。前置：FR-INST-002 已实现。

## 1. 身份与目标

- 你是 FontaineRepublic（MC 1.20.1 / Forge 47.4.18 / Java 17）实现子进程。
- 任务 ID：FR-GOV-001-IMPL；目标：实现政府模块（职位/部门/任命）。
- 只实现本任务；不实现立法/司法/财政/选举/GUI。

## 2. 工作目录与必读文档

- 工作目录：`D:\MC\FontaineRepublic\deepseek-worktrees-v2\fr-gov-001-impl`（从 develop 创建）
- 必读（按顺序）：运行手册 -> CLAUDE.md -> current_status.md ->
  `docs/architecture/fr-gov-001-a-government-module-architecture.md`（候选规范） ->
  `docs/development/FR-GOV-001-java-implementation-task.md`（本任务细化） ->
  `server/institutionaccess/api/InstitutionAccessService.java`（现场上下文） ->
  现有模块模式（`server/citizen/`、`server/land/`）

## 3. 执行约束（违反即失败）

- 只在工作树内修改；不部署/推送/SSH；不修改设计文档与台账；
- 任命/罢免 = ONSITE_OFFICIAL_DUTY：最终变异边界必须校验现场上下文；
- 政治职位永不等同技术权限（无 rank/office -> OP 映射）；
- 四柱边界：无立法/司法/财政方法；holder 经服务解析（不存游戏名）；
- 权威变更走 `DataManager.commitModuleData("government", ...)`；
- 无枚举 API；无 GUI/包；不新增依赖；开始时报告分支/HEAD/工作区状态。

## 4. 验收（FR-GOV-001-A §6）

- 部门/职位创建单快照；任命/罢免需现场上下文；holder 解析；
- 无技术权限映射；四柱边界；注入失败无发布；重启恢复；严格编解码；无枚举；
- `governmentFoundationTest` + `gradlew build` 全绿。

## 5. 交付格式

```text
任务：FR-GOV-001-IMPL
分支：{branch}
提交：{commit}
修改文件：{列表}
测试命令与结果：{命令 + 摘要}
未解决问题：{列表}
是否有越界文件：是/否（说明）
对下一任务接口说明：{GovernmentService 契约摘要}
```

## 6. 提交前自检

- [ ] 验收全有测试证据；[ ] 现场门控生效；[ ] 无技术权限映射；[ ] 未越界；[ ] build 通过
