package cn.net.rjnetwork.xianyu.manager.vip.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;

@Component
@ConfigurationProperties(prefix = "vip.community")
public class NewApiCommunityProperties {

    private static final Logger log = LoggerFactory.getLogger(NewApiCommunityProperties.class);

    private String baseUrl = "http://127.0.0.1:3000";
    private String appId = "";
    private String secret = "";
    private int connectTimeoutSeconds = 5;
    private int requestTimeoutSeconds = 15;

    /**
     * 接入密钥未配置时给出醒目提示，社区/VIP 功能自动降级不可用。
     * 密钥需按套餐付费后通过环境变量 XIANYU_COMMUNITY_APP_ID / XIANYU_COMMUNITY_SECRET 注入。
     */
    @PostConstruct
    public void validate() {
        if (!isConfigured()) {
            log.warn("[I-社区] 未配置接入密钥（vip.community.app-id / secret 为空）：I 社区与 VIP 功能不可用。"
                    + "请先按套餐付费获取接入密钥，再通过环境变量 XIANYU_COMMUNITY_APP_ID / XIANYU_COMMUNITY_SECRET 配置后重启。");
        }
    }

    /** 是否已配置完整接入密钥（未配置时社区相关能力应降级/提示，而不是带默认值悄悄上线） */
    public boolean isConfigured() {
        return secret != null && !secret.isBlank()
                && appId != null && !appId.isBlank()
                && baseUrl != null && !baseUrl.isBlank();
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getAppId() {
        return appId;
    }

    public void setAppId(String appId) {
        this.appId = appId;
    }

    public String getSecret() {
        return secret;
    }

    public void setSecret(String secret) {
        this.secret = secret;
    }

    public int getConnectTimeoutSeconds() {
        return connectTimeoutSeconds;
    }

    public void setConnectTimeoutSeconds(int connectTimeoutSeconds) {
        this.connectTimeoutSeconds = connectTimeoutSeconds;
    }

    public int getRequestTimeoutSeconds() {
        return requestTimeoutSeconds;
    }

    public void setRequestTimeoutSeconds(int requestTimeoutSeconds) {
        this.requestTimeoutSeconds = requestTimeoutSeconds;
    }
}
