# 小程序 API 规范附录

> 所有路径统一带 `/api/mini/` 前缀。对应后端内部 Controller 路径为原 `/api/xxx`，由 `ApiPrefixStripFilter` 在 Spring Boot Filter 层剥离，Controller 层零改动。
>
> 请求头约定：`Authorization: Bearer <token>` | `X-App-Type: mini-program`（可选）| `X-Account-Id: <accountId>`（如当前选中账号）

---

## 一、认证 `/api/mini/auth`

### POST /api/mini/auth/login — 管理员密码登录

```json
Request: { "username": "string", "password": "string" }
Response: {
  "code": 0, "message": "success",
  "data": {
    "accessToken": "<jwt>", "expiresIn": 86400,
    "user": { "id": 1, "username": "admin", "displayName": "管理员", "roleLevel": 100 }
  }
}
```

### GET /api/mini/auth/profile — 获取个人资料

```json
Response: { id, username, displayName, roleLevel, email, phone }
```

### PUT /api/mini/auth/profile — 修改个人资料

```json
Body: { "displayName": "...", "email": "...", "phone": "..." }
```

### PUT /api/mini/auth/password — 修改密码

```json
Body: { "oldPassword": "...", "newPassword": "..." }
```

### POST /api/mini/auth/platform-login — 平台快捷登录（P2）

```json
Headers: { "X-Platform-Code": "<login_code>" }
Body: { "platform": "weixin" | "alipay" | "douyin" | "dingtalk" }
Response: 同 login JWT Response
```

---

## 二、仪表盘 `/api/mini/monitor`

### GET /api/mini/monitor/dashboard — 首页 KPI 概览

```json
Response: {
  "data": {
    "kpiList": [
      { "label": "今日订单", "value": 23, "unit": "笔", "trend": 12, "trendDirection": "up" },
      { "label": "今日消息", "value": 156, "unit": "条", "trend": -5, "trendDirection": "down" }
    ],
    "accountStats": [
      { "accountId": 1, "displayName": "账号A", "status": "ACTIVE", "orderCount": 8, "messageCount": 32 }
    ],
    "orderTrend": [
      { "date": "2025-07-20", "value": 20 },
      { "date": "2025-07-21", "value": 25 }
    ],
    "messageActivity": [
      { "date": "2025-07-20", "value": 45 },
      { "date": "2025-07-21", "value": 60 }
    ]
  }
}
```

### GET /api/mini/monitor/accounts — 账号维度统计

```json
Response: Page<{ accountId, accountName, orderCount, messageCount, replyCount, status }>
```

---

## 三、账号管理 `/api/mini/accounts`

### GET /api/mini/accounts — 账号列表（分页）

```json
Response: { data: { records: [AccountItem], total, page, size } }
```

### POST /api/mini/accounts — Cookie 登录

```json
Body: { "accountName": "...", "cookieHeader": "...", "remark": "..." }
Response: AccountItem
```

### POST /api/mini/accounts/qr-login — 生成扫码二维码（P1）

```json
Response: { "sessionId": "...", "qrCodeUrl": "https://..." }
```

### GET /api/mini/accounts/qr-login/status?sessionId=xxx — 轮询扫码状态

```json
Response: { "status": "PENDING" | "SCANNED" | "CONFIRMED" | "EXPIRED" }
```

### PUT /api/mini/accounts/{id}/status — 切换账号状态

```json
Body: { "enabled": true | false }
```

### DELETE /api/mini/accounts/{id} — 删除账号

### GET /api/mini/accounts/{id}/profile — 获取账号实时资料（P2）

### POST /api/mini/accounts/{id}/profile/sync — 同步账号资料（P2）

---

## 四、商品管理 `/api/mini/products` + `/api/mini/local-products`

### 4.1 闲鱼商品

#### GET /api/mini/products — 商品列表

```json
Query: page=1&size=20&accountId=&keyword=&status=ON_SALE|OFF_SALE|DRAFT
Response: { data: { records: [ProductItem], total, page, size } }

ProductItem: {
  id, accountId, itemTitle, price, originalPrice, stock, status,
  images: ["https://..."], viewCount, favoriteCount, detailUrl?, createdAt
}
```

#### GET /api/mini/products/{id} — 商品详情

#### POST /api/mini/products — 创建商品（闲鱼 API）

```json
Body: { "accountId": 1, "title": "...", "description": "...", "price": 29.9,
        "stock": 100, "categoryId": "...", "images": ["url..."] }
```

#### PUT /api/mini/products/{id} — 编辑商品

#### DELETE /api/mini/products/{id} — 删除商品

#### POST /api/mini/products/sync — 同步闲鱼商品

```json
Query: accountId=xxx | Body: { "accountId": 1 }
Response: { "taskId": "...", "message": "同步已启动" }
```

#### POST /api/mini/products/upload — 上传图片/视频

```json
FormData: file (image/video)
Response: { "url": "https://cdn.xianyu.com/..." }
```

#### GET /api/mini/products/category-tree — 分类树

```json
Response: [ { "id": "...", "name": "...", "children": [...] } ]
```

#### PUT /api/mini/products/{id}/price — 改价

```json
Body: { "price": 29.9 }
```

#### PUT /api/mini/products/{id}/stock — 改库存

```json
Body: { "stock": 50 }
```

#### POST /api/mini/products/{id}/shelf-on — 上架

#### POST /api/mini/products/{id}/shelf-off — 下架

#### POST /api/mini/products/polish — 单商品擦亮

#### POST /api/mini/products/polish/batch — 批量擦亮

#### POST /api/mini/products/polish/super — 超级擦亮（P2）

#### GET /api/mini/products/for-virtual-ship — 虚拟发货用商品列表（P3）

#### PUT /api/mini/products/{id}/virtual-ship-config — 商品级虚拟发货配置（P3）

---

### 4.2 本地商品

#### GET /api/mini/local-products — 本地商品列表

#### POST /api/mini/local-products — 新建本地商品

#### GET /api/mini/local-products/{id} — 详情

#### PUT /api/mini/local-products/{id} — 编辑

#### DELETE /api/mini/local-products/{id} — 删除

#### POST /api/mini/local-products/{id}/publish — 发布单个到闲鱼

#### POST /api/mini/local-products/batch-publish — 批量发布（P2）

#### POST /api/mini/local-products/import/preview — CSV 导入预览（P3）

#### POST /api/mini/local-products/import/confirm — CSV 导入确认（P3）

---

## 五、消息管理 `/api/mini/messages`

### GET /api/mini/messages/sessions — 会话列表

```json
Response: ChatSession[]

ChatSession: {
  id: string, accountId: number, accountName: string,
  userName: string, userAvatar?: string,
  lastMessage: string, lastMessageTime: string,
  unreadCount: number, isAutoReplied: boolean
}
```

### GET /api/mini/messages/list?accountId=&limit= — 消息列表（兜底）

### GET /api/mini/messages/history?accountId=&sessionId=&limit= — 历史消息

### POST /api/mini/messages/sync?accountId=xxx — 同步消息（MTOP）

### POST /api/mini/messages/send — 发送消息

```json
Body: {
  "accountId": 1, "sessionId": "session-id",
  "content": "text or image url", "msgType": "TEXT"
}
```

### WS /ws/messages — 实时推送（P1）

```
URL: wss://<host>:8080/ws/messages?token=<jwt>&accountId=<xxx>
Messages: NEW_MESSAGE | SESSION_UPDATE | HEARTBEAT_ACK
Fallback: HTTP long-polling when WebSocket unsupported by platform
```

---

## 六、订单管理 `/api/mini/orders`

### GET /api/mini/orders — 订单列表

```json
Query: page=1&size=20&type=sold|bought|all&accountId=&status=PENDING|PAID|SHIPPED|COMPLETED|REFUNDING
Response: Page<OrderItem>
OrderItem: {
  id, orderId, accountName, itemTitle, buyerName, amount,
  status, trackingNo?, deliveryType?, createdAt
}
```

### GET /api/mini/orders/{id}?accountId=xxx — 订单详情（P1）

### POST /api/mini/orders/accounts/{id}/sync — 同步订单（P1）

### POST /api/mini/orders/{id}/delivery?trackingNo=xxx — 发货

---

## 七、规则管理 `/api/mini/rules`

### GET /api/mini/rules?accountId=&replyType=KEYWORD|AI|AUTO — 规则列表

### POST /api/mini/rules — 创建规则

### PUT /api/mini/rules/{id} — 更新规则

### DELETE /api/mini/rules/{id} — 删除规则

### POST /api/mini/rules/{id}/toggle?enabled=true|false — 开关规则

### POST /api/mini/rules/test — 测试匹配

```json
Body: { "text": "你好" }
Response: { "matched": true, "ruleName": "...", "replyText": "..." }
```

### GET /api/mini/rules/config/{accountId} — 读取 AI/Auto 配置（P2）

### POST /api/mini/rules/config/{accountId} — 保存配置（P2）

---

## 八、钱包 `/api/mini/wallet/{accountId}`

### GET /api/mini/wallet/{accountId} — 钱包概览

```json
Response: { balance, frozenBalance, withdrawableBalance, lastSyncedAt }
```

### GET /api/mini/wallet/{accountId}/transactions — 交易记录

### GET /api/mini/wallet/{accountId}/recent?limit= — 最近交易（P2）

### POST /api/mini/wallet/{accountId}/sync — 同步钱包（P2）

### GET /api/mini/wallet/{accountId}/debug — 调试原始响应（P2）

### POST /api/mini/wallet/{accountId}/probe?api=候选名&version=xxx — MTOP 探测（P2）

### GET /api/mini/wallet/api-names — 当前生效接口名（P2）

---

## 九、通知 `/api/mini/notify`

### 站内通知

| 方法 | 路径 | 说明 | 权限 |
|------|------|------|------|
| GET | /api/mini/notify/messages | 站内通知列表 | P0 |
| GET | /api/mini/notify/messages/unread-count | 未读数量 | P0 |
| POST | /api/mini/notify/messages/{id}/read | 标记已读 | P0 |
| POST | /api/mini/notify/messages/read-all | 全部已读 | P0 |
| GET | /api/mini/notify/logs | 投递日志 | P2 |
| GET | /api/mini/notify/logs/recent?limit= | 最近日志 | P2 |

### 通知通道

| 方法 | 路径 | 说明 | 权限 |
|------|------|------|------|
| GET | /api/mini/notify/channels?type=email|webhook|sms | 通道列表 | P2 |
| POST | /api/mini/notify/channels | 新增通道 | P2 |
| PUT | /api/mini/notify/channels/{id} | 更新通道 | P2 |
| DELETE | /api/mini/notify/channels/{id} | 删除通道 | P2 |
| POST | /api/mini/notify/channels/{id}/toggle | 开关通道 | P2 |
| POST | /api/mini/notify/channels/{id}/test | 测试消息 | P2 |

### 模板与订阅

| 方法 | 路径 | 说明 | 权限 |
|------|------|------|------|
| GET | /api/mini/notify/templates | 模板列表 | P2 |
| GET | /api/mini/notify/templates/scenarios | 场景列表 | P2 |
| POST | /api/mini/notify/templates | 新增/覆盖模板 | P2 |
| DELETE | /api/mini/notify/templates/{id} | 删除模板 | P2 |
| GET | /api/mini/notify/subscriptions | 订阅规则列表 | P2 |
| POST | /api/mini/notify/subscriptions | 新增订阅 | P2 |
| PUT | /api/mini/notify/subscriptions/{id} | 更新订阅 | P2 |
| DELETE | /api/mini/notify/subscriptions/{id} | 删除订阅 | P2 |
| POST | /api/mini/notify/subscriptions/{id}/toggle | 开关订阅 | P2 |

### 每日摘要

| 方法 | 路径 | 说明 | 权限 |
|------|------|------|------|
| GET | /api/mini/notify/digest/config | 摘要配置 | P2 |
| PUT | /api/mini/notify/digest/config | 保存摘要配置 | P2 |
| POST | /api/mini/notify/digest/send-now | 立即发送摘要 | P2 |

---

## 十、AI 功能

### 10.1 AI 模型与厂商

| 方法 | 路径 | 说明 | 权限 |
|------|------|------|------|
| GET | /api/mini/ai/models | 模型列表 | P2 |
| GET | /api/mini/ai/providers | 厂商列表 | P2 |
| POST | /api/mini/ai/providers | 新增厂商 | P2 |
| PUT | /api/mini/ai/providers/{id} | 编辑/启用厂商 | P2 |
| DELETE | /api/mini/ai/providers/{id} | 删除厂商 | P2 |
| GET | /api/mini/ai/providers/{providerId}/models | 厂商下模型 | P2 |
| GET | /api/mini/ai/providers/{providerId}/remote-models | 远程获取模型 | P2 |
| POST | /api/mini/ai/models | 新增模型 | P2 |
| PUT | /api/mini/ai/models/{id} | 编辑/启用模型 | P2 |
| DELETE | /api/mini/ai/models/{id} | 删除模型 | P2 |

### 10.2 AI 对话

| 方法 | 路径 | 说明 | 权限 |
|------|------|------|------|
| POST | /api/mini/ai/chat/test | AI 对话测试 | P1 |
| GET | /api/mini/ai/cs/sessions | AI 客服会话 | P2 |
| GET | /api/mini/ai/cs/knowledge | 知识库 | P2 |
| POST | /api/mini/ai/cs/knowledge | 新增知识条目 | P2 |
| DELETE | /api/mini/ai/cs/knowledge/{id} | 删除知识条目 | P2 |
| GET | /api/mini/ai/cs/session-states | 议价记录 | P2 |

### 10.3 AI Demo 能力

| 方法 | 路径 | 说明 | 权限 |
|------|------|------|------|
| POST | /api/mini/ai/demo/generate-title | 标题优化 | P1 |
| POST | /api/mini/ai/demo/optimize-description | 描述优化 | P1 |
| POST | /api/mini/ai/demo/extract-keywords | 关键词提取 | P1 |

### 10.4 AI 运营

| 方法 | 路径 | 说明 | 权限 |
|------|------|------|------|
| POST | /api/mini/ai/ops/batch-create | 批量上品 | P2 |
| GET | /api/mini/ai/ops/batch-create/progress?taskId= | 批量上品进度 | P2 |
| POST | /api/mini/ai/ops/multi-sync | 多账号同步 | P2 |
| GET | /api/mini/ai/ops/weekly-report?accountId=&modelId= | 运营周报 | P2 |
| GET | /api/mini/ai/ops/tasks?page=&size= | 任务列表 | P2 |

---

## 十一、其他模块

### 11.1 收藏 `/api/mini/collect`

| 方法 | 路径 | 说明 | 权限 |
|------|------|------|------|
| GET | /api/mini/collect?accountId=&targetType=PRODUCT|USER|STORE | 收藏列表 | P2 |
| POST | /api/mini/collect | 添加收藏 | P2 |
| DELETE | /api/mini/collect/{id} | 移除收藏 | P2 |
| POST | /api/mini/collect/sync?accountId= | 同步闲鱼收藏 | P2 |
| GET | /api/mini/collect/search?accountId=&keyword= | 搜索闲鱼商品 | P2 |
| GET | /api/mini/collect/lookup?accountId=&targetType=&targetId= | 自动识别收藏目标 | P2 |

### 11.2 市场情报 `/api/mini/market`

| 方法 | 路径 | 说明 | 权限 |
|------|------|------|------|
| GET | /api/mini/market/keywords | 关键词列表 | P2 |
| POST | /api/mini/market/keywords | 添加追踪关键词 | P2 |
| POST | /api/mini/market/keywords/{keyword}/pause | 暂停追踪 | P2 |
| POST | /api/mini/market/keywords/{keyword}/resume | 恢复追踪 | P2 |
| DELETE | /api/mini/market/keywords/{keyword} | 删除 | P2 |
| POST | /api/mini/market/keywords/{keyword}/crawl | 立即抓取 | P2 |
| GET | /api/mini/market/trend/{keyword}?days= | 趋势数据 | P2 |
| GET | /api/mini/market/distribution/{keyword}?days= | 价格分布 | P2 |
| GET | /api/mini/market/latest/{keyword} | 最新数据 | P2 |
| GET | /api/mini/market/item/{itemId} | 单品历史 | P2 |
| POST | /api/mini/market/compute-daily | 计算每日统计 | P2 |
| POST | /api/mini/market/seller-fetch/{userId} | 抓取卖家画像 | P2 |
| GET | /api/mini/market/seller/{userId} | 获取卖家画像 | P2 |
| GET | /api/mini/market/seller-search?keyword= | 搜索卖家 | P2 |

### 11.3 买家画像 `/api/mini/buyer`

| 方法 | 路径 | 说明 | 权限 |
|------|------|------|------|
| GET | /api/mini/buyer/list?page=&size=&keyword= | 买家列表 | P2 |
| GET | /api/mini/buyer/{buyerId} | 买家详情 | P2 |
| POST | /api/mini/buyer/{buyerId}/tag?tag= | 添加标签 | P2 |
| DELETE | /api/mini/buyer/{buyerId}/tag?tag= | 移除标签 | P2 |
| POST | /api/mini/buyer/{buyerId}/notes | 设置备注 | P2 |

### 11.4 评价与退款 `/api/mini/reviews`

| 方法 | 路径 | 说明 | 权限 |
|------|------|------|------|
| GET | /api/mini/reviews?accountId=&buyerId=&page=&pageSize= | 评价列表 | P2 |
| POST | /api/mini/reviews/orders/{orderId}?accountId=&rating=&content= | 发表评价 | P2 |
| GET | /api/mini/reviews/credit?accountId=&userId= | 信用画像 | P2 |
| POST | /api/mini/reviews/refunds?accountId=&orderId=&reason=&amount= | 申请退款 | P2 |
| GET | /api/mini/reviews/refunds?accountId=&disputeStatus=&page=&pageSize= | 退款列表 | P2 |
| GET | /api/mini/reviews/refunds/{refundId}?accountId= | 退款详情 | P2 |

### 11.5 自动回复日志 `/api/mini/reply-logs`

| 方法 | 路径 | 说明 | 权限 |
|------|------|------|------|
| GET | /api/mini/reply-logs?page=&size=&accountId=&replyType=&matched= | 自动回复日志 | P2 |

### 11.6 虚拟发货 `/api/mini/virtual-ship`

| 方法 | 路径 | 说明 | 权限 |
|------|------|------|------|
| GET | /api/mini/virtual-ship/tasks?page=&size= | 发货任务列表 | P2 |
| POST | /api/mini/virtual-ship/tasks/{id}/trigger | 触发发货 | P2 |
| GET | /api/mini/virtual-ship/config?accountId= | 全局自动发货配置 | P2 |
| POST | /api/mini/virtual-ship/config | 保存配置 | P2 |
| GET | /api/mini/virtual-ship/cards | 卡密池列表 | P2 |
| POST | /api/mini/virtual-ship/cards/import | 批量导入卡密 | P2 |
| POST | /api/mini/virtual-ship/cards/batch | 批量删除卡密 | P2 |
| DELETE | /api/mini/virtual-ship/cards/{id} | 删除卡密 | P2 |

### 11.7 熔断器 `/api/mini/circuit-breaker`（P3）

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | /api/mini/circuit-breaker | 熔断器列表 |
| GET | /api/mini/circuit-breaker/{accountId}/{serviceName} | 单个熔断器 |
| POST | /api/mini/circuit-breaker/{accountId}/{serviceName}/reset | 重置 |
| POST | /api/mini/circuit-breaker/global/{serviceName}/reset | 全局重置 |

### 11.8 审计日志 `/api/mini/audit`（P3）

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | /api/mini/audit/logs?page=&size=&action=&resourceType= | 审计日志 |

### 11.9 代理管理 `/api/mini/proxy`（P3）

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | /api/mini/proxy/status | 代理池状态 |
| GET | /api/mini/proxy/config | 供应商配置列表 |
| POST | /api/mini/proxy/config | 保存配置 |
| DELETE | /api/mini/proxy/config/{providerType} | 删除配置 |
| POST | /api/mini/proxy/reload | 运行时 reload |
| DELETE | /api/mini/proxy/bindings/{accountId} | 解绑账号 |
| GET | /api/mini/proxy/health-check | 健康检查 |

### 11.10 网盘存储 `/api/mini/cloud-storage`（P3）

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | /api/mini/cloud-storage/accounts?accountId= | 网盘账号列表 |
| GET | /api/mini/cloud-storage/accounts/{id} | 账号详情 |
| PUT | /api/mini/cloud-storage/accounts/{id} | 更新账号 |
| DELETE | /api/mini/cloud-storage/accounts/{id} | 删除账号 |
| GET | /api/mini/cloud-storage/auth-url?provider=&redirectUri= | OAuth 授权链接 |
| POST | /api/mini/cloud-storage/callback?provider=&code=&state=&accountId= | OAuth 回调处理 |
| GET | /api/mini/cloud-storage/files?storageAccountId= | 文件列表 |
| POST | /api/mini/cloud-storage/accounts/{id}/files | 上传文件 |
| POST | /api/mini/cloud-storage/files/{fileId}/share | 分享文件 |
| GET | /api/mini/cloud-storage/openlist/status | OpenList 状态 |
| POST | /api/mini/cloud-storage/openlist/install/start/stop/restart | OpenList 控制 |

### 11.11 Chrome 配置 `/api/mini/chrome-config`（P3）

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | /api/mini/chrome-config | 浏览器配置 |
| GET | /api/mini/chrome-config/detect | 探测浏览器 |
| GET | /api/mini/chrome-config/detect/all | 全量探测 |
| POST | /api/mini/chrome-config/save | 保存配置 |
| POST | /api/mini/chrome-config/download | 自动下载 Chrome |
| POST | /api/mini/chrome-config/validate | 校验路径 |

---

## 十二、API 优先级汇总

| 阶段 | 模块 | 端点数量 | 说明 |
|------|------|---------|------|
| **P0** | 认证 + 仪表盘 + 账号 + 商品 + 消息 + 订单 + 通知(站内) | ~35 | MVP 基础通信链 |
| **P1** | 规则 + 擦亮 + 商品增强 + AI 对话 + 钱包 + 商品发布 | ~15 | 自动化与智能 |
| **P2** | 收藏 + 市场 + 买家 + 评价 + 通知配置 + 虚拟发货 + AI 高级 | ~45 | 经营分析与高级功能 |
| **P3** | 审计 + 代理 + 网盘 + Chrome + 高级运维 | ~20 | 运维管理能力 |
