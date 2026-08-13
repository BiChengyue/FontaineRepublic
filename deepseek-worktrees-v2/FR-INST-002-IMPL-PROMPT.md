# FR-INST-002-IMPL 派发提示词（deepseek v4 flash 实现子进程）

> 依据 tools/dispatch-PROMPT.template.md。前置：FR-LAND-001 已实现。

## 1. 身份与目标

- 你是 FontaineRepublic（MC 1.20.1 / Forge 47.4.18 / Java 17）实现子进程。
- 任务 ID：FR-INST-002-IMPL；目标：实现共享机构访问边界（设施/终端/现场上下文）。
- 只实现本任务；不实现机构业务权限/紧急动作/GUI。

## 2. 工作目录与必读文档

- 工作目录：`D:\MC\FontaineRepublic\deepseek-worktrees-v2\fr-inst-002-impl`（从 develop 创建）
- 必读（按顺序）：运行手册 -> CLAUDE.md -> current_status.md ->
  `docs/architecture/fr-inst-002-a-shared-institution-access-boundary.md`（候选规范） ->
  `docs/architecture/fr-inst-001-a-institution-physical-interaction-mobile-boundary.md`（§4/§6/§7/§15.2） ->
  `docs/architecture/fr-inst-001-b-institution-session-zone-policy.md`（§3-§4 参数） ->
  `docs/development/FR-INST-002-java-implementation-task.md`（本任务细化） ->
  FR-LAND 现有代码（`server/land/`：LandService/空间数据只读消费）

## 3. 执行约束（违反即失败）

- 只在工作树内修改；不部署/推送/SSH；不修改设计文档与台账；
- **无硬编码坐标**：设施区域必须来自 FR-LAND parcel（Service-only）；
- 现场上下文短生命周期、服务端运行态、关服清空；离开/维度/登出/死亡即失效；
- 最终变异边界复检不可禁用（配置不可关闭）；返回不恢复旧上下文；
- 三类工作流默认参数按 FR-INST-001-B §3；
- 权威变更走 `DataManager.commitModuleData("institution-access", ...)`；
- 不实现机构业务权限/FR-EMG 紧急动作/GUI/包；不新增依赖；
- 开始时报告分支/HEAD/工作区状态。

## 4. 验收（FR-INST-002-A §7）

- 设施/终端注册（parcel 校验/区域校验/状态机）；上下文签发/校验/失效/单次；
- 三类工作流参数；出席轮询边界；注入失败无发布；重启上下文清空；
- 无硬编码坐标守卫；命令树无越权；`institutionAccessFoundationTest` + `gradlew build` 全绿。

## 5. 交付格式

```text
任务：FR-INST-002-IMPL
分支：{branch}
提交：{commit}
修改文件：{列表}
测试命令与结果：{命令 + 摘要}
未解决问题：{列表}
是否有越界文件：是/否（说明）
对下一任务接口说明：{InstitutionAccessService 契约摘要}
```

## 6. 提交前自检

- [ ] 验收全有测试证据；[ ] 无硬编码坐标；[ ] 未越界；[ ] build 通过
