# FR-DATA-003 Java Implementation Task

> **Status:** Prepared — Authorized by FR-PHASE2-HUMAN-APPROVAL-01
> **Task Type:** Core Infrastructure Implementation（PlayerData 存储扩展）
> **Design Input:** FR-DATA-003-A v1.0
> **Dependency:** FR-DATA-001（PlayerData 现有实现）、FR-CORE-002、FR-ID-001
> **Authority Boundary:** 本文件细化实现范围；不改变已批准设计契约。

---

## 1. Goal

在现有 `player-data` 命名空间内增加**安全精确姓名目录**（新 StoreVersion + Directory 段）：

- 精确名 -> 唯一 UUID 解析（`PlayerDirectoryService.resolveExactGameName`）；
- 永久歧义规则（第二个不同 UUID 使用同一规范化名 -> 永久 AMBIGUOUS）；
- 重命名/复用语义（RENAME 原子快照；RETIRED；回迁规则）；
- 登录验证路径唯一观察源；迁移确定性（MIGRATED_FROM_LAST_KNOWN_ONLY）；
- 严格编解码 + 双向交叉校验（Players <-> Directory）；失败关闭。

## 2. 明确不实现

- 主体/登记号/余额/公民/职位字段；前缀/模糊/枚举查询；GUI/包；
- Mojang/Microsoft 外部查询；管理员覆盖/歧义清除；JSON 备份。

## 3. 具体契约

### 3.1 存储（player-data 命名空间）

```text
player-data
├── StoreVersion: int（迁移到 v2）
├── StoreRevision: long
├── Players: <canonical UUID> -> PlayerData 记录（保留现有）
└── Directory: <normalized-name> -> Entry
    ├── DirectoryVersion / MigrationProvenance
    └── Entries: LastVerifiedSpelling / PermanentlyAmbiguous /
        UniqueHistoricalOwner / CurrentOwners / FirstObservedAt / LastObservedAt / EntryRevision
```

- 规范化名 = ASCII 小写（`[A-Za-z0-9_]{1,16}`）；输入不修剪；无 Unicode；
- `currentOwners` 确定性有序；每个 PlayerData 记录恰在一个 entry 的 currentOwners；
- 非歧义 entry 恰一个 uniqueHistoricalOwner；歧义 entry 无该字段；
- 迁移：按 lastKnownGameName 分组 -> 单 UUID 唯一 / 多 UUID 永久歧义；provenance 记录。

### 3.2 服务

```java
// server/playerdata/api/PlayerDirectoryService（概念）
interface PlayerDirectoryService {
    PlayerNameResolution resolveExactGameName(String input); // 关闭结果类型
}
```

结果类型：UNIQUE_CURRENT(UUID) / UNKNOWN / RETIRED / AMBIGUOUS / INVALID_INPUT；
仅 UNIQUE_CURRENT 携带 UUID；UNKNOWN/RETIRED/AMBIGUOUS 对外合并为同一受限消息。

### 3.3 提交语义

- 登录观察（改名）构造一个包含 Players+Directory 的完整替换快照；
- 校验 -> 编码 -> `DataManager.commitModuleData("player-data", ...)` -> COMMITTED 后发布；
- 失败：Players/Directory/所有修订不变；陈旧修订拒绝并重算。

## 4. 测试计划

`src/test/java/com/fontainerepublic/server/playerdata/PlayerDirectoryFoundationTestMain.java`

- 首次登录/未变登录/仅大小写；改名（X->Y 原子）；回迁（唯一可恢复/歧义保持）；
- 双 UUID 历史同名 -> 永久歧义；迁移分组确定性；迁移失败原样保留；
- 注入保存失败无发布；重启恢复（Players+Directory 交叉一致）；
- 严格编解码（未知字段/类型/孤儿/重复拒绝）；无枚举 API；确定性编码；
- `playerDirectoryFoundationTest` 接入 check；`gradlew build` 回归（含既有 playerDataTest）。

## 5. 验收映射

对应 FR-DATA-003-A §15 矩阵；全部须有测试证据。

## 6. 约束

- 保持现有 PlayerData 记录/修订不变（迁移不重写）；不新增依赖；
- 服务端权威；SavedData/NBT only；Repository 单写入者；
- 提交后审查（Codex）-> ROUTE TO HUMAN / RETEST / BLOCK。
