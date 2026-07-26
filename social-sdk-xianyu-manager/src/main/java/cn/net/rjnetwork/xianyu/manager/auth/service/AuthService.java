package cn.net.rjnetwork.xianyu.manager.auth.service;

import cn.net.rjnetwork.xianyu.manager.auth.dto.ChangePasswordRequest;
import cn.net.rjnetwork.xianyu.manager.auth.dto.JwtResponse;
import cn.net.rjnetwork.xianyu.manager.auth.dto.JwtResponse.AdminUserInfo;
import cn.net.rjnetwork.xianyu.manager.auth.dto.LoginRequest;
import cn.net.rjnetwork.xianyu.manager.auth.dto.UpdateProfileRequest;
import cn.net.rjnetwork.xianyu.manager.auth.mapper.AdminUserMapper;
import cn.net.rjnetwork.xianyu.manager.auth.model.AdminUser;
import cn.net.rjnetwork.xianyu.manager.auth.security.JwtUtils;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class AuthService {

    private final AdminUserMapper adminUserMapper;
    private final JwtUtils jwtUtils;
    private final PasswordEncoder passwordEncoder;

    public AuthService(AdminUserMapper adminUserMapper, JwtUtils jwtUtils, PasswordEncoder passwordEncoder) {
        this.adminUserMapper = adminUserMapper;
        this.jwtUtils = jwtUtils;
        this.passwordEncoder = passwordEncoder;
    }

    public Optional<AdminUser> findByUsername(String username) {
        LambdaQueryWrapper<AdminUser> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AdminUser::getUsername, username);
        AdminUser user = adminUserMapper.selectOne(wrapper);
        return Optional.ofNullable(user);
    }

    @Transactional
    public JwtResponse login(LoginRequest request) {
        AdminUser user = findByUsername(request.getUsername())
                .orElseThrow(() -> new IllegalArgumentException("Invalid username or password"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new IllegalArgumentException("Invalid username or password");
        }

        String token = jwtUtils.generateToken(user.getUsername());
        AdminUserInfo info = new AdminUserInfo();
        info.setId(user.getId());
        info.setUsername(user.getUsername());
        info.setDisplayName(user.getDisplayName());
        info.setEmail(user.getEmail());
        info.setPhone(user.getPhone());
        info.setRoleLevel(user.getRoleLevel());
        return JwtResponse.of(token, jwtUtils.getExpiration(), info);
    }

    @Transactional
    public void initDefaultAdmin(String username, String password) {
        if (findByUsername(username).isPresent()) {
            return;
        }

        AdminUser user = new AdminUser();
        user.setUsername(username);
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setDisplayName("管理员");
        user.setRoleLevel(9);
        adminUserMapper.insert(user);
    }

    /**
     * 更新当前管理员的个人资料（昵称 / 邮箱 / 手机号）。
     * 仅更新请求中非空字段，username 与 roleLevel 不可修改。
     */
    @Transactional
    public AdminUser updateProfile(String username, UpdateProfileRequest request) {
        AdminUser user = findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("用户不存在"));
        if (request.getDisplayName() != null) {
            user.setDisplayName(request.getDisplayName());
        }
        if (request.getEmail() != null) {
            user.setEmail(request.getEmail());
        }
        if (request.getPhone() != null) {
            user.setPhone(request.getPhone());
        }
        adminUserMapper.updateById(user);
        return user;
    }

    /**
     * 修改当前管理员的登录密码。
     * 直接使用 PasswordEncoder 重新编码新密码（不要求原密码，适合忘记原密码场景）。
     */
    @Transactional
    public void changePassword(String username, ChangePasswordRequest request) {
        AdminUser user = findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("用户不存在"));
        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        adminUserMapper.updateById(user);
    }
}
