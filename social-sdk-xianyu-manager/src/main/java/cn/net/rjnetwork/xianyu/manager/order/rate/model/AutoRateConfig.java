package cn.net.rjnetwork.xianyu.manager.order.rate.model;

import cn.net.rjnetwork.xianyu.manager.common.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 自动评价配置 —— B2。
 * <p>每账号一条：启用开关、评价等级（GOOD/NORMAL/BAD）、话术模板、延后天数、
 * 启用商品白名单（仅指定商品自动评价，避免误评）、买家黑名单（差评买家不自动评）。</p>
 *
 * <p>评价话术模板支持占位符：{itemTitle}、{buyerNick}、{accountName}，运行时替换。
 * 对标参考项目 auto_rate_config：定时扫「已收货 N 天且卖家未评」的订单，调 reviewOrder 自动好评。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("auto_rate_config")
public class AutoRateConfig extends BaseEntity {

    /** 账号 ID；null=全局规则 */
    private Long accountId;
    /** 启用开关：0=停用 1=启用 */
    private Integer enabled;
    /** 评价等级：GOOD（好评）/ NORMAL（中评）/ BAD（差评），默认 GOOD */
    private String rateLevel;
    /** 话术模板，支持 {itemTitle}/{buyerNick}/{accountName} 占位符；null=走默认好评话术 */
    private String feedbackTemplate;
    /** 延后几天才评（收货后 N 天再评，避免被风控盯上），默认 1 */
    private Integer delayDays;
    /** 商品白名单（JSON 数组 productId 字符串），null=所有商品都评 */
    private String productWhitelist;
    /** 买家黑名单（JSON 数组 buyerId），命中的买家不自动评 */
    private String buyerBlacklist;
    /** 上次执行时间（诊断用） */
    private LocalDateTime lastRunAt;
}
