package cn.net.rjnetwork.riskbird.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 企业检索筛选条件（省份/地市/行业等）。
 *
 * <p>真实站点（2026-08-03 实测）：搜索页「省份地区」筛选项为页面交互（URL 参数 province= 无效），
 * 需在页面点击筛选项触发查询；本模型承载筛选条件，由驱动负责交互落地。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RiskbirdSearchFilter {

    /** 省份（如「浙江」）。 */
    private String province;

    /** 地市（如「杭州」，省份为空时忽略）。 */
    private String city;

    /** 行业门类（如「软件和信息技术服务业」）。 */
    private String industry;

    /** 企业状态（如「在营」/「注销」/「吊销」）。 */
    private String status;

    /** 是否有筛选条件。 */
    public boolean hasAny() {
        return isNotBlank(province) || isNotBlank(city) || isNotBlank(industry) || isNotBlank(status);
    }

    private static boolean isNotBlank(String s) {
        return s != null && !s.isBlank();
    }
}
