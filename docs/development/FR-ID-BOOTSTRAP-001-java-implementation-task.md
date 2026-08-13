# FR-ID-BOOTSTRAP-001 Java Implementation Task

> **Status:** Prepared — Authorized by FR-ID-BOOTSTRAP-HUMAN-APPROVAL-01
> **Task Type:** Core Infrastructure Implementation
> **Design Input:** FR-ID-BOOTSTRAP-001-A v1.0
> **Dependency:** FR-ID-001（已实现）、FR-CORE-002、FR-DATA-001、FR-CMD-001-A
> **Authority Boundary:** 本文件细化实现范围；不改变已批准设计契约。

---

## 1. Goal

实现水神初始个人主体的一次性、console-only、可审计绑定：

- `/fr admin bootstrap subject-hydro <uuid> <reason>`（真实本地 Dedicated Server 控制台专用）；
- `/fr admin bootstrap status`（只读状态查询）；
- `subject-registry` 命名空间内 append-only 尝试链（摘要链防篡改）+ `BootstrapState`；
- 绑定成功后不可变：无解绑/重绑/转移路径；
- 绑定前 `10-000001-61` 原个人主体不可物化；绑定后物化并 BOUND。

## 2. 明确不实现

- 继任/office 绑定（未来独立设计）；官方账户；FR-EMG 配置；GUI/包；
- 普通主体分配变更（FR-ID-001 已有）；任何"修复/重绑"路径。

## 3. 具体契约

### 3.1 控制台来源分类（BootstrapConsoleClassifier）

- 通过：`server.isDedicatedServer()` 为真 + `source.getEntity() == null` +
  `source.getName().equals("Server")`（本地控制台）。
- 拒绝：RCON（名称 "Rcon"）、命令方块/矿车（entity 非空）、函数来源、
  集成主机（非 dedicated）、普通 OP/玩家。
- 最终变异边界再次校验；实现必须写明已知限制（函数来源名称伪装的理论向量），
  由审查确认可接受。

### 3.2 尝试链（subject-registry 命名空间）

```text
BootstrapState: Phase(UNBOUND|BOUND), BoundUuidDigest, BoundAt, TrailHeadDigest
BootstrapAttempts: <attemptId> -> AttemptId/At/SourceClassification/ResultCode/
                                UuidDigest/ReasonDigest/PrevDigest/SelfDigest/IdempotencyKey
```

- 结果码：REJECTED_SOURCE / REJECTED_INPUT / PLAYER_NOT_PROVISIONED（INCOMPLETE）/
  PERSISTENCE_FAILURE / IDEMPOTENT_NOOP / SUCCESS；
- 每次尝试先落 PENDING 记录（经 FR-CORE-002 门），终态后追加终态记录；
- 重启对账：TrailHeadDigest 与末条一致且存在 SUCCESS 才可视为 BOUND；
  不一致/缺失 -> fail closed（依赖服务不可用），需新的审计尝试解决。

### 3.3 绑定语义

1. 校验控制台来源 + UUID 规范 + reason 非空有界；
2. 幂等键 = 目标 UUID（规范形式）；
3. PlayerData 无记录 -> INCOMPLETE（可重试，同键）；
4. 已 BOUND 且同键 -> IDEMPOTENT_NOOP；已 BOUND 且异键 -> REJECTED（不可变）；
5. 一个完整替换快照：原个人主体（固定号/类型 10/ACTIVE）+ 索引 +
   BootstrapState=BOUND + 终态 SUCCESS 尝试 -> commitModuleData -> COMMITTED 后发布。

## 4. 测试计划

`src/test/java/com/fontainerepublic/server/registry/BootstrapFoundationTestMain.java`

- 控制台分类矩阵（Server/Rcon/命令方块/函数/玩家/集成主机）；
- 首次绑定成功（快照含主体+索引+BOUND+SUCCESS 链）；
- 拒绝输入/来源；PlayerData 缺失 INCOMPLETE + 同键重试幂等；
- 注入持久化失败 -> 无绑定发布；重放 -> 无二次绑定；
- 重启对账（一致/不一致/篡改）；不可变（异键重绑拒绝）；
- `bootstrapFoundationTest` 接入 check；`gradlew build` 回归。

## 5. 验收映射

对应 FR-ID-BOOTSTRAP-001-A §7 矩阵；全部须有测试证据。

## 6. 约束

- 不改 FR-ID-001/FR-CORE-002/FR-AUD-001 已实现行为；不新增依赖；
- 服务端权威；SavedData/NBT only；console-only；
- 提交后审查（Codex）-> ROUTE TO HUMAN / RETEST / BLOCK。
