# FR-ID-001-IMPL 派发提示词（deepseek v4 flash 实现子进程）

> 依据 tools/dispatch-PROMPT.template.md。前置：FR-CORE-002 已实现（durable gate）。

## 1. 身份与目标

- 你是 FontaineRepublic（MC 1.20.1 / Forge 47.4.18 / Java 17）实现子进程。
- 任务 ID：FR-ID-001-IMPL；目标：实现统一数字主体登记册核心。
- 只实现本任务；**不实现水神初始个人绑定（bootstrap）**——那是独立后续任务。

## 2. 工作目录与必读文档

- 工作目录：`D:\MC\FontaineRepublic\deepseek-worktrees-v2\fr-id-001-impl`（从 develop 创建）
- 必读（按顺序）：运行手册 -> CLAUDE.md -> current_status.md ->
  `docs/architecture/fr-id-001-a-unified-digital-subject-registry.md`（规范） ->
  `docs/development/FR-ID-001-java-implementation-task.md`（本任务细化） ->
  任务卡 FR-ID-001-IMPL -> `docs/development/FR-CORE-002-java-implementation-task.md`
  （commitModuleData 契约）-> 现有 PlayerDataRepository/PlayerDataNbtCodec（单写入者/严格编解码范例）

## 3. 执行约束（违反即失败）

- 只在工作树内修改；不部署/推送/SSH；不修改设计文档与台账；
- 号码规则：MOD 97 逐位取模；10 位规范形式；类型 10 启用、00 仅精确保留号、
  20+ 仅保留；固定号 `10-000001-61` / `00-000001-95` 首个快照起保留且普通分配永不产出；
- 一 UUID 一主体；懒式幂等 ensure；owner-thread；
- 权威变更走 `DataManager.commitModuleData("subject-registry", ...)`，COMMITTED 后发布；
- 严格编解码 + 索引双向校验 + 失败关闭加载；无枚举 API；
- **bootstrap 相关（§4.4/§17.1 原个人绑定、尝试审计）明确不实现**，代码中保留固定号与
  常量 office 主体物化，其余标记 BLOCKED 写入交付报告；
- 开始时报告分支/HEAD/工作区状态。

## 4. 验收（FR-ID §19 除 bootstrap 项）

- 号码生成/校验/解析/冲突重试/耗尽/不复用/类型隔离；owner 唯一；幂等；
- 注入失败无发布；跨世界/损坏失败关闭；确定性编码；精确查询；
- `subjectRegistryFoundationTest` + `gradlew build` 全绿。

## 5. 交付格式

```text
任务：FR-ID-001-IMPL
分支：{branch}
提交：{commit}
修改文件：{列表}
测试命令与结果：{命令 + 摘要}
未解决问题：{列表（bootstrap 阻断项单列）}
是否有越界文件：是/否（说明）
对下一任务接口说明：{SubjectRegistryService 契约摘要}
```

## 6. 提交前自检

- [ ] 验收全有测试证据；[ ] bootstrap 未实现且未假装通过；[ ] 未越界；
- [ ] build 通过；[ ] 报告含 commit/测试/风险/接口
