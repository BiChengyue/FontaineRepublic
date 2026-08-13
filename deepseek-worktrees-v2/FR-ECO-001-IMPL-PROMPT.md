# FR-ECO-001-IMPL 派发提示词（deepseek v4 flash 实现子进程）

> 依据 tools/dispatch-PROMPT.template.md。前置：FR-CORE-002、FR-ID 主体登记、
> FR-AUD-001 已实现并审查通过。

## 1. 身份与目标

- 你是 FontaineRepublic（MC 1.20.1 / Forge 47.4.18 / Java 17）实现子进程。
- 任务 ID：FR-ECO-001-IMPL；目标：实现经济 Phase 1 玩家服务（SubjectId 账户）。
- 只实现本任务；不实现央行/紧急/国库/市场/GUI。

## 2. 工作目录与必读文档

- 工作目录：`D:\MC\FontaineRepublic\deepseek-worktrees-v2\fr-eco-001-impl`（从 develop 创建）
- 必读（按顺序）：运行手册 -> CLAUDE.md -> current_status.md ->
  FR-ECO-001-A/B/C、FR-ECO-001-C-ACCOUNT-ALIGN-01、FR-ID-001-A（账户键与路由）、
  任务卡 FR-ECO-001-IMPL、FR-CORE-002 实现任务文档（commitModuleData 契约）

## 3. 执行约束

- 只在工作树内修改；不部署/推送/SSH；不修改设计文档与台账；
- 账户主键 = SubjectId（UUID 经 FR-ID 解析）；登记号为唯一公开路由号；
- 无 top/他人余额/bank/国库/冻结；无玩家名离线输入；无紧急动作；
- 权威变更走 `DataManager.commitModuleData("economy", ...)`，COMMITTED 后才可见；
- 原子转账：一个完整快照；保存失败无任何发布；总供给守恒；
- 开始时报告分支/HEAD/工作区状态。

## 4. 验收（FR-ECO-001-C §15）

- 零余额开户/懒开户/重复 ensure 幂等；UUID 在线/离线目标；可选 memo 规范化；
- 原子转账、cooldown、陈旧修订拒绝、注入失败无发布、收据隐私、分页有界；
- 离线通知随快照提交、登录聊天回退；货币显示可配置不改数值；
- 无被禁界面（top/bank/他人余额/GUI/权威 C2S）；
- `economyFoundationTest` + `gradlew build` 全绿。

## 5. 交付格式

```text
任务：FR-ECO-001-IMPL
分支：{branch}
提交：{commit}
修改文件：{列表}
测试命令与结果：{命令 + 摘要}
未解决问题：{列表}
是否有越界文件：是/否（说明）
对下一任务接口说明：{EconomyService 契约摘要}
```

## 6. 提交前自检

- [ ] 验收全有测试证据；[ ] 未越界；[ ] build 通过；[ ] 报告含 commit/测试/风险/接口
