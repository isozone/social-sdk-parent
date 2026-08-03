package cn.net.rjnetwork.starter.platform.riskbird.dto;

/**
 * 业务组合查询请求：按省份/地市检索某类企业，逐条取详情（电话），再查商标/软著。
 */
public class RiskbirdBizQueryRequest {

    /** 企业关键词（行业/类型描述，如「软件」）。 */
    private String keyword;

    /** 省份（如「浙江」）。 */
    private String province;

    /** 地市（如「杭州」）。 */
    private String city;

    /** 行业门类（如「软件和信息技术服务业」）。 */
    private String industry;

    /** 最多处理企业数（默认 5，逐条查详情+知识产权，耗时较长）。 */
    private Integer maxCompanies = 5;

    /** 只保留有电话的企业（默认 true）。 */
    private Boolean onlyWithPhone = true;

    public String getKeyword() {
        return keyword;
    }

    public void setKeyword(String keyword) {
        this.keyword = keyword;
    }

    public String getProvince() {
        return province;
    }

    public void setProvince(String province) {
        this.province = province;
    }

    public String getCity() {
        return city;
    }

    public void setCity(String city) {
        this.city = city;
    }

    public String getIndustry() {
        return industry;
    }

    public void setIndustry(String industry) {
        this.industry = industry;
    }

    public Integer getMaxCompanies() {
        return maxCompanies;
    }

    public void setMaxCompanies(Integer maxCompanies) {
        this.maxCompanies = maxCompanies;
    }

    public Boolean getOnlyWithPhone() {
        return onlyWithPhone;
    }

    public void setOnlyWithPhone(Boolean onlyWithPhone) {
        this.onlyWithPhone = onlyWithPhone;
    }
}
