package cn.net.rjnetwork.riskbird.api;

import cn.net.rjnetwork.riskbird.model.RiskbirdCompany;
import cn.net.rjnetwork.riskbird.model.RiskbirdSearchResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * RiskbirdParser 解析逻辑单元测试（纯逻辑，无浏览器依赖）。
 */
class RiskbirdParserTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Test
    void parseCompanies_fromListArray() throws Exception {
        String raw = """
                {"list":[
                  {"name":"测试科技有限公司","entid":"abc123","credit_code":"91110000MA1234567X",
                   "legal_person":"张三","reg_status":"存续","reg_capital":"1000万"}
                ]}
                """;
        List<RiskbirdCompany> companies = RiskbirdParser.parseCompanies(JSON.readTree(raw));
        assertEquals(1, companies.size());
        RiskbirdCompany c = companies.get(0);
        assertEquals("测试科技有限公司", c.getName());
        assertEquals("abc123", c.getEntId());
        assertEquals("91110000MA1234567X", c.getCreditCode());
        assertEquals("张三", c.getLegalPerson());
        assertEquals("存续", c.getStatus());
        assertEquals("1000万", c.getRegisteredCapital());
    }

    @Test
    void parseCompanies_fromNestedData() throws Exception {
        String raw = """
                {"data":{"result":[
                  {"companyName":"北京示例公司","ent_id":"ent-001","establishDate":"2020-01-01"}
                ]}}
                """;
        List<RiskbirdCompany> companies = RiskbirdParser.parseCompanies(JSON.readTree(raw));
        assertEquals(1, companies.size());
        assertEquals("北京示例公司", companies.get(0).getName());
        assertEquals("ent-001", companies.get(0).getEntId());
        assertEquals("2020-01-01", companies.get(0).getEstablishDate());
    }

    @Test
    void parseCompanies_fromRawArray() throws Exception {
        String raw = """
                [{"name":"A公司"},{"name":"B公司"}]
                """;
        List<RiskbirdCompany> companies = RiskbirdParser.parseCompanies(JSON.readTree(raw));
        assertEquals(2, companies.size());
        assertEquals("A公司", companies.get(0).getName());
    }

    @Test
    void parseCompanies_nullAndMalformed() {
        assertTrue(RiskbirdParser.parseCompanies(null).isEmpty());
        assertTrue(RiskbirdParser.parseCompanies(JSON.nullNode()).isEmpty());
        JsonNode wrongShape = JSON.createObjectNode().put("foo", "bar");
        assertTrue(RiskbirdParser.parseCompanies(wrongShape).isEmpty());
    }

    @Test
    void parseCompany_blankTextReturnsNull() throws Exception {
        String raw = """
                {"name":"   ","creditCode":"   "}
                """;
        RiskbirdCompany c = RiskbirdParser.parseCompany(JSON.readTree(raw));
        assertNull(c.getName());
        assertNull(c.getCreditCode());
    }

    @Test
    void parseCompany_chineseKeys() throws Exception {
        String raw = """
                {"企业名称":"示例集团","法定代表人":"李四","注册资本":"5000万元","企业状态":"存续"}
                """;
        RiskbirdCompany c = RiskbirdParser.parseCompany(JSON.readTree(raw));
        assertEquals("示例集团", c.getName());
        assertEquals("李四", c.getLegalPerson());
        assertEquals("5000万元", c.getRegisteredCapital());
        assertEquals("存续", c.getStatus());
    }

    @Test
    void buildSearchResult_totalFromField() throws Exception {
        String raw = """
                {"total": 88, "list":[{"name":"A"},{"name":"B"}]}
                """;
        RiskbirdSearchResult result = RiskbirdParser.buildSearchResult("测试", JSON.readTree(raw), "api");
        assertTrue(result.isSuccess());
        assertEquals("测试", result.getKeyword());
        assertEquals(2, result.getCompanies().size());
        assertEquals(88, result.getTotal());
        assertEquals("api", result.getChannel());
    }

    @Test
    void buildSearchResult_totalFallsBackToListSize() throws Exception {
        RiskbirdSearchResult result = RiskbirdParser.buildSearchResult("测试", null, "dom");
        assertTrue(result.isSuccess());
        assertEquals(0, result.getCompanies().size());
        assertEquals(0, result.getTotal());
        assertEquals("dom", result.getChannel());
    }

    @Test
    void parse_jsonString() {
        JsonNode node = RiskbirdParser.parse("{\"name\":\"x\"}");
        assertNotNull(node);
        assertEquals("x", node.path("name").asText());
        assertNull(RiskbirdParser.parse("{broken json"));
    }
}
