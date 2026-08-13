# FR-CLIENT-001-IMPL-B3b 派发提示词（deepseek v4 flash 实现子进程）

## 1. 身份与目标

- 你是 FontaineRepublic（MC 1.20.1 / Forge 47.4.18 / Java 17）实现子进程。
- 任务 ID：FR-CLIENT-001-IMPL-B3b；目标：法院 + 土地信息视图（协议 v5）。
- 协议 v5；账本追加 ID 7-8；登录快照发送；两个新界面。
- 不做地块枚举/个人权益视图、权威 C2S 包、客户端权威状态。

## 2. 工作目录与必读文档

- 工作目录：`D:\MC\FontaineRepublic\deepseek-worktrees-v2\fr-client-001-impl-b3b`
  （从 develop 创建，分支 codex/fr-client-001-impl-b3b）。
- 必读（按顺序）：运行手册 -> CLAUDE.md -> current_status.md ->
  `docs/architecture/fr-client-001-b3-institution-land-views.md`（§3/§5.1）->
  `docs/development/FR-CLIENT-001-B3b-java-implementation-task.md`（本任务细化）->
  Stage A/B-3a 产物：`common/network/display/`、`client/net/`、`client/gui/`、
  `server/institution/presentation/` ->
  `server/justice/api/JusticeService.java`（cases 投影）->
  `server/land/`（LandService/LandRepository/LandLimits/LandFoundationTestMain
  的无枚举验收）。

## 3. 执行约束（违反即失败）

- 只在工作树内修改；不推送/SSH；不修改设计文档与台账；
- 消息类/handler 在 common 包，无 client import；S2C handler 经
  `DistExecutor.safeRunWhenOn(Dist.CLIENT, ...)` 引用客户端执行器；
- 主入口客户端初始化沿用 FMLClientSetupEvent 模式；
- **土地只读投影必须是单值聚合（`LandSummary publicSummary()`），绝不能返回
  地块列表**——FR-LAND 明示"无批量地块列表"且测试有无枚举验收；
- 登录发送全经 `NetworkSendService.trySendToPlayer`；best-effort；
- 协议 v5、账本 9 条（ID 0-8）、freeze 单向；
- 不新增依赖；开始时报分支/HEAD/工作区状态。

## 4. 验收（FR-CLIENT-001-B3 §5.1）

- 协议 v5 谓词；账本 9 条 ID 0-8、PLAY_TO_CLIENT、有界 codec、freeze 单向；
- 登录后 FR 客户端收到法院案件摘要与土地公共概况；absent 客户端零影响；
- LandService.publicSummary() 通过无枚举验收；
- `/frclient court|land` 界面读非权威缓存；专用服务器不加载 client/ 类；
- `clientStageB3bFoundationTest` + `gradlew build` 全绿。

## 5. 交付格式

```text
任务：FR-CLIENT-001-IMPL-B3b
分支：{branch}
提交：{commit}
修改文件：{列表}
测试命令与结果：{命令 + 摘要}
未解决问题：{列表}
是否有越界文件：是/否（说明）
```

## 6. 提交前自检

- [ ] 协议 v5 + 账本 0-8；[ ] 土地聚合非列表；[ ] 无 client import 泄漏；
  [ ] 登录发送 best-effort；[ ] 未越界；[ ] build 通过
