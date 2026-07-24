package cn.net.rjnetwork.xianyu.manager.auth.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 管理员更新个人信息请求
 */
@Data
public class UpdateProfileRequest {

    @Size(max = 64, message = "昵称长度不能超过 64")
    private String displayName;

    @Size(max = 128, message = "邮箱长度不能超过 128")
    private String email;

    @Size(max = 32, message = "手机号长度不能超过 32")
    private String phone;
}
