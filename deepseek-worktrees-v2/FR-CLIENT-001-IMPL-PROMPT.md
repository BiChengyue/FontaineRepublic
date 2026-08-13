# FR-CLIENT-001-IMPL 派发提示词（deepseek v4 flash 实现子进程）

> 依据 tools/dispatch-PROMPT.template.md。Human 指示暂缓；收到"开始"信号后启用。

## 1. 身份与目标

- 你是 FontaineRepublic（MC 1.20.1 / Forge 47.4.18 / Java 17）实现子进程。
- 任务 ID：FR-CLIENT-001-IMPL；目标：实现可选客户端 UI（首版可视化操作）。
- 只做展示/输入转发；不决定任何状态。

## 2. 工作目录与必读文档

- 工作目录：`D:\MC\FontaineRepublic\deepseek-worktrees-v2\fr-client-001-impl`（从 develop 创建）
- 必读（按顺序）：运行手册 -> CLAUDE.md -> current_status.md ->
  `docs/architecture/fr-client-001-a-client-ui-module-architecture.md`（规范） ->
  架构 v2.7 §5（可选客户端合约）-> FR-NET-001-A（通道/包）-> 已实现服务端模块命令面

## 3. 执行约束（违反即失败）

- 只在工作树内修改；不部署/推送/SSH；不修改设计文档与台账；
- **客户端绝不存储权威状态/做权限决策/篡改请求**；
- 每个变异经同一服务端路径（命令或仅含操作+参数的请求包）；身份从连接上下文取；
- S2C 仅为展示；无客户端平价保持（命令/聊天全功能）；
- `client/` 包隔离（DistExecutor + package）；server 不引用 client；
- 协议不匹配拒绝；不新增依赖；开始时报告分支/HEAD/工作区状态。

## 4. 验收（FR-CLIENT-001-A §5）

- 无客户端平价；客户端缓存非权威；变异重校验；无现场伪造；包隔离；
- 握手不匹配拒绝；S2C 仅展示；游戏内引导存在；
- `gradlew build` 全绿。

## 5. 交付格式

```text
任务：FR-CLIENT-001-IMPL
分支：{branch}
提交：{commit}
修改文件：{列表}
测试命令与结果：{命令 + 摘要}
未解决问题：{列表}
是否有越界文件：是/否（说明）
```

## 6. 提交前自检

- [ ] 无客户端平价保持；[ ] 无权威状态；[ ] 包隔离；[ ] 未越界；[ ] build 通过
