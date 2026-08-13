# FR-CORE-002 Java Implementation Task

> **Status:** Prepared — Authorized by FR-PHASE2-HUMAN-APPROVAL-01（实现阶段已授权）
> **Task Type:** Core Infrastructure Implementation
> **Design Input:** FR-CORE-002-A v1.0（docs/architecture/fr-core-002-a-durable-commit-gate.md）
> **Task Card:** docs/ai_recovery/task_cards/FR-CORE-002-IMPL-task-card.md
> **Authority Boundary:** 本文件细化实现范围；不改变 FR-CORE-002-A 契约边界。

---

## 1. Goal

在现有 `DataManager` / `ModSavedData` 之上增加**可确认持久化提交路径**：

```text
commitModuleData(name, snapshot)
  -> 构建完整根快照（现有模块 + 替换该命名空间）
  -> 临时文件写入 + fsync
  -> 原子替换 fontainerepublic.dat
  -> 成功后才替换内存并允许下游可见
  -> 返回 DurableCommitResult
```

现有 `putModuleData`（非确认路径）保持不变，供高频普通操作使用。

---

## 2. Non-Goals

- WAL、分片文件、JSON/数据库/Capability 存储；
- 业务模块（Audit/Citizen/Land/Economy）；
- GUI、业务包、客户端权威逻辑；
- 改变 `putModuleData`/`saveAll`/`getModuleData` 现有行为；
- 新增外部依赖；
- FR-CORE-002-A 契约边界之外的任何设计。

---

## 3. Concrete Contract

### 3.1 New types（`com.fontainerepublic.core`）

```java
public enum DurableCommitStatus {
    COMMITTED, FAILED, UNINITIALIZED, STOPPING
}

public record DurableCommitResult(
    DurableCommitStatus status,
    String moduleName,
    long bytesWritten,
    long durationMillis,
    String failureCode   // 稳定、非机密；成功为空串
) {}
```

### 3.2 DataManager additions

```java
public static DurableCommitResult commitModuleData(String name, CompoundTag snapshot);
public static void beginShutdown();   // ServerStopping 时置 STOPPING 态（幂等）
```

规则：

1. 仅逻辑服务器主线程：`server.isSameThread()` 为假 -> `FAILED(THREAD_VIOLATION)`。
2. `UNINITIALIZED`：`savedData == null`。
3. `STOPPING`：`beginShutdown()` 之后（幂等；停止窗口内禁止新提交）。
4. 校验 name 非空、snapshot 非 null；字节预算超限 -> `FAILED(BOUNDS_EXCEEDED)`。
5. 提交间隔保护（默认 100ms，见 3.6）：超频 -> `FAILED(RATE_GUARD)`，无副作用。
6. 构建完整根：`root = ModSavedData 当前 modules 快照 + (name -> snapshot)`。
7. 世界身份：根内 `WorldIdentity` 字段校验一致；不一致 -> `FAILED(WORLD_IDENTITY)`。
8. 存储适配器 `writeAtomically` 返回成功后才 `savedData.putModuleData(name, snapshot)`
   （内存替换）并返回 `COMMITTED`；失败不触碰内存。

### 3.3 Storage adapter（可注入，便于测试）

```java
public interface DurableStore {
    DurableStoreWrite writeAtomically(CompoundTag root, Path target) throws IOException;
}

public record DurableStoreWrite(boolean ok, long bytesWritten, String failureCode) {}
```

生产实现 `NbtDurableStore`：

- 写 `target.resolveSibling(target.getFileName() + ".tmp")`；
- `NbtIo.writeCompressed(root, tmpPath)`（gzip、确定性键序）；
- `FileChannel.open(tmpPath, WRITE).force(true)`（fsync 数据+元数据）；
- `Files.move(tmp, target, REPLACE_EXISTING, ATOMIC_MOVE)`；平台不支持 ATOMIC_MOVE 时
  回退 `REPLACE_EXISTING` 并返回明确非原子代码；
- 任一步失败：删除 tmp（尽力），抛出/返回失败码，不动 target。

`DataManager` 通过静态工厂持有生产实现，测试通过注入 fake 实现注入各步骤失败。

### 3.4 World identity

- `DataManager.init`：从 `server.getWorldPath(LevelResource.DATA_DIR)` 解析数据目录，
  计算世界身份（server UUID + 数据目录规范化路径的 SHA-256 前缀）并写入根 `WorldIdentity`。
- 每次加载：校验；跨世界/缺失 -> fail closed（持久化不可用，依赖模块不可启动）。
- `commitModuleData` 提交前再次校验。

### 3.5 Lifecycle wiring

- `FontaineRepublic.onServerStopping`：先 `DataManager.beginShutdown()`，再 `saveAll()`，
  再 `coreManager.stopRuntime()`（保持现有顺序语义）。
- 启动时清理孤儿 `*.dat.tmp` 并记录日志。
- 损坏/外来根：启动拒绝加载，日志稳定错误码，不自动修复。

### 3.6 Bounds（实现设计建议值，评审确认）

| 项 | 建议默认 |
|---|---|
| 单命名空间提交字节上限 | 8 MiB |
| 提交最小间隔 | 100 ms（超出 -> RATE_GUARD） |
| 失败码集合 | THREAD_VIOLATION / UNINITIALIZED / STOPPING / WORLD_IDENTITY / IO_WRITE / IO_FSYNC / IO_RENAME / BOUNDS_EXCEEDED / RATE_GUARD |

这些值写入配置可调（ConfigManager 现有空配置项顺带提供读取骨架），但必须满足：最终
变更时提交前必须通过 `final mutation-time` 校验，不允许配置关闭校验。

---

## 4. Test Plan

### 4.1 依赖无关测试 `src/test/java/com/fontainerepublic/core/DurableCommitTestMain.java`

覆盖（对应 FR-CORE-002-A §8 矩阵）：

- 提交成功 -> COMMITTED、文件存在、内存一致；
- 注入 write/fsync/rename 失败 -> FAILED、内存/修订不变、旧文件权威；
- UNINITIALIZED / STOPPING / 跨线程 -> 对应状态；
- 跨世界根拒绝；损坏根拒绝；孤儿 tmp 清理；
- 确定性编码（同快照 -> 等价文件字节，gzip 头除外）；
- 速率保护与字节预算；
- 临时目录真实文件集成测试（成功+崩溃窗口模拟：提交前删 tmp / 提交后保留新态）。

### 4.2 Gradle

```groovy
tasks.register('durableCommitTest', JavaExec) {
    group = 'verification'
    description = 'Runs dependency-free FR-CORE-002 durable commit validation.'
    dependsOn testClasses
    classpath = sourceSets.test.runtimeClasspath
    mainClass = 'com.fontainerepublic.core.DurableCommitTestMain'
    systemProperty 'fontainerepublic.projectDir', project.projectDir.absolutePath
}
tasks.named('check') { dependsOn tasks.named('durableCommitTest') }
```

### 4.3 回归

- `gradlew build`（既有 command/network/player-data 验证 + durableCommitTest 全绿）。
- 真机运行时验证（服务端启动/停止、存档落盘）在派发完成后由运行时验证阶段补充
  （需要时请求 Human 协助）。

---

## 5. Acceptance Mapping（FR-CORE-002-A §8）

| 验收 | 落地证据 |
|---|---|
| Commit success | durableCommitTest 成功用例 + 集成测试 |
| Save/fsync/rename failure injected | 注入用例，内存不变 |
| Uninitialized/stopping/off-thread | 对应状态用例 |
| Crash windows | 临时目录模拟 |
| Cross-world / corrupt root | 拒绝用例 |
| Deterministic encode | 字节等价用例 |
| Autosave after commit | 回归构建 + 后续运行时验证 |
| Rate guard / bounds | 边界用例 |

---

## 6. Constraints

- 不改 `putModuleData`/`saveAll` 行为；不新增依赖；
- 服务端权威；模块边界；SavedData/NBT only；
- 提交后审查（Codex）-> ROUTE TO HUMAN / RETEST / BLOCK；
- 本文件与任务卡不改变 FR-CORE-002-A 批准范围。
