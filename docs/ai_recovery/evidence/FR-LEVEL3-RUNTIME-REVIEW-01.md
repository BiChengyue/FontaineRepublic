# Runtime Verification Record — FR-LEVEL3-RUNTIME-REVIEW-01

> Level 3 真机运行时验证（Dedicated Server 启动/关闭/落盘）
> 证据来源：①Human 的 `gradlew runServer` 日志（21:12）；②Reviewer 独立直连冒烟运行
> （21:15，新世界 smoke-20260813）。

---

## 1. 启动核验

| 项 | 结果 | 证据 |
|---|---|---|
| `Done (6.281s)! For help, type "help"` | PASS | smoke log |
| 11 个模块初始化（network/player-data/audit/subject-registry/citizen/land/economy/
  institution-access/government/parliament/justice） | PASS | 两条独立日志一致 |
| `Runtime startup complete: 11 available, 0 unavailable` | PASS | 同上 |
| 无 ERROR/Exception（除无害的 netty/jdk 警告） | PASS | 日志核验 |

## 2. 关停核验

| 项 | 结果 |
|---|---|
| `[DataManager] Saved` | PASS |
| 11 模块逆序关闭（justice→parliament→government→institution-access→economy→land→
  citizen→subject-registry→audit→player-data→network） | PASS |
| 世界保存（主世界/DIM-1/DIM1 `All chunks are saved`） | PASS |
| `[CoreManager] Runtime scope closed` + ExitCode=0 | PASS |
| `fontainerepublic.dat` 落盘（548 字节） | PASS |

## 3. Findings

### F-001（已修复）— 主仓库 build/classes 过期导致启动失败
- Reviewer 首轮冒烟发现 `NoClassDefFoundError: LoginProvisioningHook`：
  合并命令接线等模块后主仓库未重新编译。主仓库 `gradlew build` 后重跑通过。
- 教训：任何合并后、给玩家发布前，必须先在主仓库完整构建。

### F-002（Minor, Open）— 旧版 world 存档兼容
- `run/world/data/fontainerepublic.dat` 为 2026-07-28 早期原型（60 字节，无
  FormatVersion/WorldIdentity）。若在现有 `world` 上运行，加载可能失败关闭。
- 建议：正式使用新世界，或将旧原型文件移除以让 Mod 生成新档；
  如需旧档兼容需另行批准的迁移设计。

### F-003（NOT TESTED，可选）— 客户端加入与命令核验
- 本记录未包含：真实客户端加入、`/fr` 命令输出、崩溃窗口（强杀）测试。
- 清单：docs/development/LEVEL3-RUNTIME-VERIFICATION-CHECKLIST.md 第 4-5 步。

---

## Conclusion

服务器端模组**启动/关闭/存档落盘验证通过**（11 模块全绿，干净退出）。
剩余可选核验（客户端加入/命令/崩溃窗口）待 Human 方便时执行。
