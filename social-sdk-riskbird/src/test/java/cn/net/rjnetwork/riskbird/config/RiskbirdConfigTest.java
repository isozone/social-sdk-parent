package cn.net.rjnetwork.riskbird.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * RiskbirdConfig 配置与 QueryType 枚举单元测试（纯逻辑）。
 */
class RiskbirdConfigTest {

    @Test
    void queryType_pathAndLabel() {
        assertEquals("company", RiskbirdConfig.QueryType.COMPANY.path);
        assertEquals("查公司", RiskbirdConfig.QueryType.COMPANY.label);

        assertEquals("boss", RiskbirdConfig.QueryType.BOSS.path);
        assertEquals("查老板", RiskbirdConfig.QueryType.BOSS.label);

        assertEquals("risk", RiskbirdConfig.QueryType.RISK.path);
        assertEquals("查风险", RiskbirdConfig.QueryType.RISK.label);

        assertEquals("wenshu", RiskbirdConfig.QueryType.WENSHU.path);
        assertEquals("查文书", RiskbirdConfig.QueryType.WENSHU.label);

        assertEquals("relation", RiskbirdConfig.QueryType.RELATION.path);
        assertEquals("查关系", RiskbirdConfig.QueryType.RELATION.label);
    }

    @Test
    void queryType_hasAllEntries() {
        assertEquals(7, RiskbirdConfig.QueryType.values().length);
    }

    @Test
    void queryType_extendedCapabilities() {
        assertEquals("trademark", RiskbirdConfig.QueryType.TRADEMARK.path);
        assertEquals("商标", RiskbirdConfig.QueryType.TRADEMARK.label);

        assertEquals("person", RiskbirdConfig.QueryType.PERSON.path);
        assertEquals("人员", RiskbirdConfig.QueryType.PERSON.label);
    }

    @Test
    void defaults_scanLoginCalibrated() {
        RiskbirdConfig config = new RiskbirdConfig();
        // 登录选择器按真实站点实测校准（2026-08-03）
        assertEquals("[class*=userinfo-auth-btn]", config.getLoginEntrySelector());
        assertEquals(".popover-btn", config.getLoginTryButtonSelector());
        assertEquals("img.xs-login-left-qrcode", config.getQrImageSelector());
        assertEquals("https://www.riskbird.com/", config.getLoginSuccessUrl());
    }

    @Test
    void defaults_queryCalibrated() {
        RiskbirdConfig config = new RiskbirdConfig();
        assertEquals(RiskbirdConfig.QueryType.COMPANY, config.getDefaultQueryType());
        assertEquals("hybrid", config.getQueryChannel());
        // 未登录拦截特征文本（真实站点实测）
        assertEquals("查询次数已达到上限", config.getLoginRequiredText());
    }

    @Test
    void defaults_containerIsolation() {
        RiskbirdConfig config = new RiskbirdConfig();
        assertTrue(config.isPerAccountContainer());
        assertEquals(3, config.getMaxActiveProfiles());
    }

    @Test
    void defaults_siteEndpoints() {
        RiskbirdConfig config = new RiskbirdConfig();
        assertEquals("https://www.riskbird.com/", config.getBaseUrl());
        assertTrue(config.getEntUrlTemplate().contains("{company}"));
        assertTrue(config.getEntUrlTemplate().contains("{entid}"));
    }
}
