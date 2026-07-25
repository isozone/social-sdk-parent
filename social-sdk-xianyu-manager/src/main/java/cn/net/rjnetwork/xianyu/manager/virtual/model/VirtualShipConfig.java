package cn.net.rjnetwork.xianyu.manager.virtual.model;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import cn.net.rjnetwork.xianyu.manager.common.BaseEntity;

/**
 * 自动发货全局配置（每账号一条）
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("virtual_ship_config")
public class VirtualShipConfig extends BaseEntity {

    private Long accountId;

    private Boolean enabled;

    /** 支付成功后延时发货秒数 */
    private Integer delaySeconds;

    /** N天后自动确认收货 */
    private Integer autoConfirmDays;

    /** A10 确认收货话术模板；null=走默认「买家您好，方便确认下收货吗？有助于我店铺评分提升，谢谢~」 */
    private String confirmReceiptMessage;

    /** 发货后站内通知运营 */
    private Boolean notifyAfterShip;
}
