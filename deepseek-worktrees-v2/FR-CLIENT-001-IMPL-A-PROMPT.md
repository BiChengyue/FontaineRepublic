# FR-CLIENT-001-IMPL-A 派发提示词（deepseek v4 flash 实现子进程）

## 1. 身份与目标

- 你是 FontaineRepublic（MC 1.20.1 / Forge 47.4.18 / Java 17）实现子进程。
- 任务 ID：FR-CLIENT-001-IMPL-A；目标：客户端展示网络面（Stage A）。
- 只做展示网络面；不实现 GUI/HUD/表单、权威 C2S 包、客户端权威状态。

## 2. 工作目录与必读文档

- 工作目录：`D:\MC\FontaineRepublic\deepseek-worktrees-v2\fr-client-001-impl-a`
  （从 develop 创建，分支 codex/fr-client-001-impl-a）。
- 必读（按顺序）：运行手册 -> CLAUDE.md -> current_status.md ->
  `docs/architecture/fr-client-001-a-client-ui-module-architecture.md`（§4/§7）->
  `docs/development/FR-CLIENT-001-A-java-implementation-task.md`（本任务细化）->
  `src/main/java/com/fontainerepublic/common/network/`（NetworkProtocol /
  NetworkProductionMessageTable / NetworkBootstrap / NetworkMessageSpec /
  NetworkPayloadLimits）->
  `src/main/java/com/fontainerepublic/server/network/`（NetworkSendService /
  ServerNetworkDispatcher / NetworkRuntimeModule）->
  `src/main/java/com/fontainerepublic/server/economy/`（EconomyModule /
  DefaultEconomyService）。

## 3. 执行约束（违反即失败）

- 只在工作树内修改；不推送/SSH；不修改设计文档与台账；
- 消息类/handler 在 common 包，不得 import `net.minecraft.client.*` 或
  `com.fontainerepublic.client.*`；S2C handler 用
  `DistExecutor.safeRunWhenOn(Dist.CLIENT, () -> () -> ClientNetworkExecutor.accept(...))`；
- 专用服务器不得加载 client/ 类（safe 变体 supplier 不求值）；
- 发送全经 `NetworkSendService.trySendToPlayer`，absent/断连为非异常结果，
  业务照常；客户端缓存仅内存非权威；
- 协议 v2、账本 ID 0-2、空表约束替换为计数校验；
- 不新增依赖；开始时报分支/HEAD/工作区状态。

## 4. 验收（FR-CLIENT-001-A v1.1 §5 网络面部分）

- 协议 v2 谓词矩阵；absent 客户端放行、v1 拒绝；
- 消息账本 0-2 有界 codec、方向 PLAY_TO_CLIENT、freeze 单向；
- 侧隔离源码扫描通过；client/net 骨架编译且仅经 DistExecutor 引用；
- economy 声明 network 依赖；登录余额/待读通知、转账成功双向通知经 presence
  过滤发送；
- `clientPresentationFoundationTest` + `gradlew build` 全绿。

## 5. 交付格式

```text
任务：FR-CLIENT-001-IMPL-A
分支：{branch}
提交：{commit}
修改文件：{列表}
测试命令与结果：{命令 + 摘要}
未解决问题：{列表}
是否有越界文件：是/否（说明）
```

## 6. 提交前自检

- [ ] 无 GUI/C2S 包/权威状态；[ ] 无 client import 泄漏到 common/server；
  [ ] 协议 v2 + 账本 0-2；[ ] absent 客户端零影响；[ ] 未越界；[ ] build 通过
