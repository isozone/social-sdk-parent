package cn.net.rjnetwork.xianyu.manager.order.rate.model;

import cn.net.rjnetwork.xianyu.manager.common.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 评价模板 —— BOT-B1。
 *
 * <p>不同于 {@link AutoRateConfig}（A11 自动评价配置，单条），本表是 <b>多条评价模板</b>：
 * 买家确认收货后随机选一条评价文本发送，避免重复评价被风控。</p>
 *
 * <p>语义对标 xianyu-auto-bot 的 comment_templates 表。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("comment_templates")
public class CommentTemplate extends BaseEntity {

    /** 账号 ID；null=全局模板 */
    private Long accountId;
    /** 模板分类：POSITIVE（好评）/NEUTRAL（中评）/REPLY（追评回复） */
    private String category;
    /** 评价文本（含 {商品名} {买家昵称} 等占位符） */
    private String content;
    /** 模板名称（便于管理端识别） */
    private String name;
    /** 是否激活：1=启用 0=停用（停用的不参与随机选） */
    private Integer enabled;
    /** 优先级（数值越小越优先；同分类下按 priority 升序 + 随机选） */
    private Integer priority;
    /** 使用次数（统计用） */
    private Integer useCount;
    /** 备注 */
    private String remark;
}
