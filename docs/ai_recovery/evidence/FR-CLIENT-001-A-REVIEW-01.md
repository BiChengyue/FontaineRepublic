# Audit Report — FR-CLIENT-001-A-REVIEW-01

> 客户端 UI 模组设计候选独立审查（架构级，Level 0）。

## Agent Identity

- **Agent:** Codex
- **Role:** Reviewer（独立设计审查）
- **Task ID:** FR-CLIENT-001-A-REVIEW-01
- **Task Name:** 客户端 UI 模组架构设计候选审查

## Project Context Snapshot

- **Baseline:** develop @ c7a2482（服务端实现全部完成：区域制、议会扩展、
  FR-EMG 共享设施、紧急动作目录、紧急命令适配）
- **Reviewed artifact:** docs/architecture/fr-client-001-a-client-ui-module-architecture.md
  （v1.0 → 修正为 v1.1）

## Findings

### F-001（Critical, Fixed in v1.1）— 网络基线空表约束与展示包冲突
- 现网实现 `NetworkBootstrap.registerProductionMessagesAndFreeze()` 强制
  `messageCount == 0`；而客户端展示面必须注册 S2C 包。v1.1 §4.1 明确：空表
  约束是 FR-NET-001 基线占位，按 FR-NET-001-A §7.1 预留（首个生产消息 ID 0）
  替换为账本注册 + freeze；协议版本提升至 "2"。

### F-002（Critical, Fixed in v1.1）— S2C 接收侧路径与侧隔离
- 现网仅有 `ServerNetworkDispatcher`，`PLAY_TO_CLIENT` 到达接收侧时在接收侧
  主线程调用 spec handler。handler 位于 common，不能静态引用 client 类。
  v1.1 §4.3 规定 handler 用 `DistExecutor.safeRunWhenOn(Dist.CLIENT, ...)`
  （safe 变体在专用服务器上不求值 supplier，client 类不加载）。

### F-003（Major, Fixed in v1.1）— 发送接线与模块依赖
- `NetworkSendService` 已实现但无调用方。economy 需声明 `network` 运行时依赖
  并在登录/余额变更/转账成功后经运行时解析的 send service 发送（presence
  过滤，absent 客户端零影响）。v1.1 §4.1 第 5 条与 Stage A 覆盖。

### F-004（Major, v1.1 采纳）— 首版范围过大，需分阶段
- 原 v1.0 首版含 economy/citizen/land/institution/notifications/guide 全部
  视图。v1.1 §7 分为 Stage A（网络展示面）、B（GUI/HUD）、C（验证打磨）；
  首版消息账本仅 ID 0-2（economy 展示），机构/土地信息随后续阶段追加。

### F-005（Suggestion）— 展示包内容隐私
- TransactionNotify 载荷含 memo 与对方摘要；S2C 仅发给参与方本人，且为
  展示数据。实施时须确认客户端展示缓存不清空原则与登出清理（v1.1 §4.3）。

## Compliance

- 无客户端权威/存储/权限判定：v1.1 §2/§6 保持；
- 无权威 C2S 包：首发仅 S2C 展示，表单走命令路径；
- 专用服务器不加载 client 类：§4.3 侧隔离明确；
- no-client parity：presence 过滤 + 命令/聊天兜底；
- 与 FR-NET-001-A §6/§7/§9/§11 对齐：协议、账本、方向、发送门面一致。

**Overall Compliance:** Pass（修正已并入 v1.1；实现按 Stage A/B/C 推进，
Human 批准仍为最终门禁）。
