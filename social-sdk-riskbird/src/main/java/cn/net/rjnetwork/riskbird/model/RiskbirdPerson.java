package cn.net.rjnetwork.riskbird.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * 人员信息（查老板 / 人员查询结果，如「马云 共关联 39 家企业」+ 合作伙伴）。
 *
 * <p>真实页面结构（2026-08-03 实测）：{@code /search/person?keyword=马云} 返回
 * 「马云 | 共关联 39 家企业 | 浙江（共 14 家）杭州君瀚股权投资合伙企业（有限合伙）等 |
 * 合作伙伴 金建杭 合作9次」。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RiskbirdPerson {

    /** 姓名。 */
    private String name;

    /** 关联企业总数（「共关联 N 家企业」）。 */
    private Integer companyCount;

    /** 关联企业地区分布摘要（如「浙江（共 14 家）…」）。 */
    private String regionSummary;

    /** 合作伙伴（姓名 + 合作次数）。 */
    @Builder.Default
    private List<String> partners = new ArrayList<>();

    /** 人员详情页 URL（如 /person/xxx.html）。 */
    private String detailUrl;

    /** 查询渠道：api / dom。 */
    private String channel;
}
