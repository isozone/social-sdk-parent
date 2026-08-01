package cn.net.rjnetwork.xianyu.manager.vip.model;

import cn.net.rjnetwork.xianyu.manager.common.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@TableName("vip_order")
public class VipOrder extends BaseEntity {

    private Long localUserId;
    private String deploymentId;
    private Long communityUserId;
    private String communityUid;
    private String localOrderNo;
    private String newApiOrderNo;
    private String planId;
    private String planCode;
    private String planName;
    private String payChannel;
    private BigDecimal payAmount;
    private String currency;
    private String email;
    private Boolean identityVerified;
    private String status;
    private String payInfoJson;
    private String entitlementJson;
    private LocalDateTime paidAt;

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

    public String getLocalOrderNo() {
        return localOrderNo;
    }

    public void setLocalOrderNo(String localOrderNo) {
        this.localOrderNo = localOrderNo;
    }

    public String getNewApiOrderNo() {
        return newApiOrderNo;
    }

    public void setNewApiOrderNo(String newApiOrderNo) {
        this.newApiOrderNo = newApiOrderNo;
    }

    public String getPlanId() {
        return planId;
    }

    public void setPlanId(String planId) {
        this.planId = planId;
    }

    public String getPlanCode() {
        return planCode;
    }

    public void setPlanCode(String planCode) {
        this.planCode = planCode;
    }

    public String getPlanName() {
        return planName;
    }

    public void setPlanName(String planName) {
        this.planName = planName;
    }

    public String getPayChannel() {
        return payChannel;
    }

    public void setPayChannel(String payChannel) {
        this.payChannel = payChannel;
    }

    public BigDecimal getPayAmount() {
        return payAmount;
    }

    public void setPayAmount(BigDecimal payAmount) {
        this.payAmount = payAmount;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public Boolean getIdentityVerified() {
        return identityVerified;
    }

    public void setIdentityVerified(Boolean identityVerified) {
        this.identityVerified = identityVerified;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getPayInfoJson() {
        return payInfoJson;
    }

    public void setPayInfoJson(String payInfoJson) {
        this.payInfoJson = payInfoJson;
    }

    public String getEntitlementJson() {
        return entitlementJson;
    }

    public void setEntitlementJson(String entitlementJson) {
        this.entitlementJson = entitlementJson;
    }

    public LocalDateTime getPaidAt() {
        return paidAt;
    }

    public void setPaidAt(LocalDateTime paidAt) {
        this.paidAt = paidAt;
    }
}
