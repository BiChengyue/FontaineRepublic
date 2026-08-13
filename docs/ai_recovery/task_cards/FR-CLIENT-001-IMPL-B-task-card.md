# Task Card — FR-CLIENT-001-IMPL-B（客户端 GUI/HUD）

> Status: Authorized（Stage A 已并入 develop）
> Task Type: 客户端阶段 B-1 — 可视化界面（balance HUD/卡、转账表单、通知、
> 流水视图、引导屏；公民卡/完整历史页留待 Stage B-2）
> Design Reference: docs/architecture/fr-client-001-a-client-ui-module-architecture.md v1.1
> Dependency: FR-CLIENT-001-IMPL-A（S2C 展示网络面）、FR-CMD-001（命令路径）

## Agent Identity

- **Agent:** Codex（Designer）
- **Task ID:** FR-CLIENT-001-IMPL-B
- **Task Name:** 客户端 GUI/HUD/表单（Stage B）

## Scope

**Allowed:** balance card/HUD、transfer 表单（/fr money pay 命令路径提交）、
通知 HUD、实时交易流水视图（读 Stage A 缓存）、游戏内引导页；
客户端命令/按键打开界面。

**Forbidden:** 客户端权威状态/存储/权限判定；权威 C2S 包；新依赖；
紧急动作客户端界面；新增 S2C 消息/服务端业务改动（Stage B-2 范围）；
公民卡/完整历史页。

## Acceptance

- 界面仅读非权威展示缓存；表单经命令路径提交并由服务端校验；
- 无 FR 客户端时全部功能可用（chat 兜底不变）；
- 专用服务器不加载 client/ 类（源码扫描）；
- `gradlew build` 全绿；`clientGuiFoundationTest`（如适用）通过。
