package cn.net.rjnetwork.starter.platform.riskbird.service;

import cn.net.rjnetwork.riskbird.api.RiskbirdPageDriver;
import cn.net.rjnetwork.riskbird.config.RiskbirdConfig;
import cn.net.rjnetwork.riskbird.model.RiskbirdCompany;
import cn.net.rjnetwork.riskbird.model.RiskbirdLoginResult;
import cn.net.rjnetwork.riskbird.model.RiskbirdSearchResult;
import cn.net.rjnetwork.riskbird.service.RiskbirdSdk;
import cn.net.rjnetwork.starter.platform.riskbird.config.RiskbirdConsoleProperties;
import cn.net.rjnetwork.starter.platform.riskbird.dto.RiskbirdBizQueryRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * RiskbirdConsoleService 业务组合接口单元测试（mock driver，无浏览器依赖）。
 */
class RiskbirdConsoleServiceTest {

    private MockRiskbirdDriver driver;
    private RiskbirdConsoleService service;

    @BeforeEach
    void setUp() {
        driver = new MockRiskbirdDriver();
        RiskbirdConsoleProperties properties = new RiskbirdConsoleProperties();
        properties.setEnabled(true);
        // 注入 mock driver 工厂的 RiskbirdSdk
        RiskbirdSdk sdk = new RiskbirdSdk(new RiskbirdConfig(), (cfg, accountId) -> driver);
        service = new RiskbirdConsoleService(properties, sdk);
    }

    // ==================== 业务组合查询 ====================

    @Test
    void queryCompaniesWithIp_returnsPhoneAndIpPerCompany() throws Exception {
        // 2 家企业，均有电话
        driver.companies = List.of(
                company("杭州甲软件有限公司", "ent-a"),
                company("杭州乙软件有限公司", "ent-b"));
        driver.phones = Map.of(
                "杭州甲软件有限公司", "0571-12345678",
                "杭州乙软件有限公司", "0571-87654321");

        RiskbirdBizQueryRequest req = new RiskbirdBizQueryRequest();
        req.setKeyword("软件");
        req.setProvince("浙江");
        req.setCity("杭州");
        req.setMaxCompanies(10);

        List<Map<String, Object>> out = service.queryCompaniesWithIp(1001L, req);

        assertEquals(2, out.size());
        Map<String, Object> first = out.get(0);
        assertNotNull(first.get("phone"), "应包含电话");
        assertEquals("0571-12345678", first.get("phone"));
        assertNotNull(first.get("trademarks"));
        assertNotNull(first.get("softCopyrights"));
        // 企业详情对象
        assertNotNull(first.get("company"));
        assertTrue(first.get("company") instanceof RiskbirdCompany);
    }

    @Test
    void queryCompaniesWithIp_filtersCompaniesWithoutPhone() throws Exception {
        driver.companies = List.of(
                company("有电话公司", "ent-a"),
                company("无电话公司", "ent-b"));
        // 只有「有电话公司」有电话（无电话公司被 onlyWithPhone 过滤）
        driver.phones = Map.of("有电话公司", "0571-11111111");

        RiskbirdBizQueryRequest req = new RiskbirdBizQueryRequest();
        req.setKeyword("软件");
        req.setOnlyWithPhone(true);

        List<Map<String, Object>> out = service.queryCompaniesWithIp(1001L, req);

        assertEquals(1, out.size());
        assertEquals("0571-11111111", out.get(0).get("phone"));
    }

    @Test
    void queryCompaniesWithIp_respectsMaxCompanies() throws Exception {
        driver.companies = List.of(
                company("A公司", "ent-a"),
                company("B公司", "ent-b"),
                company("C公司", "ent-c"));
        driver.phones = Map.of(
                "A公司", "010-11111111",
                "B公司", "010-22222222",
                "C公司", "010-33333333");

        RiskbirdBizQueryRequest req = new RiskbirdBizQueryRequest();
        req.setKeyword("软件");
        req.setMaxCompanies(2);

        List<Map<String, Object>> out = service.queryCompaniesWithIp(1001L, req);

        assertEquals(2, out.size());
    }

    @Test
    void queryCompaniesWithIp_blankKeywordThrows() {
        RiskbirdBizQueryRequest req = new RiskbirdBizQueryRequest();
        req.setKeyword("  ");
        assertThrows(IllegalArgumentException.class, () -> service.queryCompaniesWithIp(1001L, req));
    }

    @Test
    void queryCompaniesWithIp_noResultsReturnsEmpty() throws Exception {
        driver.companies = List.of(); // 无结果
        RiskbirdBizQueryRequest req = new RiskbirdBizQueryRequest();
        req.setKeyword("软件");
        List<Map<String, Object>> out = service.queryCompaniesWithIp(1001L, req);
        assertTrue(out.isEmpty());
    }

    // ==================== 其他服务方法 ====================

    @Test
    void health_returnsEnabledFlag() {
        Map<String, Object> health = service.health();
        assertEquals("UP", health.get("status"));
        assertEquals(Boolean.TRUE, health.get("enabled"));
    }

    @Test
    void search_blankKeywordThrows() {
        assertThrows(IllegalArgumentException.class, () -> service.search(1001L, " ", "company", 1));
    }

    // ==================== mock 驱动 ====================

    /** 内存 mock 驱动：search 返回公司列表、queryCompany 返回电话、queryIntellectualProperty 返回空。 */
    static class MockRiskbirdDriver implements RiskbirdPageDriver {
        List<RiskbirdCompany> companies = List.of();
        Map<String, String> phones = Map.of();
        List<String> persons = List.of();

        @Override
        public RiskbirdLoginResult loginWithPassword(String username, String password) {
            return RiskbirdLoginResult.builder().success(true).build();
        }

        @Override
        public String prepareQrLogin() {
            return "data:image/png;base64,QR";
        }

        @Override
        public RiskbirdLoginResult waitQrLogin(String qrSession) {
            return RiskbirdLoginResult.builder().success(true).build();
        }

        @Override
        public RiskbirdLoginResult loginWithCookie(String cookieHeader) {
            return RiskbirdLoginResult.builder().success(true).build();
        }

        @Override
        public boolean isLoggedIn() {
            return true;
        }

        @Override
        public String extractCookieHeader() {
            return "token=mock";
        }

        @Override
        public RiskbirdSearchResult search(String keyword, int page)
                throws IOException, TimeoutException, InterruptedException {
            RiskbirdSearchResult result = new RiskbirdSearchResult();
            result.setKeyword(keyword);
            result.setSuccess(true);
            result.setCompanies(new ArrayList<>(companies));
            result.setTotal(companies.size());
            return result;
        }

        @Override
        public RiskbirdCompany queryCompany(String companyName)
                throws IOException, TimeoutException, InterruptedException {
            // 按 phones 映射返回电话（未配置则为 null，模拟无电话企业）
            RiskbirdCompany c = new RiskbirdCompany();
            c.setName(companyName);
            c.setEntId(entIdOf(companyName));
            c.setPhone(phones.get(companyName));
            return c;
        }

        /** 按名称查 entId（mock 内部映射，模拟搜索结果携带 entId）。 */
        private String entIdOf(String name) {
            for (RiskbirdCompany c : companies) {
                if (name.equals(c.getName())) {
                    return c.getEntId();
                }
            }
            return null;
        }

        @Override
        public RiskbirdSearchResult retrieve(String keyword, int maxPages)
                throws IOException, TimeoutException, InterruptedException {
            return search(keyword, 1);
        }

        @Override
        public void close() {
        }
    }

    private static RiskbirdCompany company(String name, String entId) {
        return RiskbirdCompany.builder().name(name).entId(entId).build();
    }
}
