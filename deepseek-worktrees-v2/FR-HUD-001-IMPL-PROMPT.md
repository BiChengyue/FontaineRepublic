# FR-HUD-001-IMPL 派发提示词（deepseek v4 flash 实现子进程）

## 1. 身份与目标

- 你是 FontaineRepublic（MC 1.20.1 / Forge 47.4.18 / Java 17）实现子进程。
- 任务 ID：FR-HUD-001-IMPL；目标：传讯水镜常驻信息 HUD。
- 背包持有传讯水镜时渲染；可拖动、位置持久化；新邮件未读角标。

## 2. 工作目录与必读文档

- 工作目录：`D:\MC\FontaineRepublic\deepseek-worktrees-v2\fr-hud-001-impl`
  （从 develop 创建，分支 codex/fr-hud-001-impl；依赖 FR-MAIL-001 已并入）。
- 必读（按顺序）：运行手册 -> CLAUDE.md -> current_status.md ->
  `docs/architecture/fr-hud-001-a-water-mirror-hud.md`（设计）->
  `client/`（FrHudRenderer/CommunicatorGate/ClientPresentationCache/
  ClientMailCache（邮件模块））。

## 3. 执行约束（违反即失败）

- 只在工作树内修改；不推送/SSH；不修改设计文档与台账；
- 全部代码在 `client/`，专用服务器不加载；
- 只显示本人数据（名称/身份/登记号/余额/未读邮件）；无权威；
- 背包含传讯水镜才渲染（背包判定纯函数）；无客户端/无水镜零影响；
- 位置持久化到客户端本地配置（非权威）；`/frclient hud reset` 可重置；
- 不新增依赖；开始时报分支/HEAD/工作区状态。

## 4. 验收（FR-HUD-001-A §5）

- 内容投影（含空态/占位）；背包含水镜渲染、未持有不渲染；
- 可拖动 + 位置持久化 + 重置 + 折叠；
- 未读邮件角标随 S2C 未读数更新；
- `clientHudFoundationTest` + `gradlew build` 全绿；
- 专用服务器不加载 client/ 类。

## 5. 交付格式

```text
任务：FR-HUD-001-IMPL
分支：{branch}
提交：{commit}
修改文件：{列表}
测试命令与结果：{命令 + 摘要}
未解决问题：{列表}
是否有越界文件：是/否（说明）
```

## 6. 提交前自检

- [ ] 只显示本人数据；[ ] 背包含水镜才渲染；[ ] 位置持久化/重置；
  [ ] 未读角标正确；[ ] 未越界；[ ] build 通过
