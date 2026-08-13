# FR-EMG-CMD-001-IMPL 派发提示词（deepseek v4 flash 实现子进程）

## 1. 身份与目标

- 你是 FontaineRepublic（MC 1.20.1 / Forge 47.4.18 / Java 17）实现子进程。
- 任务 ID：FR-EMG-CMD-001-IMPL；目标：实现 `/fr admin emergency` 命令适配器并挂接。
- 只实现本任务；不实现业务动作、token/日志/权威配置、GUI、包。

## 2. 工作目录与必读文档

- 工作目录：`D:\MC\FontaineRepublic\deepseek-worktrees-v2\fr-emg-cmd-001-impl`
  （从 develop 创建，分支 codex/fr-emg-cmd-001-impl）。
- 必读（按顺序）：运行手册 -> CLAUDE.md -> current_status.md ->
  `docs/architecture/fr-emg-001-a-hydro-archon-emergency-authority.md`（§13/§14）->
  `docs/development/FR-EMG-CMD-001-java-implementation-task.md`（本任务细化）->
  `src/main/java/com/fontainerepublic/server/command/`（FrameworkAdminCommand /
  CommandRuntimeResolver / CommandFeedback）->
  `src/main/java/com/fontainerepublic/server/emergency/api/EmergencyService.java`
  （preview/confirm/inspect/status）。

## 3. 执行约束（违反即失败）

- 只在工作树内修改；不推送/SSH；不修改设计文档与台账；
- 命令适配只做解析/来源映射/运行时解析与反馈；actor/console/token/日志归
  FR-EMG 服务（不复制）；
- 每次执行解析当前 ACTIVE EmergencyService，不捕获；
- token 只展示一次、绝不落日志；输出有界；非法输入 fail closed；
- 不新增依赖；开始时报分支/HEAD/工作区状态。

## 4. 验收（FR-EMG-001-A §13/§14）

- `preview/confirm/inspect/status` 命令树可构建、字面量与参数正确；
- 来源映射：LOCAL_CONSOLE→SERVER_CONSOLE；PLAYER→HYDRO_ARCHON；
  RCON/命令方块/函数/集成主机到达回调也被服务拒绝；
- 运行时解析不捕获；无服务时 fail closed；
- `emergencyCommandFoundationTest` + `gradlew build` 全绿。

## 5. 交付格式

```text
任务：FR-EMG-CMD-001-IMPL
分支：{branch}
提交：{commit}
修改文件：{列表}
测试命令与结果：{命令 + 摘要}
未解决问题：{列表}
是否有越界文件：是/否（说明）
```

## 6. 提交前自检

- [ ] 无 FR-EMG/Economy 契约修改；[ ] 无新依赖；[ ] token 不落日志；
  [ ] 未越界；[ ] build 通过
