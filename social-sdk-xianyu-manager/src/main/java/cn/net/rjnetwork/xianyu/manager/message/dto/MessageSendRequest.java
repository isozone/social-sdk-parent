package cn.net.rjnetwork.xianyu.manager.message.dto;

import lombok.Data;

@Data
public class MessageSendRequest {
    private Long accountId;
    /** 闲鱼会话 ID；为空时可用 buyerId 兜底生成。 */
    private String sessionId;
    /** 买家/对方 userId；虚拟发货、自动回复等系统链路可不依赖前端 sessionId。 */
    private String buyerId;
    private String content;
    /** 是否自动回复消息，用于本地消息标记和审计。 */
    private Boolean autoReply;
}
