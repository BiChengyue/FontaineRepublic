# FR-ITEM-001-IMPL 派发提示词（deepseek v4 flash 实现子进程）

## 1. 身份与目标

- 你是 FontaineRepublic（MC 1.20.1 / Forge 47.4.18 / Java 17）实现子进程。
- 任务 ID：FR-ITEM-001-IMPL；目标：新增"便携通讯器"物品 + 客户端 UI 门禁 +
  右键玩家转账 / 右键方块土地视图。
- 物品只是 UI 入口，无权威副作用；购地/地块申请不在本任务。

## 2. 工作目录与必读文档

- 工作目录：`D:\MC\FontaineRepublic\deepseek-worktrees-v2\fr-item-001-impl`
  （从 develop 创建，分支 codex/fr-item-001-impl）。
- 必读（按顺序）：运行手册 -> CLAUDE.md -> current_status.md ->
  `docs/architecture/fr-item-001-a-communicator.md`（设计）->
  `docs/development/FR-ITEM-001-java-implementation-task.md`（本任务细化）->
  `src/main/java/com/fontainerepublic/FontaineRepublic.java`（构造器/事件注册）->
  `src/main/java/com/fontainerepublic/client/`（ClientManager/FrMainScreen/
  gui/money/MoneyScreen/gui/land/LandScreen）。

## 3. 执行约束（违反即失败）

- 只在工作树内修改；不推送/SSH；不修改设计文档与台账；
- 物品注册在 common（双方）；客户端交互代码在 `client/`，专用服务器不加载；
- 右键交互只打开界面/预填表单，不触发服务端副作用；动作仍走命令；
- UI 门禁是 UX 检查，不改变任何服务端命令；
- 临时纹理用 `minecraft:item/clock` 占位；
- 不新增依赖；开始时报分支/HEAD/工作区状态。

## 4. 验收（FR-ITEM-001-A §4）

- `/give @s fontainerepublic:communicator` 可用；物品模型/lang 存在；
- 未手持时 /frclient 提示且不开屏；手持时正常；
- 手持右键玩家 → 转账表单目标预填；右键方块 → 土地视图；
- `clientItemFoundationTest` + `gradlew build` 全绿；
- 专用服务器不加载 client/ 类（源码扫描）。

## 5. 交付格式

```text
任务：FR-ITEM-001-IMPL
分支：{branch}
提交：{commit}
修改文件：{列表}
测试命令与结果：{命令 + 摘要}
未解决问题：{列表}
是否有越界文件：是/否（说明）
```

## 6. 提交前自检

- [ ] 物品注册/模型/lang；[ ] UI 门禁纯函数；[ ] 右键仅开界面；
  [ ] 无 client import 泄漏；[ ] 未越界；[ ] build 通过
