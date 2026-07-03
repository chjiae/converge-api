package com.github.chjiae.service.security;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.github.chjiae.service.entity.Role;
import com.github.chjiae.service.entity.User;
import com.github.chjiae.service.entity.UserRole;
import com.github.chjiae.service.mapper.RoleMapper;
import com.github.chjiae.service.mapper.UserMapper;
import com.github.chjiae.service.mapper.UserRoleMapper;
import com.github.chjiae.service.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 自定义用户详情服务，从数据库加载用户信息用于认证。
 * 实现 Spring Security 的 UserDetailsService 接口。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    /** 用户数据访问层 */
    private final UserMapper userMapper;

    /** 用户角色关联数据访问层 */
    private final UserRoleMapper userRoleMapper;

    /** 角色数据访问层 */
    private final RoleMapper roleMapper;

    /**
     * 根据用户名加载用户详情
     * 超管全局查询（tenantId 为 null），租户用户在 TenantContext 下查询
     *
     * @param username 用户名
     * @return 用户认证主体
     * @throws UsernameNotFoundException 用户不存在时抛出
     */
    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        // 认证时忽略租户过滤，允许查询所有用户及其角色（包括超管和跨租户用户）
        TenantContext.setIgnoreTenant(true);
        try {
            // 1. 根据用户名查询用户（超管 tenantId 为 null，租户用户由多租户拦截器自动过滤）
            User user = userMapper.selectOne(
                    new LambdaQueryWrapper<User>().eq(User::getUsername, username)
            );
            if (user == null) {
                log.warn("用户不存在: {}", username);
                throw new UsernameNotFoundException("用户不存在: " + username);
            }

            // 2. 查询用户的角色关联关系
            List<UserRole> userRoles = userRoleMapper.selectList(
                    new LambdaQueryWrapper<UserRole>().eq(UserRole::getUserId, user.getId())
            );

            // 3. 根据角色 ID 列表查询角色编码
            List<String> roleCodes = List.of();
            if (!userRoles.isEmpty()) {
                List<Long> roleIds = userRoles.stream()
                        .map(UserRole::getRoleId)
                        .collect(Collectors.toList());
                List<Role> roles = roleMapper.selectBatchIds(roleIds);
                roleCodes = roles.stream()
                        .map(Role::getCode)
                        .collect(Collectors.toList());
            }

            // 4. 构建并返回 UserPrincipal
            return new UserPrincipal(
                    user.getId(),
                    user.getTenantId(),
                    user.getUsername(),
                    user.getPasswordHash(),
                    user.getUserType(),
                    user.getStatus(),
                    roleCodes
            );
        } finally {
            // 重置租户忽略标志，确保后续业务查询正常使用多租户过滤
            TenantContext.setIgnoreTenant(false);
        }
    }
}
