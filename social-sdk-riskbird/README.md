# Social SDK Riskbird

基于 [riskbird.com](https://www.riskbird.com/)（风鸟企业信息查询平台）的企业信息 SDK：
封装**扫码登录 / Cookie 登录 / 查询 / 检索 / 搜索 / 商标 / 人员**能力，**每账号独立 Chrome 容器**天然多账户隔离，
并提供 **spring-boot-starter REST 接口**。

## 真实站点结构（2026-08-03 真实 Chrome 联调校准）

| 能力 | 真实实现（实测） | 封装位置 |
|---|---|---|
| 登录 | 首页「登录/注册」（`[class*=userinfo-auth-btn]`）→ 弹窗「登录试试」（`.popover-btn`，Element UI popover 内容常驻 DOM 但容器隐藏，用 JS click 触发）→ 扫码二维码（`img.xs-login-left-qrcode`，src=`/riskbird-api/createQrCode?uuid=`）；**无账号密码表单** | `ChromeRiskbirdDriver#prepareQrLogin/waitQrLogin` |
| Cookie 登录 | 注入已登录 Cookie（`token` JWT + `userinfo`（含 userId）即视为登录态） | `RiskbirdApiFacade#loginWithCookie` |
| 查公司 | `GET /search/company?keyword=&timestamp=`（实测命中 8763 条，API 通道返回 entId） | `QueryType.COMPANY` |
| 公司名模糊查询 | 查公司天然支持模糊/部分匹配（实测短词「阿里」命中 8763 条） | `QueryType.COMPANY` |
| **带省份/地市筛选的企业检索** | 筛选条件是搜索页「省份地区」筛选项的**页面交互**（URL 参数 `province=` 实测无效）；封装「导航 → 点击筛选项（省份→地市→行业→状态）→ 等待总数 → 解析结果」 | `RiskbirdSearchFilter` + `search(type, keyword, page, filter)` |
| **企业知识产权（商标/软著/专利）** | **无独立 URL**（`/search/trademark|software|softcopyright` 均 0 条）；数据在企业详情页「知识产权」tab（实测「知识产权\|999+」）；封装「进详情页 → 点击 tab → 按区块解析商标/软著/专利」 | `RiskbirdIntellectualProperty` + `queryIntellectualProperty(name, entId)` |
| 查老板 | `GET /search/boss?keyword=&timestamp=`（**搜人名**，如「马云」） | `QueryType.BOSS` |
| 人员查询 | `GET /search/person?keyword=`（接口 `POST /riskbird-api/api/v1/persons/search` 返回 JSON；实测「马云 共关联 39 家企业 + 合作伙伴」） | `QueryType.PERSON` / `searchPersons` |
| 人员电话查找 | 企业详情页文本解析电话/邮箱（实测「电话：13482393468」「邮箱：gsll@service.alibaba.com」） | `RiskbirdCompany#phone/email` |
| 商标查询 | `GET /search/trademark?keyword=`（路由实测有效，需具体商标名） | `QueryType.TRADEMARK` / `searchTrademark` |
| 查风险/文书/关系 | `GET /search/risk|wenshu|relation?keyword=` | `QueryType.RISK/WENSHU/RELATION` |
| 企业详情 | `GET /ent/{公司名}.html?entid={entid}`；**entid 必传**（来自搜索结果），字段从页面文本解析（法人/信用代码/成立/资本/地址/状态/电话/邮箱） | `RiskbirdApiFacade#queryCompany` |
| 未登录拦截 | 页面出现「查询次数已达到上限」→ 返回明确错误 | `RiskbirdConfig#loginRequiredText` |
| 额度受限 | 详情页出现「今日查询额度已用完」→ 返回明确错误 | `RiskbirdConfig#quotaExhaustedText` |

> 搜索页为 Nuxt SSR：页面 HTML 内嵌 `__NUXT_DATA__` JSON，接口请求为 `XHR/Fetch` 类型；API 通道只解析 XHR/Fetch 响应，DOM 兜底以页面「为您找到 N 条」为真实性判断（避免把导航卡片误当结果）。

## 模块结构

```
cn.net.rjnetwork.riskbird
├── config/RiskbirdConfig        # 站点 URL、登录/查询选择器、查询通道、容器隔离配置
├── model/                       # RiskbirdCompany / SearchResult / Credentials / LoginResult
├── api/
│   ├── RiskbirdPageDriver       # 驱动抽象接口（可 mock）
│   ├── ChromeRiskbirdDriver     # 默认实现：每账号独立容器 + 混合双通道（API 优先/DOM 兜底）
│   ├── RiskbirdParser           # 纯解析层（字段变体兼容，可单测）
│   └── RiskbirdApiFacade        # 登录/查询/检索/搜索门面
└── service/RiskbirdSdk         # 多账户隔离门面（accountId → 独立会话）
```

## 业务场景（底层能力组合示例）

> SDK 提供**底层能力**，业务逻辑由上层封装。以「按省份/地市检索有电话的某类企业，再查其商标和软著」为例：

```java
// 1. 按省份/地市 + 行业筛选检索企业列表（底层能力①：带筛选检索）
RiskbirdSearchResult list = acc.api().search(QueryType.COMPANY, "软件", 1,
        RiskbirdSearchFilter.builder().province("浙江").city("杭州").industry("软件和信息技术服务业").build());

for (RiskbirdCompany c : list.getCompanies()) {
    // 2. 逐条取企业详情（底层能力②：电话/邮箱字段）
    RiskbirdCompany detail = acc.api().queryCompany(c.getName(), c.getEntId());
    if (detail.getPhone() == null) {
        continue; // 业务过滤：只要「有电话」的企业
    }

    // 3. 查该企业的商标与软著（底层能力③：知识产权查询）
    RiskbirdIntellectualProperty ip = acc.api().queryIntellectualProperty(c.getName(), c.getEntId());
    if (ip.getTrademarks().isEmpty() && ip.getSoftCopyrights().isEmpty()) {
        continue; // 业务过滤：只要「有商标或软著」的企业
    }
    System.out.println(detail.getName() + " | " + detail.getPhone()
            + " | 商标=" + ip.getTrademarks().size() + " | 软著=" + ip.getSoftCopyrights().size());
}
```

对应底层能力清单：

| 底层能力 | API | 说明 |
|---|---|---|
| ① 带省份/地市/行业筛选的企业检索 | `search(type, keyword, page, filter)` | 筛选走页面交互（URL 参数无效） |
| ② 企业详情（含电话/邮箱） | `queryCompany(name, entId)` | 详情页文本解析，entId 来自搜索 |
| ③ 企业知识产权（商标/软著/专利） | `queryIntellectualProperty(name, entId)` | 详情页「知识产权」tab 解析 |
| ④ 公司名模糊查询 | `search(COMPANY, keyword, page)` | 短词即命中 |
| ⑤ 人员查询 | `searchPersons(name, maxResults)` | 关联企业数/合作伙伴 |

## 快速开始

```java
// Spring 环境（ChromeBrowser 已注入）
RiskbirdSdk sdk = new RiskbirdSdk(new RiskbirdConfig(), chromeBrowser);

// 每账号独立容器（隔离单位 = accountId）
RiskbirdSdk.RiskbirdAccount acc = sdk.account(1001L);

// ===== 登录（三选一）=====

// 1a. Cookie 登录（最快，需已登录 Cookie：token + userinfo）
acc.api().loginWithCookie("token=...; userinfo=%7B%22userId%22...%7D");

// 1b. 扫码登录：返回二维码 URL，展示后轮询
String qrUrl = acc.api().prepareQrLogin();                  // /riskbird-api/createQrCode?uuid=...
RiskbirdLoginResult login = acc.api().waitQrLogin(null);    // 等待扫码（默认 120s）

// 1c. 账号密码登录（真实站点无密码表单，返回提示引导扫码）
acc.api().loginWithPassword("user", "pass");

// ===== 查询（七类能力）=====

RiskbirdSearchResult r = acc.api().search("阿里巴巴", 1);                        // 查公司（默认，支持模糊）
RiskbirdSearchResult b = acc.api().search(QueryType.BOSS, "马云", 1);            // 查老板（搜人名）
RiskbirdSearchResult risk = acc.api().search(QueryType.RISK, "阿里巴巴", 1);     // 查风险
RiskbirdSearchResult tm = acc.api().searchTrademark("阿里巴巴", 1);              // 商标查询（独立路由）
RiskbirdSearchResult fuzzy = acc.api().search("阿里", 1);                        // 公司名模糊查询（短词）

// ===== 带省份/地市筛选的企业检索（底层能力，业务按需组合）=====

// 按省份/地市/行业/状态筛选检索企业列表（筛选为页面交互，URL 参数无效）
RiskbirdSearchResult zhejiang = acc.api().search(QueryType.COMPANY, "软件", 1,
        RiskbirdSearchFilter.builder().province("浙江").city("杭州").industry("软件和信息技术服务业").build());

// 逐条取企业详情（含电话/邮箱，为「有电话的企业列表」业务提供数据）
for (RiskbirdCompany c : zhejiang.getCompanies()) {
    RiskbirdCompany detail = acc.api().queryCompany(c.getName(), c.getEntId());
    System.out.println(detail.getName() + " | " + detail.getPhone() + " | " + detail.getEmail());
}

// ===== 企业知识产权（商标/软著/专利）=====

RiskbirdIntellectualProperty ip = acc.api().queryIntellectualProperty("阿里巴巴（中国）有限公司", "v3r2xik2nNx");
System.out.println("商标: " + ip.getTrademarks());
System.out.println("软著: " + ip.getSoftCopyrights());
System.out.println("专利: " + ip.getPatents());

// 人员查询（人员电话查找前置能力：关联企业数/地区/合作伙伴）
List<RiskbirdPerson> persons = acc.api().searchPersons("马云", 10);

// 检索（多页聚合）
RiskbirdSearchResult all = acc.api().retrieve("阿里巴巴", 3);

// 企业详情（内部先搜索拿 entId，再访问详情页文本解析；含电话/邮箱）
RiskbirdCompany c = acc.api().queryCompany("北京石头世纪科技股份有限公司");
// 或直接传已知 entId
RiskbirdCompany c2 = acc.api().queryCompany("阿里巴巴（中国）有限公司", "v3r2xik2nNx");
System.out.println(c2.getPhone() + " / " + c2.getEmail());   // 13482393468 / gsll@service.alibaba.com

// 登录态持久化复用（Cookie 落库/换容器用）
String cookie = acc.api().extractCookieHeader();
```

## Spring Boot Starter REST 接口

启用（`application.yml`）：

```yaml
social-sdk:
  console:
    riskbird:
      enabled: true              # 开启 riskbird REST
      query-channel: hybrid       # api / dom / hybrid
      per-account-container: true # 每账号独立 Chrome 容器
      default-query-type: company # company/boss/risk/wenshu/relation/trademark/person
      cookie-header: ""           # 可选预置登录态 Cookie（免扫码）
```

接口（统一前缀 `/api/social-sdk/riskbird`，响应 `StarterApiResponse`）：

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/health` | 健康检查 |
| POST | `/accounts/{id}/login` | Cookie 登录（body: `{accountId, cookieHeader}`） |
| GET | `/accounts/{id}/logged-in` | 当前登录态 |
| GET | `/accounts/{id}/cookie` | 提取登录态 Cookie |
| POST | `/accounts/{id}/search` | 统一搜索（body: `{keyword, queryType, page, maxResults}`；queryType=person 走人员查询） |
| POST | `/accounts/{id}/persons` | 人员查询（`{keyword, maxResults}`） |
| POST | `/accounts/{id}/trademark` | 商标查询（`{keyword, page}`） |
| GET | `/accounts/{id}/company?name=&entId=` | 企业详情（含电话/邮箱） |
| POST | `/accounts/{id}/biz/companies-with-ip` | **业务组合**：按省份/地市/行业筛选检索企业 → 逐条取详情（电话）→ 查商标/软著/专利（body: `{keyword, province, city, industry, maxCompanies, onlyWithPhone}`） |
| DELETE | `/accounts/{id}` | 关闭账号会话 |

示例：

```bash
# 登录（注入已登录 Cookie）
curl -X POST localhost:8080/api/social-sdk/riskbird/accounts/1001/login \
  -H 'Content-Type: application/json' \
  -d '{"accountId":1001,"cookieHeader":"token=...; userinfo=..."}'

# 查公司（模糊）
curl -X POST localhost:8080/api/social-sdk/riskbird/accounts/1001/search \
  -H 'Content-Type: application/json' \
  -d '{"keyword":"阿里","queryType":"company","page":1}'

# 人员查询
curl -X POST localhost:8080/api/social-sdk/riskbird/accounts/1001/persons \
  -H 'Content-Type: application/json' -d '{"keyword":"马云","maxResults":10}'

# 企业详情（电话/邮箱）
curl 'localhost:8080/api/social-sdk/riskbird/accounts/1001/company?name=阿里巴巴（中国）有限公司'

# 业务组合：按省份/地市筛选某类企业 → 逐条取详情（电话）→ 查商标/软著/专利
curl -X POST localhost:8080/api/social-sdk/riskbird/accounts/1001/biz/companies-with-ip \
  -H 'Content-Type: application/json' \
  -d '{"keyword":"软件","province":"浙江","city":"杭州","industry":"软件和信息技术服务业","maxCompanies":5,"onlyWithPhone":true}'
```

## 测试

| 测试 | 说明 | 运行 |
|---|---|---|
| `RiskbirdParserTest`（9） | 解析逻辑（JSON 变体/中文键/容错） | 自动 |
| `RiskbirdConfigTest`（7） | 配置与 QueryType 枚举（含商标/人员扩展） | 自动 |
| `RiskbirdSdkTest`（10） | 多账户隔离 + 登录/查询流程（mock 驱动） | 自动 |
| `RiskbirdCookieLoginTest` | **真实联调**：Cookie 注入 → 查询 → 详情文本解析（实测通过：查公司 8763 条、详情法人/信用代码/资本/地址/电话全解析） | `mvn -pl social-sdk-riskbird test -Dtest=RiskbirdCookieLoginTest -Drb.cookie="..."` |
| `RiskbirdCapabilityProbeTest` | 新能力探测：商标/模糊/人员 URL 与结果结构（实测：person 接口 `POST /riskbird-api/api/v1/persons/search`） | 同上（加 `-Djunit.jupiter.conditions.deactivate='org.junit.*DisabledCondition'`） |
| `RiskbirdE2eTest` | 真实 Chrome：未登录拦截/详情页（自动）+ 扫码登录全链路（人工扫码） | 同上，去 `@Disabled` |
| `RiskbirdIntegrationTest` | 扫码登录 + 查询 + 详情 | 去 `@Disabled` 后手动运行 |
| `RiskbirdSiteProbeTest` / `RiskbirdLoginDebugTest` / `RiskbirdPostLoginProbeTest` / `RiskbirdCalibrationProbeTest` / `RiskbirdApiStructureProbeTest` | 站点结构探索 / 交互诊断 / 联调校准工具（非断言） | 手动运行 |
| `StarterGlobalExceptionHandlerTest` / `XianyuConsoleServiceTest` | starter 通用异常与 console 服务 | 自动（starter 模块） |

## 真实联调记录（2026-08-03）

- **isLoggedIn() 误判修复**：旧判定按「cookie 名含 uid」识别登录态，把设备标识 `app-uuid`（含 uid 子串）误判为登录 → 扫码未完成却报「登录成功」。修复：显式排除 `app-uuid/app-device/X-Canary-*` 设备与灰度标识，识别 `X-Canary-Reason=ANONYMOUS` 为未登录，真实登录态以 `token`(JWT)/`userinfo`/`passport` 为准。
- **详情解析改为文本布局**：详情页无标准 `tr/li` 结构，实测从 `innerText` 解析「法定代表人：蒋芳」「统一社会信用代码：91330100799655058B」等字段；且详情页必须带搜索结果中的 `entId` 才有数据。
- **DOM 兜底误报修复**：旧实现把页面导航卡片（如「18268185209个人中心」）当搜索结果；修复为以「为您找到 N 条」为真实性门槛，N=0 返回明确未命中。
- **getResponseBody 中断修复**：对已回收资源调用 `Network.getResponseBody` 会抛 `-32000`，改为安全读取 + 只处理 XHR/Fetch/Document 类型。

## 注意事项

- **站点以扫码登录为主**（微信/风鸟App），无账号密码表单；`loginWithPassword` 返回提示引导扫码。
- **免费账号/未登录查询受限**：搜索页「查询次数已达到上限」、详情页「今日查询额度已用完」；VIP 账号实测可正常查询（8763 条）。
- **查老板搜人名、查公司搜企业名**：五类查询的 URL 相同、关键词语义不同，按业务选 `QueryType`。
- 选择器与 URL 基于 2026-08-03 实测，站内改版时调整 `RiskbirdConfig` 即可。
- 每账号独立 Chrome 容器复用 `social-sdk-chrome`：独立 profile / 代理 / 指纹 / 端口。
