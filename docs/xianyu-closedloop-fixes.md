# 闲鱼链路 · 业务闭环缺口排查与修复

## 排查结论

对 `social-sdk-xianyu-manager`（461 个 Java 文件）与前端 `social-sdk-xianyu-manager-web`（26 个 API 文件）做了闭环排查：

- **前端↔后端路由层完整**：26 个前端 API 文件全部能匹配到后端 `@*Mapping` 控制器，无 404、无 controller 桩（Explore 全量核对）。
- 真正的“业务未闭环”集中在**通知子系统**：事件要么发了没人收，要么该发却没发。

根因：`NotificationService.onEvent` 通过 `NotifyScenario.fromName(event.getScenario())` 解析场景，
**枚举里没有的场景直接 `return` 静默丢弃**；而另一些枚举场景虽然写了模板，对应业务流却从未 `publishEvent`。

## 已修复（8 个场景级闭环）

### A. 已发布但枚举缺失 → 此前被静默丢弃
文件：`notify/NotifyScenario.java`

| 场景 | 来源 | 影响 |
|---|---|---|
| `ACCOUNT_QR_LOGIN_REQUIRED` | `LoginRenewService:223` | 登录续期需扫码时**完全不告警**，人工交接断链 |
| `ACCOUNT_LOGIN_FAILED` | `LoginRenewService:324` | 登录重试耗尽不通知，自动化静默中断 |
| `ORDER_UPDATED` | `OrderSyncService:508`（仅标题变更） | 订单信息更新无通知 |
| `AUTO_SHIP_DUMMY_FAILED` | `AutoShipService:314` | 平台侧确认发货失败无人知晓，需后台补 |

### B. 枚举有模板但流程从不发布 → 补齐发布点
| 场景 | 补齐位置 | 说明 |
|---|---|---|
| `MONITOR_MATCH` | `monitor/service/MonitorTaskRunner.java` | 监控命中即推送；默认开启（仅显式关闭才跳过）；新增按 `taskId+itemId` 去重，避免每 30s 重复插入与通知轰炸；枚举去重冷却 0→600s |
| `CIRCUIT_BREAKER_OPENED` | `circuit/CircuitBreakerService.java` | 仅在「未开→开」跃迁时发布，避免持续失败反复轰炸；新增 `AccountMapper` 解析账号名 |
| `CIRCUIT_BREAKER_RECOVERED` | 同上 | 「开/半开→关」跃迁时发布 |
| `MARKET_PRICE_DROP` | `market/service/PriceHistoryService.java` | `computeDailyStat` 与上一日均价比较，下跌则发布 |

## 编译验证
```
mvn -o -q -pl social-sdk-xianyu-manager -am compile -DskipTests -Dskip.frontend=true   # 通过
```

## 仍未修（需额外子系统，列为后续，未擅自实现）
- `ACCOUNT_HEALTH_DROP`：代码无“健康分”计算（`AccountHealthTask` 只校验登录态），无数据来源 → 需新建评分子系统。
- `AI_CS_DEAL_CLOSED` / `AI_CS_DEAL_LOST`：`AiCsEngine` 仅 prompt 文本，无成交/丢单决策点 → 需补决策逻辑。
- 网盘三方 provider（`BaiduNetdiskProvider`/`QuarkNetdiskProvider` 返回 `"TODO"`）：已知 D1 P0，被 OpenList 旁路，需外部 API 密钥 + 重构 `CloudStorageService` 按 provider 派发。

## 使用提示
- 监控匹配通知：任务开启「匹配通知」即生效（默认即开启，仅显式关闭才不推）。
- 外部通道（Bark/Telegram/邮件等）仍需在「通知订阅」里为对应场景配置启用订阅，否则只落站内收件箱 + 实时广播。
