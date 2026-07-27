package cn.net.rjnetwork.xianyu.manager.vip.model;

import cn.net.rjnetwork.xianyu.manager.common.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("community_user_binding")
public class CommunityUserBinding extends BaseEntity {

    private Long localUserId;
    private String deploymentId;
    private Long communityUserId;
    private String communityUid;
    private String bindId;
    private String bindToken;
    private String newApiBaseUrl;
    private String status;
    private String initialPayChannel;
    private String initialChannelPrefix;
    private Boolean wechatBound;
    private Boolean emailBound;
    private String email;
    private Boolean emailVerified;
    private LocalDateTime emailVerifiedAt;
    private String identityStatus;
    private LocalDateTime lastRestoreAt;
    private LocalDateTime lastSyncAt;

    public Long getLocalUserId() {
        return localUserId;
    }

    public void setLocalUserId(Long localUserId) {
        this.localUserId = localUserId;
    }

    public String getDeploymentId() {
        return deploymentId;
    }

    public void setDeploymentId(String deploymentId) {
        this.deploymentId = deploymentId;
    }

    public Long getCommunityUserId() {
        return communityUserId;
    }

    public void setCommunityUserId(Long communityUserId) {
        this.communityUserId = communityUserId;
    }

    public String getCommunityUid() {
        return communityUid;
    }

    public void setCommunityUid(String communityUid) {
        this.communityUid = communityUid;
    }

    public String getBindId() {
        return bindId;
    }

    public void setBindId(String bindId) {
        this.bindId = bindId;
    }

    public String getBindToken() {
        return bindToken;
    }

    public void setBindToken(String bindToken) {
        this.bindToken = bindToken;
    }

    public String getNewApiBaseUrl() {
        return newApiBaseUrl;
    }

    public void setNewApiBaseUrl(String newApiBaseUrl) {
        this.newApiBaseUrl = newApiBaseUrl;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getInitialPayChannel() {
        return initialPayChannel;
    }

    public void setInitialPayChannel(String initialPayChannel) {
        this.initialPayChannel = initialPayChannel;
    }

    public String getInitialChannelPrefix() {
        return initialChannelPrefix;
    }

    public void setInitialChannelPrefix(String initialChannelPrefix) {
        this.initialChannelPrefix = initialChannelPrefix;
    }

    public Boolean getWechatBound() {
        return wechatBound;
    }

    public void setWechatBound(Boolean wechatBound) {
        this.wechatBound = wechatBound;
    }

    public Boolean getEmailBound() {
        return emailBound;
    }

    public void setEmailBound(Boolean emailBound) {
        this.emailBound = emailBound;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
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

    public String getIdentityStatus() {
        return identityStatus;
    }

    public void setIdentityStatus(String identityStatus) {
        this.identityStatus = identityStatus;
    }

    public LocalDateTime getLastRestoreAt() {
        return lastRestoreAt;
    }

    public void setLastRestoreAt(LocalDateTime lastRestoreAt) {
        this.lastRestoreAt = lastRestoreAt;
    }

    public LocalDateTime getLastSyncAt() {
        return lastSyncAt;
    }

    public void setLastSyncAt(LocalDateTime lastSyncAt) {
        this.lastSyncAt = lastSyncAt;
    }
}
