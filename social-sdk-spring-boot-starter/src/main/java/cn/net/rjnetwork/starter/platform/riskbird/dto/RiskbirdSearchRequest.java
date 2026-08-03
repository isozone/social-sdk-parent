package cn.net.rjnetwork.starter.platform.riskbird.dto;

/**
 * 搜索请求（查公司/查老板/查风险/查文书/查关系/商标/人员）。
 */
public class RiskbirdSearchRequest {

    /** 关键词（查公司/商标用企业或商标名，查老板/人员用人名）。 */
    private String keyword;

    /** 查询类型：company / boss / risk / wenshu / relation / trademark / person（默认 company）。 */
    private String queryType = "company";

    /** 页码（1 起，默认 1）。 */
    private Integer page = 1;

    /** 人员查询最多返回条数（仅 queryType=person 时生效，默认 10）。 */
    private Integer maxResults = 10;

    public String getKeyword() {
        return keyword;
    }

    public void setKeyword(String keyword) {
        this.keyword = keyword;
    }

    public String getQueryType() {
        return queryType;
    }

    public void setQueryType(String queryType) {
        this.queryType = queryType;
    }

    public Integer getPage() {
        return page;
    }

    public void setPage(Integer page) {
        this.page = page;
    }

    public Integer getMaxResults() {
        return maxResults;
    }

    public void setMaxResults(Integer maxResults) {
        this.maxResults = maxResults;
    }
}
