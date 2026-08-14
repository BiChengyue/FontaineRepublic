# FR-LAND-002-IMPL 任务卡

## 目标

在可选 FR 客户端的传讯水镜土地界面中，提供“我持有使用权的地块”只读视图。
查询由服务器权威 Land 服务按当前玩家主体限定，只返回当前有效权益，并支持有界
分页与刷新。

## Human 范围决定

- 手机端可查看；无客户端命令不可查看该个人权益列表。
- 只显示当前有效权益，不展示已过期或已撤销历史。
- 既有 `/fr land inspect|claim` 命令保持不变。
- 不新增独立运行时模块；投影生命周期归既有 Land 模块。

## 实现与修复

- LandRepository 增加可重建的 holder 派生索引，提交失败时索引与内存状态不变。
- LandService 增加 self-only、有界分页查询，使用确定性游标和 StoreRevision 漂移
  重置语义。
- 网络协议升至 v9，生产消息账本追加 ID 27 请求与 ID 28 页面，共 29 条消息；
  ID 27 执行 burst 2 / 每秒 10 次速率策略。
- 客户端增加“我的用地权益”页面、分页、刷新与关闭/断线缓存清理。
- 修复审查中发现的 RESET_REQUIRED 透传、游标/修订组合、负 revision、严格维度与
  枚举验证、Land 依赖可用性门控等问题。

## 审查与验证

- 独立 DSH 审查结论：APPROVE；Coordinator 完成重点复核。
- `gradlew.bat landRightsFoundationTest --console=plain`：PASS。
- `gradlew.bat build --console=plain`：BUILD SUCCESSFUL（38 tasks）。
- `git diff --check`：PASS。
- Dedicated Server 有效冒烟：`tmp/fr-land-002-runtime-02/evidence`，协议 v9、
  29 条生产消息、15 个运行时模块、Ready、干净关停、ExitCode=0。
- Human 真机 UI 验证尚待执行，不得描述为已完成。

## 提交

- 实现候选：`ecc0edd`（`feat(land): add mobile my-usage-rights view`）。
- develop 合并：`046bfb6`（`merge: mobile land usage-rights view (FR-LAND-002)`）。
