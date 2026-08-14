# Task Card — FR-MAIL-001-IMPL（传讯水镜邮箱 v1.1）

> Status: Authorized（Human 需求 2026-08-14）
> Task Type: 邮件子系统（持久化 + 协议 + 客户端 UI/HUD 提醒）
> Design Reference: docs/architecture/fr-mail-001-a-water-mirror-mail.md

## Scope

**Allowed:** `server/mail/`（MailRepository/Mailbox/MailMessage/MailService）、
收件人解析（个人+机构白名单）、机构互寄与邮箱管理员、附件（钱+物品）、
邮费/附件费、机构广播、协议 v7 消息 ID 16-21（C2S 速率策略）、
`client/gui/mail/MailScreen`、HUD 未读角标（背包含传讯水镜时）、
`/frclient mail` 入口、新邮件 S2C 提醒；契约测试。

**Forbidden:** 客户端权威；现金/市场；新依赖。

## Acceptance

- 手持传讯水镜可向个人/机构发信；收件人解析 fail closed；
- 机构互寄/管理员授权/广播（单份+按人已读+冷却）正确；
- 附件（钱+物品）领取原子、防复制；邮费 10+附件费 100、机构免费；
- 收件箱读/删/未读数正确；背包含传讯水镜时新邮件实时 HUD/聊天提醒；
- 有界/持久化/重启恢复；速率与越权拒绝；
- `mailFoundationTest` + `gradlew build` 全绿；专用服务器不加载 client/ 类。
