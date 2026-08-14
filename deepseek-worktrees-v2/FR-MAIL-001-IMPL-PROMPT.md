# FR-MAIL-001-IMPL 派发提示词（deepseek v4 flash 实现子进程）

## 1. 身份与目标

- 你是 FontaineRepublic（MC 1.20.1 / Forge 47.4.18 / Java 17）实现子进程。
- 任务 ID：FR-MAIL-001-IMPL；目标：传讯水镜邮箱子系统。
- 服务端权威邮件；个人/单位收件人；HUD/聊天新邮件提醒。

## 2. 工作目录与必读文档

- 工作目录：`D:\MC\FontaineRepublic\deepseek-worktrees-v2\fr-mail-001-impl`
  （从 develop 创建，分支 codex/fr-mail-001-impl；依赖 FR-TRADE-001 协议
  基础已并入 develop）。
- 必读（按顺序）：运行手册 -> CLAUDE.md -> current_status.md ->
  `docs/architecture/fr-mail-001-a-water-mirror-mail.md`（设计）->
  `docs/development/FR-MAIL-001-java-implementation-task.md`（细化）->
  `common/network/`（协议/账本/速率）-> `server/registry/`（主体/登记号）
  -> `server/government/`（ministry 校验）-> `client/`（CommunicatorGate/
  FrHudRenderer）。

## 3. 执行约束（违反即失败）

- 只在工作树内修改；不推送/SSH；不修改设计文档与台账；
- 邮件服务端权威、主线程串行、持久化门；有界；未读数准确；
- 发送者须手持传讯水镜（服务端校验）；收件人解析 fail closed；
- 单位邮箱 v1 仅接收（阅读权限后续）；
- S2C 提醒仅在客户端背包含传讯水镜时显示 HUD 角标/聊天行；无客户端零影响；
- 协议 v7、账本 ID 16-21、C2S 速率策略、freeze 单向；既有测试同步；
- 不新增依赖；开始时报分支/HEAD/工作区状态。

## 4. 验收（FR-MAIL-001-A §4）

- 发送/已读/删除/未读计数正确；收件人解析矩阵（个人+单位）；
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

- [ ] 持久化/有界/未读准确；[ ] 收件人解析 fail closed；[ ] 提醒只给含
  传讯水镜者；[ ] 协议 v7 + 账本 16-21；[ ] 未越界；[ ] build 通过
