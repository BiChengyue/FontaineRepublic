# Level 3 真机运行时验证清单（Dedicated Server）

> 适用：全部已实现模块（Core/审计/主体/水神绑定/公民/土地/经济/玩家目录/命令/
> 机构边界/政府/议会/司法），共 11 个运行时模块。
> 用途：Human 协助运行时验证（Level 3）。
> 说明：以下为最小验证集；每条给出"看什么、怎么算通过"。

---

## 0. 准备

- 确认 `gradlew build` 通过（已由 Codex 验证，可跳过）。
- 用开发目录启动专用服务器（与 2026-08-02 证据包相同方式：
  `runServer` 或 Gradle `runServer`，目录 `run/`）。
- 准备一个能开服/关服的控制台（终端窗口即可）。

## 1. 启动核验

用项目自带方式启动专用服务器（在 D:\MC\FontaineRepublic 目录）：

```powershell
.\gradlew.bat runServer
```

（或用你之前验证时的直接启动方式。）日志中应依次出现：

```text
[FontaineRepublic] Core initialized
[Command] Registered /fr for DEDICATED with 0 contributions
[DataManager] Initialized for world <identity>
[CoreManager] Module initialized: network
[CoreManager] Module initialized: player-data
[CoreManager] Module initialized: audit
[CoreManager] Module initialized: subject-registry
[CoreManager] Module initialized: citizen
[CoreManager] Module initialized: land
[CoreManager] Module initialized: economy
[CoreManager] Module initialized: institution-access
[CoreManager] Module initialized: government
[CoreManager] Module initialized: parliament
[CoreManager] Module initialized: justice
[CoreManager] Module initialized: emergency
Done (X.XXXs)! For help, type "help"
```

**通过条件：** 上述 12 个模块初始化日志全部出现；无 ERROR/Exception；`Done ...` 出现。

## 2. 存档落盘核验

进服后执行控制台命令：

```text
stop
```

观察日志依次出现：

```text
[DataManager] Saved
[Audit] Runtime closed
[SubjectRegistry] Runtime closed
[Citizen] Runtime closed
[Land] Runtime closed
[Economy] Runtime closed
[InstitutionAccess] Runtime closed
[Government] Runtime closed
[Parliament] Runtime closed
[Justice] Runtime closed
[Network] Runtime closed and transport state cleared
[Audit] Runtime closed
[CoreManager] Runtime scope closed
Saving players / Saving worlds / All chunks are saved (主世界/DIM-1/DIM1)
```

然后检查：

- `run/world/data/fontainerepublic.dat` 存在且非空；
- 同目录无 `fontainerepublic.dat.tmp` 残留；
- 服务器进程退出码为 0。

**通过条件：** 上述全部满足。

## 3. 重启恢复核验

再次启动服务器（同一世界目录）：

- 日志出现 `[DataManager] Initialized for world <同一 identity>`；
- 无 WORLD_IDENTITY / LOAD_FAILED 报错；
- 模块正常启动，`subject-registry` 初始化日志与上次一致（revision 不倒退）。

**通过条件：** 无失败关闭报错，identity 一致。

## 4. 命令核验（可选但推荐）

给玩家 OP 后执行（客户端或控制台）：

```text
/fr help
/fr admin status        -> modules: 12; active: 12; unavailable: 0
/fr admin modules       -> 12 个模块均 available
/fr money balance       -> 本人余额反馈
/fr citizen info        -> 本人公民状态
/fr bank balance        -> 国库总额（只读公开）
/fr help money | citizen | bank -> 分模块引导输出
```

**通过条件：** 输出与上一致；无 `Runtime unavailable`。

## 4.5 央行真机核验（设施/终端就绪后）

- 在央行设施注册终端前交互获得现场上下文后：
  - `/fr bank deposit <玩家> <金额>` -> 发钞成功、供给守恒；
  - `/fr bank withdraw <玩家> <金额>` -> 回收成功、供给守恒；
  - `/fr bank freeze <玩家>` -> 该玩家转账/提取被拒；
  - 离开设施后重试同一操作 -> 现场上下文失效被拒（无变异）。

**通过条件：** 现场门控生效、供给恒等式成立、冻结双向拒绝。

## 5. 崩溃窗口测试（可选，进阶）

- 进服后运行数秒，直接强制结束服务器进程（任务管理器结束）；
- 重启同一世界；
- 观察：若有 `*.dat.tmp` 残留则被清理并记录 WARN；数据文件能正常加载。

**通过条件：** 重启后模块可用；无自动"修复"或数据错乱。

---

## 结果回传格式

```text
启动日志：通过/不通过（附关键行）
落盘：通过/不通过（附文件大小与退出码）
重启恢复：通过/不通过
命令输出：通过/不通过
崩溃窗口：通过/不通过/未做
异常：{任何 ERROR/异常行}
```

Codex 将依据回传结果出具运行时验证结论并更新审查记录。
