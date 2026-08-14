# Audit Report — FR-MORNING-FIXES-REVIEW-01

> 早间真机核验发现问题的修复审查（Level 1-2）。

## 背景（真机日志证据）

- 客户端收包崩溃：`DisplayMessageHandlers.lambda$landInfo$17` ->
  `Unsafe Referent usage found in safe referent method`（Render thread，
  07:52:13，消息 ID 5-8 全部复现）。
- 紧急日志追加失败：`Terminal journal record 2 failed ...
  (FAILED:RATE_GUARD)`（07:58:34，确认成功后日志未落盘）。
- 紧急发钞后玩家余额不可用：经济账户键错位（紧急 provider 用
  `SubjectId.of(playerUuid)`，而主体登记册生成独立 SubjectId）。

## 修复内容（提交 d6192a7）

1. **S2C handler 侧隔离**：`DisplayMessageHandlers` 全部 9 个 handler 由
   `DistExecutor.safeRunWhenOn` 改为 `unsafeRunWhenOn`。Forge 的 safe 变体
   只接受 Minecraft/client 包作为 referent，mod 自有类在客户端真实收包时
   抛错；unsafe 变体在 handler（仅接收侧执行）中安全——专用服务器不接收
   PLAY_TO_CLIENT，不加载 client/ 类。测试与设计文档同步。
2. **紧急账户键解析**：`EconomyEmergencyProvider` 注入
   `Function<UUID, Optional<SubjectId>> targetResolver`，preview/apply 先经
   主体登记册解析玩家真实 SubjectId（`findSubjectForPlayer`），未解析目标
   以 `PLAYER_NOT_PROVISIONED` 拒绝；`EconomyModule` 接线。
3. **RATE_GUARD 重试**：`EmergencyRepository.commit` 遇
   `FAILED:RATE_GUARD` 时短暂等待（150ms，覆盖 DataManager 100ms 最小间隔）
   后重试一次，确保紧急确认的日志终态记录不丢失。

## Evidence

- `gradlew economyEmergencyFoundationTest clientPresentationFoundationTest`
  -> 两个 validation passed；
- 完整 `gradlew build` BUILD SUCCESSFUL（33 actionable tasks）。

## 遗留

- 官方职责（bank deposit/withdraw）economy+audit 双提交存在同类 RATE_GUARD
  风险，待真机核验确认后按同模式修复；
- 客户端侧隔离修复需真机连服复测（专用服务器冒烟无法覆盖收包路径）。

**Compliance:** PASS（Level 1-2）；真机复测随 Human。
