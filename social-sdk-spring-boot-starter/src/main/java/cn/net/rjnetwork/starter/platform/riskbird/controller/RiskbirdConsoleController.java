package cn.net.rjnetwork.starter.platform.riskbird.controller;

import cn.net.rjnetwork.starter.platform.common.exception.StarterApiException;
import cn.net.rjnetwork.starter.platform.common.model.StarterApiResponse;
import cn.net.rjnetwork.starter.platform.riskbird.dto.RiskbirdBizQueryRequest;
import cn.net.rjnetwork.starter.platform.riskbird.dto.RiskbirdLoginRequest;
import cn.net.rjnetwork.starter.platform.riskbird.dto.RiskbirdSearchRequest;
import cn.net.rjnetwork.starter.platform.riskbird.service.RiskbirdConsoleService;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * riskbird 企业信息查询 REST 接口（每账号独立 Chrome 容器，多账户隔离）。
 *
 * <p>启用：{@code social-sdk.console.riskbird.enabled=true}，并引入 social-sdk-riskbird + social-sdk-chrome。
 *
 * <p>典型流程：
 * <ol>
 *   <li>{@code POST /api/social-sdk/riskbird/accounts/{id}/login}（注入已登录 Cookie）</li>
 *   <li>{@code POST /api/social-sdk/riskbird/accounts/{id}/search}（查公司/商标/模糊查询等）</li>
 *   <li>{@code POST /api/social-sdk/riskbird/accounts/{id}/persons}（人员查询）</li>
 *   <li>{@code GET /api/social-sdk/riskbird/accounts/{id}/company/{name}}（企业详情，含电话/邮箱）</li>
 * </ol>
 */
@RestController
@CrossOrigin(origins = "*")
@RequestMapping("/api/social-sdk/riskbird")
public class RiskbirdConsoleController {

    private final RiskbirdConsoleService service;

    public RiskbirdConsoleController(RiskbirdConsoleService service) {
        this.service = service;
    }

    @GetMapping("/health")
    public StarterApiResponse<?> health() {
        return StarterApiResponse.ok(service.health());
    }

    // ==================== 登录 ====================

    /** Cookie 登录（免扫码，需 token + userinfo 的已登录 Cookie）。 */
    @PostMapping("/accounts/{accountId}/login")
    public StarterApiResponse<?> login(@PathVariable("accountId") long accountId,
                                       @RequestBody(required = false) RiskbirdLoginRequest request) {
        try {
            String cookie = request != null ? request.getCookieHeader() : null;
            return StarterApiResponse.ok(service.loginWithCookie(accountId, cookie));
        } catch (Exception e) {
            throw new StarterApiException("RISKBIRD_LOGIN_FAILED", e.getMessage(), e);
        }
    }

    /** 当前登录态。 */
    @GetMapping("/accounts/{accountId}/logged-in")
    public StarterApiResponse<?> isLoggedIn(@PathVariable("accountId") long accountId) {
        try {
            return StarterApiResponse.ok(service.isLoggedIn(accountId));
        } catch (Exception e) {
            throw new StarterApiException("RISKBIRD_CHECK_FAILED", e.getMessage(), e);
        }
    }

    /**
     * 获取扫码登录二维码（返回二维码图片 URL，形如 /riskbird-api/createQrCode?uuid=xxx）。
     * 业务侧展示二维码给用户扫码后，轮询调用 {@code GET /accounts/{id}/logged-in} 判断登录态，
     * 登录成功后调用 {@code GET /accounts/{id}/cookie} 提取登录态 Cookie。
     */
    @GetMapping("/accounts/{accountId}/qr")
    public StarterApiResponse<?> prepareQrLogin(@PathVariable("accountId") long accountId) {
        try {
            return StarterApiResponse.ok(service.prepareQrLogin(accountId));
        } catch (Exception e) {
            throw new StarterApiException("RISKBIRD_QR_FAILED", e.getMessage(), e);
        }
    }

    /** 等待扫码完成（最长 120s），返回登录结果。 */
    @PostMapping("/accounts/{accountId}/qr/wait")
    public StarterApiResponse<?> waitQrLogin(@PathVariable("accountId") long accountId) {
        try {
            return StarterApiResponse.ok(service.waitQrLogin(accountId));
        } catch (Exception e) {
            throw new StarterApiException("RISKBIRD_QR_WAIT_FAILED", e.getMessage(), e);
        }
    }

    /** 提取登录态 Cookie（持久化复用）。 */
    @GetMapping("/accounts/{accountId}/cookie")
    public StarterApiResponse<?> extractCookie(@PathVariable("accountId") long accountId) {
        try {
            return StarterApiResponse.ok(service.extractCookieHeader(accountId));
        } catch (Exception e) {
            throw new StarterApiException("RISKBIRD_COOKIE_FAILED", e.getMessage(), e);
        }
    }

    // ==================== 查询 / 检索 / 搜索 ====================

    /**
     * 搜索（统一入口，queryType 决定能力）：
     * company=查公司（含模糊查询）/ boss=查老板 / risk=查风险 / wenshu=查文书 /
     * relation=查关系 / trademark=商标查询 / person=人员查询。
     */
    @PostMapping("/accounts/{accountId}/search")
    public StarterApiResponse<?> search(@PathVariable("accountId") long accountId,
                                        @RequestBody RiskbirdSearchRequest request) {
        try {
            String type = request.getQueryType();
            if ("person".equalsIgnoreCase(type)) {
                // 人员查询走人员专用接口（含关联企业/合作伙伴）
                return StarterApiResponse.ok(service.searchPersons(
                        accountId, request.getKeyword(),
                        request.getMaxResults() == null ? 10 : request.getMaxResults()));
            }
            return StarterApiResponse.ok(service.search(
                    accountId, request.getKeyword(), type,
                    request.getPage() == null ? 1 : request.getPage()));
        } catch (Exception e) {
            throw new StarterApiException("RISKBIRD_SEARCH_FAILED", e.getMessage(), e);
        }
    }

    /** 人员查询（人员电话查找前置能力）。 */
    @PostMapping("/accounts/{accountId}/persons")
    public StarterApiResponse<?> searchPersons(@PathVariable("accountId") long accountId,
                                               @RequestBody RiskbirdSearchRequest request) {
        try {
            return StarterApiResponse.ok(service.searchPersons(
                    accountId, request.getKeyword(),
                    request.getMaxResults() == null ? 10 : request.getMaxResults()));
        } catch (Exception e) {
            throw new StarterApiException("RISKBIRD_PERSONS_FAILED", e.getMessage(), e);
        }
    }

    /** 商标查询（需具体商标名称）。 */
    @PostMapping("/accounts/{accountId}/trademark")
    public StarterApiResponse<?> searchTrademark(@PathVariable("accountId") long accountId,
                                                 @RequestBody RiskbirdSearchRequest request) {
        try {
            return StarterApiResponse.ok(service.search(
                    accountId, request.getKeyword(), "trademark",
                    request.getPage() == null ? 1 : request.getPage()));
        } catch (Exception e) {
            throw new StarterApiException("RISKBIRD_TRADEMARK_FAILED", e.getMessage(), e);
        }
    }

    // ==================== 企业详情 ====================

    /** 企业详情（按名称，自动搜索拿 entId；返回电话/邮箱/法人/信用代码等）。 */
    @GetMapping("/accounts/{accountId}/company")
    public StarterApiResponse<?> queryCompany(@PathVariable("accountId") long accountId,
                                              @RequestParam("name") String name,
                                              @RequestParam(value = "entId", required = false) String entId) {
        try {
            if (entId != null && !entId.isBlank()) {
                return StarterApiResponse.ok(service.queryCompany(accountId, name, entId));
            }
            return StarterApiResponse.ok(service.queryCompany(accountId, name));
        } catch (Exception e) {
            throw new StarterApiException("RISKBIRD_COMPANY_FAILED", e.getMessage(), e);
        }
    }

    // ==================== 业务组合接口 ====================

    /**
     * 业务组合查询：按省份/地市/行业筛选检索某类企业 → 逐条取详情（电话/邮箱）→ 查商标/软著。
     * 对应业务：「按省份/地市检索有电话的某类企业，再查其商标和软著」。
     *
     * <p>body 示例：{@code {"keyword":"软件","province":"浙江","city":"杭州","maxCompanies":5,"onlyWithPhone":true}}
     */
    @PostMapping("/accounts/{accountId}/biz/companies-with-ip")
    public StarterApiResponse<?> queryCompaniesWithIp(@PathVariable("accountId") long accountId,
                                                      @RequestBody(required = false) RiskbirdBizQueryRequest request) {
        try {
            return StarterApiResponse.ok(service.queryCompaniesWithIp(accountId, request));
        } catch (Exception e) {
            throw new StarterApiException("RISKBIRD_BIZ_QUERY_FAILED", e.getMessage(), e);
        }
    }

    // ==================== 会话管理 ====================

    /** 关闭账号会话（释放容器资源）。 */
    @DeleteMapping("/accounts/{accountId}")
    public StarterApiResponse<?> closeAccount(@PathVariable("accountId") long accountId) {
        service.closeAccount(accountId);
        return StarterApiResponse.ok("closed");
    }
}
