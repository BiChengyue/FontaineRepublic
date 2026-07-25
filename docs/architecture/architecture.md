# FontaineRepublic Architecture

版本：

Alpha 0.1

Minecraft:

1.20.1

Mod Loader:

Forge

项目名称：

FontaineRepublic

中文名称：

枫丹共和国核心系统

---

# 1. 项目定位

FontaineRepublic 是一个 Minecraft Forge 服务器核心 Mod。

目标：

为枫丹共和国服务器提供国家运行框架。

Mod 不只是提供单一玩法，而是管理：

- 玩家身份

- 土地归属

- 国家经济

- 城市规划

- 政府制度

- 司法系统

- AI辅助治理

---

# 2. 核心设计原则

## 2.1 服务端权威

所有核心数据必须由服务器管理。

包括：

- 金钱

- 土地

- 身份

- 职位

- 投票

- 案件

数据流程：

Client

↓

Network Packet

↓

Server Logic

↓

Data Storage

客户端只负责：

- 显示

- 输入

- 请求

禁止客户端决定游戏状态。

---

## 2.2 模块化设计

系统按照功能划分模块。

模块之间：

- 保持独立

- 使用明确接口通信

- 禁止直接修改其他模块内部数据

---

## 2.3 数据优先

开发顺序：

数据模型

↓

业务逻辑

↓

网络同步

↓

GUI

禁止：

先开发界面，再设计数据。

---

# 3. 总体架构

FontaineRepublic

Core

├── Citizen  
├── Land  
├── Audit  
├── Economy  
├── Resource  
├── City  
├── Government  
├── Parliament  
├── Justice  
├── AI  
└── Client



---

# 4. 模块说明

## Core

核心框架。

负责：

- Mod初始化

- 配置管理

- 数据加载

- 全局事件

---

## Citizen

居民身份系统。

负责：

- 玩家身份

- 国家成员信息

- 身份等级

身份：

- GOD

- KING

- COUNCIL

- CITIZEN

注意：

身份不等于权限。

---

## Land

土地管理系统。

最高优先级模块之一。

负责：

- Chunk归属

- 区域规划

- 建筑权限

- 建设许可

区域类型：

- CAPITAL

- RESIDENTIAL

- COMMERCIAL

- INDUSTRIAL

- PORT

- NATURE

---

## Audit

审计日志系统。

记录：

- 土地变化

- 金钱变化

- 交易

- 政府行为

- 司法记录

采用：

只追加记录。

禁止随意删除历史。

---

## Economy

经济系统。

负责：

- 玩家账户

- 国家财政

- 交易

- 资源回收

- 动态价格

价格模型：

基础价格

×

供需倍率

×

政策倍率

AI只提供分析，不直接控制经济。

---

## Resource

资源系统。

负责：

- 国家库存

- 资源统计

- 建设需求

---

## City

城市系统。

负责：

- 城市区域

- 建筑登记

- 公共设施

---

## Government

政府系统。

负责：

- 行政机构

- 官员

- 部门

---

## Parliament

议会系统。

负责：

- 提案

- 投票

- 国家工程审批

---

## Justice

司法系统。

负责：

- 案件管理

- AI辅助审理

- 上诉流程

---

## AI

AI辅助系统。

用途：

- 市场分析

- 司法分析

- 政策建议

AI不得直接修改国家数据。

---

## Client

客户端显示层。

负责：

- GUI

- HUD

- 信息显示

不保存核心数据。

---

# 5. 数据存储原则

Alpha阶段：

采用：

Minecraft SavedData

+

JSON备份

不使用复杂数据库。

未来根据需求升级。

---

# 6. 开发优先级

## Alpha 0.1

基础国家框架：

- Core

- Citizen

- Land

- Audit

## Alpha 0.2

经济：

- Account

- Treasury

- Market

## Alpha 0.3

资源：

- Storage

- Production

## Alpha 0.4

城市：

- District

- Building

## Alpha 0.5+

政治与司法：

- Government

- Parliament

- Justice

## Beta

AI增强。

---

# 7. 禁止事项

开发过程中禁止：

1. GUI先行

2. 客户端保存核心数据

3. 模块直接修改其他模块内部数据

4. 为未来需求引入不必要复杂架构

5. 未经过架构审查直接增加大型系统

---

# 8. Git开发流程

设计

↓

任务分配

↓

代码实现

↓

架构审查

↓

测试服验证

↓

合并

