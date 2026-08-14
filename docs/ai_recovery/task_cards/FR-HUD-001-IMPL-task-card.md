# Task Card — FR-HUD-001-IMPL（传讯水镜常驻信息 HUD）

> Status: Authorized（Human 需求 2026-08-14；依赖 FR-MAIL-001 的未读数）
> Task Type: 客户端 HUD（可拖动 + 位置持久化 + 新邮件角标）
> Design Reference: docs/architecture/fr-hud-001-a-water-mirror-hud.md

## Scope

**Allowed:** `client/hud/` 常驻信息面板（名称/身份/登记号/余额/未读邮件）、
拖动定位 + 客户端配置持久化、折叠/透明度、`/frclient hud reset`、
新邮件未读角标（读邮件 S2C 未读数缓存）；纯函数测试。

**Forbidden:** 他人/隐私数据；权威状态；新依赖。

## Acceptance

- 背包含传讯水镜时渲染面板，未持有不渲染；内容投影正确（含空态）；
- 面板可拖动、位置持久化（重启保留）、可重置；折叠可用；
- 未读邮件角标随 MailAlert/MailboxSync 更新；
- `clientHudFoundationTest` + `gradlew build` 全绿；专用服务器不加载
  client/ 类。
