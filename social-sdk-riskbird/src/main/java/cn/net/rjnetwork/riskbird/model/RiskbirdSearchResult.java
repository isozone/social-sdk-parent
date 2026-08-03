package cn.net.rjnetwork.riskbird.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * 搜索结果（关键词搜索 / 检索的通用返回）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RiskbirdSearchResult {

    /** 搜索关键词。 */
    private String keyword;

    /** 命中的企业列表。 */
    @Builder.Default
    private List<RiskbirdCompany> companies = new ArrayList<>();

    /** 总命中数（站内显示的总数，可能大于当前列表）。 */
    private Integer total;

    /** 当前页码。 */
    private Integer page;

    /** 是否成功。 */
    private boolean success;

    /** 失败原因（success=false 时填充）。 */
    private String error;

    /** 查询渠道：api / dom。 */
    private String channel;
}
