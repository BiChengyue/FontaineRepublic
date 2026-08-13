# FR-JUS-001-IMPL 派发提示词（deepseek v4 flash 实现子进程）

> 依据 tools/dispatch-PROMPT.template.md。前置：FR-INST-002、FR-LAND-001 已实现。

## 1. 身份与目标

- 你是 FontaineRepublic（MC 1.20.1 / Forge 47.4.18 / Java 17）实现子进程。
- 任务 ID：FR-JUS-001-IMPL；目标：实现司法模块（案件/证据/裁判流水线）。
- 只实现本任务；不实现裁判执行/违宪审查/AI 枢机/GUI。

## 2. 工作目录与必读文档

- 工作目录：`D:\MC\FontaineRepublic\deepseek-worktrees-v2\fr-jus-001-impl`（从 develop 创建）
- 必读（按顺序）：运行手册 -> CLAUDE.md -> current_status.md ->
  `docs/architecture/fr-jus-001-a-justice-module-architecture.md`（候选规范） ->
  `docs/development/FR-JUS-001-java-implementation-task.md`（本任务细化） ->
  `server/institutionaccess/api/InstitutionAccessService.java`（现场上下文） ->
  `server/land/api/LandService.java`（ViolationReport 入口）

## 3. 执行约束（违反即失败）

- 只在工作树内修改；不部署/推送/SSH；不修改设计文档与台账；
- 立案/证据 = ONSITE_PUBLIC_SERVICE；受理/裁判 = ONSITE_OFFICIAL_DUTY；
- 最终变异边界校验现场上下文 + standing + 证据可采性；
- 流水线封闭枚举 + 每次转换记录 actor/time/trigger/before/after/revision；
- **不执行裁判**（不触碰其他模块状态）；证据 append-only；裁判不可变；
- Land 举报立案 = 有界 intake（不读 Land NBT）；
- 权威变更走 `DataManager.commitModuleData("justice", ...)`；
- 无枚举 API；无 GUI/包；不新增依赖；开始时报告分支/HEAD/工作区状态。

## 4. 验收（FR-JUS-001-A §6）

- 立案/证据/受理/裁判现场门控；Land 举报立案；流水线封闭；复核路径；
- 独立性；无裁判执行；注入失败无发布；重启恢复；严格编解码；无枚举；
- `justiceFoundationTest` + `gradlew build` 全绿。

## 5. 交付格式

```text
任务：FR-JUS-001-IMPL
分支：{branch}
提交：{commit}
修改文件：{列表}
测试命令与结果：{命令 + 摘要}
未解决问题：{列表}
是否有越界文件：是/否（说明）
对下一任务接口说明：{JusticeService 契约摘要}
```

## 6. 提交前自检

- [ ] 验收全有测试证据；[ ] 现场门控生效；[ ] 不执行裁判；[ ] 未越界；[ ] build 通过
