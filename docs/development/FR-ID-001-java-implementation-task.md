# FR-ID-001 Java Implementation Task

> **Status:** Prepared — Authorized by FR-PHASE2-HUMAN-APPROVAL-01
> **Task Type:** Core Infrastructure Implementation
> **Design Input:** FR-ID-001-A v1.0（docs/architecture/fr-id-001-a-unified-digital-subject-registry.md）
> **Dependency:** FR-CORE-002（durable gate，已实现）、FR-DATA-001（PlayerData，已实现）
> **Authority Boundary:** 本文件细化实现范围；不改变 FR-ID-001-A 契约。

---

## 1. Goal

实现统一数字主体登记册核心（FR-ID-001-A §3–§21）：

- `SubjectId`（内部不透明 UUID 标识）与公开 `RegistryNumber`（`TT-NNNNNN-CC`，MOD 97）；
- 类型范围（10 自然人启用；00 仅精确保留号；20+ 仅保留）；
- 不可变 `SubjectRecord` + 状态（ACTIVE/SUSPENDED/REVOKED/DISSOLVED）+ 修订；
- 四个索引（Subjects/Numbers/Owners/Reservations）严格双向校验；
- 单写入者 Repository + 严格编解码 + 失败关闭加载；
- 懒式、幂等、owner-thread 的 `ensurePlayerSubject(UUID)`（PlayerData -> 主体）；
- 两个固定号码（10-000001-61 / 00-000001-95）自首个快照起保留，普通分配永不产出；
- 精确、有界、非枚举的公开查询。

## 2. 明确不实现（设计内阻断/后续任务）

- **水神初始个人绑定（bootstrap）**：FR-ID-001-A §4.4/§17.1/§22.1 要求独立的
  审计式 Human 授权绑定设计；本任务只保留固定号码与 office 主体（由常量
  `OFFICE_ID:HYDRO_ARCHON` 幂等物化），原个人主体待 bootstrap 设计批准后实现。
- 企业/机构/城市适配器；公民、余额、权限；紧急动作目录；GUI/包。

## 3. 具体契约（关键点）

### 3.1 号码

- 规范形式 10 位 ASCII 数字；展示 `TT-NNNNNN-CC`；解析只接受规范或展示形式；
- 校验位：`numeric(TTNNNNNNCC) mod 97 == 1`，逐位取模，禁用浮点/locale；
- 序列从加密强随机源注入式生成；冲突重试有界；耗尽显式失败；绝不回退顺序分配；
- 类型 10 永久剔除序列 `000001`；类型 00 无随机池，只接受精确 `00-000001-95`；
- 号码永久不复用（REVOKED/DISSOLVED 记录保留即墓碑）。

### 3.2 存储

```text
fontainerepublic.dat
└── modules
    └── subject-registry
        ├── StoreVersion / StoreRevision
        ├── Subjects / Numbers / Owners / Reservations
        └── BootstrapState（仅保留标记与物化状态；不含水神当前 UUID 或 FR-EMG 权威）
```

- 严格编解码：仅声明字段、精确 NBT 类型、规范 UUID/号码、校验位、类型一致、
  时间戳/状态/修订为正、总量与字节上限；未知新版本拒绝；迁移在隔离副本上执行；
- 加载做 Subjects<->Numbers<->Owners 双向校验；重复/孤儿/错配整体拒绝（fail closed）；
- 提交：每次变更构建完整不可变快照 -> `DataManager.commitModuleData("subject-registry", ...)`
  -> 仅 `COMMITTED` 后发布。

### 3.3 服务

```java
// server/registry/api/SubjectRegistryService.java（概念）
interface SubjectRegistryService {
    SubjectId ensurePlayerSubject(UUID playerId);       // 懒、幂等、owner-thread
    Optional<SubjectRecord> resolvePlayer(UUID playerId);
    Optional<SubjectRecord> resolveNumber(RegistryNumber number); // 精确、有界
    SubjectStatus status(SubjectId subjectId);
}
```

### 3.4 生命周期

- 模块 id `subject-registry`；依赖 `player-data`；在 FR-CORE-002 之后注册；
- 启动先完整校验命名空间再暴露服务；失败关闭则依赖模块不可启动；
- 每次 ensure：PlayerData 存在 -> 主体缺失则创建（零余额无关，无公民/余额字段）。

## 4. 测试计划

`src/test/java/com/fontainerepublic/server/registry/SubjectRegistryFoundationTestMain.java`

- 号码生成/校验位（含两个固定号 mod 97 = 1）；解析规范化；畸形输入拒绝；
- 冲突重试/耗尽；永久不复用；类型 10 剔除 000001；类型 00 封闭异常；
- 固定号先于普通分配保留；owner 唯一（一 UUID 一主体）；重复 ensure 幂等；
- 注入保存失败无发布；跨世界/损坏快照失败关闭；确定性编码；
- 懒式顺序（PlayerData -> subject）；号码精确查询不枚举；
- `subjectRegistryFoundationTest` 接入 check；`gradlew build` 回归。

## 5. 验收映射

对应 FR-ID-001-A §19 验收矩阵（除 bootstrap 相关项）；bootstrap 项标记 BLOCKED
（依赖后续设计），不假装通过。

## 6. 约束

- 不改 FR-CORE-002/FR-AUD-001/PlayerData 行为；不新增依赖；
- 服务端权威；模块边界；SavedData/NBT only；
- 提交后审查（Codex）-> ROUTE TO HUMAN / RETEST / BLOCK。
