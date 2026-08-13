# FR-CORE-002-IMPL 派发提示词（deepseek v4 flash 实现子进程）

> 依据 tools/dispatch-PROMPT.template.md 填写。Human 已批准（FR-PHASE2-HUMAN-APPROVAL-01）。

## 1. 身份与目标

- 你是 FontaineRepublic（Minecraft Forge 1.20.1 / Forge 47.4.18 / Java 17）的实现子进程。
- 任务 ID：FR-CORE-002-IMPL；目标：按 FR-CORE-002-A 实现核心持久化确认门
  （`DataManager.commitModuleData`：完整根快照原子写入 + fsync + 替换，返回可确认结果）。
- 只实现本任务；不得提前实现 Audit/Citizen/Land/Economy。

## 2. 工作目录与必读文档

- 工作目录（隔离 worktree）：`D:\MC\FontaineRepublic\deepseek-worktrees-v2\fr-core-002-impl`
  （从 develop 创建，代码基线 = Phase 1 完成态）。**只在此目录内修改。**
- 开工前完整阅读（按顺序）：
  1. `docs/governance/continuous_development_ops.md`（运行手册）
  2. `CLAUDE.md`（项目规则与状态）
  3. `docs/architecture/fr-core-002-a-durable-commit-gate.md`（本任务设计，规范）
  4. `docs/ai_recovery/task_cards/FR-CORE-002-IMPL-task-card.md`（任务卡）
  5. 现有实现参考：`src/main/java/com/fontainerepublic/core/DataManager.java`、
     `ModSavedData.java`、`server/playerdata/persistence/PlayerDataRepository.java`
     （单写入者/注入存储测试模式）
  6. 测试参考：`src/test/java/com/fontainerepublic/server/playerdata/PlayerDataTestMain.java`

## 3. 执行约束（违反即失败）

- 不得读取或修改其他 worktree、E:\Reasonix、生产服务器；不得 SSH/部署/推送。
- 不得修改 FR-CORE-002-A 设计文档、CLAUDE.md、状态台账、任务卡（问题写进交付报告）。
- 不得引入新依赖；不得实现 WAL/分片文件/JSON/数据库；不得动业务模块。
- 服务端权威与模块边界保持；持久化只用 SavedData/NBT。
- 开始时报告：当前分支、HEAD、工作区状态。

## 4. 必须实现的验收（FR-CORE-002-A §8 矩阵）

- `commitModuleData(name, snapshot)` 返回 COMMITTED / FAILED / UNINITIALIZED / STOPPING；
- 完整根快照临时文件写入 -> fsync -> 原子替换；成功后才替换内存并允许下游可见；
- 失败时内存/修订/已发布文件不变；UNINITIALIZED/STOPPING/跨线程均拒绝；
- 世界身份校验（跨世界根文件拒绝）；损坏根文件 fail closed；孤儿 *.dat.tmp 启动清理；
- 确定性编码；autosave 与 commit 不产生分歧；提交频率保护；
- 注入式存储适配器测试（各步骤失败注入）+ 临时目录集成测试 + 构建回归；
- `gradlew build` 通过（含既有 command/network/player-data 验证）。

## 5. 交付格式（统一）

```text
任务：FR-CORE-002-IMPL
分支：{branch}
提交：{commit}
修改文件：{列表}
测试命令与结果：{命令 + 输出摘要}
未解决问题：{列表}
是否有越界文件：是/否（说明）
对下一任务的接口说明：{DataManager.commitModuleData 契约摘要}
```

## 6. 提交前自检

- [ ] 每条验收有测试/证据，无"凭印象 PASS"
- [ ] 未触碰任务外文件；未引入依赖
- [ ] `gradlew build` 通过
- [ ] 报告含 commit、测试结果、风险、接口说明
