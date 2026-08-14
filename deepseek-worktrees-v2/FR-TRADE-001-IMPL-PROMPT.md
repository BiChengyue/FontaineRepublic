# FR-TRADE-001-IMPL 派发提示词（deepseek v4 flash 实现子进程）

## 1. 身份与目标

- 你是 FontaineRepublic（MC 1.20.1 / Forge 47.4.18 / Java 17）实现子进程。
- 任务 ID：FR-TRADE-001-IMPL；目标：手持通讯器玩家间安全实时交易。
- 服务端权威；钱/物品托管；双方同意 5s 后原子执行；取消/断线全额退还。

## 2. 工作目录与必读文档

- 工作目录：`D:\MC\FontaineRepublic\deepseek-worktrees-v2\fr-trade-001-impl`
  （从 develop 创建，分支 codex/fr-trade-001-impl）。
- 必读（按顺序）：运行手册 -> CLAUDE.md -> current_status.md ->
  `docs/architecture/fr-trade-001-a-communicator-trade.md`（设计）->
  `docs/development/FR-TRADE-001-java-implementation-task.md`（细化）->
  `common/network/`（协议/账本/速率策略）-> `server/economy/`（账户/托管）
  -> `server/command/`（运行时解析）-> `client/`（CommunicatorInteraction/
  TradeScreen 需自建）。

## 3. 执行约束（违反即失败）

- 只在工作树内修改；不推送/SSH；不修改设计文档与台账；
- 交易状态/金额/物品全部服务端权威、主线程串行；客户端只展示与提交意向；
- 金额托管扣减、取消退还、执行互换，杜绝双花；物品移入/退还/互换防复制；
- 断线/登出/关服 → 取消 + 全额退还；
- 双方须手持通讯器（发起与执行时核验）；
- C2S 全部速率策略；金额/槽位有界；越权拒绝；
- 协议 v6、账本 ID 9-15、freeze 单向；既有测试同步；
- 不新增依赖；开始时报分支/HEAD/工作区状态。

## 4. 验收（FR-TRADE-001-A §6）

- 状态机全转移 + 5s 倒计时回退；托管守恒；物品无复制；
- 越权/速率/越界拒绝；断线退还；
- `tradeFoundationTest` + `gradlew build` 全绿；
- 专用服务器不加载 client/ 类。

## 5. 交付格式

```text
任务：FR-TRADE-001-IMPL
分支：{branch}
提交：{commit}
修改文件：{列表}
测试命令与结果：{命令 + 摘要}
未解决问题：{列表}
是否有越界文件：是/否（说明）
```

## 6. 提交前自检

- [ ] 状态机/托管/执行安全；[ ] 协议 v6 + 账本 9-15；[ ] 断线退还；
  [ ] 无 client import 泄漏；[ ] 未越界；[ ] build 通过
