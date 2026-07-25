package cn.net.rjnetwork.xianyu.manager.product.reply.model;

import cn.net.rjnetwork.xianyu.manager.common.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 商品专属回复 —— BOT-D1。
 *
 * <p>不同于 {@code XianyuAutoReplyConfig}（A10 通用关键词回复规则），本表是 <b>商品级专属回复</b>：
 * 指定商品在指定触发场景（首次询单/拍下/付款）下回复固定内容，
 * 优先级高于通用关键词回复，让爆款/定制商品有独立话术。</p>
 *
 * <p>语义对标 xianyu-auto-bot 的 item_reply 表。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("item_reply")
public class ItemReply extends BaseEntity {

    /** 账号 ID */
    private Long accountId;
    /** 商品 ID（闲鱼 itemId） */
    private String itemId;
    /** 商品标题（冗余，便于管理端识别） */
    private String itemTitle;
    /** 触发场景：FIRST_INQUIRY（首次询单）/ORDER_PLACED（拍下）/ORDER_PAID（付款） */
    private String triggerScene;
    /** 回复内容（含 {商品名} {买家昵称} {订单号} 等占位符） */
    private String replyContent;
    /** 是否启用：1=是 0=否 */
    private Integer enabled;
    /** 优先级（数值越小越优先；同商品同场景下多条时按此排序） */
    private Integer priority;
    /** 使用次数（统计用） */
    private Integer useCount;
    /** 备注 */
    private String remark;
}
