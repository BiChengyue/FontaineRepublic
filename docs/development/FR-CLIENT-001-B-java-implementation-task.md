# FR-CLIENT-001-B Java Implementation Task（Stage B-1 — 客户端 GUI/HUD）

> **Status:** Prepared — Authorized（Stage A 已并入 develop；Human 既定顺序
> "服务端完成后继续客户端模组"）
> **Task Type:** 客户端 GUI/HUD/表单（纯展示与输入转发）
> **Design Input:** FR-CLIENT-001-A v1.1 §3/§4/§7；Stage A 产物
> **Dependency:** FR-CLIENT-001-IMPL-A（已并入）、FR-CMD-001（命令路径）

## 1. Goal

在 Stage A 的 S2C 展示网络面与客户端非权威缓存之上实现第一版可视化界面：

- balance HUD：渲染 `ClientPresentationCache.balanceSnapshot()` 的余额与货币
  符号（若缓存为空则显示占位）；
- money 屏：余额卡 + 转账表单 + 实时交易流水视图（读
  `transactionNotices()`，缓存为最近 50 条实时通知）；
- 通知 HUD/视图：读 `notificationEntries()`；
- 引导屏：静态说明，链接 `/fr help` 与玩家指南；
- 打开方式：注册客户端命令（如 `/frclient`，经
  `RegisterClientCommandsEvent`）或按键绑定，二选一即可。

转账表单提交走命令路径：

```text
/fr money pay <目标> <金额> [备注]
```

客户端仅组装命令字符串（`player.connection.sendCommand(...)`），服务端解析
目标、校验金额与备注；表单输入有界（目标 ≤64、金额 1..9_000_000_000_000_000
、备注 ≤128），提交失败仅展示服务端有界反馈，不本地落账。

## 2. 明确不实现

- 公民卡/完整历史页/机构与土地视图（需新增 S2C 消息与服务端钩子 → Stage B-2）；
- 客户端权威状态/存储/权限判定；权威 C2S 包；新依赖；
- 紧急动作客户端界面。

## 3. 契约

- 全部界面位于 `com.fontainerepublic.client.gui` / `client.hud`，经
  FMLClientSetupEvent 或 DistExecutor 客户端初始化；专用服务器不加载 client/
  类（源码扫描核验）；
- 界面只读展示缓存；缓存非权威、登出清理（Stage A 已实现）；
- 表单提交失败展示服务端反馈；无乐观落账；
- 无 FR 客户端时服务器功能不受影响（客户端模组纯可选）。

## 4. 测试计划

`src/test/java/.../client/ClientGuiFoundationTestMain.java`（无依赖 main）

- 表单命令字符串组装（金额/备注边界、非法输入拒绝）；
- 展示缓存 → 视图投影（余额渲染、流水有界、通知列表）；
- 侧隔离源码扫描：`client/gui`、`client/hud` 无 server import 反向依赖；
  专用服务器路径不引用 client/gui、client/hud（Mod 入口仅经
  FMLClientSetupEvent/DistExecutor 引用 ClientManager）；
- `gradlew build` 全绿。

## 5. 验收

FR-CLIENT-001-A v1.1 §5 矩阵（GUI 部分）；无新 S2C 消息/服务端业务改动；
提交后独立审查（Codex）→ ROUTE TO HUMAN / RETEST / BLOCK（真机连服核验
随 Stage C）。

## 6. 约束

- 不新增依赖；服务端权威；专用服务器不加载 client/ 类；
- 不改服务端业务代码（除必要的客户端初始化接线外）。
