# FR-TRADE-003-A — Secure Trade 直接集成（复刻 + 扩展）

> 状态：设计说明（Human 已指示方向，2026-08-15）
> 取代：FR-TRADE-002-A 的「受控适配」路线（不再从零重做 UI/逻辑）

## 1. 决策

Human 指示：交易功能**直接复用 Secure Trade 模组的 UI 与逻辑**，在其上添加 FR
货币/税/身份/审计，XP 直接接入——不重新做 UI 和逻辑。

## 2. 上游

- 仓库：Navielon/SecureTrade（设计文档旧写的 florensie/SecureTrade 404 不存在）
- 许可：MIT（Copyright (c) 2026 Secure Trade Mod Authors）
- 精确 commit：add98b377ffc39e5d73789a874a08c5e369790ce（已归档
  docs/third_party/securetrade/，含 LICENSE + NOTICES.md）
- 代码量：common + forge 约 2500 行（TradeScreen 484 / TradeSession 287 /
  TradeMenu 279 / TradeCommand 266 / TradeHistoryManager 260 / XPMath 58 等）

## 3. 核心约束（不可违背）

1. **服务端权威**：贸易状态、金额、物品、XP、税全由服务端决定；客户端只展示+输入。
2. **可选客户端**（FR 核心原则，昨晚刚修复的发布阻断）：**不得注册任何自定义
   Item / MenuType / SoundEvent**（这些是同步注册表，会让无 FR 客户端进不了服）。
   Secure Trade 原样注册了自定义 MenuType（trade_menu）与 SoundEvent，集成时必须替换。

## 4. 集成方案

- 把 Secure Trade 的 common + forge 代码复制进 FR（改包名到
  com.fontainerepublic.trade.securetrade.* 或等价，保留 MIT 署名）。
- **容器/MenuType**：改用原版 MenuType.GENERIC_9x6（54 格）作为贸易容器，
  用共享的 SimpleContainer(54) 承载双方报价区；客户端拦截菜单打开事件，用
  改编自 TradeScreen 的自定义屏幕渲染「双 27 格报价区 + 玩家背包」。
- **去掉自定义 SoundEvent**（用原版音效或静默）。
- **接入 FR**：货币腿走 FR EconomyService（含 5% 出钱方税，taxRateBps）；
  身份走 SubjectRegistry（交易双方主体）；审计走 AuditService（交易结算记录）；
  XP 腿用 Secure Trade 的 XPMath（FR 已有 ExperiencePointMath 可对齐）。
- **网络**：复用 Secure Trade 的网络包逻辑，挂到 FR 的 channel（或新增到生产账本）。
- **无客户端平价**：保留 /fr trade 命令面（Stage A/C 已合入），图形界面仅 FR 客户端可用。

## 5. 阶段

1. 复制 + 注册表适配（原版 MenuType、去自定义音效）+ 网络接线 + 构建通过。
2. 接入 FR 货币 + 税 + 身份 + 审计。
3. XP 腿接入结算 + 客户端界面 + 命令平价收尾。
4. 全量构建 + 专用服务器冒烟 + 交付真机测试清单。

## 6. 不改变

- 架构 v2.7、路线图 v1.1、服务端权威、数据规则（SavedData/NBT 经 DataManager）。
- 已合入的交易 Stage A/C（纯物品交易修复、冷却/超时、/fr trade 命令面）继续保留，
  本集成在此基础上叠加图形界面与 XP/身份/审计。
