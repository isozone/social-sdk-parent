package cn.net.rjnetwork.xianyu.manager.virtual.model;

import cn.net.rjnetwork.xianyu.manager.common.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 商品-卡券关联表 —— A6。
 * <p>一个商品可挂多张卡券（cardId），按 priority 决定发货优先级；
 * 同一商品同一卡券可多条（priority 不同时复用，priority 唯一）。</p>
 *
 * <p>替代老 {@link VirtualCardPool} 的 product_id 直接挂单卡券模式，
 * 支持多卡券组合发货（如先发 ACCOUNT 主号 + CARD 备用卡）。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("card_item_relation")
public class CardItemRelation extends BaseEntity {

    /** 商品 ID */
    private Long productId;
    /** 卡券 ID（关联 ship_card.id） */
    private Long cardId;
    /** 发货优先级（数字越小越优先，0 最高） */
    private Integer priority;
    /** 该关联是否启用：0=停用 1=启用 */
    private Integer enabled;
}
