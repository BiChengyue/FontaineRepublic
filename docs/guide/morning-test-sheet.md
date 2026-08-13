# 早间测试操作单（照做即可）

> 给 Human 的快速测试单；详细判定标准见
> docs/development/LEVEL3-RUNTIME-VERIFICATION-CHECKLIST.md §4.6/§4.7。
> 全程约 20 分钟。把结果（哪些通过/哪些报错）发回给 Codex 即可。

## 准备

1. 打开两个终端，都进入项目目录 `D:\MC\FontaineRepublic`。
2. 终端 A 启动服务器：`.\gradlew.bat runServer`，等日志出现
   `Done (...)s!` 后再操作。
3. 终端 B 启动客户端（可选，测客户端面时）：`.\gradlew.bat runClient`，
   在游戏里 `localhost` 连接。
4. 准备两个 UUID：你的玩家 UUID（游戏里 `F3+C` 复制或在服务器日志找
   `UUID of player`），以及一个"目标玩家"UUID（第二人，或直接给自己）。

> **重要（新世界）**：不要用 7/28 旧原型世界（`run\world` 里是 60 字节旧格式
> `fontainerepublic.dat`，会报 `LOAD_FAILED`、所有写入被拒）。请在
> `run\server.properties` 把 `level-name` 改成新名字（当前已设为
> `world-fr-v1`），旧世界文件夹保留作备份，不删除。

> **提示（原因参数）**：`preview`/`bootstrap` 的 reason 参数不加引号时只接受
> ASCII（字母数字），中文需加引号，如 `"首次初始化"`；操作单统一用 `test`。

## 一、服务器面（在服务器控制台输入）

```text
/fr admin emergency status
```
应显示 `phase=UNSET`。

```text
/fr admin emergency bootstrap <你的玩家UUID> 首次初始化
```
应提示 accepted（revision=1）。

```text
/fr admin emergency status
```
应显示 `phase=ACTIVE`。

```text
/fr admin emergency preview economy issue 1.0.0 PLAYER_UUID <目标玩家UUID> DEBUG test amount 100
```
应返回 `token=...` 与到期时间（记下 token）。

```text
/fr admin emergency confirm <刚才的token>
```
应提示成功（attempt=...）。

再执行一次 `preview`（同样的命令），然后用**旧 token** 再 `confirm` ——
应被拒绝（单次使用）。

```text
/fr admin emergency preview economy reclaim 1.0.0 PLAYER_UUID <目标玩家UUID> DEBUG test amount 999
```
应被拒绝（`INSUFFICIENT_FUNDS`，因为余额只有 100）。

进游戏（目标玩家）：`/fr money balance` 应显示 +100。

央行现场（可选）：到注册的央行区域后，
`/fr bank deposit <目标玩家> 50`、`/fr bank withdraw <目标玩家> 20`、
`/fr bank freeze <目标玩家>`（冻结后转账应被拒）、`/fr bank unfreeze <目标玩家>`。

崩溃窗口（可选）：玩几秒后任务管理器结束服务器进程，再启动同一世界，
余额/记录应完好。

## 二、客户端面（FR 客户端连接后）

```text
/frclient
```
打开主菜单，逐一点开：Money（余额/转账/流水）、Citizen（公民卡）、
History（历史）、Notifications（通知）、Government、Parliament、Court、
Land、Guide —— 各界面应有内容或"暂无"占位，不报错。

转账：`/frclient money` 界面里填目标与金额提交（或直接
`/fr money pay <目标> 10`），双方界面应实时刷新余额与流水。

平行性（可选）：用一个**没装 FR 模组**的 Forge 客户端连接，所有 `/fr`
命令与聊天反馈应正常。

## 三、两个需要你拍板的问题

1. 紧急发钞/回收目前**不经过官方冻结门**（可以对冻结账户执行）。这是你要的
   break-glass 行为吗？
2. 要不要做"我的地块"权益视图（查看自己持有使用权的土地）？设计已备好。

## 回传格式（直接发消息即可）

```text
服务器面：通过/不通过（附报错行）
客户端面：通过/不通过（附报错行）
崩溃窗口：通过/不通过/未做
问题1：允许/不允许
问题2：做/不做
其他异常：...
```
