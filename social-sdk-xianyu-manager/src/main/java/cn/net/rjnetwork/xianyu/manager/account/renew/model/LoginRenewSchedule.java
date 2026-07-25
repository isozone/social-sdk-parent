package cn.net.rjnetwork.xianyu.manager.account.renew.model;

import cn.net.rjnetwork.xianyu.manager.common.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 账号登录续期计划 —— A3。
 * <p>当 A1（浏览器刷新）+ A2（API 续期）双双失效，说明 Cookie 已彻庽失效，
 * 必须走完整登录流程（扫码 / 密码）拿全新 Cookie。本表承载每个账号的登录续期计划。</p>
 *
 * <p>登录方式优先级：QR（扫码，需人工扫码但成功率高）→ PASSWORD（密码，需账号密码字段）。
 * 扫码登录会通过通知推二维码给用户，用户手机闲鱼 App 扫码确认。</p>
 *
 * <p>触发条件：A1+A2 失败 → 熔断器 OPEN → 启动 A3；A3 也失败则账号标 COOKIE_EXPIRED，
 * 等待人工介入（推 ACCOUNT_LOGIN_FAILED 通知）。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("login_renew_schedule")
public class LoginRenewSchedule extends BaseEntity {

    /** 账号 ID */
    private Long accountId;
    /** 启用开关：0=停用 1=启用 */
    private Integer enabled;
    /** 登录方式：QR（扫码）/ PASSWORD（密码），默认 QR */
    private String loginMethod;
    /** 密码登录用：账号密码（加密存储）；扫码登录可空 */
    private String passwordEncrypted;
    /** 最大重试次数，默认 3 */
    private Integer maxRetry;
    /** 当前重试次数 */
    private Integer currentRetry;
    /** 下次执行时间 */
    private LocalDateTime nextRunAt;
    /** 上次执行时间 */
    private LocalDateTime lastRunAt;
    /** 上次结果：SUCCESS / FAILED / SKIPPED / WAITING_QR */
    private String lastResult;
    /** 上次失败原因 */
    private String lastFailureReason;
}
