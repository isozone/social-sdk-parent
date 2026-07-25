package cn.net.rjnetwork.xianyu.manager.virtual.model;

import cn.net.rjnetwork.xianyu.manager.common.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 卡券实体（四类型）—— A6。
 * <p>升级原 {@link VirtualCardPool} 单类型为四类型，承载闲鱼虚拟发货所有卡券形态：</p>
 * <ul>
 *   <li>CARD：卡密卡号（最常见，含 cardCode + cardPassword）</li>
 *   <li>ACCOUNT：账号类（如游戏账号 + 密码 + 服务器，含 cardCode=账号 cardPassword=密码 extra=服务器名）</li>
 *   <li>LINK_QRCODE：链接/二维码类（含 content=链接 URL 或二维码内容，无 cardCode/cardPassword）</li>
 *   <li>PLAIN_TEXT：纯文本话术（含 content=完整话术，无结构化字段）</li>
 * </ul>
 *
 * <p>与商品的多卡券关联走 {@link CardItemRelation}（product_id 可挂多张卡券，按优先级发货）。
 * 老 virtual_card_pool 数据迁移到本表 cardType=CARD，card_code/card_password 字段直映。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ship_card")
public class ShipCard extends BaseEntity {

    /** 卡券类型：CARD / ACCOUNT / LINK_QRCODE / PLAIN_TEXT */
    private String cardType;
    /** 卡号 / 账号名（CARD/ACCOUNT 用；LINK_QRCODE/PLAIN_TEXT 可空） */
    private String cardCode;
    /** 密码 / 密保（CARD/ACCOUNT 用） */
    private String cardPassword;
    /** 额外信息（ACCOUNT=服务器名；CARD=有效期等；可空） */
    private String extra;
    /** 链接/二维码内容 或 纯文本话术（LINK_QRCODE/PLAIN_TEXT 用；CARD/ACCOUNT 可空） */
    private String content;
    /** 卡券状态：AVAILABLE / USED / EXPIRED / DISABLED */
    private String status;
    /** 已用订单 ID（AVAILABLE 时为空） */
    private Long usedOrderId;
    /** 已用时间 */
    private LocalDateTime usedAt;
}
