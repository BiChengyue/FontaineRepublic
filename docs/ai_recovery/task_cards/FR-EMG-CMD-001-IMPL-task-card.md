# Task Card — FR-EMG-CMD-001-IMPL

> Status: Prepared（Human "把服务端暂停的任务继续做完吧" 2026-08-14 授权范围内）
> Task Type: 共享紧急权限命令适配（`/fr admin emergency`）
> Design Input: FR-EMG-001-A §13（命令边界）+ FR-CMD-001-A（admin 保留字/贡献契约）
> Dependency: FR-EMG-001（已实现）、FR-CMD-001（已实现）、FR-EMG-ECO-001（提供方目录）

## Agent Identity

- **Agent:** Codex（Designer）
- **Task ID:** FR-EMG-CMD-001-IMPL
- **Task Name:** `/fr admin emergency` 命令适配器

## Scope

**Allowed:** `EmergencyAdminCommand`（admin 子适配器）+ `FrameworkAdminCommand` 挂接 +
`CommandRuntimeResolver.emergencyService()` 运行时解析 + 测试/构建接线。

**Forbidden:** 业务动作实现；token 表/日志/权威配置复制；修改 EmergencyService 契约；
新依赖；GUI/包。

## Acceptance

- `/fr admin emergency preview|confirm|inspect|status` 可解析并运行时解析 ACTIVE
  EmergencyService（不捕获 Service）；
- 来源分类：LOCAL_CONSOLE → SERVER_CONSOLE；玩家 → HYDRO_ARCHON（服务核验）；
  RCON/命令方块/函数/集成主机即使到达回调也被服务拒绝；
- token 仅展示一次、不落日志；输出有界；
- `emergencyCommandFoundationTest` + `gradlew build` 全绿；无越界文件。
