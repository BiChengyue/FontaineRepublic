# FR-ECO-002-IMPL 派发提示词（deepseek v4 flash 实现子进程）

> 依据 tools/dispatch-PROMPT.template.md。Human 已确认默认值后启用。

## 1. 身份与目标

- 你是 FontaineRepublic（MC 1.20.1 / Forge 47.4.18 / Java 17）实现子进程。
- 任务 ID：FR-ECO-002-IMPL；目标：实现央行现场职责（/fr bank）。
- 只实现本任务；不实现现金/紧急动作/GUI。

## 2. 工作目录与必读文档

- 工作目录：`D:\MC\FontaineRepublic\deepseek-worktrees-v2\fr-eco-002-impl`（从 develop 创建）
- 必读（按顺序）：运行手册 -> CLAUDE.md -> current_status.md ->
  `docs/architecture/fr-eco-002-a-central-bank-official-duties.md`（规范，含已确认默认值） ->
  FR-ECO-001-A §6.4（银行命令约束）-> FR-ECO-001-C（供给/原子性） ->
  `server/institutionaccess/api/InstitutionAccessService.java`（现场上下文） ->
  现有 economy 实现（`server/economy/`）

## 3. 执行约束（违反即失败）

- 只在工作树内修改；不部署/推送/SSH；不修改设计文档与台账；
- **所有官方变异现场门控**（央行设施+终端+上下文，最终边界复检，单次使用）；
- OP 仅为早期闸门；供给守恒；deposit/withdraw 走 FR-CORE-002 单快照；
- 无现金/ATM/利息/市场；无他人余额；紧急 issue/reclaim 保持 FR-EMG；
- 不新增依赖；开始时报告分支/HEAD/工作区状态。

## 4. 验收（FR-ECO-002-A §5）

- balance 只读公开；deposit/withdraw/freeze 现场门控；供给守恒；
- 冻结账户拒绝转账/提取；注入失败无发布；紧急隔离；无现金/他人余额守卫；
- `economyFoundationTest` 扩展 + `gradlew build` 全绿。

## 5. 交付格式

```text
任务：FR-ECO-002-IMPL
分支：{branch}
提交：{commit}
修改文件：{列表}
测试命令与结果：{命令 + 摘要}
未解决问题：{列表}
是否有越界文件：是/否（说明）
```

## 6. 提交前自检

- [ ] 现场门控生效；[ ] 供给守恒；[ ] 无现金/他人余额；[ ] 未越界；[ ] build 通过
