# FR-DATA-003-IMPL 派发提示词（deepseek v4 flash 实现子进程）

> 依据 tools/dispatch-PROMPT.template.md。前置：FR-CORE-002、FR-ID-001 已实现。

## 1. 身份与目标

- 你是 FontaineRepublic（MC 1.20.1 / Forge 47.4.18 / Java 17）实现子进程。
- 任务 ID：FR-DATA-003-IMPL；目标：在 player-data 内实现安全精确姓名目录。
- 只实现本任务；不实现主体/余额/公民/模糊查询/GUI。

## 2. 工作目录与必读文档

- 工作目录：`D:\MC\FontaineRepublic\deepseek-worktrees-v2\fr-data-003-impl`（从 develop 创建）
- 必读（按顺序）：运行手册 -> CLAUDE.md -> current_status.md ->
  `docs/architecture/fr-data-003-a-safe-player-directory.md`（规范） ->
  `docs/development/FR-DATA-003-java-implementation-task.md`（本任务细化） ->
  现有 PlayerData 实现（`server/playerdata/`：Repository/NbtCodec/StoreSnapshot/Service）
  -> FR-CORE-002 实现任务文档（commitModuleData 契约）

## 3. 执行约束（违反即失败）

- 只在工作树内修改；不部署/推送/SSH；不修改设计文档与台账；
- 目录段位于现有 `player-data` 命名空间（v2 迁移）；Players 记录与修订保持不变；
- 规范化名 ASCII 小写；输入不修剪；永久歧义不可清除；
- 登录观察为唯一来源；RENAME 一个原子快照（Players+Directory）；
- 权威变更走 `DataManager.commitModuleData("player-data", ...)`；
- 无前缀/模糊/枚举；UNKNOWN/RETIRED/AMBIGUOUS 对外同消息；
- 不查询 Mojang/Microsoft；不新增依赖；无 GUI/包；
- 开始时报告分支/HEAD/工作区状态。

## 4. 验收（FR-DATA-003-A §15）

- 首登/未变/大小写/改名/回迁/永久歧义/迁移分组/注入失败/重启/严格编解码/无枚举；
- `playerDirectoryFoundationTest` + `gradlew build` 全绿（含既有 playerDataTest）。

## 5. 交付格式

```text
任务：FR-DATA-003-IMPL
分支：{branch}
提交：{commit}
修改文件：{列表}
测试命令与结果：{命令 + 摘要}
未解决问题：{列表}
是否有越界文件：是/否（说明）
对下一任务接口说明：{PlayerDirectoryService 契约摘要}
```

## 6. 提交前自检

- [ ] 验收全有测试证据；[ ] 未越界（Players 记录未重写）；[ ] build 通过
