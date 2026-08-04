package cn.net.rjnetwork.xianyu.manager.product.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class ProductCreateRequest {
    private Long accountId;
    private String title;
    private BigDecimal price;
    private BigDecimal originalPrice;
    private Integer stock;
    private String categoryId;
    private String description;
    private List<String> images;  // 图片 URL 列表
    private List<String> videos;  // 视频 URL 列表（预留）

    /** 商品类型：PHYSICAL / VIRTUAL（本地商品发布透传，落 XianyuProduct） */
    private String goodsType;

    /** 发货类型：CARD / ACCOUNT / LINK / FILE（虚拟商品用） */
    private String deliverType;

    /** 发货内容模板（虚拟商品用） */
    private String deliverContentTemplate;
}
