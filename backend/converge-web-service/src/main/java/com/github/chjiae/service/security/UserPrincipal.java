package com.github.chjiae.service.security;

import com.github.chjiae.common.enums.UserStatus;
import com.github.chjiae.common.enums.UserType;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 用户认证主体，封装当前登录用户的安全信息。
 * 实现 Spring Security 的 UserDetails 接口，用于认证和授权流程。
 */
@Getter
public class UserPrincipal implements UserDetails {

    /** 用户 ID */
    private final Long userId;

    /** 租户 ID（超管为 null） */
    private final Long tenantId;

    /** 用户名 */
    private final String username;

    /** 密码哈希 */
    private final String password;

    /** 用户类型 */
    private final UserType userType;

    /** 用户状态 */
    private final UserStatus status;

    /** 权限列表（角色） */
    private final Collection<? extends GrantedAuthority> authorities;

    /**
     * 构造用户认证主体
     *
     * @param userId   用户 ID
     * @param tenantId 租户 ID
     * @param username 用户名
     * @param password 密码哈希
     * @param userType 用户类型
     * @param status   用户状态
     * @param roles    角色编码列表
     */
    public UserPrincipal(Long userId, Long tenantId, String username, String password,
                         UserType userType, UserStatus status, List<String> roles) {
        this.userId = userId;
        this.tenantId = tenantId;
        this.username = username;
        this.password = password;
        this.userType = userType;
        this.status = status;
        // 将角色编码转换为 GrantedAuthority，格式为 ROLE_XXX
        this.authorities = roles.stream()
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                .collect(Collectors.toList());
    }

    /**
     * 判断账号是否未过期
     * @return 始终返回 true，当前未实现过期机制
     */
    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    /**
     * 判断账号是否未锁定
     * @return 始终返回 true，当前未实现锁定机制
     */
    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    /**
     * 判断凭证是否未过期
     * @return 始终返回 true，当前未实现凭证过期机制
     */
    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    /**
     * 判断账号是否启用
     * @return 当用户状态为 ACTIVE 时返回 true
     */
    @Override
    public boolean isEnabled() {
        return UserStatus.ACTIVE == this.status;
    }
}
