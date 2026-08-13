# FR-CMD-USER-002 Java Implementation Task

> **Status:** Prepared — Authorized by FR-PHASE2-HUMAN-APPROVAL-01 + "继续工作"
> **Task Type:** Command Wiring（转账目标输入扩展）
> **Design Input:** FR-ECO-001-C-ACCOUNT-ALIGN-01 §2.3、FR-DATA-003-A §11、FR-ID-001-A §13
> **Dependency:** FR-DATA-003（已实现）、FR-ID-001（已实现）、economy（已实现）
> **Authority Boundary:** 只扩展 `/fr money pay` 目标输入；不改变业务逻辑。

---

## 1. Goal

`/fr money pay <target> <amount> [memo]` 的目标参数扩展为三种输入，全部收敛到
同一 `SubjectId`：

1. **UUID**（现有）：PlayerData/FR-ID 解析 -> SubjectId；
2. **精确玩家名**：`PlayerDirectoryService.resolveExactGameName` -> UNIQUE_CURRENT(UUID)
   -> FR-ID resolvePlayer -> SubjectId；
3. **登记号**（`TT-NNNNNN-CC`）：FR-ID `resolveExactRegistryNumber` -> routable -> SubjectId。

## 2. 明确不实现

- 前缀/模糊/枚举姓名；top/bank/他人余额；GUI/包；新业务策略；新依赖。

## 3. 具体契约

### 3.1 输入识别

- 语法层先区分：16 位规范 UUID / 10 位数字或展示形登记号 / 否则按玩家名处理；
- 玩家名输入严格校验（`[A-Za-z0-9_]{1,16}`，不修剪）；
- 解析失败映射：UNKNOWN/RETIRED/AMBIGUOUS -> 统一反馈
  `Player name cannot be resolved uniquely.`；INVALID_INPUT -> 语法提示；
- 执行时最终再解析（suggestion 不权威），目标修订/状态失败关闭。

### 3.2 反馈

- 成功：沿用现有收据反馈（transaction id）；
- 解析失败/不可用：标准受限反馈；不暴露目录分类细节。

### 3.3 测试

- 三种输入收敛同一 SubjectId；歧义/退役/未知统一消息；畸形输入拒绝；
- 注入解析失败无转账；`commandFoundationTest` 扩展 + `gradlew build` 回归。

## 4. 验收

- `/fr money pay` 支持 UUID/姓名/登记号且收敛同一账户；
- 无枚举/模糊；反馈有界；`gradlew build` 全绿。

## 5. 约束

- 不改已实现模块行为；不新增依赖；服务端权威；
- 提交后审查（Codex）-> ROUTE TO HUMAN / RETEST / BLOCK。
