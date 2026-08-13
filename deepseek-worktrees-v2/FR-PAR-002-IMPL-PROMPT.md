# FR-PAR-002-IMPL 派发提示词（deepseek v4 flash 实现子进程）

> 依据 tools/dispatch-PROMPT.template.md。Human 已指示继续服务端暂停任务。

## 1. 身份与目标

- 你是 FontaineRepublic（MC 1.20.1 / Forge 47.4.18 / Java 17）实现子进程。
- 任务 ID：FR-PAR-002-IMPL；目标：扩展议会立法流水线（守护审阅/公投/修宪）。
- 只实现本任务；不实现法院审查逻辑/修宪后执行/FR-EMG/GUI。

## 2. 工作目录与必读文档

- 工作目录：`D:\MC\FontaineRepublic\deepseek-worktrees-v2\fr-par-002-impl`（从 develop 创建）
- 必读（按顺序）：运行手册 -> CLAUDE.md -> current_status.md ->
  `docs/architecture/fr-par-002-a-legislative-extensions.md`（规范） ->
  `docs/development/FR-PAR-002-java-implementation-task.md`（本任务细化） ->
  现有 `server/parliament/`（FR-PAR-001 实现）
  -> `server/institutionaccess/api/InstitutionAccessService.java`（区域在场）

## 3. 执行约束（违反即失败）

- 只在工作树内修改；不部署/推送/SSH；不修改设计文档与台账；
- 门槛严格按宪法/基本法（72h/7d/14d；2/3-3/4-4/5；公投参与 2/3、赞成 2/3）；
- 状态机封闭枚举 + 每次转换留痕；一人一票；冻结名册；
- 守护审阅/共同制宪同意：水神玩家在场（议会区域）+ 本地控制台（复用 bootstrap
  分类器模式）；公投投票现场（议会公众区）；
- **不实现法院审查逻辑**（仅状态/时限推进，Justice 接口预留）；
- 权威变更走 `commitModuleData("parliament", ...)`；不新增依赖；
- 开始时报告分支/HEAD/工作区状态。

## 4. 验收（FR-PAR-002-A §6）

- 守护审阅时限/超时/退回/再通过；法院审查时限推进；公投门槛与一人一票；
- 修宪流水线完整顺序 + 各阶段门槛 + 水神同意/拒绝/超时；
- 状态机封闭；现场门控；注入失败无发布；重启恢复；严格编解码；
- `legislativeExtensionsFoundationTest` + `gradlew build` 全绿。

## 5. 交付格式

```text
任务：FR-PAR-002-IMPL
分支：{branch}
提交：{commit}
修改文件：{列表}
测试命令与结果：{命令 + 摘要}
未解决问题：{列表}
是否有越界文件：是/否（说明）
```

## 6. 提交前自检

- [ ] 门槛准确；[ ] 不实现法院审查；[ ] 现场门控；[ ] 未越界；[ ] build 通过
