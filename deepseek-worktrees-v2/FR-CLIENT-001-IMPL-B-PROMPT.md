# FR-CLIENT-001-IMPL-B 派发提示词（deepseek v4 flash 实现子进程）

## 1. 身份与目标

- 你是 FontaineRepublic（MC 1.20.1 / Forge 47.4.18 / Java 17）实现子进程。
- 任务 ID：FR-CLIENT-001-IMPL-B；目标：客户端 GUI/HUD/表单（Stage B）。
- 仅做展示/输入转发；客户端不决定任何状态；表单走 `/fr` 命令路径。

## 2. 工作目录与必读文档

- 工作目录：`D:\MC\FontaineRepublic\deepseek-worktrees-v2\fr-client-001-impl-b`
  （从 develop 创建，分支 codex/fr-client-001-impl-b；Stage A 已并入）。
- 必读（按顺序）：运行手册 -> CLAUDE.md -> current_status.md ->
  `docs/architecture/fr-client-001-a-client-ui-module-architecture.md`（§3/§4/§7）->
  `docs/development/FR-CLIENT-001-B-java-implementation-task.md`（本任务细化）->
  Stage A 产物：`common/network/display/`、`client/net/`（ClientNetworkExecutor /
  展示缓存）、`server/network/`（发送接线）。

## 3. 执行约束（违反即失败）

- 只在工作树内修改；不推送/SSH；不修改设计文档与台账；
- 所有界面代码在 `com.fontainerepublic.client.*`；common/server 不得引用；
  专用服务器不加载 client/ 类；
- 表单提交经 `Minecraft.player.connection.sendCommand(...)` 或等价命令路径；
  服务端校验不可绕过；
- 展示缓存仅内存、非权威、登出/断连清理；不持久化；
- 不新增依赖；开始时报分支/HEAD/工作区状态。

## 4. 验收（FR-CLIENT-001-A v1.1 §5）

- balance card/HUD、transfer 表单、citizen 卡、通知 HUD、历史视图、引导页
  可打开且数据来自展示缓存；
- 表单提交失败（服务端拒绝）时界面给出有界反馈，不乐观落账；
- 无 FR 客户端时功能不受影响；
- `gradlew build` 全绿。

## 5. 交付格式

```text
任务：FR-CLIENT-001-IMPL-B
分支：{branch}
提交：{commit}
修改文件：{列表}
测试命令与结果：{命令 + 摘要}
未解决问题：{列表}
是否有越界文件：是/否（说明）
```

## 6. 提交前自检

- [ ] 无权威状态/存储；[ ] 表单走命令路径；[ ] client 包隔离；
  [ ] 无新依赖；[ ] 未越界；[ ] build 通过
