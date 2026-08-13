# FR-EMG-001 Java Implementation Task

> **Status:** Prepared — Authorized by Human"继续做完服务端暂停的任务"
> **Task Type:** Shared Emergency Infrastructure
> **Design Input:** FR-EMG-001-A（FIX-01）；FR-ECO-001-C §9-12（economy 目录）
> **Dependency:** FR-CORE-002（持久化门 ✅）、FR-CMD-001-A（admin 保留）、
> FR-AUD-001、FR-ID-001（bootstrap 控制台分类器可复用）、FR-DATA-001
> **Implementation Gates:** 4 个门中：①持久化门 ✅；②命令适配、③审计段、
> ④权威配置启动需本任务/后续设计落实
> **Authority Boundary:** 本文件细化实现范围；不改变设计契约。

---

## 1. Goal

实现共享紧急权限基础设施（FR-EMG-001-A）：

- 演员验证：配置水神 UUID + `SERVER_CONSOLE`（真实本地控制台，复用/对齐 bootstrap
  分类器）；普通 OP/RCON/命令方块/函数拒绝；
- 两段式 preview/confirm（30s 单次 token，ISSUED->CLAIMED->CONSUMED）；
- 共享尝试/配置日志（append-only、摘要链、段式）；
- 审计信封 + 字段分类（PUBLIC/AUTHORIZED_SUMMARY/SECRET_DIGEST_ONLY/NEVER_RECORD）；
- `/fr admin emergency <module> <action> ...` 命令适配（admin 保留下挂接）；
- 业务收据提供方 + 对账（economy.issue/reclaim 等后续目录接入）；
- 权威配置启动（首次 bootstrap 类似 FR-ID-BOOTSTRAP：控制台初始设置水神 UUID）。

## 2. 明确不实现

- 业务动作本体（economy.issue/reclaim 属 Economy 提供方，本任务只建共享基础设施
  与注册契约）；GUI/包；任意数据编辑接口；新依赖。

## 3. 具体契约

### 3.1 共享服务（server/emergency/）

```java
interface EmergencyService {
    PreviewResult preview(EmergencyRequest request, ActorSource source);
    ConfirmResult confirm(String token);
    InspectResult inspect(String actionId);          // 受限投影
    ConfigureResult stageAuthority(ConfigureRequest r, ActorSource source); // 控制台
}
```

- token 表服务端运行态；30s 过期、单次、绑定 actor/module/action/参数/修订；
- 最终变异边界复检（actor/来源/epoch/参数/修订/能力）；
- 失败关闭：权威/目标/审计任一项不可用时禁止动作。

### 3.2 尝试/配置日志（紧急命名空间）

```text
emergency
├── StoreVersion / StoreRevision
├── Config: authority UUID（digest）、revision、阶段（UNSET/STAGED/ACTIVE）
├── Attempts: 段式 append-only（PrevDigest/SelfDigest）
└── ReceiptIndex: 提供方 watermark（COMPLETE_THROUGH/INCOMPLETE）
```

- 段关闭不重写；容量/归档边界有界；
- 对账：提供方 ACTIVE 后按 watermark 导入缺失收据、校验链。

### 3.3 命令适配

- `/fr admin emergency` 挂于保留 admin 下（FR-CMD-001 对齐）；
- 执行时解析当前 ACTIVE EmergencyService；不捕获运行态服务；
- 控制台/水神通道走同一验证流；普通 OP/RCON 拒绝。

### 3.4 权威配置启动

- 首次：真实本地控制台设置水神 UUID（audited，类似 bootstrap）；
- 变更：staged -> 下次启动校验 digest+revision 接受；
- 漂移：fail closed，控制台恢复路径。

## 4. 测试计划

`src/test/java/com/fontainerepublic/server/emergency/EmergencyFoundationTestMain.java`

- 演员分类矩阵（水神 UUID/SERVER_CONSOLE/RCON/命令方块/函数/玩家/OP）；
- preview/confirm：token 过期/单次/参数篡改/并发拒绝；ISSUED->CLAIMED->CONSUMED；
- 尝试日志摘要链、段关闭、篡改检测；对账 watermark；
- 权威配置：bootstrap/staged/漂移失败关闭/控制台恢复；
- 命令适配：admin 挂接、越权拒绝、运行时解析；
- 注入失败无发布；重启恢复；严格编解码；
- `emergencyFoundationTest` 接入 check；`gradlew build` 回归。

## 5. 验收

对应 FR-EMG-001-A §20 门（除业务目录实现与真机）；全部须有测试证据。

## 6. 约束

- 不实现业务动作（economy 目录单独接入）；不新增依赖；服务端权威；
- 提交后审查（Codex）-> ROUTE TO HUMAN / RETEST / BLOCK。
