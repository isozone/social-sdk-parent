package cn.net.rjnetwork.riskbird.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 企业信息（riskbird 查公司 / 查老板 / 查风险等详情页的核心数据载体）。
 *
 * <p>字段按查询渠道（API 响应 / DOM 解析）均可填充；未知字段为 null，由调用方按需取用。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RiskbirdCompany {

    /** 企业名称。 */
    private String name;

    /** 企业 ID（详情页 entid 参数）。 */
    private String entId;

    /** 统一社会信用代码。 */
    private String creditCode;

    /** 法定代表人。 */
    private String legalPerson;

    /** 成立日期。 */
    private String establishDate;

    /** 注册资本。 */
    private String registeredCapital;

    /** 企业状态（存续/注销/吊销等）。 */
    private String status;

    /** 注册地址。 */
    private String address;

    /** 经营范围。 */
    private String businessScope;

    /** 所属行业。 */
    private String industry;

    /** 联系电话（详情页文本解析，如 13482393468）。 */
    private String phone;

    /** 联系邮箱（详情页文本解析，如 gsll@service.alibaba.com）。 */
    private String email;

    /** 详情页 URL。 */
    private String detailUrl;

    /** 查询渠道：api / dom（调试与统计用）。 */
    private String channel;
}
