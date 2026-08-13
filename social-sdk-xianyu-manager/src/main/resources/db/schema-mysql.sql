-- 闲鱼多账号管理平台 -- SQLite 数据库初始化脚本

-- 管理员用户表
CREATE TABLE IF NOT EXISTS admin_user (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(64) UNIQUE NOT NULL,
    password_hash VARCHAR(256) NOT NULL,
    display_name VARCHAR(128),
    email VARCHAR(128),
    phone VARCHAR(32),
    role_level INTEGER DEFAULT 1,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted INTEGER DEFAULT 0
);

-- 闲鱼账号表
CREATE TABLE IF NOT EXISTS xianyu_account (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    account_name VARCHAR(64) NOT NULL,
    user_id VARCHAR(64),
    display_name VARCHAR(128),
    cookie_header TEXT,
    cookies_json TEXT,
    im_cookie_header TEXT,
    im_device_id VARCHAR(128),
    im_access_token TEXT,
    im_token_expires_at TIMESTAMP,
    status VARCHAR(16) DEFAULT 'ACTIVE',
    remark VARCHAR(256),
    last_error VARCHAR(512),
    last_login_at TIMESTAMP,
    cookie_expires_at TIMESTAMP,
    -- ===== Chrome 容器隔离字段 =====
    /** 账号独占 Chrome user-data-dir 路径 */
    chrome_profile_path VARCHAR(512),
    /** 账号独占 Chrome CDP 端口 */
    cdp_port INTEGER,
    /** 账号绑定的代理 URL（http://host:port 或 socks5://host:port） */
    proxy_url VARCHAR(256),
    /** Chrome 容器当前状态（RUNNING/CRASHED/STOPPED 等） */
    chrome_status VARCHAR(32),
    /** Chrome 容器崩溃次数 */
    chrome_crash_count INTEGER DEFAULT 0,
    /** Chrome 容器指纹 seed（用于派生反检测噪声） */
    chrome_seed BIGINT,
    /** Chrome 容器启动时间 */
    chrome_launched_at TIMESTAMP,
    -- 个人信息（从闲鱼 API 获取）
    avatar VARCHAR(512),
    introduction TEXT,
    ip_location VARCHAR(64),
    followers INTEGER DEFAULT 0,
    following INTEGER DEFAULT 0,
    sold_count INTEGER DEFAULT 0,
    purchase_count INTEGER DEFAULT 0,
    collection_count INTEGER DEFAULT 0,
    on_sale_count INTEGER DEFAULT 0,
    shop_level VARCHAR(32),
    credit_score INTEGER DEFAULT 0,
    review_num INTEGER DEFAULT 0,
    profile_synced_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted INTEGER DEFAULT 0
);

-- 商品表
CREATE TABLE IF NOT EXISTS xianyu_product (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    account_id BIGINT NOT NULL,
    item_id VARCHAR(64),
    title VARCHAR(256) NOT NULL,
    price REAL,
    original_price REAL,
    stock INTEGER DEFAULT 0,
    status VARCHAR(16) DEFAULT 'DRAFT',
    category_id VARCHAR(64),
    images TEXT,
    description TEXT,
    videos TEXT, -- 视频 URL 的 JSON 数组
    goods_type VARCHAR(16) DEFAULT 'PHYSICAL', -- PHYSICAL / VIRTUAL
    deliver_type VARCHAR(16), -- CARD / ACCOUNT / LINK / FILE (虚拟商品用)
    deliver_content_template TEXT, -- 发货内容模板(虚拟商品用)
    shipping_mode VARCHAR(16) DEFAULT 'NONE', -- NONE=无需邮寄/FREE=包邮/DISTANCE=按距离计费
    detail_url VARCHAR(512),
    image_url VARCHAR(512), -- 主图 URL（商品列表返回的首图）
    view_count INTEGER DEFAULT 0,
    favorite_count INTEGER DEFAULT 0,
    raw_data TEXT,
    auction_type VARCHAR(32),
    item_status_raw VARCHAR(32),
    post_info VARCHAR(64),
    image_infos TEXT,
    pic_width INTEGER,
    pic_height INTEGER,
    has_video BOOLEAN,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted INTEGER DEFAULT 0
);

-- 消息表
CREATE TABLE IF NOT EXISTS xianyu_message (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    account_id BIGINT NOT NULL,
    session_id VARCHAR(64) NOT NULL,
    msg_id VARCHAR(64),
    sender_id VARCHAR(64),
    sender_name VARCHAR(128),
    sender_avatar VARCHAR(512),
    content TEXT,
    msg_type VARCHAR(16) DEFAULT 'TEXT',
    direction VARCHAR(8) DEFAULT 'INCOMING',
    auto_reply TINYINT(1) DEFAULT 0,
    message_time TIMESTAMP,
    cid VARCHAR(64),
    biz_order_id VARCHAR(64),
    biz_item_id VARCHAR(64),
    biz_buyer_id VARCHAR(64),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted INTEGER DEFAULT 0
);

-- 订单表
CREATE TABLE IF NOT EXISTS xianyu_order (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    account_id BIGINT NOT NULL,
    type VARCHAR(16) DEFAULT 'BOUGHT', -- SOLD, BOUGHT
    order_id VARCHAR(64),
    item_id VARCHAR(64), -- 闲鱼商品 ID
    item_title VARCHAR(256),
    counterparty_name VARCHAR(128), -- 对手方昵称：SOLD=买家，BOUGHT=卖家
    buyer_id VARCHAR(64),
    seller_id VARCHAR(64),
    order_detail_url VARCHAR(512),
    raw_data TEXT,
    amount REAL,
    status VARCHAR(32) DEFAULT 'PENDING',
    trade_status_enum VARCHAR(32), -- 闲鱼原始状态枚举 (tradeStatusEnum)
    order_status VARCHAR(32) DEFAULT 'CREATED', -- BOT-O1 订单状态机：CREATED/PAID/SHIPPED/DELIVERED/COMPLETED/REFUNDING/REFUNDED/CLOSED
    pre_refund_status VARCHAR(32), -- BOT-O1 退款前状态快照，退款取消/驳回后回滚用
    is_seller INTEGER DEFAULT 0, -- 是否为卖家订单
    tracking_no VARCHAR(64),
    order_time TIMESTAMP, -- 订单创建时间(来自闲鱼 API)
    goods_type VARCHAR(16) DEFAULT 'PHYSICAL',
    product_id BIGINT, -- 关联本地商品 id（订单同步时按 item_id 反查回填）
    require_virtual_ship INTEGER DEFAULT 0,
    virtual_shipped_at TIMESTAMP,
    auto_receipt_at TIMESTAMP,
    deliver_content TEXT, -- 实际发货内容快照
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted INTEGER DEFAULT 0
);

-- 关键词/自动回复规则表
CREATE TABLE IF NOT EXISTS xianyu_keyword_rule (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    account_id BIGINT,
    rule_name VARCHAR(128),
    reply_type VARCHAR(16) DEFAULT 'KEYWORD', -- KEYWORD, AI, AUTO
    keyword VARCHAR(256),
    match_type VARCHAR(16) DEFAULT 'CONTAINS',
    reply_text TEXT,
    enabled TINYINT(1) DEFAULT 1,
    priority INTEGER DEFAULT 100,
    action VARCHAR(16), -- 触发动作: POLISH / SUPER_POLISH / null(仅回复)
    action_target_item_id VARCHAR(64), -- 动作目标 itemId(null 时取最近在架商品)
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted INTEGER DEFAULT 0
);

-- 自动回复日志表
CREATE TABLE IF NOT EXISTS xianyu_auto_reply_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    account_id BIGINT,
    rule_id BIGINT,
    rule_name VARCHAR(128),
    reply_type VARCHAR(16),
    keyword VARCHAR(256),
    buyer_message TEXT,
    reply_text TEXT,
    matched TINYINT(1) DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 自动回复全局配置表（按账号）
CREATE TABLE IF NOT EXISTS xianyu_auto_reply_config (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    account_id BIGINT NOT NULL UNIQUE,
    -- AI 配置
    ai_enabled TINYINT(1) DEFAULT 0,
    ai_provider VARCHAR(32),
    ai_api_key VARCHAR(512),
    api_url VARCHAR(512),
    ai_model VARCHAR(64),
    ai_model_id BIGINT, -- 关联的 AI 模型 ID（ai_model.id），对应实体 aiModelId
    ai_system_prompt TEXT,
    ai_temperature REAL DEFAULT 0.7,
    -- 兜底自动回复
    auto_reply_enabled TINYINT(1) DEFAULT 0,
    welcome_message TEXT,
    fallback_reply TEXT,
    idle_timeout_minutes INTEGER DEFAULT 30,
    idle_reply TEXT,
    offline_reply_enabled TINYINT(1) DEFAULT 0,
    offline_reply TEXT,
    -- 全局配置
    notify_on_new_message TINYINT(1) DEFAULT 1,
    include_chat_history TINYINT(1) DEFAULT 1,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted INTEGER DEFAULT 0
);

-- 审计日志表
CREATE TABLE IF NOT EXISTS audit_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    operator_id BIGINT,
    operator_name VARCHAR(128),
    action VARCHAR(256),
    resource_type VARCHAR(64),
    resource_id VARCHAR(64),
    detail TEXT,
    ip_address VARCHAR(64),
    action_time TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted INTEGER DEFAULT 0
);

-- 钱包表
CREATE TABLE IF NOT EXISTS xianyu_wallet (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    account_id BIGINT NOT NULL UNIQUE,
    balance REAL DEFAULT 0,
    frozen_amount REAL DEFAULT 0,
    available_balance REAL DEFAULT 0,
    total_assets REAL DEFAULT 0,
    withdrawable_amount REAL DEFAULT 0,
    alipay_account VARCHAR(128),
    alipay_real_name VARCHAR(64),
    bank_card VARCHAR(64),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted INTEGER DEFAULT 0
);

-- 钱包交易记录表
CREATE TABLE IF NOT EXISTS xianyu_wallet_transaction (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    account_id BIGINT NOT NULL,
    transaction_id VARCHAR(64),
    type VARCHAR(16) DEFAULT 'EXPENSE',
    biz_type VARCHAR(32),
    amount REAL,
    balance_after REAL,
    description TEXT,
    status VARCHAR(16),
    trade_no VARCHAR(64),
    transaction_time TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted INTEGER DEFAULT 0
);

-- 收藏关注表
CREATE TABLE IF NOT EXISTS xianyu_collect (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    account_id BIGINT NOT NULL,
    target_type VARCHAR(16) DEFAULT 'ITEM',
    target_id VARCHAR(64),
    target_name VARCHAR(256),
    collected_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted INTEGER DEFAULT 0
);

-- 创建索引
CREATE INDEX idx_xianyu_product_account ON xianyu_product(account_id);
CREATE INDEX idx_xianyu_product_status ON xianyu_product(status);
CREATE INDEX idx_xianyu_product_item_id ON xianyu_product(item_id);
CREATE INDEX idx_xianyu_message_session ON xianyu_message(account_id, session_id);
CREATE INDEX idx_xianyu_order_account ON xianyu_order(account_id);
CREATE INDEX idx_xianyu_keyword_rule_account ON xianyu_keyword_rule(account_id);
CREATE INDEX idx_audit_log_action ON audit_log(action);
CREATE INDEX idx_xianyu_wallet_account ON xianyu_wallet(account_id);
CREATE INDEX idx_xianyu_wallet_transaction_account ON xianyu_wallet_transaction(account_id);
CREATE INDEX idx_xianyu_collect_account ON xianyu_collect(account_id);

-- ======================== AI 模块 ========================

-- AI 厂商表（OpenAI 兼容协议：api_base_url + api_key 即可接入）
CREATE TABLE IF NOT EXISTS ai_provider (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(64) NOT NULL UNIQUE,           -- 展示名（如 Agnes AI / OpenAI / DeepSeek）
    api_base_url VARCHAR(256) NOT NULL,          -- API 端点（如 https://apihub.agnes-ai.com/v1）
    api_key VARCHAR(512) NOT NULL,               -- API Key（明文存储，生产可加对称加密）
    provider_type VARCHAR(32) DEFAULT 'OPENAI_COMPATIBLE',
    enabled TINYINT(1) DEFAULT 1,
    remark VARCHAR(256),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted INTEGER DEFAULT 0
);

-- AI 模型表
CREATE TABLE IF NOT EXISTS ai_model (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    provider_id BIGINT NOT NULL,
    model_name VARCHAR(128) NOT NULL,            -- 模型标识（如 agnes-2.0-flash）
    display_name VARCHAR(128),                   -- 展示名
    model_type VARCHAR(16) NOT NULL,             -- TEXT / IMAGE / VIDEO
    capabilities TEXT,                           -- JSON 能力标签（streaming / tools / thinking / image_input）
    default_temperature REAL DEFAULT 0.7,
    default_max_tokens INTEGER DEFAULT 1024,
    enabled TINYINT(1) DEFAULT 1,
    remark VARCHAR(256),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted INTEGER DEFAULT 0,
    FOREIGN KEY (provider_id) REFERENCES ai_provider(id),
    UNIQUE (provider_id, model_name)
);

CREATE INDEX idx_ai_model_provider ON ai_model(provider_id);
CREATE INDEX idx_ai_model_type ON ai_model(model_type);

-- ======================== 通知模块 ========================
-- 通知通道（邮件 SMTP / Webhook 机器人）。config_json 密文存储敏感配置。
CREATE TABLE IF NOT EXISTS notify_channel (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    type VARCHAR(16) NOT NULL,            -- EMAIL, WEBHOOK, SMS
    name VARCHAR(128) NOT NULL,
    enabled TINYINT(1) DEFAULT 1,
    config_json TEXT,                     -- AES 加密后的 JSON 配置
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted INTEGER DEFAULT 0
);

-- 通知模板（按场景）。场景常量见 NotifyScenario。
CREATE TABLE IF NOT EXISTS notify_template (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    scenario VARCHAR(64) NOT NULL UNIQUE,
    title_tpl TEXT,
    body_tpl TEXT,
    enabled TINYINT(1) DEFAULT 1,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted INTEGER DEFAULT 0
);

-- 订阅规则：场景 -> 通道 + 接收范围
CREATE TABLE IF NOT EXISTS notify_subscription (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    scenario VARCHAR(64) NOT NULL,
    channel_id BIGINT NOT NULL,
    recipient_scope VARCHAR(16) DEFAULT 'ALL',  -- ALL / CUSTOM
    recipients TEXT,                            -- CUSTOM 时的接收人（逗号分隔/JSON）
    account_scope VARCHAR(16) DEFAULT 'ALL',    -- ALL / CUSTOM
    account_ids TEXT,                           -- CUSTOM 时的账号 ID 列表（JSON 数组）
    enabled TINYINT(1) DEFAULT 1,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted INTEGER DEFAULT 0
);

-- 投递日志
CREATE TABLE IF NOT EXISTS notify_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    scenario VARCHAR(64),
    channel_id BIGINT,
    channel_type VARCHAR(16),
    recipient VARCHAR(256),
    status VARCHAR(16),             -- SENT / FAILED
    payload TEXT,
    error TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    sent_at TIMESTAMP
);

-- 站内通知收件箱
CREATE TABLE IF NOT EXISTS notify_message (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    account_id BIGINT,             -- 关联的闲鱼账号（可为空）
    scenario VARCHAR(64),
    title VARCHAR(256),
    content TEXT,
    is_read TINYINT(1) DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_notify_sub_scenario ON notify_subscription(scenario);
CREATE INDEX idx_notify_log_scenario ON notify_log(scenario);
CREATE INDEX idx_notify_msg_read ON notify_message(is_read);
CREATE INDEX idx_notify_msg_created ON notify_message(created_at);

-- 发送重试队列（失败/限频后入队，按退避重发）
CREATE TABLE IF NOT EXISTS notify_retry (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    scenario VARCHAR(64),
    channel_id BIGINT,
    channel_type VARCHAR(16),
    recipient VARCHAR(256),
    title TEXT,
    body TEXT,
    vars_json TEXT,               -- 触发事件的模板变量 JSON（用于重试时结构化重发）
    retry_count INTEGER DEFAULT 0,
    max_retry INTEGER DEFAULT 5,
    next_retry_at TIMESTAMP,
    status VARCHAR(16),              -- PENDING / SENDING / DONE / GIVEN_UP
    last_error TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_notify_retry_due ON notify_retry(status, next_retry_at);

-- 每日摘要配置（单例 id=1）
CREATE TABLE IF NOT EXISTS notify_digest_config (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    enabled TINYINT(1) DEFAULT 0,
    channel_id BIGINT,
    recipients TEXT,
    hour INTEGER DEFAULT 9,
    minute INTEGER DEFAULT 0,
    scenarios TEXT,                  -- JSON 数组；空=全部场景
    include_in_app TINYINT(1) DEFAULT 1,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- ======================== 虚拟商品 / 自动发货 ========================

-- 给 xianyu_order 加虚拟发货/自动收货字段（已在 CREATE TABLE 中定义，此处注释避免重复）
-- ALTER TABLE xianyu_order ADD COLUMN goods_type VARCHAR(16) DEFAULT 'PHYSICAL';
-- ALTER TABLE xianyu_order ADD COLUMN require_virtual_ship TINYINT(1) DEFAULT 0;
-- ALTER TABLE xianyu_order ADD COLUMN virtual_shipped_at DATETIME;
-- ALTER TABLE xianyu_order ADD COLUMN auto_receipt_at DATETIME;
-- ALTER TABLE xianyu_order ADD COLUMN deliver_content TEXT;     -- 实际发货内容快照

-- 卡密池（Card / Account 类虚拟商品共用）
CREATE TABLE IF NOT EXISTS virtual_card_pool (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    product_id BIGINT NOT NULL,
    card_code VARCHAR(256) NOT NULL,
    card_password VARCHAR(256),
    status VARCHAR(16) DEFAULT 'AVAILABLE', -- AVAILABLE / USED / EXPIRED
    used_order_id BIGINT,
    used_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted INTEGER DEFAULT 0,
    UNIQUE(card_code),
    FOREIGN KEY (product_id) REFERENCES xianyu_product(id)
);

-- 自动发货全局配置（每账号一条）
CREATE TABLE IF NOT EXISTS virtual_ship_config (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    account_id BIGINT NOT NULL,
    enabled TINYINT(1) DEFAULT 0,
    delay_seconds INT DEFAULT 0,
    auto_confirm_days INT DEFAULT 7,
    confirm_receipt_message TEXT,
    notify_after_ship TINYINT(1) DEFAULT 1,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted INT DEFAULT 0,
    UNIQUE KEY uk_account (account_id),
    FOREIGN KEY (account_id) REFERENCES xianyu_account(id)
);

CREATE INDEX idx_virtual_card_pool_product ON virtual_card_pool(product_id);
CREATE INDEX idx_virtual_card_pool_status ON virtual_card_pool(status);
CREATE TABLE IF NOT EXISTS virtual_ship_task (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    account_id BIGINT,
    order_id BIGINT NOT NULL UNIQUE,
    product_id BIGINT NOT NULL,
    status VARCHAR(16) DEFAULT 'PENDING',
    retry_count INTEGER DEFAULT 0,
    max_retry INTEGER DEFAULT 5,
    error_message TEXT,
    execute_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    processed_at TIMESTAMP,
    deleted INTEGER DEFAULT 0,
    message_id VARCHAR(128),
    sent_at TIMESTAMP NULL
);
CREATE INDEX idx_virtual_ship_task_status ON virtual_ship_task(status);
CREATE INDEX idx_virtual_ship_task_message_id ON virtual_ship_task(message_id);
CREATE INDEX idx_virtual_ship_task_sent_at ON virtual_ship_task(sent_at);
CREATE INDEX idx_xianyu_order_require_virtual_ship ON xianyu_order(require_virtual_ship);

-- ======================== AI 客服 ========================

-- 客服会话表（按账号 + 买家分组，一个买家在一个账号下一个会话）
CREATE TABLE IF NOT EXISTS ai_cs_session (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    account_id BIGINT NOT NULL,
    buyer_id VARCHAR(64) NOT NULL,
    buyer_nickname VARCHAR(64),
    product_id BIGINT,                          -- 关联商品（可为空表示闲聊）
    order_id BIGINT,                            -- 关联订单（可为空）
    status VARCHAR(16) DEFAULT 'ACTIVE',         -- ACTIVE / CLOSED / BLOCKED
    last_message_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted INTEGER DEFAULT 0,
    UNIQUE(account_id, buyer_id)
);

-- 客服消息表（完整记录买家消息 + AI/运营回复）
CREATE TABLE IF NOT EXISTS ai_cs_message (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    session_id BIGINT NOT NULL,
    direction VARCHAR(16),                       -- INCOMING(买家) / OUTGOING(AI/运营)
    content TEXT,
    intent VARCHAR(32),                          -- 意图分类（议价/确认商品/物流/售后/闲聊）
    intent_confidence REAL,                      -- 意图识别置信度
    ai_generated TINYINT(1) DEFAULT 0,          -- 是否 AI 生成
    sent_by VARCHAR(16),                         -- AUTO(全自动) / AI_ASSIST(AI建议运营一键发) / HUMAN(纯手动)
    raw_ai_response TEXT,                        -- AI 原始回复（运营可能修改过）
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (session_id) REFERENCES ai_cs_session(id)
);

-- AI 知识库（商品 FAQ、通用话术，按账号隔离）
CREATE TABLE IF NOT EXISTS ai_cs_knowledge (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    account_id BIGINT,                          -- NULL = 全局共享
    product_id BIGINT,                          -- NULL = 通用知识
    question VARCHAR(256) NOT NULL,             -- 问题关键词 / 触发词
    answer TEXT NOT NULL,                        -- 回复内容
    category VARCHAR(32),                        -- PRICE / SHIPPING / AFTERSALES / GENERAL / PRODUCT
    priority INTEGER DEFAULT 100,                -- 优先级（越小越优先）
    is_active TINYINT(1) DEFAULT 1,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted INTEGER DEFAULT 0
);

-- AI 客服策略配置（按账号）
CREATE TABLE IF NOT EXISTS ai_cs_policy (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    account_id BIGINT NOT NULL UNIQUE,
    mode VARCHAR(16) DEFAULT 'ASSIST',           -- AUTO(全自动) / ASSIST(AI建议) / HYBRID(闲聊自动，议价辅助)
    auto_reply_enabled TINYINT(1) DEFAULT 0,    -- 是否启用自动回复（AUTO 模式）
    -- 议价策略
    price_floor_pct REAL DEFAULT 0.80,           -- 底价比例（如 0.8 = 8 折是底价）
    price_step_pct REAL DEFAULT 0.05,            -- 每次降价幅度（如 0.05 = 5%）
    max_discount_steps INTEGER DEFAULT 3,        -- 最多降价几次
    -- 风控
    max_auto_replies_per_hour INTEGER DEFAULT 10, -- 单会话每小时最大自动回复数
    transfer_to_human_intents TEXT,               -- JSON: ["售后","投诉","退款"] → 这些意图转人工
    -- 话术风格
    tone VARCHAR(32) DEFAULT 'FRIENDLY',         -- FRIENDLY / PROFESSIONAL / CASUAL / HUMOROUS
    -- 时段
    enabled_from TIME,                           -- 自动回复生效时间（NULL 表示全天）
    enabled_to TIME,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted INTEGER DEFAULT 0,
    FOREIGN KEY (account_id) REFERENCES xianyu_account(id)
);

-- AI 客服统计表（按天汇总，用于运营查看效果）
CREATE TABLE IF NOT EXISTS ai_cs_daily_stats (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    account_id BIGINT NOT NULL,
    stat_date DATE NOT NULL,
    total_sessions INTEGER DEFAULT 0,
    total_messages INTEGER DEFAULT 0,
    ai_replies INTEGER DEFAULT 0,
    human_replies INTEGER DEFAULT 0,
    auto_replies INTEGER DEFAULT 0,
    intent_price_negotiation INTEGER DEFAULT 0,  -- 议价类会话数
    intent_product_inquiry INTEGER DEFAULT 0,    -- 商品咨询数
    intent_logistics INTEGER DEFAULT 0,           -- 物流查询数
    intent_aftersales INTEGER DEFAULT 0,         -- 售后数
    avg_response_seconds INTEGER DEFAULT 0,      -- 平均响应时长（秒）
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(account_id, stat_date),
    FOREIGN KEY (account_id) REFERENCES xianyu_account(id)
);

CREATE INDEX idx_ai_cs_session_account ON ai_cs_session(account_id);
CREATE INDEX idx_ai_cs_session_buyer ON ai_cs_session(account_id, buyer_id);
CREATE INDEX idx_ai_cs_message_session ON ai_cs_message(session_id);
CREATE INDEX idx_ai_cs_knowledge_account ON ai_cs_knowledge(account_id);
CREATE INDEX idx_ai_cs_daily_stats_account ON ai_cs_daily_stats(account_id);

-- ======================== AI 运营 ========================

-- AI 运营任务表（批量上品、多账号同步等）
CREATE TABLE IF NOT EXISTS ai_ops_task (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    account_id BIGINT NOT NULL,
    task_type VARCHAR(32) NOT NULL,              -- BATCH_CREATE / MULTI_ACCOUNT_SYNC / AUTO_REFRESH
    status VARCHAR(16) DEFAULT 'PENDING',         -- PENDING / RUNNING / COMPLETED / FAILED
    payload TEXT,                                 -- JSON 任务参数
    result_summary TEXT,                          -- AI 生成摘要
    error_message TEXT,
    execute_at DATETIME,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted INTEGER DEFAULT 0,
    FOREIGN KEY (account_id) REFERENCES xianyu_account(id)
);

-- AI 建议执行记录
CREATE TABLE IF NOT EXISTS ai_ops_suggestion (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    account_id BIGINT NOT NULL,
    suggestion_type VARCHAR(32),                  -- PRICE_ADJUST / REFRESH_TIME / LISTING_OPTIMIZE
    product_id BIGINT,
    suggestion_content TEXT,                      -- JSON AI 建议详情
    confidence REAL,
    adopted TINYINT(1),                              -- 运营是否采纳
    adopted_at TIMESTAMP,
    expected_impact TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted INTEGER DEFAULT 0,
    FOREIGN KEY (account_id) REFERENCES xianyu_account(id),
    FOREIGN KEY (product_id) REFERENCES xianyu_product(id)
);

-- 运营知识库
CREATE TABLE IF NOT EXISTS ai_ops_knowledge (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    category VARCHAR(64),                         -- 商品品类
    knowledge_type VARCHAR(32),                   -- PRICING / DESCRIPTION_STYLE / POSTING_TIME / KEYWORD
    content TEXT,
    source VARCHAR(32),                           -- AI_GENERATED / MANUAL / PLATFORM_RULE
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted INTEGER DEFAULT 0
);

CREATE INDEX idx_ai_ops_task_account ON ai_ops_task(account_id);
CREATE INDEX idx_ai_ops_suggestion_account ON ai_ops_suggestion(account_id);

-- ======================== 网盘存储（虚拟发货扩展） ========================

-- 网盘账号表
CREATE TABLE IF NOT EXISTS cloud_storage_account (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    account_id BIGINT NOT NULL,
    provider VARCHAR(32) NOT NULL,                -- BAIDU_NETDISK / QUARK_NETDISK / ALIYUN_DRIVE
    access_token VARCHAR(512),
    refresh_token VARCHAR(512),
    token_expires_at TIMESTAMP,
    uid VARCHAR(64),
    total_space BIGINT DEFAULT 0,
    used_space BIGINT DEFAULT 0,
    is_active TINYINT(1) DEFAULT 1,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted INTEGER DEFAULT 0,
    FOREIGN KEY (account_id) REFERENCES xianyu_account(id)
);

-- 网盘文件表
CREATE TABLE IF NOT EXISTS cloud_storage_file (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    storage_account_id BIGINT NOT NULL,
    file_name VARCHAR(256),
    file_path VARCHAR(512),
    file_size BIGINT,
    file_hash VARCHAR(64),
    mime_type VARCHAR(64),
    share_link VARCHAR(1024),                    -- 分享链接
    extract_code VARCHAR(16),                     -- 提取码
    share_expires_at TIMESTAMP,                    -- 分享过期时间
    upload_status VARCHAR(32),                   -- PENDING / UPLOADING / COMPLETED / FAILED
    remote_file_id VARCHAR(128),                 -- 网盘侧 file_id
    extra_meta TEXT,                             -- JSON 扩展
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted INTEGER DEFAULT 0,
    FOREIGN KEY (storage_account_id) REFERENCES cloud_storage_account(id)
);

CREATE INDEX idx_cloud_storage_account_account ON cloud_storage_account(account_id);
CREATE INDEX idx_cloud_storage_file_account ON cloud_storage_file(storage_account_id);

-- ======================== 对外 OpenAPI ========================

-- 对外应用（调用方凭证）。app_secret_enc 为 AES 加密后的明文 secret，绝不落明文。
CREATE TABLE IF NOT EXISTS open_app (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    app_name VARCHAR(128) NOT NULL,                 -- 应用展示名
    app_key VARCHAR(64) NOT NULL UNIQUE,            -- 公开标识（调用方传 appKey）
    app_secret_enc VARCHAR(512),                    -- AES 加密后的 appSecret（明文仅在创建时返回一次）
    status VARCHAR(16) DEFAULT 'ENABLED',           -- ENABLED / DISABLED
    bound_account_ids TEXT,                         -- 绑定账号白名单（JSON 数组，空=不限制）
    rate_limit_per_minute INTEGER DEFAULT 60,       -- 单应用每分钟请求上限（0=不限制）
    expire_at TIMESTAMP,                             -- 凭证过期时间（NULL=不过期）
    last_used_at TIMESTAMP,                          -- 最近一次成功调用时间
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted INTEGER DEFAULT 0
);

CREATE INDEX idx_open_app_key ON open_app(app_key);

-- ======================== 价格历史 & 市场情报 ========================

-- 市场搜索快照（定时抓取指定关键词的商品列表，用于价格趋势分析）
CREATE TABLE IF NOT EXISTS market_snapshot (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    task_id BIGINT NOT NULL,                        -- 关联 monitor_task.id
    keyword VARCHAR(256) NOT NULL,                   -- 搜索关键词
    account_id BIGINT,                              -- 抓取所用账号（可空=未绑定）
    total_results INTEGER DEFAULT 0,                 -- 本次抓取到的商品总数
    raw_data TEXT,                                   -- JSON 商品列表原始数据
    snapshot_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (account_id) REFERENCES xianyu_account(id)
);

CREATE INDEX idx_market_snapshot_task ON market_snapshot(task_id);
CREATE INDEX idx_market_snapshot_keyword ON market_snapshot(keyword);
CREATE INDEX idx_market_snapshot_time ON market_snapshot(snapshot_time);

-- 价格历史记录（每个抓取到的商品一条价格记录）
CREATE TABLE IF NOT EXISTS price_history (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    keyword VARCHAR(256) NOT NULL,                   -- 归属关键词
    item_id VARCHAR(64),                             -- 闲鱼商品 ID（可空=无法关联）
    item_title VARCHAR(256),
    price REAL NOT NULL,
    currency VARCHAR(8) DEFAULT 'CNY',
    seller_id VARCHAR(64),
    seller_nickname VARCHAR(128),
    seller_credit_score INTEGER,
    item_condition VARCHAR(32),                      -- 全新 / 几乎全新 / 轻微使用 / 明显使用
    location VARCHAR(128),
    listing_time TIMESTAMP,                           -- 商品发布时间
    snapshot_id BIGINT,                             -- 关联 market_snapshot.id
    snapshot_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (snapshot_id) REFERENCES market_snapshot(id)
);

CREATE INDEX idx_price_history_keyword ON price_history(keyword);
CREATE INDEX idx_price_history_item ON price_history(item_id);
CREATE INDEX idx_price_history_time ON price_history(snapshot_time);
CREATE INDEX idx_price_history_price ON price_history(price);

-- 市场每日聚合统计（按 keyword + date 预聚合，加速仪表盘查询）
CREATE TABLE IF NOT EXISTS market_daily_stat (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    keyword VARCHAR(256) NOT NULL,
    stat_date DATE NOT NULL,
    min_price REAL,
    max_price REAL,
    avg_price REAL,
    median_price REAL,
    p25_price REAL,                                  -- 25 分位价
    p75_price REAL,                                  -- 75 分位价
    volume INTEGER DEFAULT 0,                        -- 新增上架数
    total_listings INTEGER DEFAULT 0,                -- 总在售数
    sampled_count INTEGER DEFAULT 0,                 -- 本次采样数
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(keyword, stat_date)
);

CREATE INDEX idx_market_daily_stat_keyword ON market_daily_stat(keyword);
CREATE INDEX idx_market_daily_stat_date ON market_daily_stat(stat_date);

-- ======================== 监控爬虫调度引擎 ========================

-- 监控任务表
CREATE TABLE IF NOT EXISTS monitor_task (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    account_id BIGINT NOT NULL,                     -- 绑定的闲鱼账号
    name VARCHAR(128) NOT NULL,                      -- 任务名称
    task_type VARCHAR(32) DEFAULT 'KEYWORD',         -- KEYWORD / AI / CATEGORY
    status VARCHAR(16) DEFAULT 'ACTIVE',             -- ACTIVE / PAUSED / DELETED

    -- 搜索条件
    keyword VARCHAR(256),
    category_id VARCHAR(64),
    min_price REAL,
    max_price REAL,
    item_condition VARCHAR(32),                      -- 全新 / 几乎全新 / 轻微使用 / 明显使用 / ANY
    location_province VARCHAR(64),
    location_city VARCHAR(64),
    location_district VARCHAR(64),
    free_shipping INTEGER DEFAULT 0,                 -- 0=不限 1=包邮
    max_age_hours INTEGER,                           -- 只发 N 小时内新发布的

    -- AI 决策配置
    ai_enabled INTEGER DEFAULT 0,                    -- 0=关键词判断 1=AI 分析选品
    ai_prompt TEXT,                                  -- AI 自定义 prompt（可空=用默认）
    ai_model_id BIGINT,                              -- 关联 ai_model.id

    -- 调度配置
    cron_expression VARCHAR(64),                     -- Cron 表达式（为空则用全局间隔）
    interval_minutes INTEGER DEFAULT 30,             -- 默认间隔分钟数
    next_run_at TIMESTAMP,
    last_run_at TIMESTAMP,
    last_result_summary TEXT,                        -- JSON 上次结果摘要
    run_count INTEGER DEFAULT 0,
    consecutive_failures INTEGER DEFAULT 0,          -- 连续失败次数（熔断用）
    circuit_open INTEGER DEFAULT 0,                  -- 0=正常 1=熔断中
    circuit_open_until TIMESTAMP,                     -- 熔断恢复时间

    -- 通知配置
    notify_on_match INTEGER DEFAULT 1,               -- 有匹配时通知
    notify_channel_id BIGINT,                       -- 默认通知通道

    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted INTEGER DEFAULT 0,
    FOREIGN KEY (account_id) REFERENCES xianyu_account(id),
    FOREIGN KEY (notify_channel_id) REFERENCES notify_channel(id)
);

CREATE INDEX idx_monitor_task_account ON monitor_task(account_id);
CREATE INDEX idx_monitor_task_status ON monitor_task(status);
CREATE INDEX idx_monitor_task_next_run ON monitor_task(next_run_at);

-- 监控结果（AI 推荐的商品）
CREATE TABLE IF NOT EXISTS monitor_result (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    task_id BIGINT NOT NULL,
    item_id VARCHAR(64) NOT NULL,
    item_title VARCHAR(256),
    price REAL,
    image_url VARCHAR(512),
    seller_nickname VARCHAR(128),
    seller_credit_score INTEGER,
    item_url VARCHAR(512),
    ai_score REAL,                                   -- AI 推荐置信度 0-100
    ai_reason TEXT,                                  -- AI 推荐理由
    matched_keywords TEXT,                           -- JSON 匹配到的关键词列表
    notified INTEGER DEFAULT 0,                      -- 已推送通知
    snapshot_id BIGINT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (task_id) REFERENCES monitor_task(id),
    FOREIGN KEY (snapshot_id) REFERENCES market_snapshot(id)
);

CREATE INDEX idx_monitor_result_task ON monitor_result(task_id);
CREATE INDEX idx_monitor_result_item ON monitor_result(item_id);
CREATE INDEX idx_monitor_result_created ON monitor_result(created_at);

-- 卖家情报（抓取的非自有卖家信息）
CREATE TABLE IF NOT EXISTS seller_profile (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id VARCHAR(64) UNIQUE NOT NULL,
    nickname VARCHAR(128),
    avatar VARCHAR(512),
    shop_level VARCHAR(32),
    credit_score INTEGER,
    followers INTEGER,
    following INTEGER,
    sold_count INTEGER,
    on_sale_count INTEGER,
    introduction TEXT,
    ip_location VARCHAR(64),
    last_active_at TIMESTAMP,
    profile_synced_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted INTEGER DEFAULT 0
);

CREATE INDEX idx_seller_profile_nickname ON seller_profile(nickname);

-- ======================== 买家画像 & 会话智能 ========================

-- 买家画像（跨会话聚合）
CREATE TABLE IF NOT EXISTS buyer_profile (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    buyer_id VARCHAR(64) NOT NULL,                   -- 买家 union_id / userId
    first_account_id BIGINT,                        -- 首次交互账号
    nickname VARCHAR(128),
    avatar VARCHAR(512),
    first_contact_at TIMESTAMP,
    last_contact_at TIMESTAMP,
    total_sessions INTEGER DEFAULT 0,
    total_messages INTEGER DEFAULT 0,
    total_orders INTEGER DEFAULT 0,                  -- 成交数
    total_spent REAL DEFAULT 0,                      -- 累计成交金额
    bargain_count INTEGER DEFAULT 0,                 -- 议价总次数
    avg_response_seconds INTEGER DEFAULT 0,          -- 买家平均响应时长
    credibility_score REAL DEFAULT 50,               -- 可信度评分 0-100
    tags TEXT,                                       -- JSON 标签：["爽快买家","高频议价","疑似黄牛"]
    notes TEXT,                                      -- 运营备注
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted INTEGER DEFAULT 0,
    UNIQUE(buyer_id)
);

CREATE INDEX idx_buyer_profile_last_contact ON buyer_profile(last_contact_at);

-- AI 客服会话扩展字段（议价计数等状态）
CREATE TABLE IF NOT EXISTS ai_cs_session_state (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    session_id BIGINT NOT NULL UNIQUE,
    bargain_round INTEGER DEFAULT 0,                 -- 当前议价轮次
    original_price REAL,                             -- 询问时商品原价
    lowest_offer REAL,                               -- 买家最低出价
    current_offer REAL,                              -- 当前 AI 报价
    deal_closed INTEGER DEFAULT 0,                   -- 0=进行中 1=成交 2=未成交
    closed_at TIMESTAMP,
    closed_reason VARCHAR(64),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted INTEGER DEFAULT 0,
    FOREIGN KEY (session_id) REFERENCES ai_cs_session(id)
);

CREATE INDEX idx_ai_cs_session_state_deal ON ai_cs_session_state(deal_closed);

-- ======================== 故障保护 ========================

-- 熔断器状态表（按账号 + 服务维度）
CREATE TABLE IF NOT EXISTS circuit_breaker (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    account_id BIGINT,                              -- NULL=全局级熔断
    service_name VARCHAR(64) NOT NULL,               -- MESSAGE_SYNC / ORDER_SYNC / AI_CHAT / MONITOR / PROFILE_FETCH
    state VARCHAR(16) DEFAULT 'CLOSED',              -- CLOSED / OPEN / HALF_OPEN
    failure_count INTEGER DEFAULT 0,
    success_count INTEGER DEFAULT 0,
    last_failure_at TIMESTAMP,
    last_failure_message TEXT,
    last_success_at TIMESTAMP,
    opened_at TIMESTAMP,
    cooldown_until TIMESTAMP,
    threshold_count INTEGER DEFAULT 5,               -- 连续失败 N 次后开闸
    cooldown_seconds INTEGER DEFAULT 300,            -- 熔断持续时间（秒）
    half_open_max_success INTEGER DEFAULT 3,         -- 半开状态需连续成功 N 次才关闭
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(account_id, service_name)
);

CREATE INDEX idx_circuit_breaker_account ON circuit_breaker(account_id);
CREATE INDEX idx_circuit_breaker_state ON circuit_breaker(state);

-- 故障保护事件日志
CREATE TABLE IF NOT EXISTS circuit_breaker_event (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    breaker_id BIGINT NOT NULL,
    event_type VARCHAR(32) NOT NULL,                 -- FAILURE / SUCCESS / STATE_CHANGE / RESET
    from_state VARCHAR(16),
    to_state VARCHAR(16),
    message TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (breaker_id) REFERENCES circuit_breaker(id)
);

CREATE INDEX idx_circuit_event_breaker ON circuit_breaker_event(breaker_id);
CREATE INDEX idx_circuit_event_time ON circuit_breaker_event(created_at);


-- ===== local_product：本地商品草稿（发布成功后保留记录，标记 PUBLISHED，不物理删除） =====
CREATE TABLE IF NOT EXISTS local_product (
    id          INTEGER PRIMARY KEY AUTO_INCREMENT,
    account_id  INTEGER,
    title       VARCHAR(255),
    price       DECIMAL(12,2),
    original_price DECIMAL(12,2),
    stock       INTEGER DEFAULT 1,
    category_id VARCHAR(64),
    description TEXT,
    images      TEXT,
    videos      TEXT,
    image_url   VARCHAR(512),
    goods_type  VARCHAR(16) DEFAULT 'PHYSICAL',
    deliver_type VARCHAR(16),
    deliver_content_template TEXT,
    shipping_mode VARCHAR(16) DEFAULT 'NONE',  -- NONE=无需邮寄/FREE=包邮/DISTANCE=按距离计费
    status      VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
    publish_error TEXT,
    created_at  DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted     INTEGER DEFAULT 0,
    INDEX idx_local_product_account (account_id),
    INDEX idx_local_product_status (status)
);

-- ===== market_keyword：市场关键词追踪表（与 SQLite schema-sqlite.sql 对齐） =====
CREATE TABLE IF NOT EXISTS market_keyword (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    keyword VARCHAR(256) UNIQUE NOT NULL,             -- 追踪关键词
    status VARCHAR(16) DEFAULT 'ACTIVE',              -- ACTIVE / PAUSED
    crawl_interval_minutes INTEGER DEFAULT 30,        -- 抓取间隔（分钟）
    last_crawl_at TIMESTAMP,                          -- 上次抓取时间
    last_crawl_result_count INTEGER DEFAULT 0,        -- 上次抓取到的商品数
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted INTEGER DEFAULT 0
);

CREATE INDEX idx_market_keyword_keyword ON market_keyword(keyword);
CREATE INDEX idx_market_keyword_status ON market_keyword(status);

-- ===== openlist_instance：OpenList 网盘实例表（与 SQLite schema-sqlite.sql 对齐） =====
CREATE TABLE IF NOT EXISTS openlist_instance (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    port INTEGER NOT NULL DEFAULT 5244,
    url VARCHAR(256) DEFAULT 'http://127.0.0.1:5244',
    data_dir VARCHAR(512),
    initial_username VARCHAR(128),
    initial_password VARCHAR(128),
    install_path VARCHAR(512),
    os_name VARCHAR(32),
    arch VARCHAR(16),
    installed INTEGER DEFAULT 0,
    running INTEGER DEFAULT 0,
    first_started_at TIMESTAMP,
    last_started_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

-- ===== proxy 模块表（与 SQLite proxy-bindings.sql 对齐） =====
CREATE TABLE IF NOT EXISTS proxy_account_binding (
    id              INTEGER PRIMARY KEY AUTO_INCREMENT,
    account_id      INTEGER NOT NULL UNIQUE,
    provider_type   VARCHAR(32) NOT NULL,
    host            VARCHAR(256) NOT NULL,
    port            INTEGER NOT NULL,
    username        VARCHAR(256),
    password        VARCHAR(512),
    exit_ip         VARCHAR(64),
    city            VARCHAR(64),
    bound_at        DATETIME DEFAULT CURRENT_TIMESTAMP,
    last_used_at    DATETIME DEFAULT CURRENT_TIMESTAMP,
    use_count       INTEGER DEFAULT 0,
    captcha_passed  TINYINT(1) DEFAULT 0,
    deleted         INTEGER DEFAULT 0,
    INDEX idx_proxy_binding_account (account_id),
    INDEX idx_proxy_binding_exit_ip (exit_ip),
    INDEX idx_proxy_binding_deleted (deleted)
);

CREATE TABLE IF NOT EXISTS proxy_cool_down (
    id                  INTEGER PRIMARY KEY AUTO_INCREMENT,
    ip                  VARCHAR(64) NOT NULL,
    provider_type       VARCHAR(32) NOT NULL,
    consecutive_fail   INTEGER NOT NULL DEFAULT 0,
    reason              VARCHAR(512),
    cooled_down_at      DATETIME DEFAULT CURRENT_TIMESTAMP,
    recover_at          DATETIME,
    deleted             INTEGER DEFAULT 0,
    INDEX idx_proxy_cooldown_ip (ip, deleted),
    INDEX idx_proxy_cooldown_recover (recover_at, deleted)
);

CREATE TABLE IF NOT EXISTS proxy_audit_log (
    id              INTEGER PRIMARY KEY AUTO_INCREMENT,
    account_id      INTEGER,
    action          VARCHAR(32) NOT NULL,
    provider_type   VARCHAR(32),
    host            VARCHAR(256),
    port            INTEGER,
    exit_ip         VARCHAR(64),
    detail          VARCHAR(1024),
    created_at      DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_proxy_audit_account (account_id, created_at),
    INDEX idx_proxy_audit_action (action, created_at)
);

-- ======================== 批次日志通用框架（B9） ========================
-- 统一承载所有定时任务的「一次执行批次」与明细，避免每个任务重复造一份批次表结构。
CREATE TABLE IF NOT EXISTS batch_job (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    job_type        VARCHAR(64) NOT NULL,
    job_code        VARCHAR(128),
    trigger_source  VARCHAR(16) DEFAULT 'SCHEDULER',
    status          VARCHAR(16) DEFAULT 'RUNNING',
    total_count     INTEGER DEFAULT 0,
    success_count   INTEGER DEFAULT 0,
    failed_count    INTEGER DEFAULT 0,
    skipped_count   INTEGER DEFAULT 0,
    started_at      DATETIME DEFAULT CURRENT_TIMESTAMP,
    ended_at        DATETIME,
    summary         VARCHAR(512),
    failure_summary VARCHAR(2000),
    created_at      DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted         INTEGER DEFAULT 0,
    INDEX idx_batch_job_type (job_type, started_at),
    INDEX idx_batch_job_status (status, deleted)
);

CREATE TABLE IF NOT EXISTS batch_job_item (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    batch_id        BIGINT NOT NULL,
    item_key        VARCHAR(128),
    item_label      VARCHAR(256),
    status          VARCHAR(16),
    duration_ms     BIGINT,
    failure_reason  VARCHAR(512),
    detail          TEXT,
    started_at      DATETIME DEFAULT CURRENT_TIMESTAMP,
    ended_at        DATETIME,
    created_at      DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted         INTEGER DEFAULT 0,
    INDEX idx_batch_item_batch (batch_id),
    INDEX idx_batch_item_status (status, deleted)
);

-- ======================== I7 schema 迁移版本表 ========================
CREATE TABLE IF NOT EXISTS schema_migration (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    namespace       VARCHAR(64) NOT NULL,
    version         VARCHAR(32) NOT NULL,
    description     VARCHAR(256),
    duration_ms     BIGINT,
    status          VARCHAR(16),
    failure_reason  VARCHAR(512),
    executed_at     DATETIME DEFAULT CURRENT_TIMESTAMP,
    created_at      DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted         INTEGER DEFAULT 0,
    UNIQUE KEY uk_schema_migration_ns_ver (namespace, version),
    INDEX idx_schema_migration_status (status, deleted)
);

-- ======================== A1 Cookie 浏览器刷新 ========================
CREATE TABLE IF NOT EXISTS cookie_refresh_schedule (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    account_id          BIGINT NOT NULL,
    enabled             INTEGER DEFAULT 1,
    interval_minutes    INTEGER DEFAULT 720,
    next_run_at         DATETIME,
    last_run_at         DATETIME,
    last_result         VARCHAR(16),
    last_failure_reason VARCHAR(512),
    only_on_expired     INTEGER DEFAULT 1,
    created_at          DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at          DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted             INTEGER DEFAULT 0,
    UNIQUE KEY uk_cookie_refresh_account (account_id),
    INDEX idx_cookie_refresh_next_run (next_run_at, enabled, deleted)
);

CREATE TABLE IF NOT EXISTS scheduled_cookies_refresh_log (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    trigger_source      VARCHAR(16),
    total_count         INTEGER DEFAULT 0,
    success_count       INTEGER DEFAULT 0,
    failed_count        INTEGER DEFAULT 0,
    skipped_count       INTEGER DEFAULT 0,
    status              VARCHAR(16),
    started_at          DATETIME DEFAULT CURRENT_TIMESTAMP,
    ended_at            DATETIME,
    failure_summary     VARCHAR(2000),
    batch_job_id        BIGINT,
    created_at          DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at          DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted             INTEGER DEFAULT 0,
    INDEX idx_cookies_refresh_started (started_at, deleted)
);

-- ======================== A3 登录续期 ========================
CREATE TABLE IF NOT EXISTS login_renew_schedule (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    account_id          BIGINT NOT NULL,
    enabled             INTEGER DEFAULT 1,
    login_method        VARCHAR(16) DEFAULT 'QR',
    password_encrypted  VARCHAR(512),
    max_retry           INTEGER DEFAULT 3,
    current_retry       INTEGER DEFAULT 0,
    next_run_at         DATETIME,
    last_run_at         DATETIME,
    last_result         VARCHAR(16),
    last_failure_reason VARCHAR(512),
    created_at          DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at          DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted             INTEGER DEFAULT 0,
    UNIQUE KEY uk_login_renew_account (account_id),
    INDEX idx_login_renew_next_run (next_run_at, enabled, deleted)
);

CREATE TABLE IF NOT EXISTS scheduled_login_renew_log (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    trigger_source      VARCHAR(16),
    total_count         INTEGER DEFAULT 0,
    success_count       INTEGER DEFAULT 0,
    failed_count        INTEGER DEFAULT 0,
    skipped_count       INTEGER DEFAULT 0,
    waiting_qr_count    INTEGER DEFAULT 0,
    status              VARCHAR(16),
    started_at          DATETIME DEFAULT CURRENT_TIMESTAMP,
    ended_at            DATETIME,
    failure_summary     VARCHAR(2000),
    batch_job_id        BIGINT,
    created_at          DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at          DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted             INTEGER DEFAULT 0,
    INDEX idx_login_renew_started (started_at, deleted)
);

-- ======================== A4 Token/IM 续期 ========================
CREATE TABLE IF NOT EXISTS im_token_cache (
    id                      BIGINT AUTO_INCREMENT PRIMARY KEY,
    account_id              BIGINT NOT NULL,
    mtop_token              VARCHAR(128),
    mtop_token_cookie       VARCHAR(512),
    x5sec                   VARCHAR(512),
    im_cookie_header        TEXT,
    token_expires_at        DATETIME,
    next_renew_at           DATETIME,
    last_renew_at           DATETIME,
    last_result             VARCHAR(16),
    last_failure_reason     VARCHAR(512),
    consecutive_failures    INTEGER DEFAULT 0,
    created_at              DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at              DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted                 INTEGER DEFAULT 0,
    UNIQUE KEY uk_im_token_account (account_id),
    INDEX idx_im_token_next_renew (next_renew_at, deleted)
);

CREATE TABLE IF NOT EXISTS scheduled_token_renewal_log (
    id                      BIGINT AUTO_INCREMENT PRIMARY KEY,
    trigger_source          VARCHAR(16),
    total_count             INTEGER DEFAULT 0,
    success_count           INTEGER DEFAULT 0,
    failed_count            INTEGER DEFAULT 0,
    skipped_count           INTEGER DEFAULT 0,
    captcha_triggered_count INTEGER DEFAULT 0,
    status                  VARCHAR(16),
    started_at              DATETIME DEFAULT CURRENT_TIMESTAMP,
    ended_at                DATETIME,
    failure_summary         VARCHAR(2000),
    batch_job_id            BIGINT,
    created_at              DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at              DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted                 INTEGER DEFAULT 0,
    INDEX idx_token_renewal_started (started_at, deleted)
);

-- ======================== A5 风控冷却/限流日志 ========================
CREATE TABLE IF NOT EXISTS risk_control_log (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    account_id          BIGINT,
    trigger_type        VARCHAR(32),
    trigger_scene       VARCHAR(64),
    risk_code           VARCHAR(64),
    failure_reason      VARCHAR(512),
    cooldown_seconds    INTEGER,
    cooldown_until      DATETIME,
    batch_job_id        BIGINT,
    triggered_at        DATETIME DEFAULT CURRENT_TIMESTAMP,
    recovered           INTEGER DEFAULT 0,
    created_at          DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at          DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted             INTEGER DEFAULT 0,
    INDEX idx_risk_account (account_id, triggered_at),
    INDEX idx_risk_type (trigger_type, recovered, deleted),
    INDEX idx_risk_cooldown (cooldown_until, recovered)
);

CREATE TABLE IF NOT EXISTS scheduled_recovery_log (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    trigger_source      VARCHAR(16),
    total_count         INTEGER DEFAULT 0,
    recovered_count     INTEGER DEFAULT 0,
    status              VARCHAR(16),
    started_at          DATETIME DEFAULT CURRENT_TIMESTAMP,
    ended_at            DATETIME,
    created_at          DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at          DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted             INTEGER DEFAULT 0,
    INDEX idx_recovery_started (started_at, deleted)
);

-- ======================== A6 卡券模型升级（四类型） ========================
CREATE TABLE IF NOT EXISTS ship_card (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    card_type       VARCHAR(16) NOT NULL,             -- CARD / ACCOUNT / LINK_QRCODE / PLAIN_TEXT
    card_code       VARCHAR(256),                     -- 卡号/账号名（CARD/ACCOUNT 用）
    card_password   VARCHAR(256),                     -- 密码/密保
    extra           VARCHAR(512),                     -- 额外信息（ACCOUNT=服务器名等）
    content         TEXT,                             -- 链接/二维码内容 或 纯文本话术
    status          VARCHAR(16) DEFAULT 'AVAILABLE',  -- AVAILABLE / USED / EXPIRED / DISABLED
    used_order_id   BIGINT,
    used_at         DATETIME,
    created_at      DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted         INTEGER DEFAULT 0,
    INDEX idx_ship_card_type (card_type, status, deleted),
    INDEX idx_ship_card_used_order (used_order_id),
    INDEX idx_ship_card_code (card_code)
);

CREATE TABLE IF NOT EXISTS card_item_relation (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    product_id      BIGINT NOT NULL,
    card_id         BIGINT NOT NULL,
    priority        INTEGER DEFAULT 0,
    enabled         INTEGER DEFAULT 1,
    created_at      DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted         INTEGER DEFAULT 0,
    UNIQUE KEY uk_card_rel_product_card (product_id, card_id),
    INDEX idx_card_rel_product (product_id, enabled, priority, deleted),
    INDEX idx_card_rel_card (card_id)
);

-- ======================== A7 发货规则引擎 ========================
CREATE TABLE IF NOT EXISTS delivery_block_rule (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    account_id          BIGINT,
    rule_type           VARCHAR(32) NOT NULL,
    rule_params         TEXT,
    rule_name           VARCHAR(128),
    priority            INTEGER DEFAULT 100,
    enabled             INTEGER DEFAULT 1,
    action              VARCHAR(16) DEFAULT 'BLOCK',
    notify_template     VARCHAR(512),
    last_hit_at         DATETIME,
    hit_count           INTEGER DEFAULT 0,
    created_at          DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at          DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted             INTEGER DEFAULT 0,
    INDEX idx_delivery_rule_account (account_id, enabled, priority, deleted),
    INDEX idx_delivery_rule_type (rule_type, enabled, deleted)
);

-- ======================== B1 统一任务调度中心 ========================
CREATE TABLE IF NOT EXISTS scheduled_task (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    task_key            VARCHAR(64) NOT NULL,
    task_name           VARCHAR(128),
    category            VARCHAR(32),
    cron                VARCHAR(64),
    enabled             INTEGER DEFAULT 1,
    last_run_at         DATETIME,
    last_result         VARCHAR(16),
    last_error          VARCHAR(512),
    last_duration_ms    BIGINT,
    last_batch_job_id   BIGINT,
    created_at          DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at          DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted             INTEGER DEFAULT 0,
    UNIQUE KEY uk_scheduled_task_key (task_key),
    INDEX idx_scheduled_task_category (category, enabled, deleted)
);

-- ======================== B2 自动评价 ========================
CREATE TABLE IF NOT EXISTS auto_rate_config (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    account_id          BIGINT,
    enabled             INTEGER DEFAULT 1,
    rate_level          VARCHAR(16) DEFAULT 'GOOD',
    feedback_template   TEXT,
    delay_days          INTEGER DEFAULT 1,
    product_whitelist   TEXT,
    buyer_blacklist     TEXT,
    last_run_at         DATETIME,
    created_at          DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at          DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted             INTEGER DEFAULT 0,
    INDEX idx_auto_rate_account (account_id, enabled, deleted)
);

CREATE TABLE IF NOT EXISTS scheduled_rate_log (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    trigger_source      VARCHAR(16),
    total_count         INTEGER DEFAULT 0,
    success_count       INTEGER DEFAULT 0,
    failed_count        INTEGER DEFAULT 0,
    skipped_count       INTEGER DEFAULT 0,
    status              VARCHAR(16),
    started_at          DATETIME DEFAULT CURRENT_TIMESTAMP,
    ended_at            DATETIME,
    failure_summary     VARCHAR(2000),
    batch_job_id        BIGINT,
    created_at          DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at          DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted             INTEGER DEFAULT 0,
    INDEX idx_auto_rate_started (started_at, deleted)
);

-- ======================== B3 求小红花 ========================
CREATE TABLE IF NOT EXISTS red_flower_config (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    account_id          BIGINT,
    enabled             INTEGER DEFAULT 1,
    target_type         VARCHAR(16) DEFAULT 'buyer',
    daily_limit         INTEGER DEFAULT 20,
    today_sent_count    INTEGER DEFAULT 0,
    today_date          DATETIME,
    buyer_whitelist     TEXT,
    last_run_at         DATETIME,
    created_at          DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at          DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted             INTEGER DEFAULT 0,
    INDEX idx_red_flower_account (account_id, enabled, deleted)
);

CREATE TABLE IF NOT EXISTS scheduled_red_flower_log (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    trigger_source      VARCHAR(16),
    total_count         INTEGER DEFAULT 0,
    success_count       INTEGER DEFAULT 0,
    failed_count        INTEGER DEFAULT 0,
    skipped_count       INTEGER DEFAULT 0,
    status              VARCHAR(16),
    started_at          DATETIME DEFAULT CURRENT_TIMESTAMP,
    ended_at            DATETIME,
    failure_summary     VARCHAR(2000),
    batch_job_id        BIGINT,
    created_at          DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at          DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted             INTEGER DEFAULT 0,
    INDEX idx_red_flower_started (started_at, deleted)
);

-- ======================== B4 定时擦亮 ========================
CREATE TABLE IF NOT EXISTS scheduled_polish_log (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    trigger_source      VARCHAR(16),
    account_id          BIGINT,
    total_count         INTEGER DEFAULT 0,
    success_count       INTEGER DEFAULT 0,
    failed_count        INTEGER DEFAULT 0,
    skipped_count       INTEGER DEFAULT 0,
    status              VARCHAR(16),
    started_at          DATETIME DEFAULT CURRENT_TIMESTAMP,
    ended_at            DATETIME,
    failure_summary     VARCHAR(2000),
    batch_job_id        BIGINT,
    created_at          DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at          DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted             INTEGER DEFAULT 0,
    INDEX idx_polish_started (started_at, deleted),
    INDEX idx_polish_account (account_id, started_at)
);

CREATE TABLE IF NOT EXISTS scheduled_close_notice_log (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    trigger_source      VARCHAR(16),
    account_id          BIGINT,
    total_count         INTEGER DEFAULT 0,
    success_count       INTEGER DEFAULT 0,
    failed_count        INTEGER DEFAULT 0,
    skipped_count       INTEGER DEFAULT 0,
    status              VARCHAR(16),
    started_at          DATETIME DEFAULT CURRENT_TIMESTAMP,
    ended_at            DATETIME,
    failure_summary     VARCHAR(2000),
    batch_job_id        BIGINT,
    created_at          DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at          DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted             INTEGER DEFAULT 0,
    INDEX idx_close_notice_started (started_at, deleted),
    INDEX idx_close_notice_account (account_id, started_at)
);

-- BOT-O3 发货匹配规则（关键词→卡券，决定发哪张卡/发几次；≠ A7 拦截阻断）
CREATE TABLE IF NOT EXISTS delivery_rules (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    account_id          BIGINT,
    item_id             VARCHAR(64),
    keyword             VARCHAR(500) NOT NULL,
    match_mode          VARCHAR(16) DEFAULT 'CONTAINS',
    card_id             BIGINT NOT NULL,
    delivery_count      INTEGER DEFAULT 1,
    priority            INTEGER DEFAULT 100,
    enabled             INTEGER DEFAULT 1,
    last_hit_at         DATETIME,
    hit_count           INTEGER DEFAULT 0,
    remark              VARCHAR(500),
    created_at          DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at          DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted             INTEGER DEFAULT 0,
    INDEX idx_delivery_rules_account (account_id, enabled, deleted),
    INDEX idx_delivery_rules_item (item_id, enabled, deleted)
);

-- BOT-O4 发货日志（每次尝试发货必有一条；可按订单反查）
CREATE TABLE IF NOT EXISTS delivery_log (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    account_id          BIGINT,
    order_id            BIGINT,
    product_id          BIGINT,
    buyer_id            VARCHAR(64),
    ship_card_id        BIGINT,
    rule_decision       VARCHAR(32),
    hit_rule_name       VARCHAR(128),
    deliver_content     TEXT,
    status              VARCHAR(16),
    failure_reason      VARCHAR(2000),
    batch_job_id        BIGINT,
    shipped_at          DATETIME,
    duration_ms         BIGINT,
    created_at          DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at          DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted             INTEGER DEFAULT 0,
    INDEX idx_delivery_log_order (order_id, deleted),
    INDEX idx_delivery_log_account (account_id, shipped_at, deleted)
);

-- BOT-O5 发货终态（多数量订单按 unit 跟踪 sent/finalized；支持部分成功）
CREATE TABLE IF NOT EXISTS delivery_finalization (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_id            BIGINT NOT NULL,
    unit_index          INTEGER NOT NULL,
    total_units         INTEGER DEFAULT 1,
    card_id             BIGINT,
    reservation_id      VARCHAR(64),
    status              VARCHAR(16) DEFAULT 'PENDING',
    delivery_log_id     BIGINT,
    finalized_at        DATETIME,
    reason              VARCHAR(500),
    created_at          DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at          DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted             INTEGER DEFAULT 0,
    UNIQUE KEY uk_delivery_finalization (order_id, unit_index, deleted),
    INDEX idx_delivery_finalization_status (status, deleted)
);

-- BOT-O6 卡密预留（发货前预留→成功确认消耗→失败释放；唯一约束防双花）
CREATE TABLE IF NOT EXISTS data_card_reservations (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    card_id             BIGINT NOT NULL,
    data_card_id        BIGINT NOT NULL,
    reserved_for        BIGINT NOT NULL,
    buyer_id            VARCHAR(64),
    status              VARCHAR(16) DEFAULT 'RESERVED',
    reserved_at         DATETIME DEFAULT CURRENT_TIMESTAMP,
    confirmed_at        DATETIME,
    reason              VARCHAR(500),
    created_at          DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at          DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted             INTEGER DEFAULT 0,
    UNIQUE KEY uk_data_card_reservation (data_card_id, status, deleted),
    INDEX idx_data_card_reservation_order (reserved_for, status, deleted)
);

-- BOT-B1 评价模板（多条+激活+账号级开关；≠ A11 AutoRateConfig 单条配置）
CREATE TABLE IF NOT EXISTS comment_templates (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    account_id          BIGINT,
    category            VARCHAR(16) DEFAULT 'POSITIVE',
    content             VARCHAR(2000) NOT NULL,
    name                VARCHAR(128),
    enabled             INTEGER DEFAULT 1,
    priority            INTEGER DEFAULT 100,
    use_count           INTEGER DEFAULT 0,
    remark              VARCHAR(500),
    created_at          DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at          DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted             INTEGER DEFAULT 0,
    INDEX idx_comment_templates_account (account_id, category, enabled, deleted)
);

-- BOT-D1 商品专属回复（商品级专属回复；≠ A10 通用关键词回复）
CREATE TABLE IF NOT EXISTS item_reply (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    account_id          BIGINT NOT NULL,
    item_id             VARCHAR(64) NOT NULL,
    item_title          VARCHAR(256),
    trigger_scene       VARCHAR(32) DEFAULT 'FIRST_INQUIRY',
    reply_content       VARCHAR(2000) NOT NULL,
    enabled             INTEGER DEFAULT 1,
    priority            INTEGER DEFAULT 100,
    use_count           INTEGER DEFAULT 0,
    remark              VARCHAR(500),
    created_at          DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at          DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted             INTEGER DEFAULT 0,
    INDEX idx_item_reply_match (account_id, item_id, trigger_scene, enabled, deleted)
);
