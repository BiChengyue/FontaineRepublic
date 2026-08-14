# FR-ITEM-001 Java Implementation Task

> **Status:** Prepared — Authorized
> **Task Type:** 便携通讯器物品 + UI 门禁 + 右键转账
> **Design Input:** docs/architecture/fr-item-001-a-communicator.md

## 1. Goal

1. **物品注册**（common）：
   - `com.fontainerepublic.common.item.FRItems`：`DeferredRegister<Item>`
     （`ForgeRegistries.ITEMS`，`fontainerepublic`），注册
     `communicator`（普通 Item，`stacksTo(1)`，英文名
     `item.fontainerepublic.communicator`）；
   - `FontaineRepublic` 构造器调用 `FRItems.register(bus)`；
   - 资源：`assets/fontainerepublic/models/item/communicator.json`
     （`item/generated` + `minecraft:item/clock` 占位纹理）、
     `assets/fontainerepublic/lang/en_us.json` / `zh_cn.json` 显示名
     "Portable Communicator" / "便携通讯器"；
   - 给命令获取路径（/give 即可，无需额外命令）。
2. **客户端 UI 门禁**：
   - `com.fontainerepublic.client.CommunicatorGate`：
     `static boolean holdsCommunicator(Player player)`（主手或副手
     `is(FRItems.COMMUNICATOR.get())`）；
   - `ClientManager` 的每个 open* 前检查：未持有 → 聊天提示"需要手持便携
     通讯器才能使用 FR 客户端界面"，不开屏；
3. **右键交互**（客户端，`Dist.CLIENT` 监听）：
   - `PlayerInteractEvent.EntityInteract`：手持 communicator 右键玩家 →
     打开 `MoneyScreen` 且目标框预填该玩家名；
   - `PlayerInteractEvent.RightClickBlock`：手持 communicator 右键方块 →
     打开 `LandScreen`；
   - 事件处理放 client 包（`client/`），经 FMLClientSetupEvent 或客户端
     事件总线注册，专用服务器不加载。

## 2. 明确不实现

- 购地/地块申请（FR-LAND-CLAIM-001 后续）；权威 C2S 包；服务端权限/
  状态改动；自制贴图（占位纹理）。

## 3. 契约

- 物品为 UI 入口，无任何权威副作用；动作仍经命令；
- 门禁为 UX 检查；服务端命令不变；
- 客户端代码全部在 `client/`，专用服务器不加载。

## 4. 测试计划

- 物品注册（id/名称/堆叠/物品模型文件存在）；
- `holdsCommunicator` 纯函数（主手/副手/空手/其他物品）；
- 右键目标捕获（纯函数：事件 → 目标名/坐标，避免依赖活客户端）；
- `gradlew build` 全绿；侧隔离扫描（client 引用仅经 DistExecutor/
  FMLClientSetupEvent）。

## 5. 验收

FR-ITEM-001-A §4；提交后独立审查（Codex）→ ROUTE TO HUMAN / RETEST /
BLOCK（真机右键交互核验随 Human）。

## 6. 约束

- 不新增依赖；服务端权威；专用服务器不加载 client/ 类。
