# FR-CLIENT-001-IMPL-B2 派发提示词（deepseek v4 flash 实现子进程）

## 1. 身份与目标

- 你是 FontaineRepublic（MC 1.20.1 / Forge 47.4.18 / Java 17）实现子进程。
- 任务 ID：FR-CLIENT-001-IMPL-B2；目标：公民卡 + 交易历史视图（Stage B-2）。
- 协议 v3；账本追加 ID 3-4；公民/economy 发送接线；两个新界面。
- 不做机构/土地视图、权威 C2S 包、客户端权威状态。

## 2. 工作目录与必读文档

- 工作目录：`D:\MC\FontaineRepublic\deepseek-worktrees-v2\fr-client-001-impl-b2`
  （从 develop 创建，分支 codex/fr-client-001-impl-b2）。
- 必读（按顺序）：运行手册 -> CLAUDE.md -> current_status.md ->
  `docs/architecture/fr-client-001-a-client-ui-module-architecture.md`（§4/§7）->
  `docs/development/FR-CLIENT-001-B2-java-implementation-task.md`（本任务细化）->
  Stage A/B-1 产物：`common/network/display/`、`client/net/`、`client/gui/`、
  `server/economy/presentation/` ->
  `server/citizen/`（CitizenModule/CitizenService/CitizenRecord）与
  `server/registry/`（SubjectRecord/RegistryNumber）。

## 3. 执行约束（违反即失败）

- 只在工作树内修改；不推送/SSH；不修改设计文档与台账；
- 消息类/handler 在 common 包，无 client import；S2C handler 经
  `DistExecutor.safeRunWhenOn(Dist.CLIENT, ...)` 引用客户端执行器；
- 主入口客户端初始化沿用 FMLClientSetupEvent 模式（勿用 safeRunWhenOn 引用
  mod 自有类——Forge safe-referent 校验会拒绝）；
- 发送全经 `NetworkSendService.trySendToPlayer`；absent/离线/异常 best-effort，
  业务结果不变；
- 协议 v3、账本 5 条（ID 0-4）、freeze 单向；
- 不新增依赖；开始时报分支/HEAD/工作区状态。

## 4. 验收（FR-CLIENT-001-A v1.1 §5）

- 协议 v3 谓词；账本 5 条 ID 0-4、PLAY_TO_CLIENT、有界 codec、freeze 单向；
- 登录后 FR 客户端收到公民卡与历史第一页；absent 客户端零影响；
- `/frclient citizen|history` 界面读非权威缓存；专用服务器不加载 client/ 类；
- `clientStageB2FoundationTest` + `gradlew build` 全绿。

## 5. 交付格式

```text
任务：FR-CLIENT-001-IMPL-B2
分支：{branch}
提交：{commit}
修改文件：{列表}
测试命令与结果：{命令 + 摘要}
未解决问题：{列表}
是否有越界文件：是/否（说明）
```

## 6. 提交前自检

- [ ] 协议 v3 + 账本 0-4；[ ] 无 client import 泄漏；[ ] 发送 best-effort；
  [ ] 界面只读缓存；[ ] 未越界；[ ] build 通过
