# 对照分析：social-sdk-parent vs xianyu-auto-bot

> **参考项目**：`/Users/vim/Desktop/codes/github/xianyu-auto-bot`  
> **当前项目**：`/Users/vim/Desktop/codes/github/social-sdk-parent`  
> **文档日期**：2026-07-25  
> **关联文档**：[parity-remediation-checklist.md](./parity-remediation-checklist.md)（对标 xianyu-auto-reply 完整清单）

---

## 1. 一句话定位

| 项目 | 定位 |
|------|------|
| **xianyu-auto-bot** | Python **单体**闲鱼**客服机器人**：多用户多账号、实时消息自动回复、卡券发货、订单状态机、风控/滑块/Token 保活打磨深 |
| **xianyu-auto-reply** | Python **多服务**完整运营平台：分销/采集/发布/返佣/激活等商业闭环更全 |
| **social-sdk-parent** | Java **SDK + 管理台底座**：分层清晰，AI/OpenAPI/Chrome/代理池更强，运营保活与发货深度仍不足 |

三者关系建议：

```text
auto-bot  ──► 学「运行时保活 / 订单状态机 / 发货规则 / 安全与测试」
auto-reply ──► 学「运营产品广度（分销/监控/发布/定时矩阵）」
social-sdk ──► 保留「Java SDK 架构 / AI 客服运营 / OpenAPI / 基础设施」
```

---

## 2. 架构对照

| 维度 | xianyu-auto-bot | social-sdk-parent |
|------|-----------------|-------------------|
| 语言 | Python 3.11 | Java 17 + Vue 3 |
| 形态 | **单体** FastAPI（Web + 引擎同进程） | 多模块 Maven + 单体 manager |
| 核心引擎 | `XianyuAutoAsync.py`（~1.6 万行） | `social-sdk-xianyu` + Netty IM + manager 服务 |
| Web API | `reply_server.py`（~1.4 万行） | 多个 Controller |
| 数据 | SQLite + Fernet 加密 | SQLite/MySQL/PG + AES |
| 前端 | Bootstrap 5 + Vanilla JS SPA | Vue 3 + Element Plus |
| 实时 | **SSE**（chat/orders/logs） | WebSocket/STOMP |
| 浏览器 | Playwright + DrissionPage + slidex | Chrome CDP 容器池 |
| 部署 | Docker Compose + Nginx | Docker / Electron / 移动端骨架 |
| 测试 | **大量 smoke（200+）** + unit | 少量 OpenAPI/模块测试 |
| 扩展中 | 开始 `app/api|application|domain` 分层 | 已是清晰分层 SDK |

### 架构示意

```text
xianyu-auto-bot                          social-sdk-parent
────────────────                         ────────────────
Start.py 启动                            XianyuManagerApplication
  ├─ reply_server (FastAPI UI/API)         ├─ Controllers + Services
  ├─ cookie_manager (多账号调度)           ├─ Account / Message / Order ...
  └─ XianyuLive 每账号协程                 ├─ ImMessageWatcher (Netty)
       ├─ WS 消息流                        ├─ VirtualShip / Rule / AI
       ├─ Token/保活/滑块                  ├─ Chrome 池 / Proxy 池
       ├─ 自动回复 / AI                    └─ OpenAPI
       └─ 订单状态 / 发货
```

**auto-bot 约束（README 明确）**：只支持单 Uvicorn worker、单副本；会话/锁/任务均为进程内状态。  
**我们**：同样偏单机友好，但模块边界更清晰，后续拆分成本更低。

---

## 3. 功能域对照总表

| 功能域 | auto-bot | social-sdk | 差距方向 |
|--------|----------|------------|----------|
| 多用户注册/登录/隔离 | ✅ 强 | ❌ 单 admin | **我们缺** |
| 多账号 Cookie 管理 | ✅ | ✅ | 接近 |
| 扫码登录 | ✅ 完整 + lite | ✅ | 接近 |
| 密码登录 + 人脸核验截图 | ✅ | ⚠️ 弱 | **我们缺深度** |
| 手动 Cookie 导入预检 | ✅ | ⚠️ | 可补 |
| 账号 Runtime 状态监控 | ✅ 本地快照徽章 | ⚠️ 健康任务 | **我们缺产品化** |
| Session keepalive | ✅ `loginuser.get` 周期保活 | ⚠️ | **我们缺** |
| Token 刷新/退避/夜间模式 | ✅ 配置细 | ⚠️ | **我们缺** |
| 风控日志 + 账号暂停保护 | ✅ | ⚠️ 熔断器有、日志弱 | **我们缺深度** |
| 滑块 + 远程人工兜底 | ✅ slidex + remote | ✅ CDP + 可补远程 | 可互学 |
| 关键词回复（图文/商品维度） | ✅ | ⚠️ 偏文本规则 | **我们可增强** |
| 默认回复 | ✅ | ⚠️ 配置有、产品弱 | **我们缺** |
| 商品专属回复 item_reply | ✅ | ❌ | **我们缺** |
| AI 回复（多厂商） | ✅ 引擎完整 | ✅ 更强（客服/运营） | 我们 AI 面更广 |
| 卡券 cards（api/text/data/image/yifan） | ✅ | ⚠️ 简单卡密池 | **我们缺** |
| 发货规则 delivery_rules | ✅ 关键词↔卡券 | ⚠️ 虚拟发货简 | **我们缺** |
| 发货日志 / 卡密预留 | ✅ | ⚠️ | **我们缺** |
| 订单状态机 | ✅ 很深 | ⚠️ 同步+发货 | **我们缺** |
| 订单历史同步 job | ✅ | ⚠️ 定时同步 | 可对齐 |
| 自动确认收货 | ✅ 账号开关 | ⚠️ 虚拟链路有 | 可产品化 |
| 自动评价 + 评价模板 | ✅ | ⚠️ 手动评价 API | **我们缺** |
| 商品擦亮（手动+定时） | ✅ | ⚠️ 有能力缺定时任务中心 | **我们缺调度** |
| 商品搜索/发布 | ✅ 有接口 | ✅ 更完整管理台 | 我们发布侧不弱 |
| 通知通道/模板 | ✅ | ✅ 更强 | 接近/我们略强 |
| 销售统计 sales | ✅ | ⚠️ dashboard | 可补 |
| 备份导入导出/库表管理 | ✅ 强 | ⚠️ | **我们缺** |
| 在线更新 auto_updater | ✅ | ❌ | P2 |
| 用户组 groups / 文件下载 | ✅ | ❌ | P2/P3 |
| 安全：IP 封锁/登录验证码/注册开关 | ✅ | ⚠️ JWT 有 | **我们缺** |
| OpenAPI 开放平台 | ❌ | ✅ | **我们独有** |
| AI 客服意图/议价/知识库 | ❌ 弱 | ✅ | **我们独有** |
| AI 运营 / 买家画像 / 市场情报 | ❌ | ✅ | **我们独有** |
| Chrome 容器池 / 多代理商 | ⚠️ 代理有 | ✅ | **我们更强** |
| 分销/采集监控/返佣 | ❌ | ❌（清单里待补） | 见 auto-reply |

---

## 4. auto-bot 值得重点学习的能力（按价值）

### P0 — 直接影响「能不能稳定挂机」

#### 4.1 账号运行时保活闭环
参考文件：

- `XianyuAutoAsync.py`：Token 刷新、session keepalive、重连、退避、夜间模式
- `global_config.yml`：`TOKEN_REFRESH_*`、`SESSION_KEEPALIVE_*`、`RISK_CONTROL`
- 账号 `runtime-status` API + 前端徽章

要点：

- AccessToken **按需刷新**（默认 20h 量级），不是盲目狂刷
- 轻量保活走 `mtop.taobao.idlemessage.pc.loginuser.get`
- 连续失败保护、密码登录 backoff、扫码 grace period
- 监控默认 **只读本地快照**，避免监控本身触发风控（Phase 102 设计）

**我们缺口**：`AccountHealthTask` 偏检测；缺 keepalive + token 刷新策略 + 运行时状态产品化。

#### 4.2 风控状态机与账号暂停
- `risk_control_logs` 表 + 管理端查询
- 触发验证/滑块失败后 **暂停账号** 而非死循环重试
- 运营可读中文摘要 + `operator_action_required`

**我们缺口**：有 `circuit_breaker`，但与闲鱼风控码、人工验证、恢复策略未打成一体。

#### 4.3 订单状态机 + 发货终态
参考：`order_status_handler.py`、`delivery_logs`、`delivery_finalization_states`、`data_card_reservations`

要点：

- 订单状态迁移、退款前状态、消息绑定订单
- 发货成功/失败可观测
- 卡密预留防并发重复发卡
- 多数量/多规格发货

**我们缺口**：`VirtualShipService` 有扫描发货，缺完整状态机与预留机制。

#### 4.4 卡券 + 发货规则
- `cards`：`api` / `yifan_api` / `text` / `data` / `image`
- `delivery_rules`：关键词 → 卡券，次数统计
- 延迟秒数、多规格

与 auto-reply 的卡券体系同类，但 **auto-bot 更聚焦客服发货**，没有分销。

### P1 — 客服产品完整度

| 能力 | 参考入口 | 我们现状 |
|------|----------|----------|
| 默认回复 | `/default-replies` | 半成品 |
| 商品专属回复 | `/item-reply`、`item_replay` | 缺 |
| 关键词图文/导入导出 | `/keywords*` | 弱 |
| 自动评价模板 | `comment_templates` + auto-comment | 缺任务 |
| 定时擦亮任务 | `/scheduled-tasks`、`polish-items` | 有擦亮无任务中心 |
| 聊天 SSE 实时流 | `/api/chat/stream` | WS 有，体验不同 |
| 订单 SSE | `/api/orders/stream` | 缺 |
| 登录方式全家桶 | qr / qr-lite / password / face / manual cookie | 扫码+Cookie 为主 |

### P2 — 安全、运维、工程

| 能力 | 说明 |
|------|------|
| 多用户 + 权限矩阵 | 注册、管理员、Cookie 访问控制、smoke 覆盖 |
| 登录安全 | 验证码、IP 封锁、解锁、登录统计 |
| 备份恢复 | 上传 `.db` → 完整性检查 → 维护态 → 原子替换 → 吊销会话 |
| 健康检查分层 | `/health/live` vs `/health` readiness |
| 内部 API Key | `/xianyu/reply`、`/send-message`、captcha control |
| 依赖锁文件 | `requirements.lock` + hash |
| 海量 smoke 测试 | 账户/发货/订单/权限/备份/滑块边界 |
| 在线更新 | `auto_updater.py` |

这些是 **完整对标 auto-reply 清单里工程侧应补充的另一条线**。

---

## 5. 我们已强于 auto-bot 的能力（不要回退）

1. **模块化 SDK**（core / xianyu / chrome / proxy / starter / manager）  
2. **AI 客服**（意图、议价策略、知识库、ASSIST/HYBRID）  
3. **AI 运营 / 买家画像 / 市场情报**  
4. **OpenAPI 开放平台**（appKey 鉴权 + 文档）  
5. **Chrome 每账号容器 + 指纹 + 代理池多提供商**  
6. **通知系统**（通道/模板/订阅/重试/摘要更完整）  
7. **现代前端** Vue3 管理台 + 数据大屏  
8. **多数据库** SQLite/MySQL/PostgreSQL  
9. **跨端打包骨架** Electron / iOS / Android  

整改原则：**把 auto-bot 的运行时可靠性「搬」进我们的分层架构，而不是抄成巨石脚本。**

---

## 6. 与已有 auto-reply 清单的关系（合并优先级）

之前 [parity-remediation-checklist.md](./parity-remediation-checklist.md) 以 **auto-reply** 为完整产品对标。  
引入 **auto-bot** 后，建议对 M1 做如下校准：

| 原清单项 | auto-bot 补充价值 | 建议 |
|----------|-------------------|------|
| A1–A4 Cookie/Token 续期 | auto-bot 的 keepalive + token 策略更可落地 | **优先对照 auto-bot 实现细节** |
| A5 风控 | auto-bot risk log + 暂停保护更成熟 | 直接对标 |
| A6–A9 卡券发货 | auto-bot 的 cards/rules/finalization 更贴客服主路径 | M1 用 auto-bot 模型即可；分销留给 auto-reply |
| B2 自动评价 | auto-bot comment_templates 更具体 | 采用 |
| B4 擦亮 | auto-bot scheduled_tasks 更具体 | 采用 |
| 订单状态 | auto-bot `order_status_handler` 是金标准 | **新增工作项 O1** |
| 多用户 | 两边都有；auto-bot 更轻量 | E1 可先对齐 auto-bot 模型 |
| 分销/采集/返佣 | 仅 auto-reply | 仍按原清单 F/G |
| 测试/安全/备份 | auto-bot 明显更强 | **新增工作项 S1–S4** |

### 建议新增工作项（相对原清单）

| ID | 标题 | 优先级 | 主要参考 |
|----|------|--------|----------|
| O1 | 订单状态机（消息绑定/退款前状态/终态） | P0 | `order_status_handler.py` |
| O2 | 发货终态 + 卡密预留防重 | P0 | `delivery_finalization_states`、`data_card_reservations` |
| O3 | 账号 Runtime 状态（只读快照 + 徽章） | P1 | Phase 102 runtime-status |
| O4 | Session Keepalive 循环 | P0 | `SESSION_KEEPALIVE_*` |
| O5 | 商品专属回复 item_reply | P1 | `item_replay` 表与 API |
| O6 | 评价模板 comment_templates | P1 | auto-comment API |
| S1 | 健康检查 live/ready 分层 | P1 | `/health/live` `/health` |
| S2 | 数据库备份恢复维护态 | P1 | admin backup upload 流程 |
| S3 | 内部 API Key 边界 + 权限矩阵测试 | P1 | smoke public boundaries |
| S4 | 核心路径 smoke 测试套件 | P0 | `tests/smoke/*` |
| S5 | 登录安全（验证码/IP 封锁） | P2 | admin security |
| S6 | 销售统计 sales API | P2 | `/api/sales` |

---

## 7. auto-bot 核心表（便于迁移设计）

| 表 | 用途 |
|----|------|
| `users` / `user_settings` / `user_groups` | 多用户 |
| `cookies` / `cookie_status` | 账号 |
| `keywords` | 关键词（含 type/image/item_id） |
| `default_replies` / `default_reply_records` | 默认回复 |
| `item_replay` | 商品专属回复 |
| `ai_reply_settings` / `ai_config_presets` / `ai_conversations` | AI |
| `cards` / `delivery_rules` / `delivery_logs` | 卡券发货 |
| `delivery_finalization_states` / `data_card_reservations` | 发货终态/预留 |
| `orders` | 订单（含议价流标记、退款前状态等） |
| `item_info` | 商品缓存 |
| `chat_messages` | 聊天落库 |
| `comment_templates` | 评价模板 |
| `notification_*` | 通知 |
| `risk_control_logs` / `audit_logs` | 风控/审计 |
| `scheduled_tasks` | 定时任务（如擦亮） |
| `system_settings` / `download_files` | 系统/文件 |

---

## 8. 前端能力对照（auto-bot static/js）

| 模块 | 文件 | 我们对应 |
|------|------|----------|
| 账号 | `app-accounts.js` | `views/accounts` — 需补 runtime/登录全家桶 |
| 自动回复 | `app-auto-reply.js` | `rules` — 需补默认回复/商品回复/图文 |
| AI | `app-ai-reply.js` | `ai` / `aiCs` — 我们更强，可吸收账号级 preset |
| 卡券/发货 | `app-cards.js` `app-delivery.js` | `virtualShip` — 需升级 |
| 订单 | `app-orders.js` | `orders` — 需状态机与 SSE/刷新 |
| IM 聊天 | `app-im.js` | `messages` — 可对齐会话体验 |
| 商品 | `app-items.js` | `products` — 接近 |
| 通知 | `app-notifications.js` | `notify` — 接近 |
| 日志 | `app-logs.js` | `audit` / replyLogs — 需风控/发货日志 |
| 用户/设置 | `app-users.js` `app-settings.js` | 缺多用户与系统设置深度 |
| 仪表盘 | `app-dashboard.js` | `dashboard` — 可补 sales |

---

## 9. 实施建议（结合两份参考）

### 阶段 M1 修订版（建议）

目标：**先成为「可靠的 Java 版 auto-bot」**，再扩展 auto-reply 的广度。

1. **O4 + A4**：Session keepalive + Token 刷新/退避（对标 auto-bot 配置语义）  
2. **O1 + O2**：订单状态机 + 发货终态/卡密预留  
3. **A6 + A7 + A8 + A9**：卡券/规则/自动发货/补发（模型优先对齐 auto-bot，字段预留分销）  
4. **A5 + O3**：风控日志 + 账号暂停 + Runtime 徽章  
5. **S4**：为上述路径补 smoke/集成测试（学 auto-bot 测试习惯）  
6. **B9**：批次/任务日志框架（为擦亮、评价、续期铺路）

### 不要在 M1 做的

- 分销 / 返佣 / 采集监控（auto-reply 专属，放 M5+）  
- 推倒 Vue 改 Bootstrap  
- 把 manager 抄成 1.6 万行单类（保持 Java 分层）

### 参考代码阅读顺序（给实现同学）

1. `global_config.yml` — 配置契约  
2. `cookie_manager.py` — 多账号调度  
3. `XianyuAutoAsync.py` 中：连接状态、token、keepalive、risk pause  
4. `order_status_handler.py` — 订单状态  
5. `db_manager/base.py` cards/delivery/orders 表  
6. `reply_server.py` 对应路由段  
7. `tests/smoke/test_order_delivery_transitions.py` 等 — 行为规格  

---

## 10. 结论

| 问题 | 答案 |
|------|------|
| auto-bot 是不是完整商业平台？ | **否**，它是**客服机器人 + 发货**垂直产品，广度小于 auto-reply |
| 对我们最有价值的是什么？ | **运行时保活、风控暂停、订单状态机、卡券发货闭环、安全与测试** |
| 和 auto-reply 清单冲突吗？ | **不冲突**：auto-bot 补「深度与可靠性」，auto-reply 补「广度与商业运营」 |
| M1 应该以谁为准？ | **保活/发货/订单以 auto-bot 为金标准**；分销/采集/返佣仍以 auto-reply 为准 |
| 我们相对优势？ | SDK 分层、AI 客服运营、OpenAPI、Chrome/代理基础设施 |

---

## 11. 下一步可选动作

1. 把本文 **O1–O6 / S1–S6** 合并进 `parity-remediation-checklist.md`  
2. 输出 **M1 详细设计**（表结构 + 类图 + 对标 auto-bot 函数清单）  
3. 直接开工 **O4 Session Keepalive** 或 **O1 订单状态机**

> 建议顺序：先合并清单与 M1 设计，再编码，避免两套参考源导致模型反复横跳。
