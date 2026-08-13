# FR-EMG-CMD-001 Java Implementation Task

> **Status:** Prepared — Authorized by Human "继续做完服务端暂停的任务"（2026-08-14）
> **Task Type:** Shared emergency command adapter（`/fr admin emergency`）
> **Design Input:** FR-EMG-001-A §13、FR-CMD-001-A §7
> **Dependency:** FR-EMG-001（已实现）、FR-CMD-001（已实现）、FR-EMG-ECO-001（已入 develop）

## 1. Goal

在保留的 `admin` 字面量下挂接 foundation 拥有的紧急命令子适配器，提供：

- `/fr admin emergency preview <module> <action> <version> <targetType> <targetId>
  <category> <reason> [<key> <value> ...]`
- `/fr admin emergency confirm <token>`
- `/fr admin emergency inspect <attemptId>`
- `/fr admin emergency status`

每次执行通过 `CommandRuntimeResolver` 运行时解析当前 ACTIVE 的 `EmergencyService`，
绝不捕获 Service 实例。来源分类走 `EmergencyConsoleClassifier`；玩家来源转
`HYDRO_ARCHON`（服务端核验配置 UUID），本地专用服务器控制台转 `SERVER_CONSOLE`，
其余来源即使解析到达回调也由服务拒绝。

## 2. 明确不实现

- 业务动作（economy.issue/reclaim 等由 provider 承担）；
- token 表、尝试/配置日志、权威配置生命周期、审查矩阵（FR-EMG-001 已拥有）；
- 修改 `EmergencyService` / `EmergencyRequest` / 结果类型契约；
- 新依赖、GUI、包、客户端权限。

## 3. 具体契约

### 3.1 命令树

`EmergencyAdminCommand.create(CommandRuntimeResolver)` 返回 literal `emergency`，
挂到 `FrameworkAdminCommand` 的 `admin` 之下（与 bootstrap/institution 并列）。
参数解析构造 `EmergencyRequest`：

- module/action/version 有界字符串（模块 32、动作 64、版本 16）；
- targetType 仅接受枚举名（`PLAYER_UUID` 等）；targetId 为规范 UUID（PLAYER_UUID）；
- category 仅接受 `EmergencyCategory` 枚举名；reason 非空、≤200；
- 参数键值对 ≤16，键 ≤32、值 ≤200；
- 非法输入返回有界 failure 反馈，不调用服务。

### 3.2 来源与反馈

- `EmergencyConsoleClassifier.classify(source)` →
  `LOCAL_CONSOLE` 构建 `SERVER_CONSOLE` 源；`PLAYER` 构建 `HYDRO_ARCHON` 源
  （UUID 取自 `source.getEntity()` 的 `ServerPlayer`）；
- preview 成功：展示单次 token 与到期时间；token 绝不写入日志/聊天历史；
- confirm/inspect/status：按 `ConfirmResult` / `EmergencyInspection` / `EmergencyStatus`
  输出有界摘要；失败输出 bounded failureCode；
- 所有反馈经 `CommandFeedback`，服务不可用时 fail closed。

### 3.3 运行时解析

`CommandRuntimeResolver` 增加 `emergencyService()`：解析 ACTIVE `EmergencyModule` →
`EmergencyModule.service()`，每次调用解析、不缓存。

## 4. 测试计划

`src/test/java/.../emergency/EmergencyCommandFoundationTestMain.java`

- 纯函数：请求构造（合法/非法参数、UUID、类别、参数上限）；来源映射
  （分类 → actor 源）矩阵；token 不落日志策略静态核验；
- 树形状：`EmergencyAdminCommand.create(...)` 字面量/子节点/参数存在且可构建；
- 服务层复用：拒绝路径（非控制台/无效 token/无服务）返回有界失败；
- `gradlew build` 回归全绿。

## 5. 验收

对应 FR-EMG-001-A §13 命令边界 + §14 可用性；无 FR-EMG/Economy 契约变更；
提交后独立审查（Codex）→ ROUTE TO HUMAN / RETEST / BLOCK。

## 6. 约束

- 不新增依赖；服务端权威；admin 保留字不重复贡献；
- 提交后审查（Codex）→ ROUTE TO HUMAN / RETEST / BLOCK。
