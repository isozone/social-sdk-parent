package cn.net.rjnetwork.xianyu.manager.vip.dto;

public class VipCreateOrderRequest {
    private Integer planId;
    private String channel;

    public Integer getPlanId() {
        return planId;
    }

    public void setPlanId(Integer planId) {
        this.planId = planId;
    }

    public String getChannel() {
        return channel;
    }

    public void setChannel(String channel) {
        this.channel = channel;
    }
}
