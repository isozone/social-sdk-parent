# 差异补集：xianyu-auto-bot 相对现有整改清单

> **用途**：`parity-remediation-checklist.md` 已以 **xianyu-auto-reply** 为基准在写。  
> 本文只列 **auto-bot 有、现有清单未覆盖或覆盖过浅** 的差异项，供后续往主清单「补」入。  
> **不重复** 主清单已写清的 A1–I7 条目正文。  
> **参考**：`/Users/vim/Desktop/codes/github/xianyu-auto-bot`  
> **主清单**：`plan/parity-remediation-checklist.md`  
> **日期**：2026-07-25

---

## 0. 怎么用本文

| 分类 | 含义 | 后续动作 |
|------|------|----------|
| **Δ 全新** | 主清单无对应 ID，必须新增工作项 | 补进主清单（建议 ID：`BOT-*`） |
| **↑ 加深** | 主清单有相关项，但 auto-bot 实现更深/语义不同 | 在原 ID 下追加「金标准参考=auto-bot」与验收细则 |
| **≈ 可忽略** | 与主清单等价或我们已更强 | 不补，仅作备注 |

**合并原则**：

1. 保活 / 订单状态 / 发货终态 → **实现细节优先对标 auto-bot**  
2. 分销 / 采集 / 返佣 / 激活 → 仍以 auto-reply 主清单为准（auto-bot 无）  
3. 同一能力两边都有时：主清单保留 ID，本文只写「加深点」

---

## 1. 总览：差异热力图

| 域 | 主清单覆盖 | auto-bot 差异 | 类型 |
|----|------------|---------------|------|
| Cookie/Token 续期 | A1–A4 | 有，但缺 **Keepalive 循环**、**夜间倍率**、**扫码 grace**、**恢复锁** | ↑ + Δ |
| 风控 | A5 | 有冷却；缺 **账号暂停保护**、**runtime 只读徽章**、**运营动作提示** | ↑ + Δ |
| 卡券 | A6 | 有类型；缺 **yifan_api**、**卡密预留 reservation** | ↑ |
| 发货规则 | A7 | ⚠️ **语义不同**（见 §2.1） | Δ 易混 |
| 自动发货 | A8–A9 | 有主链路；缺 **finalization 多件终态**、**delivery_logs 产品化** | ↑ + Δ |
| 订单 | B7 同步 | 缺完整 **订单状态机**、**历史同步 Job**、**SSE** | Δ |
| 默认回复/关键词 | D2/D6 | 有；缺 **商品专属回复 item_reply** | Δ |
| 自动评价 | B2 | 有任务；缺 **评价模板 CRUD**（comment_templates） | ↑ |
| 擦亮 | B4 | 有；auto-bot 的 scheduled_tasks 可作实现参考 | ≈/↑ |
| 多用户 | E1 | 有；缺 **注册验证码/邮件**、**登录 IP 封锁**、**用户组** | ↑ + Δ |
| 密码/人脸 | E6 | 有；缺 **失败 backoff**、**人脸截图 API 产品化**、**qr-lite** | ↑ |
| 备份 | B6 | 有定时备份；缺 **维护态恢复**（完整性检查+吊销会话） | ↑ |
| 测试/安全边界 | I6 | 有真验；缺 **smoke 矩阵**、**内部 API Key 边界**、**live/ready** | ↑ + Δ |
| 销售统计 | E8 笼统 | 缺 **sales/summary API** | Δ |
| SSE 实时 | D5 聊天 | 缺 **订单流 / 统一 SSE 契约** | Δ |
| 文件下载令牌 | 无 | download_files + token | Δ |
| 在线更新 | H7 | 有；auto-bot auto_updater 可作参考 | ≈ |
| 分销/返佣/采集 | F/G/C | auto-bot **没有** | 不补 |

---

## 2. 关键混淆点（补清单前必读）

### 2.1 A7「发货规则」≠ auto-bot `delivery_rules`

| | 主清单 A7（auto-reply） | auto-bot `delivery_rules` |
|--|------------------------|---------------------------|
| 语义 | **拦截/阻断** 发货（信用、黑名单、未确认…） | **匹配** 发货：关键词 → 卡券 |
| 表 | `xy_delivery_block_rule` | `delivery_rules`（keyword, card_id, delivery_count…） |
| 时机 | 发货前校验是否允许 | 决定发哪张卡、发几次 |

**补清单建议**：

- 保留 **A7** = Block Rules（拦截）  
- **新增 BOT-D1** = Match Rules（关键词→卡券匹配）  
- A8 主链路同时依赖 A7 + BOT-D1

### 2.2 A4 Token 续期 ≠ Session Keepalive

| | A4 Token/IM 续期 | auto-bot Keepalive |
|--|------------------|--------------------|
| 目的 | 刷新 access/IM token，避免鉴权失效 | 周期轻量探测会话仍活着 |
| 典型 API | token 相关 mtop | `mtop.taobao.idlemessage.pc.loginuser.get` |
| 频率 | 长周期 / 重连前 | 默认约 10 分钟（可配） |

**补清单建议**：A4 保留；**新增 BOT-A1 Session Keepalive**。

### 2.3 B6 备份任务 ≠ 维护态恢复

主清单 B6 = 定时导出备份文件。  
auto-bot = 上传 `.db` → 完整性检查 → **暂停账号任务** → 原子替换 → 刷新 CookieManager → **吊销全部登录/下载令牌**。

**补清单建议**：B6 保留；**新增 BOT-S2 维护态 DB 恢复**。

---

## 3. 建议新增工作项（Δ 全新）

> 建议 ID 前缀 `BOT-`，合并进主清单时可改挂到 A/B/D/E/I 阶段下。

### 3.1 运行时保活与连接（建议挂阶段 A）

#### BOT-A1. Session Keepalive 循环
| 项 | 内容 |
|----|------|
| 类型 | Δ 全新 |
| 优先级 | **P0** |
| 参考 | `global_config.yml` → `SESSION_KEEPALIVE_*`；`XianyuAutoAsync` keepalive 逻辑 |
| 主清单关系 | 与 A4 互补，**不是** A4 子集 |
| 整改 | 1) 每账号周期调用 loginuser.get 2) 失败走 `SESSION_KEEPALIVE_RETRY_INTERVAL` 3) 与夜间倍率联动 4) 可手动触发（已有 session-keepalive 路由语义） |
| 配置项 | `session_keepalive_interval`、`session_keepalive_retry_interval` |
| 验收 | 账号 24h 内无业务时仍保持会话；失败可观测且不风暴重试 |

#### BOT-A2. 连接状态机 + 消息流 Watchdog
| 项 | 内容 |
|----|------|
| 类型 | Δ 全新 |
| 优先级 | **P0** |
| 参考 | `ConnectionState`、`message_stream_watchdog_loop`、`_force_websocket_reconnect` |
| 主清单关系 | A4/IM 重连的加深与显式化 |
| 整改 | 1) 明确 CONNECTING/CONNECTED/RECONNECTING/PAUSED 等状态 2) 业务消息空闲超时强制重连 3) 心跳与业务空闲分离统计 |
| 验收 | 人为掐断流后自动恢复；状态可在 runtime API 读到 |

#### BOT-A3. 扫码 Grace / 认证恢复锁 / Token 预热
| 项 | 内容 |
|----|------|
| 类型 | Δ 全新（细节） |
| 优先级 | **P1** |
| 参考 | `mark_qr_login_grace`、`acquire_auth_recovery_lock`、`cache_auth_prewarmed_token`、`manual_refresh_handoff` |
| 主清单关系 | 深化 A3/A4，避免扫码成功后立刻被自动恢复逻辑打架 |
| 整改 | 1) 扫码成功 grace 窗口内推迟激进 auth recovery 2) 同账号恢复互斥锁 3) 预热 token 交接 |
| 验收 | 扫码加号后 N 分钟内不因 keepalive/token 任务误杀会话 |

#### BOT-A4. 夜间模式倍率
| 项 | 内容 |
|----|------|
| 类型 | Δ 全新 |
| 优先级 | **P2** |
| 参考 | `RISK_CONTROL.night_*` |
| 整改 | 夜间拉长 keepalive / cookie refresh 间隔，降低风控 |
| 配置 | `night_mode_enabled`、`night_start/end_hour`、multiplier |
| 验收 | 夜间实际间隔 = 日间 × multiplier |

#### BOT-A5. 账号 Runtime 只读监控
| 项 | 内容 |
|----|------|
| 类型 | Δ 全新 |
| 优先级 | **P1** |
| 参考 | Phase 102：`/cookies/{cid}/runtime-status`、`monitoring_safe=true`、前端徽章 |
| 主清单关系 | 不同于 A5 风控动作；是 **可观测性** |
| 整改 | 1) 聚合本地：运行中/重连/风控暂停/未运行 2) **禁止**监控路径自动触发 token/扫码/历史拉取 3) 账号表徽章 + 诊断面板 5s 刷新（仅当前页可见时） |
| 验收 | 监控接口不产生任何出站闲鱼写操作；徽章与真实任务状态一致 |

#### BOT-A6. 风控暂停保护（防死循环）
| 项 | 内容 |
|----|------|
| 类型 | ↑ A5 加深 → 建议拆独立 ID 以免 A5 过大 |
| 优先级 | **P0** |
| 参考 | `_pause_account_for_manual_verification`、`_protect_account_from_risk_login_retry`、`FAIL_SYS_USER_VALIDATE` 处理 |
| 整改 | 1) 识别官方风控码 2) 暂停账号任务 3) 写 risk_log + 中文运营摘要 + `operator_action_required` 4) 通知 5) 禁止自动狂重试滑块/登录 |
| 验收 | 触发 captcha_max_retries 后账号进入暂停，不再刷官方接口 |

---

### 3.2 订单与发货深度（建议挂阶段 A/B）

#### BOT-O1. 订单状态机
| 项 | 内容 |
|----|------|
| 类型 | Δ 全新 |
| 优先级 | **P0** |
| 参考 | `order_status_handler.py`（~1700 行）、`orders.pre_refund_status`、议价流标记字段 |
| 主清单关系 | B7 只是「拉取同步」；本项是 **消息/事件驱动状态迁移** |
| 整改 | 1) 状态枚举与迁移表 2) 消息绑定订单（sid/buyer/item） 3) 退款前状态保留 4) 待更新缓冲 pending updates 5) 与发货/评价触发点挂钩 |
| 表字段 | `order_status`、`pre_refund_status`、`bargain_*`、`platform_*_at` 等 |
| 验收 | 下单→付款→发货→完成/退款 全路径状态正确；单测对齐 smoke `test_order_status_*` |

#### BOT-O2. 订单历史同步 Job（可取消）
| 项 | 内容 |
|----|------|
| 类型 | Δ 全新（B7 的 Job 化） |
| 优先级 | **P1** |
| 参考 | `/api/orders/history-sync`、`job_id`、cancel |
| 整改 | 异步 job：创建/查进度/取消；非阻塞 HTTP |
| 验收 | 大账号历史回填可中途取消且不脏写 |

#### BOT-O3. 发货匹配规则（关键词→卡券）
| 项 | 内容 |
|----|------|
| 类型 | Δ 全新（与 A7 并列） |
| 优先级 | **P0** |
| 参考 | `delivery_rules` 表、`/delivery-rules*`、`app-delivery.js` |
| 整改 | CRUD + stats；匹配模式；与卡券、商品关键词联动 |
| 表 | `delivery_rules`（勿与 block_rule 混表） |
| 验收 | 配置「关键词X→卡券Y」后自动发货命中正确 |

#### BOT-O4. 发货日志 delivery_logs
| 项 | 内容 |
|----|------|
| 类型 | Δ 全新 |
| 优先级 | **P0** |
| 参考 | `delivery_logs`、`/delivery-logs/recent` |
| 主清单关系 | A8/A9/B9 有日志概念；本项是 **发货专用结构化日志** |
| 字段 | order_id、card_type、match_mode、channel(auto/manual)、status、reason… |
| 验收 | 每次尝试发货必有一条；可按订单反查 |

#### BOT-O5. 发货终态 + 多件 unit_index
| 项 | 内容 |
|----|------|
| 类型 | Δ 全新 |
| 优先级 | **P0** |
| 参考 | `delivery_finalization_states`（order_id + unit_index） |
| 整改 | 多数量订单按 unit 跟踪 sent/finalized；支持部分成功 |
| 验收 | quantity=3 时 3 个 unit 状态独立且最终一致 |

#### BOT-O6. 卡密预留 reservation（防并发重复发卡）
| 项 | 内容 |
|----|------|
| 类型 | Δ 全新 |
| 优先级 | **P0** |
| 参考 | `data_card_reservations` |
| 主清单关系 | A6/A8 加深 |
| 整改 | 发货前预留→发送成功确认消耗→失败释放；唯一约束防双花 |
| 验收 | 并发两个发货请求不会发同一条 data 卡密 |

#### BOT-O7. 多数量发货开关（商品级）
| 项 | 内容 |
|----|------|
| 类型 | Δ 全新 |
| 优先级 | **P1** |
| 参考 | `/items/.../multi-quantity-delivery`、`multi-spec` |
| 整改 | 商品维度开关：是否按购买数量拆 unit 发货 |
| 验收 | 与 BOT-O5 联调通过 |

---

### 3.3 回复与客服（建议挂阶段 D）

#### BOT-D1. 商品专属回复 item_reply
| 项 | 内容 |
|----|------|
| 类型 | Δ 全新 |
| 优先级 | **P1** |
| 参考 | `item_replay` 表、`/item-reply/*`、`/itemReplays` |
| 主清单关系 | 不同于 D2 默认回复、D6 关键词；优先级应明确：**商品专属 > 关键词 > 默认 > AI** |
| 整改 | 按 cookie_id+item_id 配置回复；批量删；聊天侧快捷绑定 |
| 验收 | 同账号不同商品命中不同专属回复 |

#### BOT-D2. 会话级自动回复暂停 pause-duration
| 项 | 内容 |
|----|------|
| 类型 | Δ 全新 |
| 优先级 | **P1** |
| 参考 | `AutoReplyPauseManager`、`/cookies/{cid}/pause-duration` |
| 整改 | 人工介入某会话后 N 分钟内不自动回；过期清理 |
| 验收 | 暂停期内关键词/AI 均不触发；到期自动恢复 |

#### BOT-D3. 关键词图文批次与跨商品复制
| 项 | 内容 |
|----|------|
| 类型 | ↑ D6 加深（可并入 D6，若 D6 已写图文可只加复制） |
| 优先级 | **P1** |
| 参考 | `/keywords/{cid}/image-batch`、`/api/chat/keywords/.../copy` |
| 整改 | 图片关键词批量上传；A 商品关键词复制到 B 商品 |
| 验收 | 复制后独立可改，互不影响 |

#### BOT-D4. 聊天/订单 SSE（或等价推送契约）
| 项 | 内容 |
|----|------|
| 类型 | Δ 全新（实现可用 WS，契约对齐事件） |
| 优先级 | **P1** |
| 参考 | `/api/chat/stream`、`/api/orders/stream`、`chat_event_hub`、`order_event_hub` |
| 主清单关系 | D5 聊天增强应消费同一事件总线 |
| 整改 | 统一事件：new_message / order_updated / delivery_result / account_runtime |
| 验收 | 前端打开聊天与订单页可实时更新，无需整表轮询 |

---

### 3.4 评价模板（建议挂阶段 B）

#### BOT-B1. 评价模板 comment_templates
| 项 | 内容 |
|----|------|
| 类型 | ↑ B2 加深 → 建议子项 |
| 优先级 | **P1** |
| 参考 | `/cookies/{cid}/comment-templates*`、`auto-comment` 开关 |
| 整改 | 多模板、激活模板、账号级 auto-comment 开关；B2 任务消费激活模板 |
| 验收 | 改模板后自动评价内容立即变化 |

---

### 3.5 卡券类型增量（建议挂 A6）

#### BOT-C1. yifan_api 卡券类型
| 项 | 内容 |
|----|------|
| 类型 | ↑ A6 类型枚举补丁 |
| 优先级 | **P2**（无亦凡对接可降 P3） |
| 参考 | `cards.type` 含 `yifan_api`、`YIFAN_API` 配置 |
| 整改 | A6 枚举增加 `yifan_api` + 回调/查询适配器 |
| 验收 | 配置后发货走亦凡回调链路（若业务需要） |

---

### 3.6 账号登录体验增量（建议挂 E6 / A3）

#### BOT-E1. 二维码登录 Lite
| 项 | 内容 |
|----|------|
| 类型 | Δ 全新 |
| 优先级 | **P2** |
| 参考 | `/qr-login-lite/*` |
| 整改 | 轻量扫码路径（少浏览器依赖）与完整扫码并存 |
| 验收 | lite 与 full 均可加号 |

#### BOT-E2. 手动 Cookie 导入预检 + 异步会话
| 项 | 内容 |
|----|------|
| 类型 | Δ 全新（E4 导入的深化） |
| 优先级 | **P1** |
| 参考 | `/manual-cookie-import`、`check/{session_id}`、smoke `test_manual_cookie_import_*` |
| 整改 | 提交→预检（滑块/有效性）→轮询会话→成功入库；失败原因结构化 |
| 验收 | 无效 Cookie 在入库前被拒绝并提示 |

#### BOT-E3. 密码登录失败 Backoff
| 项 | 内容 |
|----|------|
| 类型 | ↑ E6 加深 |
| 优先级 | **P1** |
| 参考 | `password_login_failure_backoff`、分类 reason→等待秒数 |
| 整改 | 连续失败指数退避；风控类失败转 BOT-A6 暂停 |
| 验收 | 错误密码不会打爆官方接口 |

#### BOT-E4. 人脸核验截图 API
| 项 | 内容 |
|----|------|
| 类型 | ↑ E6 加深 |
| 优先级 | **P1** |
| 参考 | `/face-verification/screenshot/{account_id}` |
| 整改 | 需人工人脸时落截图供前端展示；用完可删 |
| 验收 | 触发人脸时前端能展示最新截图 |

---

### 3.7 多用户与安全（建议挂 E / I）

#### BOT-E5. 注册开关 / 登录验证码 / 邮件验证码
| 项 | 内容 |
|----|------|
| 类型 | Δ 全新（E1 周边） |
| 优先级 | **P2** |
| 参考 | `registration-settings`、`login-captcha-settings`、`email_verifications`、`captcha_codes` |
| 整改 | 系统设置控制是否开放注册；登录图形验证码；邮箱验证码注册 |
| 验收 | 关闭注册后匿名无法 register |

#### BOT-E6. 登录安全：IP 封锁 / 解锁用户
| 项 | 内容 |
|----|------|
| 类型 | Δ 全新 |
| 优先级 | **P2** |
| 参考 | `/admin/security/*`、login-stats |
| 整改 | 失败次数封锁 IP；管理员解锁；黑名单 IP |
| 验收 | 暴力破解触发封锁；admin 可解 |

#### BOT-E7. 用户组 groups
| 项 | 内容 |
|----|------|
| 类型 | Δ 全新 |
| 优先级 | **P3** |
| 参考 | `/api/groups*`、`user_groups` |
| 整改 | 用户分组与成员管理（权限粗粒度） |
| 验收 | 组内资源策略可配置（若需要） |

#### BOT-S1. 健康检查 live / ready 分层
| 项 | 内容 |
|----|------|
| 类型 | Δ 全新 |
| 优先级 | **P1** |
| 参考 | `/health/live`、`/health`、`/health/ready`；README 部署限制说明 |
| 整改 | live=进程活；ready=DB+CookieManager+资源；恢复维护期 ready=503 |
| 验收 | K8s/Docker 探针配置文档化且行为符合 |

#### BOT-S2. 维护态数据库恢复
| 项 | 内容 |
|----|------|
| 类型 | ↑ B6 旁路能力 |
| 优先级 | **P1** |
| 参考 | admin backup upload 流程、smoke `test_backup_restore_runtime` |
| 整改 | 上传→校验表齐全→维护锁→停任务→备份旧库→原子替换→刷新缓存→吊销 token→失败回滚 |
| 验收 | 恢复成功后须重新登录；失败时原库完好 |

#### BOT-S3. 内部服务 API Key 边界
| 项 | 内容 |
|----|------|
| 类型 | Δ 全新 |
| 优先级 | **P1** |
| 参考 | `XIANYU_REPLY_API_KEY`、`SEND_MESSAGE_API_KEY`、`CAPTCHA_CONTROL_API_KEY`；`test_public_boundaries` |
| 主清单关系 | 我们有 OpenAPI；这是 **内部回调/运维入口** 的密钥边界 |
| 整改 | 内部回复回调、发消息、远程滑块控制均强制密钥；禁止默认测试 key |
| 验收 | 匿名打内部接口 401；无硬编码测试密钥 |

#### BOT-S4. Smoke 测试矩阵（核心路径）
| 项 | 内容 |
|----|------|
| 类型 | ↑ I6 加深 |
| 优先级 | **P0** |
| 参考 | `tests/smoke/*`（账户/发货迁移/订单/权限/备份/滑块守卫…） |
| 整改 | 为 BOT-O1/O3–O6、A8、保活、权限隔离建立 Java 侧等价冒烟 |
| 验收 | CI 必跑；主路径回归不靠手工 |

#### BOT-S5. 管理端数据表浏览器（可选）
| 项 | 内容 |
|----|------|
| 类型 | Δ 全新 |
| 优先级 | **P3** |
| 参考 | `/admin/data/{table_name}` export/delete |
| 整改 | 管理员只读/导出调试（生产默认关闭） |

---

### 3.8 其他产品增量

#### BOT-P1. 销售统计 Sales
| 项 | 内容 |
|----|------|
| 类型 | Δ 全新（E8 可吸收） |
| 优先级 | **P2** |
| 参考 | `/api/sales`、`/api/sales/summary` |
| 整改 | 按账号/时间聚合成交额、单量；仪表盘卡片 |
| 验收 | 与订单表对得上 |

#### BOT-P2. 文件中心 + 下载令牌
| 项 | 内容 |
|----|------|
| 类型 | Δ 全新 |
| 优先级 | **P2** |
| 参考 | `/api/files*`、`download_files`、`download_records`、file download tokens |
| 主清单关系 | 与网盘/虚拟发货文件不同：偏 **系统内文件分发与一次性令牌** |
| 验收 | 令牌过期不可下；权限校验 |

#### BOT-P3. 滑块轨迹池 + 远程人工兜底产品化
| 项 | 内容 |
|----|------|
| 类型 | ↑ 我们已有 CDP 滑块 |
| 优先级 | **P1** |
| 参考 | `TRAJECTORY_POOL`、`REMOTE_CAPTCHA`、`api_captcha_remote`、`captcha_control.html` |
| 整改 | 1) 轨迹池 LRU/加权 2) 自动失败转远程人工 WebSocket 3) 控制入口 API Key（BOT-S3） 4) 管理端滑块统计 |
| 验收 | 自动失败后人工可在超时内接管；统计可查 |

#### BOT-P4. 账号备注 / 独立代理绑定 UI
| 项 | 内容 |
|----|------|
| 类型 | ↑ 我们代理池已有，账号备注可能弱 |
| 优先级 | **P2** |
| 参考 | `/cookies/{cid}/remark`、`/cookie/{cid}/proxy` |
| 整改 | 账号备注 CRUD；每账号代理读写与我们 proxy 模块对齐产品入口 |

#### BOT-P5. 系统缓存热加载
| 项 | 内容 |
|----|------|
| 类型 | Δ 全新 |
| 优先级 | **P3** |
| 参考 | `/system/reload-cache` |
| 整改 | 不重启刷新 CookieManager/配置缓存 |

---

## 4. 主清单已有项：仅「加深」清单（↑ 不新增 ID 时怎么改）

合并时在主清单对应条目下追加一小节即可：

| 主清单 ID | 用 auto-bot 加深什么 | 金标准文件 |
|-----------|----------------------|------------|
| **A4** | 重连前按需刷新、dedup window、retry min wait、与 captcha 联动 | `TOKEN_*`、token refresh 相关 |
| **A5** | 中文运营摘要、operator_action_required；与 BOT-A6 暂停联动 | risk log 创建/更新 |
| **A6** | 双规格 spec_name_2；delay_seconds；enabled；user_id 隔离 | `cards` 表 |
| **A8** | 发货通道 channel=auto/manual；与 BOT-O3/O4/O5/O6 串起来 | delivery 相关 + order handler |
| **A10** | 账号级 auto-confirm **开关**（不仅是话术） | `/cookies/{cid}/auto-confirm` |
| **B2** | 消费 BOT-B1 激活模板；账号级 auto-comment 开关 | comment-templates + auto-comment |
| **B4** | scheduled_tasks 表驱动擦亮（可与 B1 合并实现） | `scheduled_tasks`、`polish-items` |
| **B6** | 导出格式兼容；恢复走 BOT-S2 | backup 路由 |
| **B7** | 订单字段对齐 BOT-O1；大同步走 BOT-O2 | order history sync |
| **D2** | default_reply_records 防重复默认回 | `default_reply_records` |
| **D5** | 会话历史、发送、账号切换对齐 `/api/chat/*` | app-im.js |
| **D6** | type=text/image、item_id 维度、导入导出 | keywords 表 |
| **E1** | Cookie 级访问控制矩阵（owner 校验） | smoke `test_cookie_access_control`、`test_authz_matrix` |
| **E6** | 与 BOT-E1/E2/E3/E4 一起验收 | password/qr/face 路由 |
| **I6** | 必须包含 BOT-S4 冒烟集 | tests/smoke |
| **H7** | 可参考 `auto_updater.py` 进度/哈希/重启 | update API |

---

## 5. 不建议从 auto-bot 补进主清单的

| 项 | 原因 |
|----|------|
| 把后端收成 reply_server 巨型单文件 | 与我们分层架构冲突 |
| Bootstrap 重写 Vue 前端 | 无收益 |
| 放弃 OpenAPI / AI 客服深度 | 我们优势 |
| 亦凡 API（BOT-C1）若无业务 | 可直接 P3/砍 |
| 用户组（BOT-E7）若无多租户协作需求 | 可砍 |
| admin 任意表浏览器（BOT-S5）生产默认开 | 安全风险，仅调试 |

---

## 6. 建议补进主清单的最小集合（按优先级）

### P0（M1 必补，否则「像 bot 一样稳」做不到）

| ID | 标题 |
|----|------|
| BOT-A1 | Session Keepalive |
| BOT-A2 | 连接状态机 + Watchdog |
| BOT-A6 | 风控暂停保护 |
| BOT-O1 | 订单状态机 |
| BOT-O3 | 发货匹配规则（≠ A7） |
| BOT-O4 | delivery_logs |
| BOT-O5 | 发货终态 multi-unit |
| BOT-O6 | 卡密预留 reservation |
| BOT-S4 | Smoke 测试矩阵 |

### P1（M1–M2 紧随）

| ID | 标题 |
|----|------|
| BOT-A3 | Grace / 恢复锁 / Token 预热 |
| BOT-A5 | Runtime 只读监控 |
| BOT-O2 | 历史同步 Job |
| BOT-O7 | 多数量发货开关 |
| BOT-D1 | 商品专属回复 |
| BOT-D2 | 会话暂停 pause |
| BOT-D4 | 聊天/订单实时事件 |
| BOT-B1 | 评价模板 |
| BOT-E2 | Cookie 导入预检 |
| BOT-E3 | 密码失败 Backoff |
| BOT-E4 | 人脸截图 |
| BOT-S1 | live/ready |
| BOT-S2 | 维护态 DB 恢复 |
| BOT-S3 | 内部 API Key |
| BOT-P3 | 轨迹池 + 远程人工滑块 |

### P2–P3（可后置）

BOT-A4 夜间模式、BOT-E1 qr-lite、BOT-E5 注册验证码、BOT-E6 IP 封锁、BOT-E7 用户组、BOT-P1 Sales、BOT-P2 文件令牌、BOT-P4 备注代理 UI、BOT-P5 缓存热加载、BOT-C1 yifan、BOT-S5 表浏览器、BOT-D3 关键词复制（若 D6 未覆盖）

---

## 7. 合并进主清单时的推荐写法

在 `parity-remediation-checklist.md` 文首「参考路径」改为双参考：

```markdown
> **参考路径**：
> - 产品广度：`xianyu-auto-reply`
> - 运行时深度：`xianyu-auto-bot`（详见 plan/parity-diff-auto-bot.md）
```

阶段 A 末尾追加一节：

```markdown
### 阶段 A+ — auto-bot 运行时深度（差异补集）
#### BOT-A1 ...
```

或把 BOT-A* 直接编为 **A11+**，BOT-O* 编为 **A12+ / 新阶段 O**，避免与 auto-reply 条目抢号。

**推荐编号方案（最少改主清单）**：

| 原建议 | 并入主清单编号 |
|--------|----------------|
| BOT-A1 | **A11** Session Keepalive |
| BOT-A2 | **A12** 连接状态机/Watchdog |
| BOT-A6 | **A13** 风控暂停保护 |
| BOT-O1 | **A14** 订单状态机 |
| BOT-O3 | **A15** 发货匹配规则 |
| BOT-O4 | **A16** delivery_logs |
| BOT-O5 | **A17** 发货终态 |
| BOT-O6 | **A18** 卡密预留 |
| BOT-D1 | **D9** 商品专属回复 |
| BOT-D2 | **D10** 会话暂停 |
| BOT-B1 | 并入 **B2** 子节 |
| BOT-S1–S4 | **I8–I11** |

---

## 8. 双参考决策表（写主清单时用）

| 能力 | 广度参考 | 深度/实现参考 |
|------|----------|----------------|
| Cookie 浏览器刷新 | auto-reply 任务+批次日志 | auto-bot 夜间倍率/冷却 |
| Token | auto-reply 批次日志 | **auto-bot** 按需刷新+dedup |
| Keepalive | — | **auto-bot only** |
| 发货拦截规则 | **auto-reply** block rules | — |
| 发货匹配规则 | — | **auto-bot** delivery_rules |
| 卡券模型 | auto-reply 分销字段 | **auto-bot** 发货/预留/终态 |
| 订单状态 | auto-reply 订单页 | **auto-bot** state machine |
| 分销/采集/返佣 | **auto-reply only** | — |
| 安全/备份恢复/smoke | auto-reply 部分 | **auto-bot** 更强 |
| AI 客服深度 | — | **我们自己** |

---

## 9. 一句话结论

> 主清单已经覆盖了 **auto-reply 的产品广度**。  
> 相对主清单，**auto-bot 真正要补的差异**集中在：  
> **Keepalive/连接状态机、风控暂停、订单状态机、发货匹配规则（≠拦截）、发货日志/终态/卡密预留、商品专属回复、会话暂停、评价模板、维护态恢复、内部密钥与 smoke 测试。**  
> 先把 **§6 的 P0 九项** 补进主清单，M1 才同时具备「广度规划 + 运行时深度」。

---

## 10. 相关文档

| 文档 | 说明 |
|------|------|
| [parity-remediation-checklist.md](./parity-remediation-checklist.md) | 主清单（auto-reply 广度，你正在写） |
| [parity-xianyu-auto-bot.md](./parity-xianyu-auto-bot.md) | auto-bot 全量对照分析 |
| 本文 | **仅差异补集**，供合并进主清单 |
