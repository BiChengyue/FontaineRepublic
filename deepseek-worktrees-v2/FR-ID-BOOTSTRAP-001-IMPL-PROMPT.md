# FR-ID-BOOTSTRAP-001-IMPL 派发提示词（deepseek v4 flash 实现子进程）

> 依据 tools/dispatch-PROMPT.template.md。Human 已批准（FR-ID-BOOTSTRAP-HUMAN-APPROVAL-01）。

## 1. 身份与目标

- 你是 FontaineRepublic（MC 1.20.1 / Forge 47.4.18 / Java 17）实现子进程。
- 任务 ID：FR-ID-BOOTSTRAP-001-IMPL；目标：实现水神初始个人主体一次性绑定。
- 只实现本任务；不实现继任/office/官方账户/FR-EMG 相关。

## 2. 工作目录与必读文档

- 工作目录：`D:\MC\FontaineRepublic\deepseek-worktrees-v2\fr-id-bootstrap-001-impl`（从 develop 创建）
- 必读（按顺序）：运行手册 -> CLAUDE.md -> current_status.md ->
  `docs/architecture/fr-id-bootstrap-001-a-original-person-bootstrap.md`（规范） ->
  `docs/development/FR-ID-BOOTSTRAP-001-java-implementation-task.md`（本任务细化） ->
  FR-ID-001 现有代码（`server/registry/`：Repository/Service/Module/编解码）
  -> FR-CMD-001 命令接线（`server/command/`：FrameworkAdminCommand/CommandBootstrap）

## 3. 执行约束（违反即失败）

- 只在工作树内修改；不部署/推送/SSH；不修改设计文档与台账；
- 唯一授权源 = 真实本地 Dedicated Server 控制台（分类器按实现任务 §3.1，最终变异边界再校验）；
- 尝试链 append-only + 摘要链；PENDING 先落库再执行；终态 SUCCESS 才 BOUND；
- 绑定不可变：成功后无解绑/重绑/转移；异键重绑拒绝；
- 权威变更走 `DataManager.commitModuleData("subject-registry", ...)`；
- 与 FR-EMG 隔离：不得读取/推断 FR-EMG UUID；不引入依赖；
- 不实现继任/office/官方账户；不实现 GUI/包；
- 开始时报告分支/HEAD/工作区状态。

## 4. 验收（设计 §7 矩阵）

- 控制台分类矩阵；首次绑定成功；拒绝/INCOMPLETE/幂等重放；注入失败无发布；
- 重启对账一致/不一致/篡改；不可变；`bootstrapFoundationTest` + `gradlew build` 全绿。

## 5. 交付格式

```text
任务：FR-ID-BOOTSTRAP-001-IMPL
分支：{branch}
提交：{commit}
修改文件：{列表}
测试命令与结果：{命令 + 摘要}
未解决问题：{列表}
是否有越界文件：是/否（说明）
对下一任务接口说明：{命令/服务契约摘要}
```

## 6. 提交前自检

- [ ] 验收全有测试证据；[ ] 未越界；[ ] build 通过；[ ] 报告含 commit/测试/风险/接口
