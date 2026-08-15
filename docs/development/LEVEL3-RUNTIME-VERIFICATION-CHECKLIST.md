# Level 3 真机运行时验证清单（Dedicated Server）

> 适用：全部已实现模块（网络/玩家数据/审计/主体登记/公民/土地/经济/机构访问/
> 政府/议会/司法/紧急/通信信函/土地认领），共 **14 个运行时模块**；
> 通信交易为 Secure Trade 独立子信道（fontainerepublic:trade），不计入模块运行时。
> 用途：Human 协助运行时验证（Level 3）。
> 说明：以下为最小验证集；每条给出"看什么、怎么算通过"。部分需要 FR 客户端
> 连服、部分需要两个在线玩家，逐条标注。
> 重要：自动化专用服务器 smoke 是构建期/启动期探针，**不能代替** Human 对
> 客户端界面与交易/信函/土地认领等业务流程的界面与端到端业务核验（见 §结果回传格式）。

---

## 0. 准备

- 确认 `gradlew build` 通过（已由 Codex 验证，可跳过）。
- 用开发目录启动专用服务器（与 2026-08-02 证据包相同方式：
  `runServer` 或 Gradle `runServer`，目录 `run/`）。
- 准备一个能开服/关服的控制台（终端窗口即可）。
- **必须使用新世界**：`run/server.properties` 的 `level-name` 须为新名字
  （当前 `world-fr-v1`）。旧世界 `run/world` 含 7/28 原型 60 字节旧根，
  严格解码失败会令 DataManager 进入 `LOAD_FAILED`，所有模组写入（含紧急
  bootstrap）被拒；旧世界文件夹保留作备份，勿删除。
- 需要两批验证材料：
  - **单玩家验证**（一人即可）：启动、落盘、重启恢复、命令、央行真机、
    紧急权限、客户端展示面、本人当前用地权益、土地认领 inspect/claim、
    信函收发/读取/删除。
  - **双玩家验证**（需两名在线玩家、均手持传讯水镜）：通信交易全流程。

## 1. 启动核验

用项目自带方式启动专用服务器（在 D:\MC\FontaineRepublic 目录）：

```powershell
.\gradlew.bat runServer
```

（或用你之前验证时的直接启动方式。）日志中必须出现以下事实。Forge 1.20.1
可能先记录 `Done`，再触发本模组的 `ServerStartingEvent` 运行时初始化，因此不得仅按
日志的先后位置误判；执行任何业务命令前，14 个模块必须全部进入 ACTIVE：

```text
[FontaineRepublic] Core initialized
[Command] Registered /fr for DEDICATED with N contributions
[DataManager] Initialized for world <identity>
[CoreManager] Module initialized: network
[CoreManager] Module initialized: player-data
[CoreManager] Module initialized: audit
[CoreManager] Module initialized: subject-registry
[CoreManager] Module initialized: emergency
[CoreManager] Module initialized: citizen
[CoreManager] Module initialized: land
[CoreManager] Module initialized: institution-access
[CoreManager] Module initialized: landclaim
[CoreManager] Module initialized: economy
[CoreManager] Module initialized: government
[CoreManager] Module initialized: parliament
[CoreManager] Module initialized: justice
[CoreManager] Module initialized: mail
Done (X.XXXs)! For help, type "help"
```

**通过条件：** 上述 **14 个模块**初始化日志全部出现（模块 ID `network` →
`landclaim`/`mail` 的依赖拓扑共 14 行，不要求与 `Done` 严格先后）；后续出现
`[Mail] …`、`[LandClaim] Runtime initialized (halfWidth=…, height=…, zone=…)`
等已绑定服务日志；Secure Trade 子信道 `fontainerepublic:trade` 注册成功；
无 ERROR/Exception；`Done ...` 出现。

## 2. 存档落盘核验

进服后执行控制台命令（单玩家即可）：

```text
stop
```

观察日志依次出现（模块关闭按启动逆序；`[DataManager] Saved` 先于模块关闭）：

```text
[DataManager] Saved
[Mail] Runtime closed
[Justice] Runtime closed
[Parliament] Runtime closed
[Government] Runtime closed
[Economy] Runtime closed
[LandClaim] Runtime closed
[InstitutionAccess] Runtime closed; contexts cleared
[Land] Runtime closed
[Citizen] Runtime closed
[Emergency] Runtime closed
[SubjectRegistry] Runtime closed
[Audit] Runtime closed
[PlayerData] Runtime closed
[Network] Runtime closed and transport state cleared
[CoreManager] Runtime scope closed
Saving players / Saving worlds / All chunks are saved (主世界/DIM-1/DIM1)
```

然后检查：

- `run/world/data/fontainerepublic.dat` 存在且非空；
- 同目录无 `fontainerepublic.dat.tmp` 残留；
- 服务器进程退出码为 0。

**通过条件：** 上述全部满足。

## 3. 重启恢复核验（单玩家即可）

再次启动服务器（同一世界目录）：

- 日志出现 `[DataManager] Initialized for world <同一 identity>`；
- 无 WORLD_IDENTITY / LOAD_FAILED 报错；
- 模块正常启动，`subject-registry` 初始化日志与上次一致（revision 不倒退）；
- （若 §4.6 后执行）`/fr admin emergency status` 记录数与 watermark 不倒退。

**通过条件：** 无失败关闭报错，identity 一致。

## 4. 命令核验（可选但推荐；单玩家，给玩家 OP 后执行）

客户端或控制台：

```text
/fr help
/fr admin status        -> modules: 15; active: 15; unavailable: 0
/fr admin modules       -> 14 个模块均 available
/fr money balance       -> 本人余额反馈
/fr citizen info        -> 本人公民状态
/fr bank balance        -> 国库总额（只读公开）
/fr help money | citizen | bank -> 分模块引导输出
```

**通过条件：** 输出与上一致；无 `Runtime unavailable`。

## 4.5 央行真机核验（设施/终端就绪后；OP/控制台）

- 在央行设施注册终端前交互获得现场上下文后：
  - `/fr bank deposit <玩家> <金额>` -> 发钞成功、供给守恒；
  - `/fr bank withdraw <玩家> <金额>` -> 回收成功、供给守恒；
  - `/fr bank freeze <玩家>` -> 该玩家转账/提取被拒；
  - 离开设施后重试同一操作 -> 现场上下文失效被拒（无变异）。

**通过条件：** 现场门控生效、供给恒等式成立、冻结双向拒绝。

## 4.6 紧急权限真机核验（FR-EMG + FR-EMG-ECO-001，依赖 FR-EMG-CMD-001 命令面；控制台）

进入控制台执行（仅本地专用服务器控制台可 bootstrap/recover）：

```text
/fr admin emergency bootstrap <水神UUID> <理由>   -> ACTIVE（config revision 1）
/fr admin emergency status                       -> phase=ACTIVE、摘要、记录数
/fr admin emergency preview economy issue 1.0.0 PLAYER_UUID <目标UUID> DEBUG <理由> amount 100
                                                 -> 单次 token + 到期时间
/fr admin emergency confirm <token>              -> 成功（attempt id）
/fr money balance                                -> 目标玩家余额 +100、供给 +100
/fr admin emergency preview economy reclaim 1.0.0 PLAYER_UUID <目标UUID> DEBUG <理由> amount 999
                                                 -> INSUFFICIENT_FUNDS 拒绝
/fr admin emergency inspect <attemptId>          -> 有界脱敏摘要（无明文金额/原因）
```

再验：

- token 二次 confirm -> 拒绝（单次）；等待 >30s 后 confirm -> 过期拒绝；
- 命令方块/RCON/函数来源执行 -> 服务端拒绝（即使解析到达回调）；
- `/fr admin emergency stage <新UUID> <理由>` 后重启 -> STAGED 接受或漂移 fail
  closed；`recover` 仅控制台可修复；
- 重启后 `/fr admin emergency status` 记录数与 watermark 不倒退；
- 存储失败路径（如需）：注入失败后 confirm 拒绝且余额/供给/receipt 不变。

**Human 已确认（2026-08-14）：** 紧急 issue/reclaim 不经过官方冻结门，允许对冻结
账户执行（FR-EMG-ECO-001-REVIEW-01 F-002）。这是预期的 break-glass 行为，限定用于
调试、修正、补偿、救灾和紧急响应；仍须通过水神/控制台来源门、预览确认、单次短时
token、金额与目标绑定、最终复验及完整持久审计。账户冻结不得成为上述紧急职责的绕过
防线，也不得因该裁决削弱普通经济路径的冻结约束。

**通过条件：** 上述命令面全部可用；来源门槛与 token 生命周期生效；供给恒等成立；
receipt/watermark 持久化且可 inspect。

## 4.7 客户端展示面真机核验（FR-CLIENT-001 Stage A/B + FR-TRADE/MAIL/LAND-CLAIM 展示，需 FR 客户端连服）

使用带 FontaineRepublic 模组的 Forge 客户端连接（协议 **v9**）：

- 服务端日志出现 `Channel fontainerepublic:main protocol 9 frozen with 29
  production messages`；无客户端类加载/握手拒绝；
- 登录后客户端应收到余额与待读通知展示包；随后收到信函同步（mailbox sync）
  与未读提醒（仅当背包携带传讯水镜时）。
- 打开各界面（**需手持传讯水镜**，主/副手任一；未手持时命令被拒并给出聊天提示）：
  - `/frclient`（主界面/余额卡入口）、`/frclient money`（余额/转账/流水）、
  - `/frclient mail`（信函：收发/读取/删除）、`/frclient citizen`（公民卡）、
  - `/frclient history`（历史）、`/frclient notifications`、
  - `/frclient government`（部门摘要）、`/frclient parliament`（提案摘要）、
  - `/frclient court`（法院摘要）、`/frclient land`（土地概况）、`/frclient guide`；
- `/frclient` 命令树共直接打开 **11 个界面**（主界面 + 10 个子命令界面）；
  `Land` 界面中的“我的地块”再打开第 **12 个**嵌套界面“我的用地权益”，
  其专项验证见 §5.7；右击方块打开的 `LandLocationScreen` 属于土地认领交互，
  不计入 `/frclient` 界面数量；
- 进行一笔 `/fr money pay` 后，收款/付款方收到交易通知与余额刷新；
- **无 FR 模组的 Forge 客户端仍可连接并使用全部命令/聊天功能**（no-client
  parity；服务端不向其发送 FR 包；旧版模组客户端在握手时被拒）；
- 旧协议（v8 及更早）模组客户端被握手拒绝（协议 v9，仅接受原样匹配或 ABSENT）；
- 登出后客户端展示缓存清空（重进不显示旧数据）。

**通过条件：** 握手、登录同步、11 个 `/frclient` 直接界面及“我的用地权益”
嵌套界面（共 12 个）、通信器持有门禁、交易通知、无客户端平行性、登出清理
全部成立。

---

## 5. 通信交易真机核验（FR-TRADE-001；**需两名在线玩家、均手持传讯水镜**）

仅客户端（C2S 路径：右击玩家发起；FR 客户端 /frclient 交易界面展示/输入）：
- 发起方右击目标玩家（手持传讯水镜）-> 发送交易请求；或经 /frclient 界面发起。
- 目标玩家响应接受 -> 会话进入 OPEN。
- 双方各自出价：
  - 货币出价（`/fr money` 之外，交易界面的金额槽）；
  - 物品出价（4 个槽位，各选 1 个主背包槽，来源槽按 `[0, 35]` 索引；某槽撤下 =
    空槽）；
- 双方均"同意"后会话进入 **5 秒（100 tick）LOCKED 确认窗口**；窗口内任一方
  改价/撤槽会将会话退回 OPEN 并清除双方同意；
- 5 秒到期自动结算（main-thread 原子）：
  - **卖家/付款方税**：`floor(出价额 * taxRatePercent / 100)`（默认 **5%**，
    可配置），按付款方各自计；结算失败整单拒绝（fail closed）；
  - **来源槽重校验（防复制）**：执行时逐槽重读原背包槽，物品/数量/NBT 与
    出价快照不一致 -> 整单拒绝；
  - **接收方背包容量预校验**：任一人接收背包放不下 -> 整单拒绝；
- 失败路径（均应整单拒绝、无部分结算）：
  - 余额不足（结算 -> INSUFFICIENT_FUNDS）；
  - 接收背包满；
  - 任一方断线/登出 -> 会话被取消（无 escrow，无退款需求）；
  - 任一方在 LOCKED 期不再在线或不再手持设备 -> 拒绝。

**通过条件：** request/respond/offer(money/item)/agree 全链路；5 秒锁定窗口生效；
默认 5% 付款方税入账且供给守恒；来源槽重校验防复制；余额不足/背包满/断线三类
失败均整单拒绝；取消/断线不产生部分结算或丢物。结算与税仅发生在确认且执行成功时。

## 5.5 通信信函真机核验（FR-MAIL-001；单玩家即可，收件人可在线或离线）

服务端/命令与客户端均可；收件人解析支持 玩家名 / UUID / 公开登记号 / 机构：
- **即时发送（个人发件人）**：上传信件，费用为 **邮费 10 + 每份附件 100**（附件数
  = 金钱附件 1 + 物品槽数，各计一份）→ 从发件人个人账户扣费入国库；余额不足则拒；
- **机构路径免费用**：仅当发件人具有机构发件资格（政府/议会/法院/央行/部门）时，
  机构发件免邮费/附件费、且不能携带金钱附件；机构收件仍可接收；
- **list / read / delete**：读取视口列出直接邮件与派生公告；读一封直接邮件 =
  标记已读并**在读取瞬间原子领取**附件：
  - 金钱附件：读取时从发件人转账入收件人账户（claim-on-read），防重复凭
    `moneyDelivered` 标志；
  - 物品附件：放入收件人背包，能放则放；**放不下（背包满）则留在邮件中待后续领取**
    （部分领取，`STATUS_PARTIAL`）；完全领取后邮件不可再取；
- **授权机构公告广播**：政府/议会/法院/央行/部门源可公告全体公民；同一机构
  广播有 **冷却（默认 5 分钟 / 300000 ms）**，冷却内再发被拒（CODE_COOLDOWN）；
- **HUD/聊天提醒仅当携带传讯水镜**：新邮件提醒与广播提醒仅在收件人/公民在线且
  背包（任意槽）**携带传讯水镜**时推送；未携带只落库存邮件、不弹提醒；
- 登出/发布均在 FR-CORE-002 持久化门之后，落盘可重启恢复。

**通过条件：** 个人邮费=10+100×附件数正确扣费；机构免费路径生效；list/read/delete
一致；金钱附件读取即入账、物品附件满背包暂留；公告冷却生效；HUD/聊天提醒仅在携带
水镜时出现；重启后邮件不丢。

## 5.6 通信土地查看/认领真机核验（FR-LAND-CLAIM-001；单玩家即可）

两条等价入口（服务端不可区分；无绕过）：
- 客户端：手持传讯水镜 **右击方块** -> 打开该位置的土地方块视图（携带方块坐标与
  当前维度），自动发送 inspect，可认领时再发 claim；
- 无 FR 模组客户端：`/fr land inspect <x> <y> <z>`、`/fr land claim <x> <y> <z>`
  （面向当前玩家当前位置与维度）。

核验点：

- **inspect**：未认领且可认领 -> `claimable`；已认领 -> 返回地块 ID、分区、
  访问级别与边界摘要（不返回持有人）；
  默认规划区域会与相邻地块重叠时如实报 `CLAIM_OVERLAP`（inspect 承诺真实）；
- **默认规划区域**：以点击/坐标方块为中心，**半宽 1 → 3×3 地表**、高 8
  （半宽与高度可配置，半宽上限 16），区划默认 `RESIDENTIAL`、访问 `PRIVATE`；
  地块归属永久 `REPUBLIC`，认领在同一原子快照内授予创领人**非限期的使用权**
  （立即可建）；
- **重叠拒绝**：认领区域与相邻地块重叠 -> 拒绝（`CLAIM_OVERLAP`）；
- **门禁**（任一 不满足即 fail closed）：
  - 仅玩家（非玩家来源拒）；维度串须合法且与玩家当前维度一致（`WRONG_DIMENSION`）；
  - 需手持传讯水镜（`NOT_HOLDING`）；
  - 目标方块须已加载（`NOT_LOADED`）；
  - 目标须在玩家交互距离内（`OUT_OF_REACH`）；
- **重启持久化**：认领落盘（land 持久化门），重启后地块/使用权仍在；
- **无方块激活穿透**：手持水镜右击被认领视口接管的方块时，**不额外触发** 该方块
  原本的交互（箱子/门/按钮/拉杆不会同时被激活）——land 视图 *取代* 方块正常使用；
  且不以空白指针落地（SUCCESS 完成交互，客户端停止重试）。

**通过条件：** 右击方块与无客户端 `/fr land inspect|claim` 两条路径都可用且结果一致；
默认 3×3×8 区域正确；重叠/维度/手持/加载/距离门禁生效；重启后地块与使用权仍在；
右击认领不穿透激活方块。

## 5.7 本人用地权益真机核验（FR-LAND-002；单玩家、FR 客户端）

1. 使用安装 FontaineRepublic 模组的客户端登录，并在主手或副手手持传讯水镜；
2. 执行 `/frclient land`，在土地概况界面点击“我的地块”，进入“我的用地权益”；
3. 确认列表只显示当前登录玩家本人**当前有效**的使用权，不显示他人的地块，也不显示
   已撤销或已到期的历史权益；无权益时应显示空状态而非报错；
4. 权益超过一页时点击 `>`/Next 读取下一页；点击 `Refresh` 回到最新第一页；
   服务端存储修订在翻页期间变化时，界面应提示刷新而不是混合两个版本；
5. 关闭界面（Back/Esc）后重新打开，应从新的第一页请求开始，不显示上次临时页；
   断开服务器后重连也不得显示旧缓存；
6. 未手持传讯水镜时，`/frclient land` 应被客户端门禁拒绝并给出聊天提示；服务端收到
   未持有水镜的权益请求时也应 fail closed，不返回个人权益；
7. `/fr land` 命令树不得提供 `mine`、`list` 等个人权益列表命令；无 FR 客户端路径仅保留
   `/fr land inspect <x> <y> <z>` 与 `/fr land claim <x> <y> <z>`，两者仍须正常可用。

**通过条件：** 手机端只读、自查本人、仅当前有效权益；分页与刷新正常；关闭/断线后
临时缓存清空；未持有水镜时拒绝；不存在个人列表命令，且既有 inspect/claim 不回归。

## 6. 崩溃窗口测试（可选，进阶；单玩家即可）

- 进服后运行数秒，直接强制结束服务器进程（任务管理器结束）；
- 重启同一世界；
- 观察：若有 `*.dat.tmp` 残留则被清理并记录 WARN；数据文件能正常加载。

**通过条件：** 重启后模块可用；无自动"修复"或数据错乱。

---

## 结果回传格式

> 非技术向快速操作单见 docs/guide/morning-test-sheet.md。
> 每条可注明所需器具：`单玩家` / `双玩家` / `OP或控制台` / `FR客户端` / `无模组客户端`。

```text
启动日志：通过/不通过（附关键行；14 个模块全启动）
落盘：通过/不通过（附文件大小与退出码）
重启恢复：通过/不通过
命令输出：通过/不通过（modules=15; active=15; unavailable=0）
央行真机：通过/不通过/未做（现场门控 + 供给守恒 + 冻结）
紧急权限：通过/不通过/未做（含已确认的冻结账户 break-glass 行为）
客户端展示面：通过/不通过/未做（协议 v9/29 消息、11 个 /frclient 直接界面 +
  “我的用地权益”嵌套界面、通信器门禁、no-client 平行性、v8 及更早协议拒绝）
通信交易：通过/不通过/未做（双玩家全流程 + 5 秒锁定 + 5% 付款方税 + 来源槽重校验 +
  余额不足/背包满/断线失败）
通信信函：通过/不通过/未做（邮费 10+100×附件、机构免费、list/read/delete、
  领读取附件、背包满暂留、公告冷却、仅携带水镜提醒）
土地查看/认领：通过/不通过/未做（右击方块 + /fr land inspect|claim、默认 3×3×8、
  重叠/维度/手持/加载/距离门禁、重启持久化、无方块穿透）
本人用地权益：通过/不通过/未做（FR 客户端 + 手持水镜、仅本人当前有效权益、
  Next/刷新、关闭/断线缓存清理、无个人列表命令、inspect/claim 无回归）
崩溃窗口：通过/不通过/未做
异常：{任何 ERROR/异常行}
```

> 说明：自动化专用服务器 smoke 通过**不等于** Human UI/业务流程验证通过——本清单
> 中的客户端界面、双玩家交易、资产领取与土地认领等条目只能由 Human 在真机/多人环境
> 实际完成并回填结论。

Codex 将依据回传结果出具运行时验证结论并更新审查记录。
