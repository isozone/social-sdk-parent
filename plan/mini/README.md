# AI 鱼多宝 — 小程序设计方案

> 基于现有 `social-sdk-xianyu-manager`（Spring Boot）+ `social-sdk-xianyu-manager-web`（Vue3），
> 设计面向微信/支付宝/抖音/QQ/百度/快手/飞书/钉钉/美团 九大平台的小程序方案。

---

## 一、产品定位

管理后台功能过于庞大（40+ 页面），不适合做小程序。小程序定位为 **轻量级移动经营助手**：

| 维度 | Web 管理后台 | 小程序 |
|------|-------------|--------|
| 用户 | 重度运营者 | 碎片化查看者 |
| 场景 | 发品/配置规则/数据分析/批量操作 | 看数据/回消息/查订单/快速上下架 |
| 频率 | 每天多次 × 数分钟 | 每次几分钟 × 多次 |
| 权限 | 全功能 | 按账号筛选 |

### TabBar 入口

```
首页 (仪表盘)      消息      商品      我的
   📊              💬        🛒        👤
```

---

## 二、技术选型

| 类别 | 选型 | 版本 | 原因 |
|------|------|------|------|
| 框架 | uni-app | ^3.x | 一套代码 9 平台，Vue 语法零学习成本 |
| 语言 | TypeScript | ^5.4 | 新项目引入 TS |
| UI 组件库 | uni-ui（自封装品牌主题） | latest | 体积小，可定制 |
| 状态管理 | Pinia | ^2.1 | 与 Web 端一致 |
| HTTP | uni.request | - | 免依赖，跨端统一 |
| 构建 | Vite + @dcloudio/vite-plugin-uni | - | 快速编译到 9 平台 |
| 条件编译 | `#ifdef MP-*` | - | 平台差异隔离 |

> **为什么选 uni-app**
> - Vue 语法一致，团队学习成本为零（Web 前端已经是 Vue 3 SFC）
> - 一键编译到 9 个平台，维护一套组件库
> - Taro（React 系）切换成本高；原生分端开发需要 9 套代码不可接受

---

## 三、目录结构

```
social-sdk-parent/
├── social-sdk-xianyu-manager/             ← 现有后端（Controller 不变）
├── social-sdk-xianyu-manager-web/         ← 现有 Vue3 Web（待接入 /api/web/*）
└── social-sdk-xianyu-miniprogram/         ← 新建：uni-app 项目
    ├── manifest.json                      ← 各平台 appid / 包体积配置
    ├── pages.json                         ← 路由 + TabBar + 分包
    ├── package.json
    ├── tsconfig.json
    ├── vite.config.ts                     ← uni-app Vite 插件
    └── src/
        ├── App.vue
        ├── main.ts
        ├── uni.scss                       ← 全局样式变量（品牌紫 #4f46e5 / 青 #22d3ee）
        ├── api/
        │   ├── request.ts                 ← 请求封装：Token 注入 / 错误处理 / 重试
        │   ├── auth.ts                    ← POST /api/mini/auth/login
        │   ├── dashboard.ts               ← GET /api/mini/monitor/dashboard
        │   ├── accounts.ts                ← 账号 CRUD
        │   ├── products.ts                ← 商品 CRUD + 发布 + 擦亮
        │   ├── messages.ts                ← 会话 / 聊天 / 同步
        │   ├── orders.ts                  ← 订单列表 / 发货
        │   ├── rules.ts                   ← 关键词规则
        │   ├── wallet.ts                  ← 钱包余额
        │   ├── notify.ts                  ← 站内通知
        │   ├── collect.ts                 ← 收藏关注
        │   ├── market.ts                  ← 市场情报
        │   ├── buyer.ts                   ← 买家画像
        │   ├── reviews.ts                 ← 评价退款
        │   ├── ai.ts                      ← AI 模型/对话/客服/运营
        │   └── virtual-ship.ts            ← 虚拟发货
        ├── store/
        │   ├── modules/
        │   │   ├── auth.ts                ← Token + 管理员信息
        │   │   ├── account.ts             ← 当前选中闲鱼账号
        │   │   ├── dashboard.ts           ← 仪表盘缓存
        │   │   ├── message.ts             ← 会话 / 未读 / WebSocket
        │   │   └── product.ts             ← 商品列表缓存
        │   └── index.ts
        ├── types/
        │   ├── product.ts
        │   ├── order.ts
        │   ├── message.ts
        │   ├── account.ts
        │   └── dashboard.ts
        ├── components/
        │   ├── business/
        │   │   ├── StatCard.vue           ← KPI 卡片
        │   │   ├── ProductCard.vue        ← 商品卡片（紧凑/卡片两种模式）
        │   │   ├── ChatBubble.vue         ← 聊天气泡
        │   │   ├── AccountSwitcher.vue    ← 账号切换
        │   │   ├── OrderStatusTag.vue     ← 订单状态标签
        │   │   ├── ConfirmDialog.vue      ← 确认弹窗
        │   │   └── EmptyState.vue         ← 空状态
        │   └── common/
        │       ├── PageHeader.vue
        │       ├── LoadingSpinner.vue
        │       ├── SearchInput.vue
        │       ├── PaginationHint.vue     ← 上拉加载更多
        │       ├── ErrorBoundary.vue
        │       └── OfflineBar.vue
        ├── views/
        │   ├── login/index.vue            ← 登录页（通用 + 平台快捷登录）
        │   ├── index/index.vue            ← 仪表盘
        │   ├── accounts/list.vue          ← 账号列表
        │   ├── accounts/detail.vue        ← 账号详情
        │   ├── accounts/add.vue           ← Cookie 粘贴 / 扫码登录
        │   ├── products/list.vue          ← 商品列表
        │   ├── products/detail.vue        ← 商品详情
        │   ├── products/publish.vue       ← 发布商品（图片上传）
        │   ├── messages/index.vue         ← 会话列表
        │   ├── messages/chat.vue          ← 聊天窗口
        │   ├── orders/list.vue            ← 订单列表
        │   ├── orders/detail.vue          ← 订单详情 / 发货
        │   ├── rules/list.vue             ← 规则列表（P1）
        │   ├── rules/edit.vue             ← 规则编辑（P1）
        │   ├── wallet/index.vue           ← 钱包资产（P1）
        │   ├── collect/list.vue           ← 收藏关注（P2）
        │   ├── reviews/index.vue          ← 评价退款（P2）
        │   ├── reply-logs/index.vue       ← 自动回复日志（P2）
        │   ├── profile/index.vue          ← 个人中心
        │   ├── packages/ai/               ← P3: AI 厂商/运营/客服
        │   └── packages/monitor/          ← P3: 审计/代理/熔断器
        ├── utils/
        │   ├── storage.ts                 ← localStorage 封装
        │   ├── time.ts                    ← 时间格式化
        │   ├── route-guard.ts             ← 路由守卫
        │   └── offline-handler.ts         ← 离线状态检测
        └── platforms/                     ← 平台特定代码
            ├── index.ts                   ← 平台检测 + 统一登录
            ├── weixin.ts                  ← #ifdef MP-WEIXIN
            ├── alipay.ts                  ← #ifdef MP-ALIPAY
            ├── douyin.ts                  ← #ifdef MP-TOUTIAO
            └── errors.ts                  ← 各平台错误映射
```

### 编译目标输出

```
dist/dev/mp-weixin/        ← 微信开发者工具
dist/build/mp-alipay/      ← 支付宝小程序
dist/build/mp-toutiao/     ← 抖音小程序
dist/build/mp-baidu/       ← 百度智能小程序
dist/build/mp-qq/          ← QQ 小程序
dist/build/mp-kuaishou/    ← 快手小程序
dist/build/mp-lark/        ← 飞书小程序
dist/build/mp-dingtalk/    ← 钉钉小程序
dist/build/mp-meituan/     ← 美团小程序
```

---

## 四、后端路由策略

### 4.1 前缀约定

所有端通过统一前缀区分来源，后续方便路由转发、权限隔离、限流策略分开配置：

| 端类型 | 前缀 | 说明 |
|--------|------|------|
| **小程序** | `/api/mini/xxx` | 本方案，移动端运营助手 |
| H5/Web SPA | `/api/web/xxx` | 现有 Vue3 管理后台 |
| OpenAPI | `/api/open/xxx` | 第三方接入（已有 `openapi.controller` 包） |
| App Native | `/api/app/xxx` | 未来独立 App |

### 4.2 实现方式 — Spring Boot Filter

现有 Controller 的 `@RequestMapping` 都是 `/api/xxx`，无需改造。新增一个 Spring Boot Filter 剥离前缀后转发到内部 Controller：

```java
@Component
public class ApiPrefixStripFilter implements Filter {
    public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain) {
        HttpServletRequest r = (HttpServletRequest) req;
        String newUri = stripPrefix(r.getRequestURI());
        if (!newUri.equals(r.getRequestURI())) {
            chain.doFilter(new UriRewriteHttpServletRequestWrapper(r, newUri), res);
        } else {
            chain.doFilter(r, res);
        }
    }

    private String stripPrefix(String uri) {
        if (uri.startsWith("/api/mini/")) return "/api" + uri.substring("/api/mini".length());
        if (uri.startsWith("/api/web/"))  return "/api" + uri.substring("/api/web".length());
        if (uri.startsWith("/api/open/")) return "/openapi" + uri.substring("/api/open".length());
        if (uri.startsWith("/api/app/"))  return "/api" + uri.substring("/api/app".length());
        return uri;
    }
}

// UriRewriteHttpServletRequestWrapper — 自定义 Wrapper
// 重写 getRequestURI / getServletPath / getPathInfo
// 使 DispatcherServlet 按剥离后的路径匹配内部 Controller
```

**优势：**
- Controller 层零改动
- 新增一个端只需加一条 `if`
- 各端的限流/白名单/IP 策略天然通过前缀区分
- 不依赖 Nginx 配置

### 4.3 统一请求头

| Header | 值 | 用途 |
|--------|-----|------|
| `Authorization` | `Bearer <jwt>` | JWT 认证 |
| `X-App-Type` | `mini-program` | 源端标识（可选） |
| `X-Account-Id` | `<accountId>` | 当前选中的闲鱼账号 |

> 响应格式保持现有 `ApiResponse<T>`（code/message/data），无需后端改造。

---

## 五、API 清单

所有路径统一带 `/api/mini/` 前缀。对应后端 Controller 路径为原 `/api/xxx`（由 Filter 剥离）。

### 5.1 P0 — MVP 必做

| 方法 | 小程序路径 | 目标模块 | 说明 |
|------|-----------|---------|------|
| POST | /api/mini/auth/login | 认证 | 用户名密码登录 |
| GET | /api/mini/auth/profile | 认证 | 获取个人资料 |
| PUT | /api/mini/auth/password | 认证 | 修改密码 |
| GET | /api/mini/monitor/dashboard | 仪表盘 | 核心 KPI 概览 |
| GET | /api/mini/accounts | 账号 | 账号列表 |
| POST | /api/mini/accounts | 账号 | Cookie 登录 |
| PUT | /api/mini/accounts/{id}/status | 账号 | 切换状态 |
| DELETE | /api/mini/accounts/{id} | 账号 | 删除 |
| GET | /api/mini/products | 商品 | 商品列表 |
| GET | /api/mini/products/{id} | 商品 | 商品详情 |
| POST | /api/mini/products | 商品 | 创建商品 |
| POST | /api/mini/products/sync | 商品 | 同步闲鱼商品 |
| POST | /api/mini/products/upload | 商品 | 上传图片/视频 |
| GET | /api/mini/products/category-tree | 商品 | 分类树 |
| POST | /api/mini/products/{id}/shelf-on | 商品 | 上架 |
| POST | /api/mini/products/{id}/shelf-off | 商品 | 下架 |
| GET | /api/mini/local-products | 商品 | 本地商品列表 |
| POST | /api/mini/local-products | 商品 | 新建本地商品 |
| GET | /api/mini/messages/sessions | 消息 | 会话列表 |
| POST | /api/mini/messages/send | 消息 | 发送消息 |
| GET | /api/mini/messages/history | 消息 | 历史消息 |
| POST | /api/mini/messages/sync | 消息 | 同步消息(MTOP) |
| WS | /ws/messages | 消息 | 实时推送 |
| GET | /api/mini/orders | 订单 | 订单列表 |
| POST | /api/mini/orders/{id}/delivery | 订单 | 发货 |
| GET | /api/mini/notify/messages | 通知 | 站内通知列表 |
| GET | /api/mini/notify/messages/unread-count | 通知 | 未读数量 |

### 5.2 P1 — 第二阶段

| 方法 | 小程序路径 | 说明 |
|------|-----------|------|
| GET | /api/mini/rules | 规则列表 |
| POST | /api/mini/rules | 创建规则 |
| PUT | /api/mini/rules/{id} | 更新规则 |
| DELETE | /api/mini/rules/{id} | 删除规则 |
| POST | /api/mini/rules/test | 测试规则匹配 |
| POST | /api/mini/products/polish | 单商品擦亮 |
| POST | /api/mini/products/polish/batch | 批量擦亮 |
| PUT | /api/mini/products/{id}/price | 改价 |
| GET | /api/mini/wallet/{accountId} | 钱包概览 |
| GET | /api/mini/wallet/{accountId}/transactions | 交易记录 |
| POST | /api/mini/ai/chat/test | AI 对话测试 |
| POST | /api/mini/ai/demo/generate-title | AI 标题优化 |
| GET | /api/mini/ai/cs/sessions | AI 客服会话 |

### 5.3 P2 — 第三阶段

| 方法 | 小程序路径 | 说明 |
|------|-----------|------|
| GET | /api/mini/collect | 收藏列表 |
| POST | /api/mini/collect/sync | 同步闲鱼收藏 |
| GET | /api/mini/reply-logs | 自动回复日志 |
| GET | /api/mini/reviews | 评价列表 |
| POST | /api/mini/reviews/refunds | 申请退款 |
| GET | /api/mini/market/trend/{keyword} | 趋势数据 |
| GET | /api/mini/market/latest/{keyword} | 最新数据 |
| GET | /api/mini/buyer/list | 买家列表 |
| POST | /api/mini/buyer/{buyerId}/tag | 添加标签 |
| GET | /api/mini/ai/ops/batch-create/progress | 批量上品进度 |

### 5.4 P3 — 高级/运维

| 模块 | 关键接口 |
|------|---------|
| AI 厂商/模型 | `GET /api/mini/ai/providers`、`POST /api/mini/ai/models` |
| 通知配置 | 通道/模板/订阅/投递日志/每日摘要 |
| 虚拟发货 | 任务列表/触发发货/卡密池/全局配置 |
| 市场情报 | 关键词/爬虫/价格分布/卖家画像 |
| 买家画像 | 列表/详情/标签/备注 |
| 熔断器 | 列表/重置/全局重置 |
| 代理管理 | 状态/配置/健康检查 |
| 审计日志 | 操作记录查询 |
| 网盘/OpenList | OAuth/文件管理/存储挂载 |
| Chrome 配置 | 探测/保存/下载 |

---

## 六、页面规划

### 6.1 P0 — Phase 1 页面

| 页面 | 路由 | 能力 |
|------|------|------|
| 登录页 | `/pages/login/index` | 用户名密码登录（纯文本表单） |
| 首页/仪表盘 | `/pages/index/index` | KPI 卡片 + 账号健康列表 + 下拉刷新 + skeleton |
| 账号列表 | `/pages/accounts/list` | 分页 + Cookie 粘贴 |
| 账号详情 | `/pages/accounts/detail` | 资料 + 状态切换 + 同步 |
| 商品列表 | `/pages/products/list` | 搜索 + 筛选 + Tab（闲鱼/本地） |
| 商品详情 | `/pages/products/detail` | 图片轮播 + 库存/价格/擦亮 + 上下架 |
| 商品发布 | `/pages/products/publish` | 图片选择 + 分类 + 文案输入 |
| 消息会话 | `/pages/messages/index` | 会话列表 + 未读红点 + 下拉刷新 |
| 聊天窗口 | `/pages/messages/chat` | 聊天流 + 发送文本/图片 + WebSocket 实时更新 |
| 订单列表 | `/pages/orders/list` | 买入/卖出 Tab + 搜索 + 下拉加载 |
| 订单详情 | `/pages/orders/detail` | 订单信息 + 填写物流单号发货 |
| 个人中心 | `/pages/profile/index` | 昵称/邮箱/手机 + 修改密码 + 退出登录 |

### 6.2 P1 — Phase 2 页面

| 页面 | 路由 | 能力 |
|------|------|------|
| 关键词规则列表 | `/pages/rules/list` | 创建/编辑/开关/测试 |
| 规则编辑 | `/pages/rules/edit` | 关键词/匹配类型/回复内容 |
| 钱包资产 | `/pages/wallet/index` | 余额/冻结/可提现/交易记录/同步 |
| AI 对话测试 | `/pages/ai/chat` | 模型选择 + 简洁对话 |
| 自动回复日志 | `/pages/reply-logs/index` | 按账号/类型/命中状态查询 |

### 6.3 P2 — Phase 3 页面

| 页面 | 路由 | 能力 |
|------|------|------|
| 评价与退款 | `/pages/reviews/index` | 评价列表 + 发表评价 + 退款申请 |
| 收藏关注 | `/pages/collect/list` | 商品/用户/店铺收藏 + 同步 |
| AI 厂商/模型 | `/pages/ai/providers` | 厂商 + 模型管理 |
| AI 运营 | `/pages/ai-ops/index` | 批量上品 + 周报 |
| AI 客服 | `/pages/ai-cs/index` | 知识库 + 议价记录 |
| 监控任务 | `/pages/tasks/index` | 定时任务 CRUD |

### 6.4 小程序 vs Web 裁剪

| Web 功能 | 小程序支持 | 说明 |
|---------|-----------|------|
| 仪表盘（完整 ECharts） | ✅ 简化版 | 顶部 KPI 卡片 + 1-2 个趋势图 |
| 批量擦亮/CSV 导入/大盘实时监控 | ❌ 不做 | 保留在 Web |
| Chrome 配置/OpenList | ❌ 暂不做 | 桌面运维能力 |
| 通知通道配置 | ⚠️ 仅查看 | 复杂配置放 Web |
| 商品详情跳转 Web 链接 | ✅ | 深度页面兜底 |

---

## 七、组件设计

### 7.1 设计 Token

```scss
$brand-primary: #4f46e5;        // 品牌主色
$brand-secondary: #7c3aed;      // 次要强调
$accent-cyan: #22d3ee;          // 数据可视化辅助色
$bg-page: #f5f5f7;
$bg-card: #ffffff;
$text-primary: #111827;
$text-secondary: #6b7280;
$danger: #ef4444;
$warning: #f59e0b;
$success: #22c55e;
$border-color: #e5e7eb;
$card-radius: 16rpx;
$card-shadow: 0 2rpx 12rpx rgba(0,0,0,0.04);
$btn-gradient: linear-gradient(135deg, $brand-primary, $brand-secondary);
```

### 7.2 Element Plus → 小程序映射

| Web Element Plus | 小程序等价 |
|-----------------|-----------|
| `<el-card>` | `<view class="mp-card">` |
| `<el-button type="primary">` | `<button class="btn-primary">` （CSS 渐变背景） |
| `<el-tag>` | `<view class="tag tag-violet">` |
| `<el-table>` | `<scroll-view>` + `<view class="list-item">` |
| `<el-pagination>` | 上拉加载更多（不暴露数字分页） |
| `<ElMessageBox.confirm>` | `uni.showModal()` |
| `<ElMessage>` | `uni.showToast({ icon: 'none' })` |
| `<el-tabs>` | 自定义横向 scroll-view Tab |
| `<el-input>` | `<input v-model="val">` |

### 7.3 核心业务组件

| 组件 | Props | 作用 |
|------|-------|------|
| `StatCard` | label/value/unit/trend/trendDir | KPI 卡片 |
| `ProductCard` | product/compact | 商品卡片（紧凑/卡片两模式） |
| `ChatBubble` | message/showTime | 聊天气泡 |
| `AccountSwitcher` | accounts/current (v-model) | 账号切换下拉 |
| `ConfirmDialog` | visible/title/content/confirm/cancel | 统一确认弹窗 |
| `EmptyState` | text/actionText/@action | 空状态 |
| `PaginationHint` | status | 上拉加载提示 |

### 7.4 字体

| 用途 | 字号 (rpx) | 行高 | 字重 |
|------|-----------|------|------|
| 页面标题 | 36 | 48rpx | 600 |
| 卡片标题 | 28 | 40rpx | 500 |
| 正文 | 28 | 40rpx | 400 |
| 辅助文字 | 24 | 36rpx | 400 |
| 极小标注 | 22 | 32rpx | 400 |

---

## 八、状态管理与数据流

### 8.1 Store 模块

| Module | 职责 |
|--------|------|
| `auth` | Token、JWT Response、个人资料、登录/登出 |
| `account` | 当前选中的闲鱼账号（影响 `X-Account-Id` header） |
| `dashboard` | KPI + 趋势数据（30s TTL 刷新） |
| `message` | 会话列表 + 未读数 + WebSocket 连接状态 |
| `product` | 商品列表缓存（分页惰性加载） |

### 8.2 API 请求流程

```
View → Store Action → ApiClient
  ├─ 拼接 URL + query params
  ├─ Authorization: Bearer <token>
  ├─ X-Account-Id: <currentAccountId>   (如已选中账号)
  ├─ X-App-Type: mini-program
  ├─ uni.request()
  ├─ response interceptor:
  │   ├─ code === 0 → return data
  │   ├─ 401 → clear token → redirect login
  │   ├─ network error → retry once
  │   └─ other → throw formatted error
  └─ View renders
```

### 8.3 持久化

| Key | 值 | TTL | 清理时机 |
|-----|-----|-----|---------|
| `aiyudb_token` | JWT string | 24h | 登出 / 401 |
| `aiyudb_profile` | User object | 24h | 与 token 同步 |
| `aiyudb_accountId` | number | 永久 | 用户手动切换 |
| `aiyudb_lastPushToken` | platform push token | 1h | push token 变化时 |

### 8.4 异常处理

| HTTP Status | 处理 |
|-------------|------|
| 200 + code=0 | 正常渲染 |
| 200 + code≠0 | Toast + 可选详情 |
| 401 | 清除 token → 跳转登录页 |
| 403 | Toast "权限不足" |
| 500 | Toast "服务异常，请稍后重试" |
| Network Error | 底部固定提示条 + 监听网络状态 |

### 8.5 WebSocket 实时消息

```
URL: wss://<host>:8080/ws/messages?token=<jwt>&accountId=<xxx>

Messages:
  ├─ NEW_MESSAGE           → append to current session
  ├─ SESSION_UPDATE        → refresh session list count
  └─ HEARTBEAT_ACK         → no-op

Heartbeat: ping every 15s, auto-reconnect exponential backoff

Fallback: HTTP long-polling when WebSocket unsupported by platform
```

---

## 九、跨平台差异

### 9.1 条件编译

```vue
<!-- #ifdef MP-WEIXIN -->
<button open-type="getPhoneNumber" bindgetphonenumber="onGetPhone">微信登录</button>
<!-- #endif -->
<!-- #ifdef MP-ALIPAY -->
<button @click="alipayLogin">支付宝登录</button>
<!-- #endif -->
<!-- #ifndef MP-WEIXIN || MP-ALIPAY -->
<!-- 其他平台通用逻辑 -->
<!-- #endif -->
```

### 9.2 平台特性对照

| 能力 | 微信 | 支付宝 | 抖音 | 百度 | QQ | 快手 | 飞书 | 钉钉 | 美团 |
|------|------|--------|------|------|----|------|------|------|------|
| 快捷登录 | ✅ | ⚠️ | ✅ | ⚠️ | ⚠️ | ⚠️ | ⚠️ | ✅ | ⚠️ |
| 分享 | ✅ | ⚠️ | ✅ | ⚠️ | ⚠️ | ⚠️ | ❌ | ❌ | ❌ |
| 支付 | ✅ | ⚠️ | ✅ | ❌ | ❌ | ✅ | ❌ | ❌ | ✅ |
| WebSocket | ✅ | ⚠️ | ✅ | ❌ | ❌ | ✅ | ❌ | ✅ | ❌ |
| 推送 | ✅ | ❌ | ✅ | ✅ | ❌ | ✅ | ✅ | ✅ | ✅ |

> **MVP 策略：所有平台仅使用标准登录（用户名密码），不做平台内登录。**
> 这样避免大量条件编译，快速出 MVP。后续再做平台特性增强。

### 9.3 关键兼容注意事项

| 限制 | 影响 | 解决方案 |
|------|------|---------|
| 部分平台无 WebSocket | 实时消息不可用 | HTTP long-polling fallback |
| 百度要求 sjs 替代 wxs | 数据格式化 | 服务端预格式化 |
| 支付宝不支持 CSS var() | 样式失效 | uni.scss 编译为具体值 |
| 微信小程序 HTTPS 强制 | 本地开发不便 | 用微信开发者工具调试 + Nginx TLS |
| 各平台 Canvas 差异大 | 图表渲染 | MVP 用 CSS 圆环；后期按平台接入图表库 |

---

## 十、安全设计

| 措施 | 说明 |
|------|------|
| HTTPS 强制 | 后端 TLS + Nginx termination，小程序必须 HTTPS |
| JWT 认证 | 复用现有体系，小程序端不新增 OAuth |
| Cookie AES-256 加密 | 后端已实现，不动 |
| Token 有效期 | 24h，与现有一致 |
| 请求限速 | 同 Token 每分钟 60 次，防止滥用 |
| IP 白名单 | 按前缀 `/api/mini/` 区分 |
| 敏感字段脱敏 | userId/nickname 按需脱敏 |

---

## 十一、部署方案

### 11.1 开发环境

| 环境 | 地址 | 说明 |
|------|------|------|
| 后端开发 | `http://localhost:8080` | 现有 Spring Boot |
| 小程序 H5 预览 | `http://localhost:5170` | uni-app Vite dev server |

### 11.2 生产部署架构

```
┌──────────────┐
│  小程序客户端  │  各平台自有分发 CDN
└──────────────┘
      │ HTTPS
      ▼
┌──────────────┐
│  Nginx       │  TLS termination + 静态资源
│  /api/mini/  │  前缀路由到后端
│  /api/web/   │
│  /ws/        │  WebSocket 长连接
└──────────────┘
      │
      ▼
┌──────────────┐
│  Spring Boot │  ApiPrefixStripFilter 剥离前缀
│  JAR        │  JwtAuthenticationFilter 鉴权
│             │  Controller 层零改动
└──────────────┘
      │
      ▼
┌──────────────┐
│  SQLite / MySQL │
└──────────────┘
```

### 11.3 多平台 CI/CD

```bash
# CI Pipeline: 提交后构建 → 测试 → 提审
1. npm install --registry npmmirror
2. npm run build:mp-weixin        # 微信
3. npm run build:mp-alipay        # 支付宝
4. npm run build:mp-toutiao       # 抖音
5. [后续平台...]
6. 各平台 CLI 上传 或 手动上传开发版
7. 提审 → 等待审核通过 → 发布
```

### 11.4 发布节奏

| 阶段 | 交付物 | 周期 |
|------|--------|------|
| **Week 1** | 脚手架 + 登录 + TabBar + API 层 + 路由守卫 | 基础设施 |
| **Week 2-3** | 首页 + 账号 + 商品 + 消息 + 订单 + 个人中心 | MVP 微信提审 |
| **Week 4-5** | 规则 + 钱包 + AI + 通知 + 商品发布 | 支付宝 + 抖音 |
| **Week 6-7** | 收藏 + 评价 + 市场 + 买家 + 物流发货 | 百度 + QQ + 快手 |
| **Week 8+** | 高级功能 + 飞书 + 钉钉 + 美团 | 企业/本地场景 |

**建议先上微信 → 支付宝 → 抖音三平台，其余六平台后续逐步覆盖。**

### 11.5 平台审核常见驳回原因

| 平台 | 常见问题 | 应对 |
|------|---------|------|
| 微信 | 涉营销/诱导分享 | 避免敏感词，明确经营范围 |
| 支付宝 | 缺少隐私协议 | 完善协议和授权说明 |
| 抖音 | 类目不符 | 选择正确类目 |
| 百度/QQ/快手 | 功能不完整 | 完善所有必填项 |
| 飞书/钉钉 | 企业认证缺失 | 完成企业认证 |

---

## 十二、风险与待决

| 编号 | 问题 | 等级 | 建议 |
|------|------|------|------|
| R1 | 后端 CORS 配置，小程序直连可能被拦截 | 🔴 | MVP 启动前补充 CORS |
| R2 | 各平台要求 HTTPS | 🔴 | 配 SSL + Nginx |
| R3 | 各平台上传/支付 API 差异大 | 🟡 | 初期只用标准登录 + uni.uploadFile |
| R4 | 部分平台不支持 WebSocket | 🟡 | 必须实现 HTTP long-polling fallback |
| R5 | 图表在各平台兼容性不一致 | 🟡 | MVP 用 CSS 圆环，后期按需接入 |
| R6 | 9 平台同时上线成本高 | 🟢 | 先三平台验证，再逐步扩展 |

---

## 十三、文件索引

| 文件 | 说明 |
|------|------|
| `README.md`（本文档） | 完整方案总览：产品/架构/API/页面/组件/安全/部署 |
| `api-spec.md` | API 规范全文：全部端点路径、参数、响应示例、优先级 |
