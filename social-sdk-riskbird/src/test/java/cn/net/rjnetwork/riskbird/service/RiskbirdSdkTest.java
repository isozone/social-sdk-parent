package cn.net.rjnetwork.riskbird.service;

import cn.net.rjnetwork.riskbird.api.RiskbirdPageDriver;
import cn.net.rjnetwork.riskbird.config.RiskbirdConfig;
import cn.net.rjnetwork.riskbird.model.RiskbirdCompany;
import cn.net.rjnetwork.riskbird.model.RiskbirdLoginResult;
import cn.net.rjnetwork.riskbird.model.RiskbirdSearchResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * RiskbirdSdk 多账户隔离 + 登录/查询流程单元测试。
 *
 * <p>使用内存 mock 驱动（{@link MockRiskbirdDriver}），不依赖真实浏览器/站点。
 */
class RiskbirdSdkTest {

    private RiskbirdSdk sdk;
    private MockRiskbirdDriverFactory factory;

    @BeforeEach
    void setUp() {
        factory = new MockRiskbirdDriverFactory();
        sdk = new RiskbirdSdk(new RiskbirdConfig(), factory);
    }

    // ==================== 多账户隔离 ====================

    @Test
    void account_sameIdReturnsSameInstance() {
        RiskbirdSdk.RiskbirdAccount a1 = sdk.account(1001L);
        RiskbirdSdk.RiskbirdAccount a2 = sdk.account(1001L);
        assertSame(a1, a2);
        assertEquals(1, sdk.accountCount());
    }

    @Test
    void account_differentIdsIsolated() {
        RiskbirdSdk.RiskbirdAccount a1 = sdk.account(1001L);
        RiskbirdSdk.RiskbirdAccount a2 = sdk.account(1002L);
        assertNotSame(a1, a2);
        assertEquals(2, sdk.accountCount());
        // 每个账号独立驱动实例
        assertEquals(2, factory.createdDrivers.size());
        assertNotSame(factory.createdDrivers.get(1001L), factory.createdDrivers.get(1002L));
    }

    @Test
    void account_queriesDoNotInterfere() throws Exception {
        // 账号 1 登录成功
        sdk.account(1001L).api().loginWithCookie("uid=111; token=t1");
        // 账号 2 未登录
        RiskbirdSdk.RiskbirdAccount acc2 = sdk.account(1002L);
        assertFalse(acc2.api().isLoggedIn());

        // 账号 1 搜索
        RiskbirdSearchResult r1 = sdk.account(1001L).api().search("甲公司", 1);
        assertEquals(1, r1.getCompanies().size());
        assertEquals("甲公司", r1.getCompanies().get(0).getName());

        // 账号 2 搜索不受影响
        RiskbirdSearchResult r2 = acc2.api().search("乙公司", 1);
        assertEquals(1, r2.getCompanies().size());
        assertEquals("乙公司", r2.getCompanies().get(0).getName());
    }

    // ==================== 登录 ====================

    @Test
    void login_withCookie_success() throws Exception {
        RiskbirdSdk.RiskbirdAccount acc = sdk.login(1001L, "uid=1; token=abc");
        assertTrue(acc.api().isLoggedIn());
        assertEquals("cookie", factory.createdDrivers.get(1001L).lastLoginMethod);
    }

    @Test
    void login_withPassword_success() throws Exception {
        RiskbirdSdk.RiskbirdAccount acc = sdk.login(1001L, "user1", "pass1");
        assertTrue(acc.api().isLoggedIn());
        assertEquals("password", factory.createdDrivers.get(1001L).lastLoginMethod);
    }

    @Test
    void login_failureThrows() {
        factory.failLogin = true;
        assertThrows(IllegalStateException.class, () -> sdk.login(1001L, "user1", "badpass"));
    }

    // ==================== 查询 / 检索 / 搜索 ====================

    @Test
    void search_delegatesToDriver() throws Exception {
        RiskbirdSearchResult result = sdk.account(1001L).api().search("查询词", 1);
        assertTrue(result.isSuccess());
        assertEquals("查询词", result.getKeyword());
        assertEquals(1, result.getCompanies().size());
    }

    @Test
    void queryCompany_delegatesToDriver() throws Exception {
        RiskbirdCompany company = sdk.account(1001L).api().queryCompany("某公司");
        assertEquals("某公司", company.getName());
        assertEquals("张三", company.getLegalPerson());
    }

    @Test
    void retrieve_aggregatesMultiplePages() throws Exception {
        factory.pageSize = 2; // 每页 2 条，共 5 条 → 3 页
        factory.totalItems = 5;
        RiskbirdSearchResult result = sdk.account(1001L).api().retrieve("聚合", 5);
        assertEquals(5, result.getCompanies().size());
    }

    @Test
    void closeAccount_removesInstance() {
        sdk.account(1001L);
        sdk.closeAccount(1001L);
        assertEquals(0, sdk.accountCount());
        // 重新获取是新实例
        RiskbirdSdk.RiskbirdAccount again = sdk.account(1001L);
        assertNotNull(again);
        assertEquals(1, sdk.accountCount());
    }

    // ==================== mock 驱动 ====================

    private static final class MockRiskbirdDriverFactory implements RiskbirdSdk.RiskbirdDriverFactory {
        final Map<Long, MockRiskbirdDriver> createdDrivers = new ConcurrentHashMap<>();
        boolean failLogin;
        int pageSize = 1;
        int totalItems = 1;

        @Override
        public RiskbirdPageDriver create(RiskbirdConfig config, long accountId) {
            MockRiskbirdDriver driver = new MockRiskbirdDriver(accountId, this);
            createdDrivers.put(accountId, driver);
            return driver;
        }
    }

    private static final class MockRiskbirdDriver implements RiskbirdPageDriver {
        final long accountId;
        final MockRiskbirdDriverFactory factory;
        boolean loggedIn;
        String lastLoginMethod;

        MockRiskbirdDriver(long accountId, MockRiskbirdDriverFactory factory) {
            this.accountId = accountId;
            this.factory = factory;
        }

        @Override
        public RiskbirdLoginResult loginWithPassword(String username, String password) {
            lastLoginMethod = "password";
            loggedIn = !factory.failLogin;
            return RiskbirdLoginResult.builder().success(loggedIn).accountId(accountId)
                    .message(loggedIn ? "ok" : "bad").build();
        }

        @Override
        public String prepareQrLogin() {
            return "data:image/png;base64,QR";
        }

        @Override
        public RiskbirdLoginResult waitQrLogin(String qrSession) {
            loggedIn = true;
            return RiskbirdLoginResult.builder().success(true).accountId(accountId).build();
        }

        @Override
        public RiskbirdLoginResult loginWithCookie(String cookieHeader) {
            lastLoginMethod = "cookie";
            loggedIn = true;
            return RiskbirdLoginResult.builder().success(true).accountId(accountId).build();
        }

        @Override
        public boolean isLoggedIn() {
            return loggedIn;
        }

        @Override
        public String extractCookieHeader() {
            return "uid=" + accountId;
        }

        @Override
        public RiskbirdSearchResult search(String keyword, int page) throws IOException, TimeoutException, InterruptedException {
            int start = (page - 1) * factory.pageSize;
            if (start >= factory.totalItems) {
                return RiskbirdSearchResult.builder().keyword(keyword).success(true).build();
            }
            RiskbirdSearchResult result = new RiskbirdSearchResult();
            result.setKeyword(keyword);
            result.setSuccess(true);
            int count = Math.min(factory.pageSize, factory.totalItems - start);
            for (int i = 0; i < count; i++) {
                result.getCompanies().add(RiskbirdCompany.builder()
                        .name(keyword).entId("ent-" + (start + i)).build());
            }
            return result;
        }

        @Override
        public RiskbirdCompany queryCompany(String companyName) {
            return RiskbirdCompany.builder().name(companyName).legalPerson("张三").build();
        }

        @Override
        public RiskbirdSearchResult retrieve(String keyword, int maxPages) throws IOException, TimeoutException, InterruptedException {
            RiskbirdSearchResult merged = new RiskbirdSearchResult();
            merged.setKeyword(keyword);
            merged.setSuccess(true);
            merged.setCompanies(new java.util.ArrayList<>(List.of()));
            for (int p = 1; p <= maxPages; p++) {
                RiskbirdSearchResult pageResult = search(keyword, p);
                merged.getCompanies().addAll(pageResult.getCompanies());
                if (pageResult.getCompanies().isEmpty()) {
                    break;
                }
            }
            return merged;
        }

        @Override
        public void close() {
        }
    }
}
