package cn.net.rjnetwork.xianyu.manager.account.renew.model;

import cn.net.rjnetwork.xianyu.manager.common.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 账号 Cookie 刷新计划 —— A1。
 * <p>每个账号一条：启停、刷新间隔、下次执行时间。对标参考项目 cookie_renew_browser_service.py 的计划表。</p>
 *
 * <p>刷新策略优先级：API 续期（A2）→ 浏览器刷新（A1）→ 登录续期（A3）。
 * 本表只承载「浏览器刷新」通道的计划配置；API 续期走轻量通道（A2），不建独立计划表。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("cookie_refresh_schedule")
public class CookieRefreshSchedule extends BaseEntity {

    /** 账号 ID */
    private Long accountId;
    /** 启用开关：0=停用 1=启用 */
    private Integer enabled;
    /** 刷新间隔（分钟），默认 720（12 小时） */
    private Integer intervalMinutes;
    /** 下次执行时间 */
    private LocalDateTime nextRunAt;
    /** 上次执行时间 */
    private LocalDateTime lastRunAt;
    /** 上次结果：SUCCESS / FAILED / SKIPPED */
    private String lastResult;
    /** 上次失败原因 */
    private String lastFailureReason;
    /** 是否仅在健康检测失效时才刷新：0=定时刷新 1=失效触发（默认 1，减少无谓刷新） */
    private Integer onlyOnExpired;
}
