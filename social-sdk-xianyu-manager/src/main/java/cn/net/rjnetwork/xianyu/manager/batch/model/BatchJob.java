package cn.net.rjnetwork.xianyu.manager.batch.model;

import cn.net.rjnetwork.xianyu.manager.common.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 批次任务主表 —— 统一承载所有定时任务的「一次执行批次」。
 * <p>对标参考项目 scheduled_batch_log_service.py：避免每个定时任务重复造一份批次表结构，
 * 用 job_type + job_code 区分任务种类，items 关联 {@link BatchJobItem} 记录每条明细。</p>
 *
 * <p>典型 job_type：cookies_refresh / api_cookie_renew / login_renew / token_renewal /
 * redelivery / rate / red_flower / polish / close_notice / db_backup / fetch_items / fetch_orders 等。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("batch_job")
public class BatchJob extends BaseEntity {

    /** 任务种类，如 cookies_refresh / redelivery / rate */
    private String jobType;
    /** 任务实例标识，同一 job_type 下可有多实例（如不同账号各一份）；可空 */
    private String jobCode;
    /** 触发来源：SCHEDULER（定时）/ MANUAL（管理端手动）/ SYSTEM（系统事件） */
    private String triggerSource;
    /** 批次状态：RUNNING / SUCCESS / PARTIAL / FAILED / CANCELLED */
    private String status;
    /** 计划执行账号/目标数 */
    private Integer totalCount;
    /** 成功数 */
    private Integer successCount;
    /** 失败数 */
    private Integer failedCount;
    /** 跳过数（如冷却中、风控中） */
    private Integer skippedCount;
    /** 批次开始时间 */
    private LocalDateTime startedAt;
    /** 批次结束时间 */
    private LocalDateTime endedAt;
    /** 批次汇总信息（如「10 账号中 8 成功 2 失败」） */
    private String summary;
    /** 失败原因聚合（便于管理端一眼定位） */
    private String failureSummary;
}
