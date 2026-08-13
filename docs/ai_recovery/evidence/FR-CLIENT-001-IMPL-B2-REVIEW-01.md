# Audit Report — FR-CLIENT-001-IMPL-B2-REVIEW-01

> 独立代码与验证审查（Level 1-2）；本报告只做审阅，不代替 Human Approval。

## Agent Identity

- **Agent:** Codex
- **Role:** Reviewer（独立复核 + 独立复跑；实现主体由派发子进程完成，中途退出后
  Reviewer 修复编译错误、补齐测试与既有测试同步）
- **Task ID:** FR-CLIENT-001-IMPL-B2-REVIEW-01
- **Task Name:** 公民卡 + 交易历史视图（Stage B-2）

## Project Context Snapshot

- **Branch:** codex/fr-client-001-impl-b2（实现提交 `84bb477`）
- **Baseline:** develop @ 040da9c

## Audit Report

**Files reviewed:**

- 网络面：协议 v3（`NetworkProtocol.VERSION = "3"`）、账本 5 条（ID 0-4）：
  `CitizenInfoPacket(3)`、`TransactionHistorySyncPacket(4)`（有界 codec、
  构造 + decode 双重边界、hex 校验、≤128 列表）；
- 公民发送接线：`CitizenPresentationNotifier` /
  `ServerCitizenPresentationNotifier`（登记号经 SubjectRegistry 解析、
  presence 过滤、best-effort）、`PresentationAwareCitizenService` 装饰器
  （展示异常吞咽，业务结果不变）、`CitizenModule` 增 `network` 依赖与绑定；
- economy 历史：`EconomyPresentationNotifier.syncHistory` 扩展 +
  `PresentationAwareEconomyService` 登录后发送第一页 +
  `ServerEconomyPresentationNotifier` 组装历史包；
- 客户端：缓存增 citizen/history 快照与 `setHistory/clear`、执行器/handler
  增两个 accept、`/frclient citizen|history`、`CitizenScreen`/`HistoryScreen`、
  `ClientViewProjection` citizen/history 投影（空态占位）；
- 测试：新增 `ClientStageB2FoundationTestMain`（8 组）；既有
  `NetworkFoundationTestMain`（v3 谓词、账本 0-4/5 条）、
  `ClientPresentationFoundationTestMain`（v3、handler 引用 5 次）、
  `CitizenFoundationTestMain`（依赖集含 network）同步更新。

**Evidence sources:**

1. 上述源码审阅。
2. 独立复跑：`gradlew clientStageB2FoundationTest` ->
   `[FR-CLIENT-001-B2] Client stage B-2 foundation validation passed`；
   完整 `gradlew build` BUILD SUCCESSFUL（31 actionable tasks）。

## Acceptance Matrix（FR-CLIENT-001-A v1.1 §5）

| # | 验收 | 判定 |
|---|---|---|
| 1 | 协议 v3；账本 5 条 ID 0-4、PLAY_TO_CLIENT、有界 codec、freeze 单向 | PASS |
| 2 | 登录后 FR 客户端收到公民卡与历史第一页（presence 过滤、absent 零影响） | PASS（源码 + 装饰器/notifier 测试；真机待 Stage C）|
| 3 | 公民/历史界面读非权威缓存 | PASS |
| 4 | 专用服务器不加载 client/ 类 | PASS（FMLClientSetupEvent 模式 + 扫描）|
| 5 | 无新依赖；服务端业务规则不变 | PASS |

## Findings

### F-001（Process）— 子进程轮次上限退出
- 子进程完成全部生产代码后达上限退出（未提交、未写测试）；Reviewer 修复
  编译错误（ClientManager 缺 openCitizen/openHistory、SubjectRecord import 缺失）、
  补齐 `ClientStageB2FoundationTestMain` 与 build.gradle 接线、同步既有测试
  （v3/5 条/依赖集/匿名 notifier 补 syncHistory）、修正历史空态占位断言。

### F-002（Suggestion, Open）— 历史分页
- 首发仅推送第一页（`afterId=0`）；翻页需新 C2S 请求包或更多 S2C 消息，
  留待后续阶段（当前设计禁止权威 C2S 包）。

### F-003（Suggestion, Open）— 机构/土地视图
- `InstitutionInfoPacket`/`LandInfoPacket` 需对应模块提供公共信息查询面，
  留待 Stage B-3。

## Compliance

**Overall Compliance:** Pass -> ROUTE TO HUMAN（真机目视核验随清单）。

## Final Statement

本报告为独立审查，不构成 Human Approval；Reviewer 修复子进程遗留问题并
独立复跑通过（Level 1-2）。
