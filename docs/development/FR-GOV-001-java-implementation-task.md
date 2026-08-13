# FR-GOV-001 Java Implementation Task

> **Status:** Prepared — Authorized by FR-PHASE2-HUMAN-APPROVAL-01 + "继续工作"
> **Task Type:** Feature Module Implementation（政府模块）
> **Design Input:** FR-GOV-001-A（候选）
> **Dependency:** FR-INST-002（已实现）、FR-CIT-001、FR-CORE-002、FR-AUD-001、FR-ID-001
> **Authority Boundary:** 本文件细化实现范围；不改变设计契约。

---

## 1. Goal

实现政府模块：职位/部门/办公室 + 任命/罢免（现场官方职责）+ 只读查询。

## 2. 明确不实现

- 立法/司法/财政权力；政治政策；选举；GUI/包；技术权限映射；新依赖。

## 3. 具体契约

### 3.1 模型与存储

```text
government
├── StoreVersion / StoreRevision
├── Ministries / Positions / Offices
```

- 严格编解码 + 失败关闭 + 确定性编码 + 有界限制；
- 持有者经 PlayerData/FR-ID 服务解析（不存游戏名）。

### 3.2 服务

- createPosition / createMinistry / appoint / dismiss / currentOffice /
  positionsByMinistry（有界）；
- appoint/dismiss = ONSITE_OFFICIAL_DUTY：最终变异边界必须
  `InstitutionAccessService.validateAtMutation(context, ONSITE_OFFICIAL_DUTY, now)`；
- 每次权威变更一个完整快照 -> `commitModuleData("government", ...)` -> COMMITTED 后发布。

### 3.3 命令

- `/fr government ministry create|list`、`position create|list`、
  `appoint|dismiss`（现场门控）、`office`（只读）；
- 经 CommandContributionRegistry 贡献；运行时解析；输出有界。

## 4. 测试计划

`src/test/java/com/fontainerepublic/server/government/GovernmentFoundationTestMain.java`

- 部门/职位创建单快照；任命/罢免需现场上下文（无上下文拒绝）；
- holder 解析；无技术权限映射（反射/源码守卫）；四柱边界（无立法/司法/财政方法）；
- 注入失败无发布；重启恢复；严格编解码；无枚举；
- `governmentFoundationTest` 接入 check；`gradlew build` 回归。

## 5. 验收

对应 FR-GOV-001-A §6；全部须有测试证据。

## 6. 约束

- 不改已实现模块；不新增依赖；服务端权威；SavedData/NBT only；
- 提交后审查（Codex）-> ROUTE TO HUMAN / RETEST / BLOCK。
