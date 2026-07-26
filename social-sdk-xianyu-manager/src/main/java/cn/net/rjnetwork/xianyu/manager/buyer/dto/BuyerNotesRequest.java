package cn.net.rjnetwork.xianyu.manager.buyer.dto;

import lombok.Data;

/**
 * 买家备注请求
 */
@Data
public class BuyerNotesRequest {
    private String notes;
    /** 兼容 note 字段名 */
    private String note;

    public String resolveNotes() {
        if (notes != null) return notes;
        return note;
    }
}
