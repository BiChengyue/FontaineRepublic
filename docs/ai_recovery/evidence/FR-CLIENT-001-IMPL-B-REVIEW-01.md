# Audit Report — FR-CLIENT-001-IMPL-B-REVIEW-01

> 独立代码与验证审查（Level 1-2）；本报告只做审阅，不代替 Human Approval。

## Agent Identity

- **Agent:** Codex
- **Role:** Reviewer（独立复核 + 独立复跑；实现主体由派发子进程完成并提交）
- **Task ID:** FR-CLIENT-001-IMPL-B-REVIEW-01
- **Task Name:** 客户端 GUI/HUD/表单（Stage B-1）

## Project Context Snapshot

- **Branch:** codex/fr-client-001-impl-b（实现提交 `ba50aa0`）
- **Baseline:** develop @ 3dbb33d

## Audit Report

**Files reviewed:**

- `ClientManager`：`/frclient` 客户端命令（main/money/notifications/guide）+
  HUD 渲染监听；仅经 `DistExecutor.safeRunWhenOn(Dist.CLIENT, ...)` 从主入口
  引用（专用服务器不加载 client/ 类）；
- `MoneyScreen` + `TransferFormComposer`：余额卡/转账表单/实时流水视图；表单
  经 `TransferFormComposer.compose` 组装 `fr money pay <目标> <金额> [备注]`
  并经 `player.connection.sendCommand` 提交（纯输入转发，无乐观落账）；目标/
  金额/备注均有客户端展示边界，服务端重新解析与校验；
- `ClientViewProjection`：纯展示投影（余额行/流水行/通知行/千分位格式化），
  只读非权威缓存；
- `FrHudRenderer` / `FrMainScreen` / `GuideScreen` / `NotificationScreen` /
  `FrGuiUtil`：HUD 与界面均为薄视图；
- `FontaineRepublic` 构造器：`DistExecutor.safeRunWhenOn(Dist.CLIENT, ...)`
  侧隔离接线；
- `build.gradle`：`clientGuiFoundationTest` 挂入 `check`；
- `ClientGuiFoundationTestMain`：表单组装边界、投影渲染、侧隔离源码扫描。

**Evidence sources:**

1. 上述源码审阅。
2. 独立复跑：`gradlew clientGuiFoundationTest` ->
   `[FR-CLIENT-001-B] Client GUI/HUD foundation validation passed`；
   完整 `gradlew build` BUILD SUCCESSFUL（30 actionable tasks）。

## Acceptance Matrix（FR-CLIENT-001-A v1.1 §5 GUI 部分）

| # | 验收 | 判定 |
|---|---|---|
| 1 | balance card/HUD、转账表单、通知视图、流水视图、引导屏可打开且读展示缓存 | PASS（源码核验；视觉真机待 Human）|
| 2 | 表单提交失败仅展示服务端反馈，无乐观落账 | PASS（sendCommand 路径 + 无本地落账）|
| 3 | 无 FR 客户端时服务器功能不受影响 | PASS（纯客户端模组，无服务端业务改动）|
| 4 | 专用服务器不加载 client/ 类 | PASS（safeRunWhenOn 侧隔离 + 扫描）|
| 5 | 无新依赖；无新 S2C 消息/服务端业务改动 | PASS |

## Findings

### F-001（Suggestion, Open）— 真机视觉核验
- 界面布局、HUD 渲染、表单交互需 Human 连服目视核验（Level 3 清单 §4.7）；
  本阶段仅有构建 + 纯逻辑测试。

### F-002（Critical, Fixed）— 主入口 safeRunWhenOn 导致专用服务器加载失败
- 冒烟发现：`DistExecutor.safeRunWhenOn(Dist.CLIENT, () -> () ->
  ClientManager.init())` 在专用服务器模组构造期触发 Forge safe-referent
  校验（"Unsafe Referent usage"，safe 变体仅接受 Minecraft/client 包），
  模组加载失败。
- 修复：主入口改为注册 `FMLClientSetupEvent` 监听器（仅物理客户端触发），
  监听器方法体惰性引用 ClientManager；专用服务器不加载 client/ 类。
  重跑冒烟通过（12 模块、协议 v2、Ready、ExitCode=0）。提交 `4cdc95d`。

### F-003（Suggestion, Open）— Stage B-2 范围
- 公民卡、完整历史页（需新 S2C 消息与服务端查询投影）、机构/土地视图留待
  Stage B-2；建议按消息账本追加规则（ID 3+，必要时协议升版）实施。

### F-002（Suggestion, Open）— Stage B-2 范围
- 公民卡、完整历史页（需新 S2C 消息与服务端查询投影）、机构/土地视图留待
  Stage B-2；建议按消息账本追加规则（ID 3+，必要时协议升版）实施。

## Compliance

**Overall Compliance:** Pass -> ROUTE TO HUMAN（真机目视核验随清单）。

## Final Statement

本报告为独立审查，不构成 Human Approval；子进程实现完整并已提交，Reviewer
独立复跑通过（Level 1-2）。
