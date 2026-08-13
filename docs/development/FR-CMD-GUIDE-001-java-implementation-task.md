# FR-CMD-GUIDE-001 Java Implementation Task

> **Status:** Prepared — Authorized by Human directive "功能开发完成后提供引导"
> **Task Type:** Command/Help Wiring（引导）
> **Design Input:** FR-CMD-001-A（help 契约）
> **Dependency:** 已实现模块的命令面（money/citizen/government/parliament/court）
> **Authority Boundary:** 只扩展帮助/引导输出；不新增业务逻辑。

---

## 1. Goal

扩展 `/fr help` 为分模块引导：

- `/fr help` -> 分类列表；
- `/fr help money` / `citizen` / `government` / `parliament` / `court` / `institution` ->
  各模块命令与用途（有界文本）；
- 支持 en_us/zh_cn 语言文件；
- 对无权限/不可用模块给出受限提示（不泄露内部状态）。

## 2. 明确不实现

- 新业务逻辑/命令面；GUI；新依赖。

## 3. 具体契约

- help 节点挂载于现有 `/fr` 根（FRCommand）；
- 内容有界（每模块固定条目）；i18n 键放 lang 文件；
- 未知模块 -> 标准受限反馈；不枚举隐藏命令。

## 4. 测试计划

- `commandFoundationTest` 扩展：help 树/输出有界/未知模块反馈/语言键存在；
- `gradlew build` 回归。

## 5. 验收

- `/fr help <模块>` 可用且有界；无越权信息；`gradlew build` 全绿。

## 6. 约束

- 不改业务模块；不新增依赖；提交后审查（Codex）-> ROUTE TO HUMAN / RETEST / BLOCK。
