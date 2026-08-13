# FR-CLIENT-001 Java Implementation Task

> **Status:** Prepared — 待 Human"服务端确认 + 开始客户端"后转 Authorized
> **Task Type:** Client UI Module（首版可视化操作）
> **Design Input:** FR-CLIENT-001-A（候选）
> **Dependency:** FR-NET-001-A（channel）、已实现服务端模块命令面、架构 v2.7 §5
> **Authority Boundary:** 本文件细化实现范围；不改变可选客户端合约。

---

## 1. Goal

实现可选客户端模组首版：

- 经济：余额卡片、流水视图、转账表单（经命令/请求路径提交）；
- 公民：身份/等级卡片；
- 土地：地块信息视图（只读）；
- 机构：政府/议会/司法公开信息视图；
- 通知：入账/案件/法案提醒（HUD + 聊天回退）；
- 引导：客户端内帮助（链接 /fr help 与玩家指南）。

## 2. 明确不实现

- 客户端权威状态/存储/权限决策；业务逻辑；无客户端平价破坏；
- 权威 C2S 包（首版仅展示/转发）；GUI 全覆盖（后续修订）；新依赖。

## 3. 具体契约

### 3.1 包结构与接线

```text
com.fontainerepublic.client/
├── ClientManager.java        # DistExecutor.unsafeRunWhenOn(CLIENT) 初始化
├── gui/…                     # 屏幕（薄视图）
├── hud/…                     # HUD（余额/通知）
└── net/…                     # S2C 展示包处理
```

- 所有客户端注册走 DistExecutor + package 隔离；server 永不引用 client；
- 无 FR 客户端时：命令/聊天全功能保持（平价不破坏）。

### 3.2 网络

- S2C 展示包（非权威）：BalanceSync、TransactionNotify、Notification、InstitutionInfo；
- 经 FR-NET-001 注册；协议不匹配握手拒绝；
- 首版无权威 C2S：表单提交走命令路径或仅含操作+参数的请求包（服务端重校验）。

### 3.3 数据流

- 客户端缓存 = 乐观展示；每次变异由服务端最终裁决；
- 现场/设施从不由客户端断言（FR-INST-002）。

## 4. 测试计划

- `clientFoundationTest`（纯客户端逻辑，无需渲染：缓存非权威、路由、包解析拒绝）；
- 无客户端平价：服务端命令面回归（既有 foundation 测试）；
- `gradlew build` 全绿。

## 5. 验收

对应 FR-CLIENT-001-A §5；全部须有测试证据。

## 6. 约束

- 不改服务端已实现行为；不新增依赖；服务端权威；
- 提交后审查（Codex）-> ROUTE TO HUMAN / RETEST / BLOCK。
