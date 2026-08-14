# FR-MAIL-001-A — 传讯水镜邮箱子系统（设计候选 v1.0）

> **Task ID:** FR-MAIL-001-A
> **Status:** Design Candidate — 实施中（Human 需求 2026-08-14）
> **Purpose:** 传讯水镜（原"便携通讯器"）的邮件收发 + 新邮件实时提醒

## 1. 需求（Human）

- 传讯水镜具备邮箱功能；
- 只要身上（背包）有传讯水镜，收到新邮件时在屏幕适当位置**实时显示**
  （HUD 未读角标）或聊天提醒；
- 可向**任何个人与单位**收发邮件。

## 2. 设计决策

### 2.1 数据与权威

- 新增持久化命名空间 `mail`（单写者 `MailRepository`，走 FR-CORE-002 持久化
  门）；`Mailbox`（收件人维度，有界 ≤100 封）、`MailMessage`（id/from/to/
  subject≤64/body≤2000/sentAt/read，不可变）；
- 服务端权威：发送/已读/删除全部主线程串行 + 持久化确认；
- 普通历史有界裁剪：最早删除不影响未读数/权威性。

### 2.2 收件人（个人与单位）

- 收件人 = 邮箱键：
  - 个人：玩家 UUID（经 PlayerData+主体登记册解析：名字/UUID/登记号收敛到
    主体，未登记/歧义 fail closed）；
  - 单位：`gov:government`、`gov:ministry:<id>`、`parliament`、`court`、
    `bank`（有界白名单；ministry id 经 GovernmentService 校验）；
- 发送者必须为玩家且**手持传讯水镜**（邮件设备）；
- 单位邮箱 v1 **仅可接收**（供公民写信/举报/申请）；单位侧阅读权限策略
  （职位持有人等）留待后续设计。

### 2.3 提醒

- 玩家背包任意位置含传讯水镜时：新邮件 → S2C `MailAlertPacket`（未读数），
  客户端在 HUD 适当位置显示未读角标（FR HUD 区域）+ 可选聊天一行提醒；
- 登录时同步未读数；无客户端/无传讯水镜不影响功能（聊天兜底）。

### 2.4 网络面（协议 v7，账本 ID 16-21）

| ID | 方向 | 消息 | 载荷 |
|---:|---|---|---|
| 16 | C2S | `MailSendPacket` | to(≤128)、subject(≤64)、body(≤2000) |
| 17 | C2S | `MailListRequestPacket` | afterId(≥0)、limit(≤64) |
| 18 | C2S | `MailReadPacket` | mailId(>0) |
| 19 | C2S | `MailDeletePacket` | mailId(>0) |
| 20 | S2C | `MailboxSyncPacket` | 有界邮件列表 + 未读数 |
| 21 | S2C | `MailAlertPacket` | 未读数(≥0)、最新邮件摘要(≤128) |

### 2.5 客户端

- `client/gui/mail/MailScreen`：收件箱/阅读/删除 + 写信（收件人/主题/正文）；
  入口 `/frclient mail` 与主菜单按钮；
- `client/hud/FrHudRenderer` 增未读角标（背包含传讯水镜时显示）；
- 提交意向走 C2S，服务端全量校验（发送者手持校验、收件人解析、界限、速率）。

## 3. 权威边界

- 邮件是持久化服务端状态；客户端只展示与提交；
- 无 FR 客户端/未手持 → 无 UI/无提醒，功能经命令/聊天保持可用（v1 邮件
  以 UI 为主，命令兜底可选后续）；
- 速率/界限/越权（非收件人读删）fail closed。

## 4. 测试

- 仓库有界/持久化/重启恢复；收件人解析（个人名字/UUID/登记号、单位白名单、
  未知 fail closed）；
- 发送/已读/删除/未读计数；速率与越权拒绝；裁剪不丢权威；
- codec 往返与边界；`gradlew build` 全绿。

## 5. Review Gate

设计候选；实施后独立审查（Codex）→ ROUTE TO HUMAN / RETEST / BLOCK
（真机邮件收发 + 提醒随 Human）。

## 6. 修订 v1.1（Human 需求 2026-08-14）

### 6.1 机构互寄与访问策略

- **所有有 ID 的个人与机构之间均可互寄**：个人 ↔ 个人、个人 ↔ 机构、
  机构 ↔ 机构；
- 机构发信/读信权限（bounded）：
  - 部门（ministry）邮箱：该部门任一职位的在职持有人可读/发（经
    `GovernmentService.currentOffice(positionId)` 解析持有人）；
  - 政府/议会/法院/央行邮箱：由**水神（配置的水神 UUID）与本地控制台**
    可读/发（v1；后续可细化各机构内部权限）；
- 收件人解析扩展：单位键含 `gov:government`、`gov:ministry:<id>`、
  `parliament`、`court`、`bank`；发件身份为机构时须满足上述访问策略；
- 越权读/删/发 fail closed。

### 6.2 附件（钱和物品）

- 邮件可携带附件：**金额**（≤1 次，有界）与**物品**（≤4 槽）；
- 发送时：金额从发件人经济账户扣至**邮件托管**（同交易 escrow 语义，
  取消/删除未读邮件时退还）；物品从发件人背包移入邮件托管槽；
- 收件人**打开/领取**邮件时原子到账/入包（背包满则物品留在邮件中待领或
  提示）；已读后附件不可重复领取（防复制）；
- 附件有界、类型受限（金额≤maxBalance；物品槽位固定）；持久化随邮件。

### 6.3 邮费

- **所有邮件向发件人收邮费**（默认 1 Mora，可配置 `mail.postageFee`）；
- **含附件额外收费**：金额附件按金额比例（默认 1%，可配置
  `mail.attachmentMoneyRate`，上限有界）或固定附加费（可配置
  `mail.attachmentItemFee`，按物品槽计）；
- 邮费入国库（supply 守恒）；发件人余额不足 → 发送失败（fail closed）；
- 机构发件同样收费（从机构托管/国库扣？v1：机构发件邮费暂免或由水神配置
  的机构账户承担——设计默认**机构发件免邮费**，待 Human 确认）。

### 6.4 提醒与 HUD（并入 FR-HUD-001）

- 新邮件提醒与常驻信息 HUD 由 FR-HUD-001 统一设计（背包含传讯水镜时显示
  关键信息面板 + 未读角标，可拖动调整位置）。
