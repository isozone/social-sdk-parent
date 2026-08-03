package cn.net.rjnetwork.riskbird.api;

import cn.net.rjnetwork.riskbird.model.RiskbirdCompany;
import cn.net.rjnetwork.riskbird.model.RiskbirdSearchResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;

/**
 * Riskbird 数据解析器（纯逻辑，无浏览器依赖，可单元测试）。
 *
 * <p>把查询渠道返回的原始数据（API JSON / DOM 提取的 JSON 结构）解析为业务模型。
 * 字段名做了常见变体兼容（snake_case / camelCase / 中文键），解析失败不抛异常、返回空字段。
 */
public final class RiskbirdParser {

    private static final ObjectMapper JSON = new ObjectMapper();

    private RiskbirdParser() {}

    /**
     * 解析企业列表（搜索/检索结果的 companies 数组）。
     *
     * @param raw 可能是 {@code {list:[...]}} / {@code {data:{list:[...]}}} / 直接数组
     */
    public static List<RiskbirdCompany> parseCompanies(JsonNode raw) {
        List<RiskbirdCompany> out = new ArrayList<>();
        if (raw == null || raw.isNull() || raw.isMissingNode()) {
            return out;
        }
        JsonNode arr = raw;
        if (raw.isObject()) {
            arr = firstArray(raw, "list", "data", "result", "results", "items", "rows");
        }
        if (arr == null || !arr.isArray()) {
            return out;
        }
        for (JsonNode item : arr) {
            if (item.isObject()) {
                out.add(parseCompany(item));
            }
        }
        return out;
    }

    /**
     * 解析单个企业对象（详情页 / 列表项）。
     * 字段名做常见变体兼容。
     */
    public static RiskbirdCompany parseCompany(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return RiskbirdCompany.builder().build();
        }
        return RiskbirdCompany.builder()
                .name(text(node, "name", "companyName", "company_name", "entName", "ent_name", "企业名称"))
                .entId(text(node, "entid", "entId", "ent_id", "id", "companyId"))
                .creditCode(text(node, "creditCode", "credit_code", "creditcode", "uscc", "统一社会信用代码"))
                .legalPerson(text(node, "legalPerson", "legal_person", "legalMan", "legal_man", "法定代表人"))
                .establishDate(text(node, "establishDate", "establish_date", "estiblishTime", "成立日期"))
                .registeredCapital(text(node, "registeredCapital", "registered_capital", "regCap", "reg_capital", "注册资本"))
                .status(text(node, "status", "regStatus", "reg_status", "企业状态"))
                .address(text(node, "address", "regAddr", "reg_addr", "注册地址"))
                .businessScope(text(node, "businessScope", "business_scope", "scope", "经营范围"))
                .industry(text(node, "industry", "industryType", "所属行业"))
                .detailUrl(text(node, "detailUrl", "detail_url", "url"))
                .build();
    }

    /**
     * 组装搜索结果对象。
     *
     * @param keyword 搜索关键词
     * @param raw     原始响应（可为 null）
     * @param channel 渠道（api / dom）
     */
    public static RiskbirdSearchResult buildSearchResult(String keyword, JsonNode raw, String channel) {
        List<RiskbirdCompany> companies = parseCompanies(raw);
        RiskbirdSearchResult result = new RiskbirdSearchResult();
        result.setKeyword(keyword);
        result.setCompanies(companies);
        result.setTotal(totalOf(raw, companies.size()));
        result.setPage(1);
        result.setSuccess(true);
        result.setChannel(channel);
        return result;
    }

    /** 从响应中提取总数（兼容常见字段；取不到则用列表长度）。 */
    private static Integer totalOf(JsonNode raw, int fallback) {
        if (raw != null && raw.isObject()) {
            JsonNode t = firstValue(raw, "total", "totalCount", "total_count", "count");
            if (t != null && t.isNumber()) {
                return t.asInt();
            }
        }
        return fallback;
    }

    private static JsonNode firstArray(JsonNode node, String... keys) {
        for (String k : keys) {
            JsonNode v = node.get(k);
            if (v != null && v.isArray()) {
                return v;
            }
            if (v != null && v.isObject()) {
                JsonNode nested = firstArray(v, keys);
                if (nested != null) {
                    return nested;
                }
            }
        }
        return null;
    }

    private static JsonNode firstValue(JsonNode node, String... keys) {
        for (String k : keys) {
            JsonNode v = node.get(k);
            if (v != null && !v.isNull()) {
                return v;
            }
        }
        return null;
    }

    private static String text(JsonNode node, String... keys) {
        JsonNode v = firstValue(node, keys);
        if (v == null || v.isNull() || v.isMissingNode()) {
            return null;
        }
        if (v.isTextual()) {
            String s = v.asText();
            return s.isBlank() ? null : s.trim();
        }
        return v.asText();
    }

    /** 便捷：把 JSON 字符串解析为节点（失败返回 null）。 */
    public static JsonNode parse(String json) {
        try {
            return JSON.readTree(json);
        } catch (Exception e) {
            return null;
        }
    }
}
