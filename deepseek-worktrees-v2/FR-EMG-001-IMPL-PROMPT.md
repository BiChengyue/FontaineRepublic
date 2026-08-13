# FR-EMG-001-IMPL 派发提示词（deepseek v4 flash 实现子进程）

> 依据 tools/dispatch-PROMPT.template.md。Human 已指示继续服务端暂停任务。

## 1. 身份与目标

- 你是 FontaineRepublic（MC 1.20.1 / Forge 47.4.18 / Java 17）实现子进程。
- 任务 ID：FR-EMG-001-IMPL；目标：实现共享紧急权限基础设施。
- 只实现共享基础设施；**不实现业务动作**（economy.issue/reclaim 属 Economy 提供方）。

## 2. 工作目录与必读文档

- 工作目录：`D:\MC\FontaineRepublic\deepseek-worktrees-v2\fr-emg-001-impl`（从 develop 创建）
- 必读（按顺序）：运行手册 -> CLAUDE.md -> current_status.md ->
  `docs/architecture/fr-emg-001-a-hydro-archon-emergency-authority.md`（规范） ->
  `docs/development/FR-EMG-001-java-implementation-task.md`（本任务细化） ->
  现有 `server/registry/service/BootstrapConsoleClassifier.java`（控制台分类可复用） ->
  FR-CMD-001 admin 接线

## 3. 执行约束（违反即失败）

- 只在工作树内修改；不部署/推送/SSH；不修改设计文档与台账；
- 演员验证：配置水神 UUID + SERVER_CONSOLE（真实本地控制台）；OP/RCON/命令方块/
  函数/玩家一律拒绝；
- preview/confirm：30s 单次 token；ISSUED->CLAIMED->CONSUMED；参数/修订篡改拒绝；
- 尝试/配置日志 append-only + 摘要链 + 段式；对账 watermark；
- `/fr admin emergency` 挂于保留 admin 下，执行时解析当前 ACTIVE 服务；
- 权威配置：首次控制台 bootstrap、staged 变更、漂移失败关闭、控制台恢复；
- **不实现业务动作目录**；不新增依赖；开始时报告分支/HEAD/工作区状态。

## 4. 验收

- 演员分类矩阵；token 生命周期；日志链/段/篡改；对账；配置启动/漂移；
- 命令适配与越权拒绝；注入失败无发布；重启恢复；严格编解码；
- `emergencyFoundationTest` + `gradlew build` 全绿。

## 5. 交付格式

```text
任务：FR-EMG-001-IMPL
分支：{branch}
提交：{commit}
修改文件：{列表}
测试命令与结果：{命令 + 摘要}
未解决问题：{列表}
是否有越界文件：是/否（说明）
```

## 6. 提交前自检

- [ ] 无业务动作实现；[ ] 演员拒绝矩阵完整；[ ] token 单次；[ ] 未越界；[ ] build 通过
