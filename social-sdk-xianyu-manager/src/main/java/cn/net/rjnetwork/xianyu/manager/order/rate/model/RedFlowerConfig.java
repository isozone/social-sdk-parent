package cn.net.rjnetwork.xianyu.manager.order.rate.model;

import cn.net.rjnetwork.xianyu.manager.common.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 求小红花配置 —— B3。
 * <p>每账号一条：启用开关、送花目标类型（buyer/seller）、每日送花上限、
 * 已送花计数（reset by 定时）、白名单（仅指定买家送花）。</p>
 *
 * <p>闲鱼侧送红花走 {@link cn.net.rjnetwork.xianyu.api.XianyuMessageApiService#sendRedFlower}（mtop.taobao.idlemessage.red.flower）。
 * 对标参考项目 red_flower_service.py：定时给买家送红花提信誉，提升店铺评分回收。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("red_flower_config")
public class RedFlowerConfig extends BaseEntity {

    /** 账号 ID；null=全局规则 */
    private Long accountId;
    /** 启用开关：0=停用 1=启用 */
    private Integer enabled;
    /** 送花目标类型：buyer（给买家送）/ seller（给卖家送），默认 buyer */
    private String targetType;
    /** 每日送花上限（防风控盯上），默认 20 */
    private Integer dailyLimit;
    /** 今日已送花计数（每日定时 reset） */
    private Integer todaySentCount;
    /** 今日计数日期（reset 判断用） */
    private LocalDateTime todayDate;
    /** 买家白名单（JSON 数组 buyerId），null=所有买家都送 */
    private String buyerWhitelist;
    /** 上次执行时间（诊断用） */
    private LocalDateTime lastRunAt;
}
