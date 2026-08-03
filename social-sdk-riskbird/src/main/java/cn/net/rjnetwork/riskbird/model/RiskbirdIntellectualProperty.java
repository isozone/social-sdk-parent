package cn.net.rjnetwork.riskbird.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * 企业知识产权（商标 / 软著 / 专利）。
 *
 * <p>真实站点（2026-08-03 实测）：无独立商标/软著搜索 URL（/search/trademark|software|softcopyright
 * 均返回 0 条），商标/软著/专利位于<b>企业详情页「知识产权」tab</b>（详情页文本含「知识产权|999+」）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RiskbirdIntellectualProperty {

    /** 商标列表（名称/注册号等原始条目文本）。 */
    @Builder.Default
    private List<String> trademarks = new ArrayList<>();

    /** 软件著作权列表。 */
    @Builder.Default
    private List<String> softCopyrights = new ArrayList<>();

    /** 专利列表。 */
    @Builder.Default
    private List<String> patents = new ArrayList<>();

    /** 原始知识产权区文本（调试/排障用）。 */
    private String rawText;

    /** 是否有数据。 */
    public boolean hasAny() {
        return !trademarks.isEmpty() || !softCopyrights.isEmpty() || !patents.isEmpty();
    }
}
