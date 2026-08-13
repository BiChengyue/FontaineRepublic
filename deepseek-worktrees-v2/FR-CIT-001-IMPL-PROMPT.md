# FR-CIT-001-IMPL 派发提示词（deepseek v4 flash 实现子进程）

> 依据 tools/dispatch-PROMPT.template.md。前置：FR-ID-001（主体登记）已实现。

## 1. 身份与目标

- 你是 FontaineRepublic（MC 1.20.1 / Forge 47.4.18 / Java 17）实现子进程。
- 任务 ID：FR-CIT-001-IMPL；目标：实现公民身份/政治等级基础设施。
- 只实现本任务；不实现投票/议会/任命/司法/Land/Economy。

## 2. 工作目录与必读文档

- 工作目录：`D:\MC\FontaineRepublic\deepseek-worktrees-v2\fr-cit-001-impl`（从 develop 创建）
- 必读（按顺序）：运行手册 -> CLAUDE.md -> current_status.md ->
  `docs/architecture/fr-cit-001-a-citizen-module-architecture.md`（规范） ->
  FR-ID-001 现有代码（`server/registry/`：Service/Repository 模式） ->
  FR-CORE-002 实现任务文档（commitModuleData 契约） ->
  PlayerData/PlayerDataService（身份前置）

## 3. 执行约束（违反即失败）

- 只在工作树内修改；不部署/推送/SSH；不修改设计文档与台账；
- 开户链 PlayerData -> subject -> citizen；幂等；无 subject 的 citizen 拒绝；
- rank/status 权威变更走 `DataManager.commitModuleData("citizen", ...)`；
- **rank 永不等于技术权限**：无 rank->OP/权限映射；GOD 仅政治分类；
- 无余额/土地/案件/职位/登记号字段；无枚举 API；
- 不实现投票/议会/任命/司法资格策略；无 GUI/包/客户端权威；
- 开始时报告分支/HEAD/工作区状态。

## 4. 验收（FR-CIT-001-A §7）

- 懒开户幂等；无 subject 拒绝；rank/status 变更单快照+门；rank 无技术效果；
- 严格编解码；重启恢复；无枚举；`citizenFoundationTest` + `gradlew build` 全绿。

## 5. 交付格式

```text
任务：FR-CIT-001-IMPL
分支：{branch}
提交：{commit}
修改文件：{列表}
测试命令与结果：{命令 + 摘要}
未解决问题：{列表}
是否有越界文件：是/否（说明）
对下一任务接口说明：{CitizenService 契约摘要}
```

## 6. 提交前自检

- [ ] 验收全有测试证据；[ ] rank 无权限映射；[ ] 未越界；[ ] build 通过
