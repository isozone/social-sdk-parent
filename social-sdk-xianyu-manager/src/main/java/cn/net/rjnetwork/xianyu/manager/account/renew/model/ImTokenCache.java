package cn.net.rjnetwork.xianyu.manager.account.renew.model;

import cn.net.rjnetwork.xianyu.manager.common.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 账号 IM token 缓存 —— A4。
 * <p>每账号一行：缓存的 _m_h5_tk token（MTOP 签名用）、x5sec（滑块风控 cookie）、
 * 下次续期时间、上次续期结果。对标参考项目 token_cache 表。</p>
 *
 * <p>与 A1/A2/A3 的区别：A1-A3 续的是「登录 cookie」（cookie2/unb/sgcookie），
 * A4 续的是「IM token + x5sec」（_m_h5_tk、x5sec）—— 这些是 MTOP 调用与 IM 长连接专用，
 * 失效后消息同步/发送会触发风控，需要单独走滑块链路刷新。</p>
 *
 * <p>续期链路：定时扫描 token 缓存到期 → 调 MTOP pc.login.token 拿新 token →
 * 若被风控 punish 则联动滑块（captchaSolver.solve）刷新 x5sec → 写回本表 + imCookieHeader。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("im_token_cache")
public class ImTokenCache extends BaseEntity {

    /** 账号 ID */
    private Long accountId;
    /** 缓存的 _m_h5_tk token（MTOP 签名用，下划线前半段） */
    private String mtopToken;
    /** 缓存的 _m_h5_tk 完整 cookie 值（含过期时间戳后缀） */
    private String mtopTokenCookie;
    /** 缓存的 x5sec（滑块风控 cookie） */
    private String x5sec;
    /** 完整 imCookieHeader（x5sec + 其他 IM 专用 cookie） */
    private String imCookieHeader;
    /** token 预计过期时间（_m_h5_tk 后缀时间戳） */
    private LocalDateTime tokenExpiresAt;
    /** 下次续期扫描时间 */
    private LocalDateTime nextRenewAt;
    /** 上次续期时间 */
    private LocalDateTime lastRenewAt;
    /** 上次续期结果：SUCCESS / FAILED / SKIPPED / CAPTCHA_SOLVED */
    private String lastResult;
    /** 上次失败原因 */
    private String lastFailureReason;
    /** 连续失败次数（熔断用） */
    private Integer consecutiveFailures;
}
