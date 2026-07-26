package cn.net.rjnetwork.xianyu.manager.buyer.dto;

import lombok.Data;

import java.util.List;

/**
 * 买家打标请求（支持单个 tag 或多个 tags）
 */
@Data
public class BuyerTagRequest {
    /** 单个标签 */
    private String tag;
    /** 多个标签（批量追加） */
    private List<String> tags;
}
