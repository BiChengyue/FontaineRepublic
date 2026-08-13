# FR-AUD-001-IMPL 派发提示词（deepseek v4 flash 实现子进程）

> 依据 tools/dispatch-PROMPT.template.md 填写。前置：FR-CORE-002 已实现并审查通过。

## 1. 身份与目标

- 你是 FontaineRepublic（MC 1.20.1 / Forge 47.4.18 / Java 17）实现子进程。
- 任务 ID：FR-AUD-001-IMPL；目标：按 FR-AUD-001-A 实现 append-only 审计模块。
- 只实现本任务；不得实现 Citizen/Land/Economy 或 FR-EMG。

## 2. 工作目录与必读文档

- 工作目录：`D:\MC\FontaineRepublic\deepseek-worktrees-v2\fr-aud-001-impl`（从含 FR-CORE-002 的分支创建）
- 必读：运行手册 -> CLAUDE.md -> current_status.md ->
  `docs/architecture/fr-aud-001-a-audit-module-architecture.md` ->
  `docs/ai_recovery/task_cards/FR-AUD-001-IMPL-task-card.md` ->
  `docs/development/FR-CORE-002-java-implementation-task.md`（commitModuleData 契约）

## 3. 执行约束

- 只在工作树内修改；不部署/推送/SSH；不修改设计文档与台账；
- 不引入依赖；无 GUI/包/客户端权威；审计 append-only；
- 权威条目必须经 `DataManager.commitModuleData("audit", ...)` 并在 COMMITTED 后才可见；
- 与 FR-EMG 隔离：不重复紧急日志，不读取 FR-EMG 命名空间；
- 开始时报告分支/HEAD/工作区状态。

## 4. 验收（FR-AUD-001-A §8）

- append-only、段摘要链、分类强制、有界分页、确定性编码；
- recordAuthoritative 注入失败无副作用；重启恢复；孤儿清理；
- `auditFoundationTest` + `gradlew build` 全绿。

## 5. 交付格式

```text
任务：FR-AUD-001-IMPL
分支：{branch}
提交：{commit}
修改文件：{列表}
测试命令与结果：{命令 + 摘要}
未解决问题：{列表}
是否有越界文件：是/否（说明）
对下一任务接口说明：{AuditService 契约摘要}
```

## 6. 提交前自检

- [ ] 验收全有测试证据；[ ] 未越界；[ ] build 通过；[ ] 报告含 commit/测试/风险/接口
