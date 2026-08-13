# FR-EMG-ECO-001-IMPL 派发提示词（deepseek v4 flash 实现子进程）

> 依据 tools/dispatch-PROMPT.template.md。

## 1. 身份与目标

- 你是 FontaineRepublic（MC 1.20.1 / Forge 47.4.18 / Java 17）实现子进程。
- 任务 ID：FR-EMG-ECO-001-IMPL；目标：实现 economy.issue / economy.reclaim 紧急
  动作提供方并注册进 FR-EMG。
- 只实现本任务；不实现普通官方发钞/FR-EMG 复刻/GUI。

## 2. 工作目录与必读文档

- 工作目录：`D:\MC\FontaineRepublic\deepseek-worktrees-v2\fr-emg-eco-001-impl`（从 develop 创建）
- 必读（按顺序）：运行手册 -> CLAUDE.md -> current_status.md ->
  `docs/architecture/fr-eco-001-c-foundation-scope-emergency-authority.md`（§9-12 目录） ->
  `docs/architecture/fr-emg-001-a-hydro-archon-emergency-authority.md`（共享基础设施契约） ->
  `docs/development/FR-EMG-ECO-001-java-implementation-task.md`（本任务细化） ->
  `server/emergency/api/`（Provider/Descriptor/Envelope/Plan/ReceiptProvider） ->
  `server/economy/`（EconomyService/Repository/供给守恒）

## 3. 执行约束（违反即失败）

- 只在工作树内修改；不部署/推送/SSH；不修改设计文档与台账；
- 提供方只做业务 prepare/mutation；**actor/console/token/日志归 FR-EMG**（不复刻）；
- prepare 无副作用；mutation 经确认信封 + 单快照 + 供给守恒 + 成功收据 + 离线通知；
- issue 可懒开户、reclaim 需余额充足；注入失败无发布；
- 注册到 FR-EMG EmergencyActionRegistry（冻结前）；收据提供方接对账；
- 不新增依赖；开始时报告分支/HEAD/工作区状态。

## 4. 验收（FR-ECO-001-C §15.2）

- 描述符/类别/参数 schema；prepare（溢出/余额不足/懒开户）；mutation 单快照+守恒；
- 信封校验与篡改拒绝；注入失败无发布；收据/通知；重启恢复；
- 无 FR-EMG 复刻守卫；`economyEmergencyFoundationTest` + `gradlew build` 全绿。

## 5. 交付格式

```text
任务：FR-EMG-ECO-001-IMPL
分支：{branch}
提交：{commit}
修改文件：{列表}
测试命令与结果：{命令 + 摘要}
未解决问题：{列表}
是否有越界文件：是/否（说明）
```

## 6. 提交前自检

- [ ] 无 FR-EMG 复刻；[ ] 供给守恒；[ ] 收据+通知；[ ] 未越界；[ ] build 通过
