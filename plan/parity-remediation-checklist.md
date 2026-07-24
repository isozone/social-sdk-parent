# 完整对标整改清单

> **目标**：以 `xianyu-auto-reply` 为功能基准，将 `social-sdk-parent` 补齐到完整运营产品能力。  
> **原则**：保留并强化我们已有的 Java SDK、AI 客服/运营、OpenAPI、Chrome 容器池、代理池、熔断器等差异化优势；业务语义与 MTOP 真验参数以参考项目为准。  
> **参考路径**：`/Users/vim/Desktop/codes/github/xianyu-auto-reply`  
> **当前路径**：`/Users/vim/Desktop/codes/github/social-sdk-parent`  
> **文档版本**：2026-07-25  
> **对标策略**：完整对标（商业运营闭环 + 多用户/分销/返佣/激活等全量能力）

---

## 0. 状态图例

| 标记 | 含义 |
|------|------|
| ❌ 缺失 | 基本无对应实现 |
| ⚠️ 半成品 | 有 SDK/接口/雏形，未产品化或深度不足 |
| ✅ 已具备 | 已达可用水平（仍可优化，不作为主整改项） |
| 🔁 需改造 | 有类似能力，但模型/链路需按参考重做 |

| 优先级 | 含义 |
|--------|------|
| P0 | 7×24 稳定挂机刚需，不做会频繁掉线/漏发 |
| P1 | 核心运营能力，完整对标必做 |
| P2 | 商业化/体验/运营配套 |
| P3 | 可后置或按需裁剪 |

---

## 1. 总览对照

| 域 | 参考项目 | 我们现状 | 差距等级 |
|----|----------|----------|----------|
| 账号保活（Cookie/Token/登录续期） | 完整任务 + 批次日志 | 仅健康检测 | **P0 严重** |
| 卡券 / 自动发货 | 多类型卡券 + 规则引擎 + 补发 | 简单卡密池 + 定时扫描 | **P0 严重** |
| 定时运维矩阵 | ~20 类任务 | ~8 类（同步/健康/虚拟发货/监控） | **P0 严重** |
| 商品发布流水线 | 素材库/地址库/批量/日志 | local_product 半成品 | **P1** |
| 商品监控 / 采集 | 上新监控 + Goofish 采集 + fallback 账号 | market/monitor 另一套情报模型 | **P1** |
| 黑名单 / 默认回复 / 消息过滤 | 完整 UI + 规则 | SDK 测试接口或规则半覆盖 | **P1** |
| 多用户 / 权限 / 租户 | 用户体系 + 菜单权限 | 单 admin | **P1** |
| 分销体系 | 货源/对接/代理/结算 | 无 | **P1** |
| 返佣子系统 promotion | 独立前后端 | 无 | **P2** |
| 支付 / 激活 / 充值提现 | 有 | 无 | **P2** |
| 公告广告反馈教程 | 有 | 仅协议/隐私 | **P2** |
| 桌面启动器 / 激活 | launcher EXE + 激活码 | Electron 脚本无商业化 | **P2** |
| AI 客服 / AI 运营 / 画像 | 有基础 AI 回复 | **更强** | 保持优势 |
| OpenAPI / Chrome 池 / 代理池 | 弱或无 | **更强** | 保持优势 |

---

## 2. 分阶段整改路线

| 阶段 | 主题 | 目标 | 预估工作量（人周，粗估） |
|------|------|------|--------------------------|
| **A** | 保活 + 发货闭环 | 多账号可长期在线、支付后可靠发货 | 4–6 |
| **B** | 定时运维矩阵 | 评价/擦亮/小红花/补发/备份等可配置可观测 | 3–4 |
| **C** | 发布 + 监控采集 | 素材/地址/批量发布 + 上新监控/采集 | 4–5 |
| **D** | 消息与风控产品化 | 黑名单/默认回复/过滤/快捷话术/聊天增强 | 2–3 |
| **E** | 多用户与系统管理 | 用户/角色/设置/日志中心 | 3–4 |
| **F** | 分销 | 货源/对接/代理订单/结算 | 4–6 |
| **G** | 返佣 promotion | 独立或内嵌返佣子系统 | 3–5 |
| **H** | 商业化与分发 | 激活码/支付/广告/启动器 | 3–4 |
| **I** | 架构硬化 | 可选服务拆分、任务隔离、备份高可用 | 2–4 |

> 完整对标建议按 A→H 顺序推进；I 可与中后期并行。  
> 每个条目验收标准：后端 API + 数据表 + 前端页面 + 定时任务（如适用）+ 对照参考项目主路径可走通。

---

## 3. 详细整改清单

### 阶段 A — 账号保活 + 发货闭环（P0）

#### A1. Cookie 浏览器刷新任务
| 项 | 内容 |
|----|------|
| 状态 | ❌ 缺失 |
| 优先级 | P0 |
| 参考 | `common/services/cookie_renew_browser_service.py`、`scheduler/.../cookies_refresh_task.py`、前端 `cookiesRefreshLogs/*` |
| 现状 | 仅 `AccountHealthTask` 检测过期 |
| 整改 | 1) 用 Chrome/CDP 池按账号刷新 Cookie 2) 批次执行记录 3) 成功写回加密 Cookie 4) 失败触发通知/熔断 |
| 后端包建议 | `account.renew` / `task.CookiesRefreshTask` |
| 表 | `cookie_refresh_schedule`、`scheduled_cookies_refresh_log`（批次+明细） |
| 前端 | 任务配置 + 批次列表 + 批次详情 |
| 验收 | 人为过期 Cookie 后，任务可自动恢复账号 ACTIVE |

#### A2. Cookie 接口续期（API renew）
| 项 | 内容 |
|----|------|
| 状态 | ❌ 缺失 |
| 优先级 | P0 |
| 参考 | `common/services/cookie_renew_api_service.py`、`scheduler/.../api_cookie_renew_task.py`、`apiCookieRenewLogs/*` |
| 整改 | 无头浏览器之外的轻量接口续期路径；与 A1 形成双通道（优先 API，失败降级浏览器） |
| 表 | `scheduled_api_cookie_renew_log` |
| 验收 | API 通道成功比例可观测，失败自动降级 |

#### A3. 登录续期（Login renew）
| 项 | 内容 |
|----|------|
| 状态 | ❌ 缺失 |
| 优先级 | P0 |
| 参考 | `scheduler/.../login_renew_task.py`、`loginRenewLogs/*`、密码登录/扫码相关 |
| 整改 | Cookie 彻底失效时触发重新登录流程（扫码/密码/共享扫码）；记录登录日志 |
| 表 | `account_login_log`、`scheduled_login_renew_log` |
| 前端 | 登录续期批次日志；账号页展示最近登录结果 |
| 验收 | 完全失效账号可被任务拉起或明确标记需人工扫码 |

#### A4. Token / IM Token 续期
| 项 | 内容 |
|----|------|
| 状态 | ⚠️ 半成品（IM 连接可能内嵌刷新，无产品化任务与日志） |
| 优先级 | P0 |
| 参考 | `token_renewal_cache_service.py`、`token_renewal_task.py`、`token_captcha_renewal.py`、`tokenRenewalLogs/*` |
| 整改 | IM/H5 token 缓存 + 定时续期 + 验证码联动 + 批次日志 |
| 表 | `token_cache`、`scheduled_token_renewal_log` |
| 验收 | 长连接 24h 不断因 token 过期静默失败 |

#### A5. 账号冷却 / 限流 / 风控等待
| 项 | 内容 |
|----|------|
| 状态 | ⚠️ 有熔断器，缺细粒度冷却与风控日志产品化 |
| 优先级 | P0 |
| 参考 | `account_cooldown.py`、`account_limit_service.py`、`risk_control_log_*`、前端 `admin/RiskLogs` |
| 整改 | 1) 账号级冷却队列 2) 接口频控 3) 风控命中写 `risk_control_log` 4) 管理端查询/清理 |
| 表 | `risk_control_log`（扩展现有 circuit_breaker 或并行） |
| 验收 | 触发风控后账号自动降速/暂停，日志可检索 |

#### A6. 卡券模型升级（对标 xy_cards）
| 项 | 内容 |
|----|------|
| 状态 | 🔁 需改造（现有 `virtual_card_pool` 过简） |
| 优先级 | P0 |
| 参考 | `common/models/card.py`、`card_item_relation.py`、`backend-web/.../cards.py`、`frontend/pages/cards/*` |
| 整改 | 新卡券域（建议表名 `xy_card` 或 `ship_card`）： |
| | - 类型：`api` / `text` / `data` / `image` |
| | - 延迟秒数、启用开关、发货次数 |
| | - 多规格 `is_multi_spec/spec_name/spec_value` |
| | - 与商品多对多 `card_item_relation` |
| | - 图片/批量卡密/API 配置 JSON |
| | - 分销预留字段：对接价、最低价、可见性、手续费承担方 |
| 迁移 | 将 `virtual_card_pool` 数据迁移为 `data` 类型卡券 |
| 前端 | 卡券列表/表单/商品绑定/批量导入导出 |
| 验收 | 四种类型均可绑定商品并完成一次真实发货 |

#### A7. 发货规则引擎（Delivery Block Rules）
| 项 | 内容 |
|----|------|
| 状态 | ❌ 缺失 |
| 优先级 | P0 |
| 参考 | `websocket/.../delivery_rules/*`、`xy_delivery_block_rule`、账号页 `DeliveryBlockRulesModal` |
| 规则至少包含 | 1) 买家信用不足 2) 无历史订单 3) 存在未确认收货 4) 个人黑名单 5) 平台黑名单 6) 可扩展自定义 |
| 整改 | `DeliveryRuleEngine` + 规则注册表 + 上下文；发货前强制校验 |
| 表 | `xy_delivery_block_rule`（或 `delivery_block_rule`） |
| 前端 | 账号级规则配置弹窗 |
| 验收 | 命中规则时不发货并写日志/通知 |

#### A8. 自动发货主链路对齐
| 项 | 内容 |
|----|------|
| 状态 | ⚠️ 有 VirtualShipService，链路偏简 |
| 优先级 | P0 |
| 参考 | `websocket/.../auto_delivery_handler.py`、`card_matcher.py`、`card_delivery_content.py`、`redelivery_task.py` |
| 整改 | 1) 订单支付事件/扫描触发 2) 卡券匹配 3) 规则校验 4) 延迟发送 5) 内容渲染（文本/卡密/图/API） 6) IM 发送 7) 结果落库 8) 失败进入补发队列 |
| 表 | 扩展 `virtual_ship_task` 或新建 `ship_task`/`ship_log` |
| 验收 | 下单→支付→延迟→发货→买家收到消息全链路通过 |

#### A9. 自动补发货（Redelivery）
| 项 | 内容 |
|----|------|
| 状态 | ❌ 缺失 |
| 优先级 | P0 |
| 参考 | `scheduler/.../redelivery_task.py`、`redeliveryLogs/*` |
| 整改 | 定时扫描失败/超时未送达任务；有限次重试；批次日志 |
| 表 | `scheduled_redelivery_log` |
| 前端 | 补发货批次列表/详情 |
| 验收 | 人为制造发送失败后，任务可补发成功 |

#### A10. 确认收货话术 / 自动确认
| 项 | 内容 |
|----|------|
| 状态 | ⚠️ 有 `autoConfirmReceipt` 定时，缺可配置话术与记录 |
| 优先级 | P1（与 P0 发货闭环强相关） |
| 参考 | `confirm_receipt_message.py`、相关服务 |
| 整改 | 确认收货前后消息模板；N 天自动确认配置产品化 |
| 表 | `confirm_receipt_message` |
| 验收 | 配置模板后发货完成订单按策略确认 |

---

### 阶段 B — 定时运维矩阵（P0/P1）

#### B1. 统一任务调度中心
| 项 | 内容 |
|----|------|
| 状态 | ⚠️ 有 `@Scheduled` 散落，无管理台任务中心 |
| 优先级 | P0 |
| 参考 | `scheduler/app/services/scheduled_task_service.py`、`frontend/admin/ScheduledTasks`、`scheduled_task` 模型 |
| 整改 | 1) 任务注册表（code/cron/enabled/参数 JSON） 2) 启停/改 cron 3) 最近运行状态 4) 手动触发 5) 与批次日志关联 |
| 表 | `scheduled_task` |
| 前端 | `/app/tasks` 升级为「系统定时任务中心」（现 monitor tasks 需改名区分） |
| 验收 | 管理台可开关每个任务并看到上次执行结果 |

#### B2. 自动评价任务
| 项 | 内容 |
|----|------|
| 状态 | ⚠️ 有手动 `ReviewService.reviewOrder`，无自动任务 |
| 优先级 | P0 |
| 参考 | `rate_service.py`、`rate_task.py`、`auto_rate_config`、`rateLogs/*` |
| 整改 | 评价模板、延迟、好评内容、批次日志；订单完成后自动评 |
| 表 | `auto_rate_config`、`scheduled_rate_log` |
| 前端 | 评价配置 + 补评价批次日志 |
| 验收 | 完成订单自动评价，日志可追溯 |

#### B3. 求小红花任务
| 项 | 内容 |
|----|------|
| 状态 | ⚠️ SDK 有 red-flower 接口，无任务/UI |
| 优先级 | P1 |
| 参考 | `red_flower_task.py`、`redFlowerLogs/*` |
| 整改 | 定时对符合条件订单/会话求小红花；频控；批次日志 |
| 表 | `scheduled_red_flower_log` |
| 前端 | 小红花批次日志 |
| 验收 | 任务可跑通并写日志 |

#### B4. 定时擦亮（批量）+ 日志
| 项 | 内容 |
|----|------|
| 状态 | ⚠️ 有擦亮 API/页面/规则触发，缺定时批量与批次日志 |
| 优先级 | P1 |
| 参考 | `polish_task.py`、`polishLogs/*` |
| 整改 | 按账号/商品池定时擦亮；超级擦亮策略；批次详情 |
| 表 | `scheduled_polish_log` |
| 前端 | 擦亮批次列表/详情（可与现 `/app/polish` 合并） |
| 验收 | 定时任务批量擦亮成功可统计 |

#### B5. 关闭消息通知任务
| 项 | 内容 |
|----|------|
| 状态 | ❌ 缺失 |
| 优先级 | P2 |
| 参考 | `close_notice_task.py`、`closeNoticeLogs/*` |
| 整改 | 批量关闭闲鱼站内部分通知，降低打扰/风控 |
| 表 | `scheduled_close_notice_log` |

#### B6. 数据库备份任务
| 项 | 内容 |
|----|------|
| 状态 | ❌ 缺失 |
| 优先级 | P1 |
| 参考 | `db_backup_task.py`、`db_backup_log`、`admin/DbBackupLogs` |
| 整改 | SQLite/MySQL/PG 定时备份到本地/对象存储；保留策略；日志 |
| 表 | `db_backup_log` |
| 验收 | 可一键恢复最近备份 |

#### B7. 商品/订单定时拉取增强
| 项 | 内容 |
|----|------|
| 状态 | ⚠️ 已有 `autoSyncProducts` / `autoSyncOrders` |
| 优先级 | P1 |
| 参考 | `fetch_items_task.py`、`fetch_orders_task.py`、`seller_fill_task.py` |
| 整改 | 1) 可配置间隔 2) 失败重试 3) 卖家信息补全 4) 同步日志 5) 与发货扫描解耦 |
| 验收 | 管理台可见同步延迟与失败账号 |

#### B8. 浏览器数据清理 / 日切任务
| 项 | 内容 |
|----|------|
| 状态 | ❌ 缺失 |
| 优先级 | P2 |
| 参考 | `cleanup_browser_data_task.py`、`day_switch_task.py` |
| 整改 | Chrome profile 缓存清理、日切统计重置 |

#### B9. 批次日志通用框架
| 项 | 内容 |
|----|------|
| 状态 | ❌ 缺失 |
| 优先级 | P0（基础设施） |
| 参考 | `scheduled_batch_log_service.py`、各 `*Batches.tsx` / `*BatchDetail.tsx` |
| 整改 | 统一 `BatchJob` + `BatchJobItem` 抽象，避免 10 套重复表结构；或按参考一表一类但共享 Service |
| 前端 | 通用批次列表组件 |
| 验收 | 新任务接入日志 < 0.5 人日 |

---

### 阶段 C — 商品发布 + 监控采集（P1）

#### C1. 素材库
| 项 | 内容 |
|----|------|
| 状态 | ⚠️ `local_product` 可充当草稿，非运营素材库 |
| 优先级 | P1 |
| 参考 | `product_material.py`、`product-publish/ProductMaterials`、`product_publish_service.py` |
| 整改 | 素材 CRUD：标题/描述/图片/价格/类目/淘口令/短链/库存/状态；批量删除；导入 |
| 表 | `product_material` |
| 前端 | `/app/product-publish/materials` |
| 验收 | 素材可一键用于单品发布 |

#### C2. 地址库（全局 + 个人）
| 项 | 内容 |
|----|------|
| 状态 | ⚠️ SDK 有地址 API，管理台无地址库 |
| 优先级 | P1 |
| 参考 | `publish_address.py`、`user_publish_address.py`、`PublishAddresses` |
| 整改 | 全局地址库 + 用户/账号级地址；发布时选择 |
| 表 | `publish_address`、`user_publish_address` |
| 前端 | 地址库 Tab |
| 验收 | 发布商品可绑定发货地址 |

#### C3. 单品发布 / 批量发布 / 发布日志
| 项 | 内容 |
|----|------|
| 状态 | ⚠️ `LocalProductService.publishOne/batchPublish` 存在 |
| 优先级 | P1 |
| 参考 | `product_publish.py`、`publish_execution_service.py`、`publish_log*`、前端 `product-publish/*` |
| 整改 | 1) 与素材库打通 2) 批量任务状态查询 3) 发布日志可检索 4) 图片上传链路产品化 5) 失败原因结构化 |
| 表 | `publish_log`（及 batch_id） |
| 前端 | 单品发布 / 批量发布 / 发布日志三页 |
| 验收 | 10 条素材批量发布成功率可统计 |

#### C4. 上新监控 Listing Monitor
| 项 | 内容 |
|----|------|
| 状态 | 🔁 与现有 `monitor_task` 不同域，需新建 |
| 优先级 | P1 |
| 参考 | `listing_monitor_*` 模型/服务、`product-monitor/*` 页面 |
| 整改 | 监控任务（关键词/类目/账号）→ 抓取上新 → 去重 → 可选自动私信 → 日志 |
| 表 | `listing_monitor_task/category/item/log` |
| 前端 | 监控总览/分类/任务/日志/采集商品 |
| 验收 | 新上架商品可在 1 个调度周期内入库 |

#### C5. 采集/下单 Fallback 账号
| 项 | 内容 |
|----|------|
| 状态 | ❌ 缺失 |
| 优先级 | P1 |
| 参考 | `collect_fallback_account*`、`order_fallback_account*` |
| 整改 | 采集与下单使用独立马甲账号池，失败轮换 |
| 表 | `collect_fallback_account`、`order_fallback_account` |
| 前端 | product-monitor 下两个账号管理页 |

#### C6. Goofish 定时采集
| 项 | 内容 |
|----|------|
| 状态 | ⚠️ 有 market crawl，非 goofish job 模型 |
| 优先级 | P1 |
| 参考 | `goofish_crawler.py`、`goofish_crawl_*`、`crawler/GoofishScheduledCrawler` |
| 整改 | Job：启停/立即跑/状态/结果商品列表 |
| 表 | `goofish_crawl_job`、`goofish_crawl_item` |
| 前端 | 定时采集页 |

#### C7. 商品搜索 / Compass 数据（可选增强）
| 项 | 内容 |
|----|------|
| 状态 | ⚠️ SDK 有 search；缺运营页 |
| 优先级 | P2 |
| 参考 | `search/*`、`goofish_compass`、`compass/*` |
| 整改 | 站内搜商品页；罗盘类数据分析（可与现 data-board/market 融合） |

---

### 阶段 D — 消息 / 回复 / 风控产品化（P1）

#### D1. 黑名单管理（个人 + 平台）
| 项 | 内容 |
|----|------|
| 状态 | ⚠️ SDK 有 blacklist API，无业务模块/UI |
| 优先级 | P1 |
| 参考 | `xy_personal_blacklist`、`xy_platform_blacklist`、`blacklist/*` |
| 整改 | 本地黑名单库 + 同步闲鱼黑名单；发货/回复前拦截 |
| 表 | `xy_personal_blacklist`、`xy_platform_blacklist` |
| 前端 | `/app/blacklist` 双 Tab |
| 验收 | 拉黑用户后不自动回复、不自动发货 |

#### D2. 默认回复
| 项 | 内容 |
|----|------|
| 状态 | ⚠️ `xianyu_auto_reply_config` 有兜底字段，缺独立默认回复管理 |
| 优先级 | P1 |
| 参考 | `default_reply.py`、`default_replies` 路由 |
| 整改 | 账号级/全局默认回复；启停；与关键词/AI 优先级明确 |
| 表 | `default_reply` 或扩展现有 config |
| 前端 | 关键词页内模块或独立页 |

#### D3. 消息过滤规则
| 项 | 内容 |
|----|------|
| 状态 | ❌ 缺失 |
| 优先级 | P1 |
| 参考 | `message_filters/*`、相关后端 |
| 整改 | 过滤系统消息/特定关键词/特定用户，不进入自动回复 |
| 表 | `message_filter_rule` |
| 前端 | `/app/message-filters` |

#### D4. 快捷话术
| 项 | 内容 |
|----|------|
| 状态 | ❌ 缺失 |
| 优先级 | P2 |
| 参考 | `chat_quick_phrase`、`QuickPhrasesPanel` |
| 整改 | 聊天侧边栏快捷话术 CRUD + 一键发送 |
| 表 | `chat_quick_phrase` |

#### D5. 在线聊天增强
| 项 | 内容 |
|----|------|
| 状态 | ⚠️ 有 messages 页 + WS，深度不如 chat-new |
| 优先级 | P1 |
| 参考 | `chat-new/*`、`chat_new*.py`、`chat_customer_order` |
| 整改 | 1) 会话列表体验 2) 图片消息 3) 客户订单面板 4) 与自动回复日志联动 5) 未读/多账号切换 |
| 前端 | 升级 `/app/messages` 或新增 `/app/chat` |
| 验收 | 可在后台完成日常客服接待主路径 |

#### D6. 关键词规则增强
| 项 | 内容 |
|----|------|
| 状态 | ⚠️ 已有规则引擎 + 擦亮动作 |
| 优先级 | P1 |
| 参考 | `keywords/*`、`xy_keyword_rule`、图片关键词等 |
| 整改 | 图片关键词、商品专属回复、多回复随机、匹配统计增强 |
| 验收 | 与参考 keywords 页能力对齐 |

#### D7. 自动回复日志增强
| 项 | 内容 |
|----|------|
| 状态 | ⚠️ 有 `replyLogs` |
| 优先级 | P2 |
| 参考 | `auto_reply_message_log`、`autoReplyLogs/*` |
| 整改 | 匹配路径、命中规则、发送结果、耗时、失败原因字段对齐 |

#### D8. 退款自动处理 / 取消订单策略
| 项 | 内容 |
|----|------|
| 状态 | ⚠️ 有退款 API，缺策略自动化 |
| 优先级 | P2 |
| 参考 | `refund_cancel_service.py`、`RefundCancelModal` |
| 整改 | 账号级自动同意/拒绝退款策略（谨慎默认关闭） |

---

### 阶段 E — 多用户与系统管理（P1/P2）

#### E1. 多用户体系
| 项 | 内容 |
|----|------|
| 状态 | ❌ 仅 `admin_user` |
| 优先级 | P1（完整对标必做） |
| 参考 | `user.py`、`users.py`、`admin/Users`、`auth/Register` |
| 整改 | 用户注册/登录/角色（admin/user）、密码重置、用户启用禁用；账号资源归属 `user_id` |
| 表 | `sys_user`（或扩展 admin_user）、资源表加 `owner_user_id` |
| 影响面 | **几乎所有业务表需加租户字段**，建议尽早做 |
| 验收 | 两用户数据隔离，管理员可管全部 |

#### E2. 个人设置 / 菜单可见性
| 项 | 内容 |
|----|------|
| 状态 | ⚠️ 有 profile 页雏形 |
| 优先级 | P2 |
| 参考 | `user_setting`、`personalSettings/*`、`MenuVisibilitySettings` |
| 整改 | 主题、菜单隐藏、个人偏好；非 admin 按设置过滤导航 |

#### E3. 系统设置中心
| 项 | 内容 |
|----|------|
| 状态 | ⚠️ 配置散落 yml + 少量页面 |
| 优先级 | P1 |
| 参考 | `system_settings`、`settings/*`、`system_control` |
| 整改 | 系统 KV 设置表；滑块模式、密码登录开关、品牌文案、服务重启（可选） |
| 表 | `system_setting` |

#### E4. 账号导入导出
| 项 | 内容 |
|----|------|
| 状态 | ❌ 缺失 |
| 优先级 | P2 |
| 参考 | `account_import_service`、`account_export_service` |
| 整改 | Cookie/账号批量导入导出（加密） |

#### E5. 共享扫码登录
| 项 | 内容 |
|----|------|
| 状态 | ❌ 缺失 |
| 优先级 | P1 |
| 参考 | `shared_scan_*`、`shared-scan/*` |
| 整改 | 扫码会话 + worker；多端/多人协助扫码加号 |
| 表 | `shared_scan_session`、`shared_scan_worker` |
| 前端 | 共享扫码管理页 + 公开扫码页 |

#### E6. 密码登录 / 人脸核验
| 项 | 内容 |
|----|------|
| 状态 | ⚠️ 有扫码/Cookie；密码/人脸弱 |
| 优先级 | P1 |
| 参考 | `password_login/*`、`xianyu_login/*`、`face_verification` |
| 整改 | 密码登录链路 + 人脸验证状态机 + 前端引导 |
| 验收 | 无法扫码场景可用密码登录加号 |

#### E7. 日志管理中心（管理端）
| 项 | 内容 |
|----|------|
| 状态 | ⚠️ 有 audit；缺运营批次日志聚合入口 |
| 优先级 | P1 |
| 参考 | `admin/logs` 导航组 |
| 整改 | 侧边栏「日志管理」聚合：补发/评价/擦亮/续期/备份/风控/登录 |
| 前端 | 路由组 + 统一布局 |

#### E8. 数据分析
| 项 | 内容 |
|----|------|
| 状态 | ⚠️ dashboard + data-board + market |
| 优先级 | P2 |
| 参考 | `data_analysis/*` |
| 整改 | 数据总览指标对齐参考（成交/回复/发货成功率等） |

---

### 阶段 F — 分销体系（P1）

#### F1. 货源 / 可对接卡券广场
| 项 | 内容 |
|----|------|
| 状态 | ❌ 缺失 |
| 优先级 | P1 |
| 参考 | `distribution.py`、`SourceManagement`、`SupplyManagement`、卡券 `is_dockable` |
| 整改 | 公开/分销商可见货源列表；对接价格/最低价校验 |

#### F2. 对接记录 Dock
| 项 | 内容 |
|----|------|
| 状态 | ❌ 缺失 |
| 优先级 | P1 |
| 参考 | `dock_record`、`dock_code_binding`、`DockedProducts` |
| 整改 | 创建/更新/删除对接；取货 URL；启停；级联状态 |
| 表 | `dock_record`、`dock_code_binding` |

#### F3. 分销卡券取货
| 项 | 内容 |
|----|------|
| 状态 | ❌ 缺失 |
| 优先级 | P1 |
| 参考 | `pickup_service`、`CardPickup` |
| 整改 | 安全取货接口（明文/加密策略对齐参考） |

#### F4. 代理订单
| 项 | 内容 |
|----|------|
| 状态 | ❌ 缺失 |
| 优先级 | P1 |
| 参考 | `agent_order`、`AgentOrders` |
| 整改 | 分销订单同步、状态、详情 |

#### F5. 分销商 / 下级分销商
| 项 | 内容 |
|----|------|
| 状态 | ❌ 缺失 |
| 优先级 | P1 |
| 参考 | `dealers`、`sub-dealers` 路由与页面 |
| 整改 | 二级分销关系、启停、详情、sub-dock |

#### F6. 资金流水 / 结算 / 提现
| 项 | 内容 |
|----|------|
| 状态 | ❌ 缺失（闲鱼钱包≠分销资金） |
| 优先级 | P1 |
| 参考 | `fund_flow`、`settlement_record`、`payment.py`、`FundFlows` |
| 整改 | 充值/消费/结算流水；提现申请/审核；风控校验 |
| 表 | `fund_flow`、`settlement_record`、`recharge_order` |
| 注意 | 与现有 `xianyu_wallet` 分离命名，避免混淆 |

---

### 阶段 G — 返佣子系统 promotion（P2）

#### G1. 返佣账号管理
| 项 | 内容 |
|----|------|
| 状态 | ❌ 缺失 |
| 优先级 | P2 |
| 参考 | `promotion/`、`fy_account` |
| 整改 | 独立模块或独立 Spring Boot 子应用；Cookie 管理 |

#### G2. 选品规则 → 素材库
| 项 | 内容 |
|----|------|
| 状态 | ❌ 缺失 |
| 优先级 | P2 |
| 参考 | `fy_product_rule`、`fy_material`、`ProductRules`、`Materials` |

#### G3. 发布规则 / 删除规则
| 项 | 内容 |
|----|------|
| 状态 | ❌ 缺失 |
| 优先级 | P2 |
| 参考 | `fy_publish_rule`、`fy_delete_rule`、相关页面与 scheduler 补偿 |

#### G4. 淘宝联盟搜索 / 短链修复 / 卡券补偿
| 项 | 内容 |
|----|------|
| 状态 | ❌ 缺失 |
| 优先级 | P2 |
| 参考 | `taobao_alliance`、promotion services |
| 架构建议 | 优先 `social-sdk-xianyu-promotion` 子模块，复用 publish/chrome/cookie 公共能力 |

---

### 阶段 H — 商业化与分发（P2）

#### H1. 激活码 / 机器绑定
| 项 | 内容 |
|----|------|
| 状态 | ❌ 缺失 |
| 优先级 | P2 |
| 参考 | `activation.py`、`launcher/activation.py`、`hardware_id.py`、`auth/GetActivation` |
| 整改 | 机器码、激活码签发/续期、本地校验、历史记录 |

#### H2. 支付充值（支付宝等）
| 项 | 内容 |
|----|------|
| 状态 | ❌ 缺失 |
| 优先级 | P2 |
| 参考 | `payment.py`、`alipay_service`、`recharge_service` |
| 整改 | 充值下单、异步通知、余额到账 |

#### H3. 公告 / 弹窗公告
| 项 | 内容 |
|----|------|
| 状态 | ❌ 缺失 |
| 优先级 | P2 |
| 参考 | `announcement`、`popup_announcement`、对应前端 |
| 整改 | 管理端发布、用户端展示 |

#### H4. 广告系统
| 项 | 内容 |
|----|------|
| 状态 | ❌ 缺失 |
| 优先级 | P3 |
| 参考 | `advertisement`、`advertisements/*` |
| 整改 | 广告位、申请、支付（可裁剪） |

#### H5. 意见反馈 / 教程 / 关于 / 免责声明
| 项 | 内容 |
|----|------|
| 状态 | ⚠️ 有协议/隐私；缺反馈教程 |
| 优先级 | P2 |
| 参考 | `feedback/*`、`tutorial`、`about`、`disclaimer` |
| 整改 | 静态页 + 反馈工单表 |

#### H6. 桌面启动器商业化
| 项 | 内容 |
|----|------|
| 状态 | ⚠️ 有 Electron 脚本 |
| 优先级 | P2 |
| 参考 | `launcher/*`、`EXE打包构建.bat` |
| 整改 | 激活校验、服务启停、日志面板、自动更新（对齐 launcher 能力） |

#### H7. 版本检查 / 在线更新
| 项 | 内容 |
|----|------|
| 状态 | ❌ 缺失 |
| 优先级 | P2 |
| 参考 | `version.py`、`updater.py`、`update.sh` |

---

### 阶段 I — 架构与工程硬化（并行）

#### I1. 任务执行隔离
| 项 | 内容 |
|----|------|
| 状态 | ⚠️ 单体 `@Scheduled` |
| 优先级 | P1 |
| 整改 | 方案二选一：A) 保持单体但独立线程池/进程内隔离；B) 拆 `manager-scheduler` 模块对标 scheduler 服务 |
| 建议 | 完整对标后期采用 B，前期用 A 顶住 |

#### I2. IM / 消息处理隔离
| 项 | 内容 |
|----|------|
| 状态 | ⚠️ 内嵌 Netty IM |
| 优先级 | P1 |
| 整改 | 评估拆 `manager-im` 进程，避免浏览器任务拖垮消息 |

#### I3. Redis 支持（多实例可选）
| 项 | 内容 |
|----|------|
| 状态 | ❌ 默认无 Redis |
| 优先级 | P2 |
| 参考 | `common/db/redis_client.py` |
| 整改 | 分布式锁、会话、任务互斥；单机可关闭 |

#### I4. 一键部署脚本增强
| 项 | 内容 |
|----|------|
| 状态 | ⚠️ 有 docker-compose 多 profile |
| 优先级 | P2 |
| 参考 | `deploy.sh`、`update.sh`、`deploy_remote.sh` |
| 整改 | 生成 env、健康检查、滚动更新、数据卷备份提示 |

#### I5. OpenAPI 覆盖新业务
| 项 | 内容 |
|----|------|
| 状态 | ✅ 已有框架 |
| 优先级 | P2 |
| 整改 | 卡券/发货/发布/监控等新域同步暴露 OpenAPI（保持我们优势） |

#### I6. 测试与真验基线
| 项 | 内容 |
|----|------|
| 状态 | ⚠️ 部分 OpenAPI 测试 |
| 优先级 | P0（贯穿全程） |
| 整改 | 1) 每个 MTOP 调用对照参考项目参数 golden file 2) 发货/续期集成测试 3) 关键回归清单 |
| 文档 | 扩展 `docs/` 真验对照表 |

#### I7. 数据迁移与兼容
| 项 | 内容 |
|----|------|
| 状态 | 必做 |
| 优先级 | P0 |
| 整改 | schema 版本化迁移（Flyway/Liquibase 或自研）；`virtual_*` → 新卡券模型迁移脚本 |

---

## 4. 数据表新增/改造总表

### 4.1 建议新增（名称可按项目规范调整）

| 表名（建议） | 阶段 | 说明 |
|--------------|------|------|
| `cookie_refresh_schedule` | A | 账号刷新计划 |
| `scheduled_cookies_refresh_log` | A | Cookie 浏览器刷新日志 |
| `scheduled_api_cookie_renew_log` | A | Cookie 接口续期日志 |
| `scheduled_login_renew_log` | A | 登录续期日志 |
| `account_login_log` | A | 账号登录日志 |
| `token_cache` | A | Token 缓存 |
| `scheduled_token_renewal_log` | A | Token 续期日志 |
| `risk_control_log` | A | 风控日志 |
| `ship_card` / 升级 `virtual_card_pool` | A | 卡券主表 |
| `card_item_relation` | A | 卡券-商品关联 |
| `delivery_block_rule` | A | 发货拦截规则 |
| `ship_task` / 扩展 `virtual_ship_task` | A | 发货任务 |
| `scheduled_redelivery_log` | A | 补发日志 |
| `confirm_receipt_message` | A | 确认收货话术 |
| `scheduled_task` | B | 任务注册中心 |
| `auto_rate_config` | B | 自动评价配置 |
| `scheduled_rate_log` | B | 评价日志 |
| `scheduled_red_flower_log` | B | 小红花日志 |
| `scheduled_polish_log` | B | 擦亮日志 |
| `scheduled_close_notice_log` | B | 关通知日志 |
| `db_backup_log` | B | 备份日志 |
| `product_material` | C | 素材库 |
| `publish_address` | C | 全局地址 |
| `user_publish_address` | C | 用户地址 |
| `publish_log` | C | 发布日志 |
| `listing_monitor_task` | C | 上新监控任务 |
| `listing_monitor_category` | C | 监控分类 |
| `listing_monitor_item` | C | 监控商品 |
| `listing_monitor_log` | C | 监控日志 |
| `collect_fallback_account` | C | 采集马甲 |
| `order_fallback_account` | C | 下单马甲 |
| `goofish_crawl_job` | C | 采集任务 |
| `goofish_crawl_item` | C | 采集结果 |
| `personal_blacklist` | D | 个人黑名单 |
| `platform_blacklist` | D | 平台黑名单 |
| `default_reply` | D | 默认回复 |
| `message_filter_rule` | D | 消息过滤 |
| `chat_quick_phrase` | D | 快捷话术 |
| `sys_user` | E | 多用户 |
| `user_setting` | E | 用户设置 |
| `system_setting` | E | 系统设置 |
| `shared_scan_session` | E | 共享扫码会话 |
| `shared_scan_worker` | E | 扫码 worker |
| `dock_record` | F | 分销对接 |
| `dock_code_binding` | F | 对接码 |
| `agent_order` | F | 代理订单 |
| `fund_flow` | F | 资金流水 |
| `settlement_record` | F | 结算 |
| `recharge_order` | F | 充值订单 |
| `fy_account` 等 | G | 返佣域 |
| `announcement` / `popup_announcement` | H | 公告 |
| `advertisement` | H | 广告 |
| `feedback` / `feedback_message` | H | 反馈 |
| `activation_*`（按需） | H | 激活 |

### 4.2 需改造的现有表

| 表 | 改造点 |
|----|--------|
| `xianyu_account` | 续期状态、冷却至、fallback 标记、owner_user_id |
| `xianyu_order` | 发货/评价/小红花状态字段补齐 |
| `xianyu_keyword_rule` | 图片关键词、商品维度、多回复 |
| `xianyu_auto_reply_config` | 与 default_reply 职责厘清 |
| `virtual_*` | 迁移到新卡券/发货模型或扩展字段 |
| `monitor_task` | 与 listing_monitor 命名/菜单一一区分 |
| 几乎所有业务表 | 多用户后加 `owner_user_id` |

---

## 5. 前端页面整改清单

### 5.1 需新增页面（对标参考导航）

| 路由建议 | 页面 | 阶段 |
|----------|------|------|
| `/app/cards` | 卡券管理 | A |
| `/app/logs/redelivery` | 补发货日志 | A |
| `/app/logs/cookies-refresh` | Cookie 刷新日志 | A |
| `/app/logs/api-cookie-renew` | 接口续期日志 | A |
| `/app/logs/login-renew` | 登录续期日志 | A |
| `/app/logs/token-renewal` | Token 续期日志 | A |
| `/app/logs/rate` | 评价批次日志 | B |
| `/app/logs/red-flower` | 小红花日志 | B |
| `/app/logs/polish-batches` | 擦亮批次日志 | B |
| `/app/logs/close-notice` | 关通知日志 | B |
| `/app/logs/db-backup` | 备份日志 | B |
| `/app/logs/risk` | 风控日志 | A |
| `/app/logs/account-login` | 账号登录日志 | A |
| `/app/scheduled-tasks` | 系统定时任务中心 | B |
| `/app/product-publish/materials` | 素材库 | C |
| `/app/product-publish/single` | 单品发布 | C |
| `/app/product-publish/batch` | 批量发布 | C |
| `/app/product-publish/addresses` | 地址库 | C |
| `/app/product-publish/logs` | 发布日志 | C |
| `/app/product-monitor/*` | 监控总览/分类/任务/日志/商品/fallback | C |
| `/app/crawler` | Goofish 定时采集 | C |
| `/app/blacklist` | 黑名单 | D |
| `/app/message-filters` | 消息过滤 | D |
| `/app/chat` 或升级 messages | 在线聊天增强 | D |
| `/app/admin/users` | 用户管理 | E |
| `/app/settings` | 系统设置 | E |
| `/app/shared-scan` | 共享扫码 | E |
| `/app/distribution/*` | 分销全套 | F |
| `/app/promotion/*` 或独立前端 | 返佣 | G |
| `/app/announcements` 等 | 公告广告反馈 | H |

### 5.2 需改造现有页面

| 现有页面 | 改造点 |
|----------|--------|
| `accounts` | 续期状态、冷却、发货规则弹窗、退款策略、导入导出 |
| `virtualShip` | 升级为卡券+发货中心或拆分到 cards |
| `rules` / `keywords` | 默认回复、图片关键词、商品专属 |
| `polish` | 接入定时任务与批次日志 |
| `reviews` | 接入自动评价配置 |
| `orders` | 补发、评价状态、黑名单快捷操作 |
| `messages` | 对齐 chat-new |
| `tasks` | 与「系统定时任务」区分命名 |
| `replyLogs` | 字段对齐 |
| 布局导航 | 按参考分组：业务 / 日志 / 管理 / 分销 |

---

## 6. 后端模块整改清单（包结构建议）

```text
social-sdk-xianyu-manager/
  account/          # 已有 → 增加 renew/cooldown/import-export/shared-scan
  card/             # 新建：卡券
  ship/             # 新建或升级 virtual/：发货引擎+规则+补发
  schedule/         # 新建：任务中心+各 Task+BatchLog
  publish/          # 新建：素材/地址/发布执行/日志
  listing/          # 新建：上新监控
  crawler/          # 新建：goofish 采集
  blacklist/        # 新建
  filter/           # 新建：消息过滤
  chat/             # 升级 message/
  user/             # 新建：多用户（替代/扩展 auth）
  system/           # 扩展：settings
  distribution/     # 新建：分销
  promotion/        # 新建或独立模块：返佣
  commerce/         # 新建：激活/支付/广告（可后置）
  risk/             # 新建：风控日志（与 circuit 协作）
```

SDK 层（`social-sdk-xianyu`）补充原则：

- 所有新 MTOP 调用必须在 Facade 暴露
- 参数/版本号对照参考项目 Python 实现，写入真验注释
- 禁止只在 manager 里拼 raw mtop 而不进 SDK（除非临时调试）

---

## 7. 定时任务对照表（必须落地）

| 任务 code | 参考 | 我们 | 阶段 |
|-----------|------|------|------|
| `cookies_refresh` | ✅ | ❌ | A |
| `api_cookie_renew` | ✅ | ❌ | A |
| `login_renew` | ✅ | ❌ | A |
| `token_renewal` | ✅ | ⚠️ | A |
| `token_captcha_renewal` | ✅ | ⚠️ | A |
| `auto_delivery_scan` | ✅ | ⚠️ `autoScanVirtualShip` | A |
| `redelivery` | ✅ | ❌ | A |
| `delivery_timeout` | ✅ | ⚠️ 部分确认收货 | A |
| `auto_rate` | ✅ | ❌ | B |
| `red_flower` | ✅ | ❌ | B |
| `polish` | ✅ | ⚠️ 无定时批量 | B |
| `close_notice` | ✅ | ❌ | B |
| `db_backup` | ✅ | ❌ | B |
| `fetch_items` | ✅ | ⚠️ `autoSyncProducts` | B |
| `fetch_orders` | ✅ | ⚠️ `autoSyncOrders` | B |
| `seller_fill` | ✅ | ❌ | B |
| `listing_monitor` | ✅ | ❌ | C |
| `goofish_crawl` | ✅ | ⚠️ market 不同 | C |
| `dm_send`（监控私信） | ✅ | ❌ | C |
| `cleanup_browser_data` | ✅ | ❌ | I |
| `day_switch` | ✅ | ❌ | I |
| `account_health` | ✅ | ✅ | — |
| `promotion_*` 补偿 | ✅ | ❌ | G |

---

## 8. 我们已具备、完整对标时「保留并写进验收」的优势项

以下不是缺口，整改时禁止弱化：

| 能力 | 说明 |
|------|------|
| AI 客服（意图/议价/知识库/模式） | 保持并与新默认回复/关键词优先级协同 |
| AI 运营助手 | 可与素材库/批量发布打通 |
| OpenAPI 开放平台 | 新业务域同步开放 |
| Chrome 容器池 + 指纹 | 作为 Cookie 刷新/发布的执行底座 |
| 代理池多提供商 | 与账号绑定、刷新任务联动 |
| 熔断器 | 与风控日志、冷却体系统一 |
| 网盘虚拟发货 | 作为卡券 `api/image` 类型的一种履约方式 |
| 多数据库 profile | SQLite/MySQL/PG 迁移脚本三端一致 |
| Electron/iOS/Android 打包骨架 | 商业化阶段接激活，不推倒重来 |

---

## 9. 验收标准（完整对标 Definition of Done）

### 9.1 稳定性
- [ ] 10 账号连续运行 72h，Cookie 自动续期成功率 ≥ 95%
- [ ] IM 断线可自动恢复，token 过期不导致静默丢消息
- [ ] 发货失败自动补发，人工可在日志中定位原因

### 9.2 业务主路径
- [ ] 加号（扫码/密码/共享扫码/Cookie）→ 同步商品订单 → 关键词/AI 回复 → 支付自动发货 → 评价/小红花
- [ ] 素材 → 地址 → 单品/批量发布 → 发布日志
- [ ] 上新监控任务产出商品并可私信
- [ ] 分销：上架可对接卡券 → 对接 → 取货 → 代理订单 → 流水

### 9.3 管理与商业
- [ ] 多用户数据隔离
- [ ] 定时任务中心可配置
- [ ] 全量批次日志可查
- [ ] （若启用）激活码可绑定机器并过期失效

### 9.4 工程
- [ ] 全部新表有 SQLite/MySQL/PG 三套 schema
- [ ] 关键迁移脚本可重复执行
- [ ] 关键 MTOP 有参考项目对照注释或测试
- [ ] 前端导航与权限完整

---

## 10. 建议排期里程碑

| 里程碑 | 包含阶段 | 交付物 |
|--------|----------|--------|
| M1 | A + B9 + I6/I7 启动 | 保活+发货+补发可演示 |
| M2 | B 全量 | 运维任务矩阵 + 日志中心 |
| M3 | C + D | 发布/监控/消息产品化 |
| M4 | E | 多用户 + 系统设置 + 共享扫码 |
| M5 | F | 分销全链路 |
| M6 | G + H | 返佣 + 商业化分发 |
| M7 | I 收尾 | 隔离部署、Redis、一键更新 |

---

## 11. 工作项 ID 索引（便于开 Issue / PR）

| ID | 标题 | 优先级 | 阶段 |
|----|------|--------|------|
| A1 | Cookie 浏览器刷新 | P0 | A |
| A2 | Cookie 接口续期 | P0 | A |
| A3 | 登录续期 | P0 | A |
| A4 | Token/IM 续期 | P0 | A |
| A5 | 冷却/限流/风控日志 | P0 | A |
| A6 | 卡券模型升级 | P0 | A |
| A7 | 发货规则引擎 | P0 | A |
| A8 | 自动发货主链路 | P0 | A |
| A9 | 补发货 | P0 | A |
| A10 | 确认收货话术 | P1 | A |
| B1 | 任务调度中心 | P0 | B |
| B2 | 自动评价 | P0 | B |
| B3 | 求小红花 | P1 | B |
| B4 | 定时擦亮+日志 | P1 | B |
| B5 | 关通知任务 | P2 | B |
| B6 | DB 备份 | P1 | B |
| B7 | 商品订单同步增强 | P1 | B |
| B8 | 浏览器清理/日切 | P2 | B |
| B9 | 批次日志框架 | P0 | B |
| C1 | 素材库 | P1 | C |
| C2 | 地址库 | P1 | C |
| C3 | 发布流水线 | P1 | C |
| C4 | 上新监控 | P1 | C |
| C5 | Fallback 账号 | P1 | C |
| C6 | Goofish 采集 | P1 | C |
| C7 | 搜索/Compass | P2 | C |
| D1 | 黑名单 | P1 | D |
| D2 | 默认回复 | P1 | D |
| D3 | 消息过滤 | P1 | D |
| D4 | 快捷话术 | P2 | D |
| D5 | 聊天增强 | P1 | D |
| D6 | 关键词增强 | P1 | D |
| D7 | 回复日志增强 | P2 | D |
| D8 | 退款策略 | P2 | D |
| E1 | 多用户 | P1 | E |
| E2 | 个人设置/菜单 | P2 | E |
| E3 | 系统设置 | P1 | E |
| E4 | 账号导入导出 | P2 | E |
| E5 | 共享扫码 | P1 | E |
| E6 | 密码/人脸登录 | P1 | E |
| E7 | 日志管理中心 | P1 | E |
| E8 | 数据分析对齐 | P2 | E |
| F1–F6 | 分销全套 | P1 | F |
| G1–G4 | 返佣全套 | P2 | G |
| H1–H7 | 商业化分发 | P2/P3 | H |
| I1–I7 | 架构硬化 | P0–P2 | I |

**条目合计**：约 **60+** 独立工作项（F/G/H 子项展开后更多）。

---

## 12. 执行约定

1. **每项整改开独立 PR**，标题带工作项 ID（如 `feat(A6): upgrade card model`）。  
2. **MTOP 参数**以 `xianyu-auto-reply` 源码为准，禁止凭猜测命名。  
3. **先表结构与领域模型，再 API，再前端，最后定时任务接线**。  
4. **多用户（E1）若确定要做，应尽量提前到 M2 前完成表字段设计**，避免二次迁移。  
5. **虚拟发货旧数据**必须在 A6 提供迁移脚本，禁止直接删表。  
6. 本清单随实现更新状态：建议在每项标题后追加 ` [ ] / [x] ` 勾选。

---

## 13. 相关文档

| 文档 | 说明 |
|------|------|
| [plan/requirements.md](./requirements.md) | 早期轻量版需求（单管理员） |
| [plan/index.md](./index.md) | API/路由速查（偏早期） |
| [readme.md](../readme.md) | 当前项目能力说明 |
| [docs/openapi.md](../docs/openapi.md) | OpenAPI 文档 |
| 参考项目 README | `xianyu-auto-reply/README.md` |

---

> **下一步建议**：从 **M1（A1–A9 + B9）** 开工，先打通「账号不掉线 + 支付必发货 + 失败可补发 + 日志可查」。  
> 需要时可将本清单拆成 GitHub Issues（按 ID 批量创建）。
