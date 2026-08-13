# FR-CLIENT-001-IMPL-B3a 派发提示词（deepseek v4 flash 实现子进程）

## 1. 身份与目标

- 你是 FontaineRepublic（MC 1.20.1 / Forge 47.4.18 / Java 17）实现子进程。
- 任务 ID：FR-CLIENT-001-IMPL-B3a；目标：政府 + 议会信息视图（协议 v4）。
- 协议 v4；账本追加 ID 5-6；登录快照发送；两个新界面。
- 不做法院/土地视图、权威 C2S 包、模块定义改动、客户端权威状态。

## 2. 工作目录与必读文档

- 工作目录：`D:\MC\FontaineRepublic\deepseek-worktrees-v2\fr-client-001-impl-b3a`
  （从 develop 创建，分支 codex/fr-client-001-impl-b3a）。
- 必读（按顺序）：运行手册 -> CLAUDE.md -> current_status.md ->
  `docs/architecture/fr-client-001-b3-institution-land-views.md`（§3/§4）->
  `docs/development/FR-CLIENT-001-B3a-java-implementation-task.md`（本任务细化）->
  Stage A/B-2 产物：`common/network/display/`、`client/net/`、`client/gui/`、
  `server/economy/presentation/`、`server/citizen/presentation/` ->
  `server/government/api/GovernmentService.java`（ministries/positionsByMinistry）->
  `server/parliament/api/ParliamentService.java`（proposals）。

## 3. 执行约束（违反即失败）

- 只在工作树内修改；不推送/SSH；不修改设计文档与台账；
- 消息类/handler 在 common 包，无 client import；S2C handler 经
  `DistExecutor.safeRunWhenOn(Dist.CLIENT, ...)` 引用客户端执行器；
- 主入口客户端初始化沿用 FMLClientSetupEvent 模式（勿用 safeRunWhenOn 引用
  mod 自有类）；
- 登录发送全经 `NetworkSendService.trySendToPlayer`；absent/离线/异常
  best-effort；不改 government/parliament 模块定义；
- 协议 v4、账本 7 条（ID 0-6）、freeze 单向；
- 不新增依赖；开始时报分支/HEAD/工作区状态。

## 4. 验收（FR-CLIENT-001-B3 §6）

- 协议 v4 谓词；账本 7 条 ID 0-6、PLAY_TO_CLIENT、有界 codec、freeze 单向；
- 登录后 FR 客户端收到政府/议会公开摘要；absent 客户端零影响；
- `/frclient government|parliament` 界面读非权威缓存；专用服务器不加载
  client/ 类；
- `clientStageB3aFoundationTest` + `gradlew build` 全绿。

## 5. 交付格式

```text
任务：FR-CLIENT-001-IMPL-B3a
分支：{branch}
提交：{commit}
修改文件：{列表}
测试命令与结果：{命令 + 摘要}
未解决问题：{列表}
是否有越界文件：是/否（说明）
```

## 6. 提交前自检

- [ ] 协议 v4 + 账本 0-6；[ ] 无 client import 泄漏；[ ] 登录发送 best-effort；
  [ ] 界面只读缓存；[ ] 未越界；[ ] build 通过
