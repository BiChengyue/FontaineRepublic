# FR-LAND-001-IMPL 派发提示词（deepseek v4 flash 实现子进程）

> 依据 tools/dispatch-PROMPT.template.md。前置：FR-CIT-001（公民）已实现。

## 1. 身份与目标

- 你是 FontaineRepublic（MC 1.20.1 / Forge 47.4.18 / Java 17）实现子进程。
- 任务 ID：FR-LAND-001-IMPL；目标：实现共和国土地制度基础。
- 只实现本任务；不实现 LandMarket/司法/经济交易/GUI。

## 2. 工作目录与必读文档

- 工作目录：`D:\MC\FontaineRepublic\deepseek-worktrees-v2\fr-land-001-impl`（从 develop 创建）
- 必读（按顺序）：运行手册 -> CLAUDE.md -> current_status.md ->
  `docs/architecture/fr-land-001-a-land-module-architecture.md`（规范） ->
  现有模块模式参考（`server/audit/`、`server/registry/`） ->
  FR-CORE-002 实现任务文档（commitModuleData 契约）

## 3. 执行约束（违反即失败）

- 只在工作树内修改；不部署/推送/SSH；不修改设计文档与台账；
- **ownership 恒 = REPUBLIC**：无所有权转移 API；使用权仅 grant/renew/revoke；
- 无硬编码坐标；无自动建筑合规判定（人工）；无经济交易；
- rank/GOD 不得绕过权限（配置驱动 PermissionResolver）；
- 权威变更走 `DataManager.commitModuleData("land", ...)`，COMMITTED 后发布；
- 事件（Build/Interaction 仅 BlockEntity）在事件时解析，缓存非权威；
- 无枚举 API；无 GUI/包/客户端权威；不新增依赖；
- 开始时报告分支/HEAD/工作区状态。

## 4. 验收（FR-LAND-001-A §7）

- 所有权不可变；grant/renew/revoke/zone/access 单快照+门；holder 经服务解析；
- PermissionResolver 配置驱动、rank 无绕过；违规举报只读入口；
- 严格编解码；重启恢复；无枚举；`landFoundationTest` + `gradlew build` 全绿。

## 5. 交付格式

```text
任务：FR-LAND-001-IMPL
分支：{branch}
提交：{commit}
修改文件：{列表}
测试命令与结果：{命令 + 摘要}
未解决问题：{列表}
是否有越界文件：是/否（说明）
对下一任务接口说明：{LandService / PermissionResolver 契约摘要}
```

## 6. 提交前自检

- [ ] 验收全有测试证据；[ ] ownership 无转移路径；[ ] 无硬编码坐标；
- [ ] 未越界；[ ] build 通过
