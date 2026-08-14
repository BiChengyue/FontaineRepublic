# FR-MAIL-001-IMPL 派发提示词（deepseek v4 flash 实现子进程）

## 1. 身份与目标

- 你是 FontaineRepublic（MC 1.20.1 / Forge 47.4.18 / Java 17）实现子进程。
- 任务 ID：FR-MAIL-001-IMPL；目标：传讯水镜邮箱子系统（v1.1）。
- 服务端权威邮件；个人/机构互寄；附件（钱+物品）；邮费/附件费；
  机构一键向全体公民发公告；HUD/聊天新邮件提醒。

## 2. 工作目录与必读文档

- 工作目录：`D:\MC\FontaineRepublic\deepseek-worktrees-v2\fr-mail-001-impl`
  （从 develop 创建，分支 codex/fr-mail-001-impl；依赖 FR-TRADE-001 协议
  基础已并入 develop）。
- 必读（按顺序）：运行手册 -> CLAUDE.md -> current_status.md ->
  `docs/architecture/fr-mail-001-a-water-mirror-mail.md`（设计 v1.1，含 §6）->
  `docs/development/FR-MAIL-001-java-implementation-task.md`（细化）->
  `common/network/`（协议/账本/速率）-> `server/registry/`（主体/登记号）
  -> `server/government/`（ministry 校验/职位持有人）-> `server/citizen/`
  （公民列表，供广播）-> `server/economy/`（邮费/附件金额处理）
  -> `client/`（CommunicatorGate/FrHudRenderer）。

## 3. 执行约束（违反即失败）

- 只在工作树内修改；不推送/SSH；不修改设计文档与台账；
- 邮件服务端权威、主线程串行、持久化门；有界；未读数准确；
- 发送者须手持传讯水镜（服务端校验）；收件人解析 fail closed；
- **机构邮箱**：部门由职位持有人；议会/法院/央行由邮箱管理员
  （mailboxManager 配置，默认水神/控制台）；机构互寄；越权 fail closed；
- **邮费**：固定 10/封 + 附件费 100/个（可配）；发送时从发件人扣，余额不足
  失败；机构发件免费；
- **附件**：金额附件发送时不扣款，领取时原子复核到账；物品附件发送时移入
  邮件附件槽，领取时移入背包（背包满留待领）；已读防重复领取；
- **广播**：仅授权机构身份；单份存储 + 按人已读 + 冷却；
- S2C 提醒仅在客户端背包含传讯水镜时显示 HUD 角标/聊天行；无客户端零影响；
- 协议 v7、账本 ID 16-21（含 MailBroadcastPacket）、C2S 速率策略、freeze
  单向；既有测试同步；
- 不新增依赖；开始时报分支/HEAD/工作区状态。

## 4. 验收（FR-MAIL-001-A §4/§6）

- 发送/已读/删除/未读计数正确；收件人解析矩阵（个人+机构）；
- 机构互寄/管理员授权/广播（水神/控制台/负责人/普通玩家拒绝/冷却/离线可见）；
- 附件领取原子性/防复制；邮费与附件费计算与扣款（含机构免费）；
- 有界/持久化/重启恢复；越权/速率拒绝；
- 新邮件 HUD/聊天提醒（背包含传讯水镜时）；
- `mailFoundationTest` + `gradlew build` 全绿；
- 专用服务器不加载 client/ 类。

## 5. 交付格式

```text
任务：FR-MAIL-001-IMPL
分支：{branch}
提交：{commit}
修改文件：{列表}
测试命令与结果：{命令 + 摘要}
未解决问题：{列表}
是否有越界文件：是/否（说明）
```

## 6. 提交前自检

- [ ] 持久化/有界/未读准确；[ ] 收件人解析 fail closed；[ ] 机构互寄/广播
  授权矩阵；[ ] 附件防复制；[ ] 邮费/附件费正确；[ ] 协议 v7 + 账本 16-21；
  [ ] 未越界；[ ] build 通过
