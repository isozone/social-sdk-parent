package cn.net.rjnetwork.xianyu.manager.vip.model;

import cn.net.rjnetwork.xianyu.manager.common.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("sdk_deployment")
public class SdkDeployment extends BaseEntity {

    private String deploymentId;
    private LocalDateTime installTime;
    private String serverUrl;
    private String appId;
    private String appSecret;
    private String boundEmail;
    private Boolean emailVerified;
    private LocalDateTime emailVerifiedAt;
    private String communityUid;
    private LocalDateTime lastIdentitySyncAt;
    private Long accessExpiredAt; // 部署接入密钥有效期（Unix 秒，0=未设置/永久）；new-api 按套餐 duration_days 下发

    public String getDeploymentId() {
        return deploymentId;
    }

    public void setDeploymentId(String deploymentId) {
        this.deploymentId = deploymentId;
    }

    public LocalDateTime getInstallTime() {
        return installTime;
    }

    public void setInstallTime(LocalDateTime installTime) {
        this.installTime = installTime;
    }

    public String getBoundEmail() {
        return boundEmail;
    }

    public void setBoundEmail(String boundEmail) {
        this.boundEmail = boundEmail;
    }

    public Boolean getEmailVerified() {
        return emailVerified;
    }

    public void setEmailVerified(Boolean emailVerified) {
        this.emailVerified = emailVerified;
    }

    public LocalDateTime getEmailVerifiedAt() {
        return emailVerifiedAt;
    }

    public void setEmailVerifiedAt(LocalDateTime emailVerifiedAt) {
        this.emailVerifiedAt = emailVerifiedAt;
    }

    public String getCommunityUid() {
        return communityUid;
    }

    public void setCommunityUid(String communityUid) {
        this.communityUid = communityUid;
    }

    public LocalDateTime getLastIdentitySyncAt() {
        return lastIdentitySyncAt;
    }

    public void setLastIdentitySyncAt(LocalDateTime lastIdentitySyncAt) {
        this.lastIdentitySyncAt = lastIdentitySyncAt;
    }

    public String getServerUrl() {
        return serverUrl;
    }

    public void setServerUrl(String serverUrl) {
        this.serverUrl = serverUrl;
    }

    public String getAppId() {
        return appId;
    }

    public void setAppId(String appId) {
        this.appId = appId;
    }

    public String getAppSecret() {
        return appSecret;
    }

    public void setAppSecret(String appSecret) {
        this.appSecret = appSecret;
    }

    public Long getAccessExpiredAt() {
        return accessExpiredAt;
    }

    public void setAccessExpiredAt(Long accessExpiredAt) {
        this.accessExpiredAt = accessExpiredAt;
    }
}
