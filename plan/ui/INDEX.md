# AI 鱼多宝 — 小程序端 UI 设计稿索引

> 基于 `plan/mini/README.md` 设计方案与 `plan/mini/api-spec.md` API 规范，按模块/xxx.html 命名输出高质量页面设计稿。
> **最后更新：2026-07-25 · 共 33 个模块页面 + 26 个旧版编号参考页**

## 设计基准

- **品牌色**：主紫 `#4f46e5` → 紫罗兰 `#7c3aed` → 青 `#22d3ee`
- **渐变**：`linear-gradient(135deg, #4f46e5, #7c3aed)`
- **字号体系**：36 / 32 / 28 / 24 / 22 rpx
- **圆角**：12 / 16 / 20 / 24 px
- **阴影**：柔和多层阴影 + 品牌色光晕
- **背景**：`#f5f5f7` 灰白底 + 白色卡片 + 玻璃拟态（深色页）
- **设备框架**：iPhone 14 Pro 393×852px，Dynamic Island，safe-area TabBar

## P0 — MVP 必做页面

| 模块 | 路由 | 设计稿 | 优先级 |
|------|------|--------|--------|
| 登录 | `/pages/login/index` | [`auth/login.html`](auth/login.html) | P0 |
| 仪表盘 | `/pages/index/index` | [`dashboard/index.html`](dashboard/index.html) | P0 |
| 消息会话 | `/packages/messages/index` | [`messages/session-list.html`](messages/session-list.html) | P0 |
| 聊天窗口 | `/packages/messages/chat` | [`messages/chat.html`](messages/chat.html) | P0 |
| 商品列表 | `/packages/products/list` | [`products/list.html`](products/list.html) | P0 |
| 商品详情 | `/packages/products/detail` | [`products/detail.html`](products/detail.html) | P0 |
| 商品发布 | `/packages/products/publish` | [`products/publish.html`](products/publish.html) | P0 |
| 账号列表 | `/packages/accounts/list` | [`accounts/list.html`](accounts/list.html) | P0 |
| 账号详情 | `/packages/accounts/detail` | [`accounts/detail.html`](accounts/detail.html) | P0 |
| 添加账号 | `/packages/accounts/add` | [`accounts/add.html`](accounts/add.html) | P0 |
| 订单列表 | `/pages/orders/list` | [`orders/list.html`](orders/list.html) | P0 |
| 订单详情 | `/pages/orders/detail` | [`orders/detail.html`](orders/detail.html) | P0 |
| 个人中心 | `/pages/profile/index` | [`profile/index.html`](profile/index.html) | P0 |
| 通知中心 | `/packages/notify/index` | [`notifications/index.html`](notifications/index.html) | P0 |

## P1 — 第二阶段

| 模块 | 路由 | 设计稿 | 优先级 |
|------|------|--------|--------|
| 规则列表 | `/packages/rules/list` | [`rules/list.html`](rules/list.html) | P1 |
| 规则编辑 | `/packages/rules/edit` | [`rules/edit.html`](rules/edit.html) | P1 |
| 钱包资产 | `/packages/wallet/index` | [`wallet/index.html`](wallet/index.html) | P1 |
| AI 对话测试 | `/packages/ai/chat` | [`ai/chat.html`](ai/chat.html) | P1 |
| 自动回复日志 | `/packages/replyLogs` | [`other/reply-logs.html`](other/reply-logs.html) | P1 |

## P2 — 第三阶段

| 模块 | 路由 | 设计稿 | 优先级 |
|------|------|--------|--------|
| 评价退款 | `/packages/reviews/index` | [`reviews/index.html`](reviews/index.html) | P2 |
| 收藏关注 | `/packages/collect/list` | [`other/collect.html`](other/collect.html) | P2 |
| AI 厂商/模型 | `/packages/ai/providers` | [`ai/providers.html`](ai/providers.html) | P2 |
| AI 客服 | `/packages/ai/cs` | [`ai/cs.html`](ai/cs.html) | P2 |
| AI 运营 | `/packages/ai/ops` | [`ai/ops.html`](ai/ops.html) | P2 |
| AI 管理中心 | — | [`ai/index.html`](ai/index.html) | P2 |
| 市场情报 | `/packages/market/index` | [`market/index.html`](market/index.html) | P2 |
| 买家画像 | `/packages/buyer/detail` | [`buyer/detail.html`](buyer/detail.html) | P2 |

## P3 — 高级运维 & 增强功能

| 模块 | 路由 | 设计稿 | 优先级 |
|------|------|--------|--------|
| 虚拟发货 | `/packages/virtualShip/tasks` | [`virtual-ship/tasks.html`](virtual-ship/tasks.html) | P3 |
| 熔断器 | `/packages/monitor/circuit-breaker` | [`circuit-breaker/index.html`](circuit-breaker/index.html) | P3 |
| 审计日志 | `/packages/monitor/audit-logs` | [`audit-logs/index.html`](audit-logs/index.html) | P3 |
| 代理管理 | `/packages/monitor/proxy-management` | [`proxy-management/index.html`](proxy-management/index.html) | P3 |
| 网盘存储 | `/packages/storage/cloud-storage` | [`cloud-storage/index.html`](cloud-storage/index.html) | P3 |
| Chrome 配置 | `/packages/storage/chrome-config` | [`chrome-config/index.html`](chrome-config/index.html) | P3 |

## TabBar

四个固定入口：**首页** 📊 · **消息** 💬 · **商品** 🛒 · **我的** 👤

## 设计特色

- 每个页面包含完整手机外框（iPhone 393×852）、Dynamic Island、状态栏
- 首页/消息/商品/个人中心带 TabBar，导航页带返回箭头
- 使用品牌紫→紫罗兰渐变 Hero 区 + 浮动光球动画
- 玻璃拟态卡片（dark mode 页）、圆角 20-24px 卡片系统
- 统一的状态标签、空状态、下拉刷新骨架屏模式
- 所有按钮使用 `cubic-bezier(.16,1,.3,1)` 缓动，符合移动端交互直觉
- 响应式考虑：rpx 单位体系适配不同屏幕尺寸

## 参考文件

- 旧版编号页面 `01-login.html` ~ `26-tasks.html`：早期迭代版本，保留供对比参考
- 当前推荐采用模块命名目录下的新设计稿（`module/page.html`），质量更高且结构统一
