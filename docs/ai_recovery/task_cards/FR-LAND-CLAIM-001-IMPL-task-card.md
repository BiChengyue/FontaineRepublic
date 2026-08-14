# FR-LAND-CLAIM-001-IMPL 任务卡

## 目标

传讯水镜购地：手持传讯水镜右键方块 → 土地位置视图 → 无主土地可申请创建共和国
地块并取得使用权（Human 2026-08-14 定义“购地=创建地块+取得使用权”）。

## 派发

- 提示词：`deepseek-worktrees-v2/FR-LAND-CLAIM-001-IMPL-PROMPT.md`
- 设计：`docs/architecture/fr-land-claim-001-a-communicator-land-claim.md`
- 工作树：`deepseek-worktrees-v2/fr-land-claim-001-impl`（分支
  `codex/fr-land-claim-001-impl`；从含交易/邮件的 develop 创建）
- 派发命令（示例）：
  `powershell -File tools\dispatch-task.ps1 -TaskName FR-LAND-CLAIM-001-IMPL
  -PromptFile deepseek-worktrees-v2\FR-LAND-CLAIM-001-IMPL-PROMPT.md
  -Wave fr-land-claim-001-20260814 -Worktree
  D:\MC\FontaineRepublic\deepseek-worktrees-v2\fr-land-claim-001-impl`

## 前置条件

- FR-TRADE-001 与 FR-MAIL-001 已合入 develop（协议账本为其后追加）。
- LandService 存在；仓库 `findParcelAt` 存在。
- 执行前预审已经证明现有 `createParcel` + `grantUsage` 为两个独立提交；实现必须
  先增加 Land-owned 单快照 `createParcelWithUsage`，不得按旧两步路径落地。

## 验收

- 右键无主方块：视图显示“无主 + 申请创建地块”；申请后获得无期限使用权并可建造。
- 右键有主方块：只读概要，无申领入口。
- 完整 3×3 区域与既有地块相交时拒绝，不产生孤立地块。
- 未手持水镜 / 未登记 / 错误维度 / 未加载 / 超出交互距离：服务端拒绝并提示。
- 无客户端玩家可用 `/fr land inspect <x> <y> <z>` 与
  `/fr land claim <x> <y> <z>` 获得同等功能，且不存在 OP 绕过。
- 成功申领只发生一次 durable commit；parcel/right/store revision 各 +1；失败零
  副作用并可安全重试。
- `landClaimFoundationTest` + `gradlew build` 全绿；专用服务器不加载 client/ 类。
