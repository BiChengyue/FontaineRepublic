# Audit Report — FR-CLIENT-V1-RELEASE-AUDIT-01

> 客户端首版（FR Client v1）里程碑审查：逐阶段证据汇总 + 一致性核验。

## Agent Identity

- **Agent:** Codex
- **Role:** Reviewer（里程碑整合审查）
- **Task ID:** FR-CLIENT-V1-RELEASE-AUDIT-01
- **Task Name:** 客户端首版发布审查

## 1. 范围

对客户端阶段 A / B-1 / B-2 / B-3a / B-3b 五轮交付做整合核验：消息账本、
协议版本、界面命令、测试接线、构建与冒烟证据、文档同步、git 状态。

## 2. 证据来源

- 各阶段独立审查报告：FR-CLIENT-001-IMPL-A/B/B2/B3a/B3b-REVIEW-01；
- 各阶段完整构建：`gradlew build` BUILD SUCCESSFUL
  （28/29/30/31/32/33 actionable tasks，逐轮递增）；
- 各阶段全栈冒烟（专用服务器）：协议 2/3/4/5 → 生产消息 3/5/7/9；
  12 模块 0 unavailable；Ready=True、ExitCode=0；无 client 类加载 /
  Unsafe Referent / NoClassDefFoundError；
- 当前 develop HEAD `6741222` 与 origin/develop 同步；
- 玩家指南 §五.5 与 Level 3 清单 §4.7 已含全部 /frclient 命令。

## 3. 消息账本（协议 v5，ID 0-8）

| ID | 消息 | 触发 |
|---:|---|---|
| 0 | `BalanceSyncPacket` | 登录/余额变更 |
| 1 | `TransactionNotifyPacket` | 本人参与的转账成功 |
| 2 | `NotificationPacket` | 登录后待读通知 |
| 3 | `CitizenInfoPacket` | 登录/公民记录就绪 |
| 4 | `TransactionHistorySyncPacket` | 登录首屏历史 |
| 5 | `GovernmentInfoPacket` | 登录（部门摘要）|
| 6 | `ParliamentInfoPacket` | 登录（提案摘要）|
| 7 | `JusticeInfoPacket` | 登录（案件摘要）|
| 8 | `LandInfoPacket` | 登录（土地公共概况）|

## 4. 界面命令（10 个 /frclient 视图）

`frclient`（主菜单）、`money`（余额/转账/流水）、`citizen`、`history`、
`notifications`、`government`、`parliament`、`court`、`land`、`guide`。

## 5. 一致性核验

- 协议常量与全部测试断言一致（v5；账本 9 条 ID 0-8；handler 引用 9 次）；
- 侧隔离：common display 无 client import；主入口 FMLClientSetupEvent 模式；
  server/ 不引用 client/；
- 服务端发送全部经 `NetworkSendService.trySendToPlayer`（presence 过滤，
  absent/离线/异常 best-effort）；业务结果不受展示影响；
- 客户端缓存非权威、登出清理；表单走命令路径（`fr money pay`）；
- FR-LAND 无枚举契约保持（公共概况为单值聚合）。

## 6. 里程碑判定

**客户端首版功能集完成**（对应 FR-CLIENT-001-A v1.1 首版范围中
economy/citizen/land(概况)/government/parliament/court/notifications/guide
全部视图；"我的地块"权益视图为后续阶段）。

## 7. 待办（需 Human）

- Level 3 真机核验（清单 §4.6 服务器面 + §4.7 客户端面）；
- 政策确认：紧急发钞/回收是否允许作用于冻结账户；
- 后续：个人用地权益视图（FR-LAND-002-A 设计已备）、机构实时推送、B-3b 后
  续打磨。

## Compliance

**Overall Compliance:** PASS（Level 1-2 全绿 + 冒烟证据）；真机目视核验随
Human 早间测试（ROUTE TO HUMAN）。
