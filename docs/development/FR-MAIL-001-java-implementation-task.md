# FR-MAIL-001 Java Implementation Task

> **Status:** Prepared — Authorized
> **Task Type:** 传讯水镜邮箱子系统
> **Design Input:** docs/architecture/fr-mail-001-a-water-mirror-mail.md

## 1. Goal

按设计实现邮件子系统：持久化 `mail` 命名空间、个人/单位收件人解析、协议
v7（ID 16-21）、MailScreen、HUD 未读角标与 S2C 提醒。

## 2. 明确不实现

- 单位邮箱阅读权限（职位持有人等，后续设计）；附件/群发；命令兜底（可选
  后续）；新依赖。

## 3. 契约

- 服务端权威、主线程串行、FR-CORE-002 持久化门；有界（每箱 ≤100 封、
  subject≤64、body≤2000）；未读数随已读更新；
- 发送者须手持传讯水镜（服务端校验）；收件人解析：个人经 PlayerData+主体
  登记册（名字/UUID/登记号），单位白名单（gov:government/
  gov:ministry:<id>/parliament/court/bank，ministry 经 GovernmentService
  校验）；未知 fail closed；
- 新邮件提醒：S2C MailAlertPacket（未读数）；客户端在背包含传讯水镜时显示
  HUD 角标 + 可选聊天行；登录同步；
- 协议 v7、账本 ID 16-21（C2S 带速率策略），freeze 单向；
- 客户端 MailScreen 只读展示 + 提交意向。

## 4. 测试计划

- 仓库有界/裁剪/持久化/重启；收件人解析矩阵；
- 发送/已读/删除/未读计数；越权与速率拒绝；
- codec 往返与边界；既有网络/客户端测试同步（v7/账本/未读角标）；
- `gradlew build` 全绿。

## 5. 验收

FR-MAIL-001-A §4；提交后独立审查（Codex）→ ROUTE TO HUMAN / RETEST /
BLOCK（真机邮件+提醒随 Human）。

## 6. 约束

- 不新增依赖；服务端权威；专用服务器不加载 client/ 类。
