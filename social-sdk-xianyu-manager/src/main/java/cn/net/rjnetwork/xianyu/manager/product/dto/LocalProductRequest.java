package cn.net.rjnetwork.xianyu.manager.product.dto;

import lombok.Data;
import java.math.BigDecimal;
import java.util.List;

@Data
public class LocalProductRequest {
    private Long id;
    private Long accountId;
    private String title;
    private BigDecimal price;
    private BigDecimal originalPrice;
    private Integer stock;
    private String categoryId;
    private String description;
    private List<String> images;
    private List<String> videos;
    private String goodsType;
    private String deliverType;
    private String deliverContentTemplate;

    /** 运费偏好：NONE=无需邮寄（默认）/ FREE=包邮 / DISTANCE=按距离计费 */
    private String shippingMode;

    /** DRAFT 保存 / SUBMIT 提交发布 */
    private String action;
}
