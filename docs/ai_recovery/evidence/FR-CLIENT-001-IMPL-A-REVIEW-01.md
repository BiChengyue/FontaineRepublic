# Audit Report — FR-CLIENT-001-IMPL-A-REVIEW-01

> 独立代码与验证审查（Level 1-2）；本报告只做审阅，不代替 Human Approval。

## Agent Identity

- **Agent:** Codex
- **Role:** Reviewer（独立复核 + 独立复跑；实现主体由派发子进程完成，中途退出后
  Reviewer 修复编译错误、补齐测试/接线并验证）
- **Task ID:** FR-CLIENT-001-IMPL-A-REVIEW-01
- **Task Name:** 客户端展示网络面（Stage A）

## Project Context Snapshot

- **Branch:** codex/fr-client-001-impl-a（实现提交 `68f5746`）
- **Baseline:** develop @ 81b6879

## Audit Report

**Files reviewed:**

- 网络面：`NetworkProtocol.VERSION="2"` + 谓词；`NetworkProductionMessageTable`
  注册 ID 0-2（BalanceSync/TransactionNotify/Notification，PLAY_TO_CLIENT）；
  `NetworkBootstrap` 空表强制替换为期望计数校验；
- 展示包：三个消息类有界 codec（构造 + decode 双重边界、hex 校验、≤128 列表）；
- 侧隔离：`DisplayMessageHandlers` 位于 common，仅以全限定名在
  `DistExecutor.safeRunWhenOn(Dist.CLIENT, ...)` supplier 内引用
  `ClientNetworkExecutor`（专用服务器不求值、不加载 client 类）；
- 客户端：`ClientPresentationCache`（非权威、内存、50 条交易环、登出清理）、
  `ClientNetworkExecutor`（3 个 accept + 懒注册登出清理）；
- 服务端发送接线：`EconomyPresentationNotifier` /
  `ServerEconomyPresentationNotifier`（全经 `trySendToPlayer`，presence 过滤，
  absent/离线/异常均 best-effort）、`PresentationAwareEconomyService` 装饰器
  （展示失败绝不影响业务结果）、`EconomyModule` 增补 `network` 依赖与发送绑定、
  紧急 provider 包装（apply 成功后刷新余额展示）、`FontaineRepublic` 传
  `networkBootstrap.sendService()`；
- 测试：新增 `ClientPresentationFoundationTestMain`（谓词矩阵、codec 边界、
  侧隔离源码扫描、展示缓存、absent 零发送、装饰器吞异常）；既有
  `NetworkFoundationTestMain`（v2 谓词、账本 3 条 ID 0-2/方向/freeze、
  业务 token 扫描豁免批准面）与 `EconomyFoundationTestMain`（依赖集含 network）。

**Evidence sources:**

1. 上述源码审阅。
2. 独立复跑：`gradlew clientPresentationFoundationTest` ->
   `[FR-CLIENT-001-A] Client presentation foundation validation passed`；
   完整 `gradlew build` BUILD SUCCESSFUL（29 actionable tasks）。

## Acceptance Matrix（FR-CLIENT-001-A v1.1 §5 网络面部分）

| # | 验收 | 判定 |
|---|---|---|
| 1 | 协议 v2 谓词矩阵；v1 拒绝、absent 放行 | PASS |
| 2 | 消息账本 ID 0-2、PLAY_TO_CLIENT、有界 codec、freeze 单向 | PASS |
| 3 | 空表强制替换为期望计数校验 | PASS |
| 4 | S2C handler 侧隔离；专用服务器不加载 client/ 类 | PASS（源码扫描 + 设计模式核验）|
| 5 | economy 声明 network 依赖并接线发送；absent 客户端零影响 | PASS |
| 6 | 客户端缓存非权威、内存、登出清理 | PASS |
| 7 | 无 GUI/C2S 包/权威状态；无新依赖 | PASS |

## Findings

### F-001（Process）— 子进程轮次上限退出
- 子进程完成全部生产代码与既有测试更新后达工具上限退出；Reviewer 修复
  编译错误（writeCollection 参数顺序、EVENT_BUS.addListener 重载）、补写
  客户端测试与 build.gradle 接线、调整侧隔离扫描（import 检查 + safe-supplier
  顺序校验）、豁免账本文件于业务 token 扫描。

### F-002（Suggestion, Open）— 登录展示同步触发点
- `ensureAccountForPlayer` 装饰器在登录路径触发 `syncAccount`；真实连服路径
  （登录 → 余额/通知包到达客户端）留待 Stage C 真机验证。建议 Stage B 前补
  一次 Human 连服核验。

### F-003（Suggestion, Open）— 协议版本管理
- 协议 v2 已落地；后续 Stage B 追加消息时需按 FR-NET-001-A §6.4 评估再升版。
  建议将消息表变更与版本提升作为 Stage 收尾检查项。

## Compliance

**Overall Compliance:** Pass -> ROUTE TO HUMAN（真机连服核验随清单）。

## Final Statement

本报告为独立审查，不构成 Human Approval；Reviewer 修复了子进程遗留问题并
独立复跑通过（Level 1-2）。
