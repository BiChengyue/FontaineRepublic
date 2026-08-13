# FR-INST-002-B-IMPL 派发提示词（deepseek v4 flash 实现子进程）

> 依据 tools/dispatch-PROMPT.template.md。Human 已指示区域制。

## 1. 身份与目标

- 你是 FontaineRepublic（MC 1.20.1 / Forge 47.4.18 / Java 17）实现子进程。
- 任务 ID：FR-INST-002-B-IMPL；目标：把机构访问边界从终端模型重构为区域模型。
- 只改 institution-access 模块与 help 文案；不动消费者业务模块。

## 2. 工作目录与必读文档

- 工作目录：`D:\MC\FontaineRepublic\deepseek-worktrees-v2\fr-inst-002-b-impl`（从 develop 创建）
- 必读（按顺序）：运行手册 -> CLAUDE.md -> current_status.md ->
  `docs/architecture/fr-inst-002-b-zone-based-institution-access.md`（规范） ->
  `docs/development/FR-INST-002-B-java-implementation-task.md`（本任务细化） ->
  现有 `server/institutionaccess/`（Terminal 版实现）

## 3. 执行约束（违反即失败）

- 只在工作树内修改；不部署/推送/SSH；不修改设计文档与台账；
- **删除 Terminal 模型/目录/命令**；新增 Zone（PUBLIC/OFFICIAL/SECURE；
  有界区域；capabilitySet）；
- 现场上下文由"区域内在场"签发；离开/换维度/登出/死亡/吊销即失效；返回不恢复；
- 最终变异边界复检不可禁用；无硬编码坐标（区域来自 parcel/有界子区域）；
- `InstitutionAccessService` 接口保持兼容（gov/par/jus/eco-002 不改）；
- 权威变更走 `commitModuleData("institution-access", ...)`；
- 不新增依赖；开始时报告分支/HEAD/工作区状态。

## 4. 验收（FR-INST-002-B §7）

- Zone 注册/改大小/改 kind；区域内签发/区域外拒绝；离开失效/返回不恢复；
- 三类工作流参数；无终端引用（守卫）；v1 根处理；消费者照常；
- `institutionAccessFoundationTest` + 消费者 foundation 测试 + `gradlew build` 全绿。

## 5. 交付格式

```text
任务：FR-INST-002-B-IMPL
分支：{branch}
提交：{commit}
修改文件：{列表}
测试命令与结果：{命令 + 摘要}
未解决问题：{列表}
是否有越界文件：是/否（说明）
```

## 6. 提交前自检

- [ ] 无 Terminal 残留；[ ] 区域在场生效；[ ] 消费者未改；[ ] 未越界；[ ] build 通过
