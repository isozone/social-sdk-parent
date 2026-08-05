package cn.net.rjnetwork.xianyu.manager.message.model;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import cn.net.rjnetwork.xianyu.manager.common.BaseEntity;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("xianyu_message")
public class XianyuMessage extends BaseEntity {

    private Long accountId;
    private String sessionId;
    private String msgId;     // 消息唯一 id（去重用）
    private String senderId;
    private String senderName;
    private String senderAvatar;
    private String content;
    private String msgType; // TEXT, IMAGE, SYSTEM
    private String direction; // INCOMING, OUTGOING
    private Boolean autoReply;
    private LocalDateTime messageTime;

    // ===== 旁路业务字段（从闲鱼消息体原始 JSON 透传保留，方便后续业务关联使用）=====
    /** 闲鱼会话/卡片 ID（cid）—— 后续按卡片定位会话/动作的关联键 */
    private String cid;
    /** 关联订单 ID（commonData.orderId/orderIdStr）—— 消息↔订单关联 */
    private String bizOrderId;
    /** 关联商品 ID（commonData.itemId）—— 消息↔商品关联 */
    private String bizItemId;
    /** 买家用户 ID（biz.buyerId/peerUserId）—— 消息↔买家档案关联 */
    private String bizBuyerId;
}
