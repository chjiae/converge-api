package com.github.chjiae.service.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.github.chjiae.common.enums.UserStatus;
import com.github.chjiae.common.enums.UserType;
import com.github.chjiae.common.exception.BusinessException;
import com.github.chjiae.common.result.PageResult;
import com.github.chjiae.service.dto.user.CreateUserRequest;
import com.github.chjiae.service.dto.user.UpdateUserRequest;
import com.github.chjiae.service.dto.user.UserResponse;
import com.github.chjiae.service.entity.Role;
import com.github.chjiae.service.entity.User;
import com.github.chjiae.service.entity.UserRole;
import com.github.chjiae.service.mapper.RoleMapper;
import com.github.chjiae.service.mapper.UserMapper;
import com.github.chjiae.service.mapper.UserRoleMapper;
import com.github.chjiae.service.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 用户管理服务，提供租户内用户的 CRUD、状态管理和角色分配等功能。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    /** 用户数据访问层 */
    private final UserMapper userMapper;

    /** 角色数据访问层 */
    private final RoleMapper roleMapper;

    /** 用户角色关联数据访问层 */
    private final UserRoleMapper userRoleMapper;

    /** 密码编码器 */
    private final PasswordEncoder passwordEncoder;

    /**
     * 获取当前登录用户信息（含角色列表）
     *
     * @return 用户响应
     */
    public UserResponse getCurrentUser() {
        UserPrincipal principal = getCurrentUserPrincipal();
        Long currentUserId = principal.getUserId();
        log.info("查询当前用户信息，用户 ID: {}", currentUserId);

        User user = userMapper.selectById(currentUserId);
        if (user == null) {
            log.warn("查询当前用户失败，用户不存在: {}", currentUserId);
            throw new BusinessException(404, "用户不存在");
        }

        List<String> roleCodes = getUserRoleCodes(currentUserId);
        return toUserResponse(user, roleCodes);
    }

    /**
     * 更新当前用户信息（手机号、邮箱）
     *
     * @param request 更新请求参数
     * @return 更新后的用户响应
     */
    public UserResponse updateCurrentUser(UpdateUserRequest request) {
        UserPrincipal principal = getCurrentUserPrincipal();
        Long currentUserId = principal.getUserId();
        log.info("更新当前用户信息，用户 ID: {}", currentUserId);

        User user = userMapper.selectById(currentUserId);
        if (user == null) {
            log.warn("更新用户失败，用户不存在: {}", currentUserId);
            throw new BusinessException(404, "用户不存在");
        }

        // 仅更新非空字段
        if (request.getPhone() != null) {
            user.setPhone(request.getPhone());
        }
        if (request.getEmail() != null) {
            // 检查邮箱是否已被其他用户使用
            Long emailCount = userMapper.selectCount(
                    new LambdaQueryWrapper<User>()
                            .eq(User::getEmail, request.getEmail())
                            .eq(User::getTenantId, user.getTenantId())
                            .ne(User::getId, currentUserId)
            );
            if (emailCount > 0) {
                log.warn("更新用户失败，邮箱已被使用: {}", request.getEmail());
                throw new BusinessException(400, "邮箱已被使用");
            }
            user.setEmail(request.getEmail());
        }
        user.setUpdatedAt(LocalDateTime.now());
        userMapper.updateById(user);

        log.info("当前用户信息更新成功，用户 ID: {}", currentUserId);
        List<String> roleCodes = getUserRoleCodes(currentUserId);
        return toUserResponse(user, roleCodes);
    }

    /**
     * 修改当前用户密码（需验证旧密码）
     *
     * @param oldPassword 旧密码
     * @param newPassword 新密码
     */
    public void changePassword(String oldPassword, String newPassword) {
        UserPrincipal principal = getCurrentUserPrincipal();
        Long currentUserId = principal.getUserId();
        log.info("修改密码请求，用户 ID: {}", currentUserId);

        User user = userMapper.selectById(currentUserId);
        if (user == null) {
            log.warn("修改密码失败，用户不存在: {}", currentUserId);
            throw new BusinessException(404, "用户不存在");
        }

        // 验证旧密码
        if (!passwordEncoder.matches(oldPassword, user.getPasswordHash())) {
            log.warn("修改密码失败，旧密码错误，用户 ID: {}", currentUserId);
            throw new BusinessException(400, "旧密码不正确");
        }

        // 更新密码
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.setUpdatedAt(LocalDateTime.now());
        userMapper.updateById(user);

        log.info("密码修改成功，用户 ID: {}", currentUserId);
    }

    /**
     * 分页查询当前租户的用户列表（多租户拦截器自动注入 tenant_id）
     *
     * @param page 页码（从 1 开始）
     * @param size 每页数量
     * @return 分页用户列表
     */
    public PageResult<UserResponse> listUsers(int page, int size) {
        log.info("分页查询用户列表，页码: {}，每页数量: {}", page, size);

        // 多租户拦截器自动注入 tenant_id
        Page<User> pageParam = new Page<>(page, size);
        Page<User> resultPage = userMapper.selectPage(pageParam,
                new LambdaQueryWrapper<User>()
                        .orderByDesc(User::getCreatedAt)
        );

        List<UserResponse> list = resultPage.getRecords().stream()
                .map(user -> toUserResponse(user, getUserRoleCodes(user.getId())))
                .collect(Collectors.toList());

        return PageResult.of(list, resultPage.getTotal(), page, size);
    }

    /**
     * 创建用户（BCrypt 加密密码，默认分配 TENANT_MEMBER 角色）
     *
     * @param request 创建用户请求参数
     * @return 用户响应
     */
    @Transactional(rollbackFor = Exception.class)
    public UserResponse createUser(CreateUserRequest request) {
        UserPrincipal principal = getCurrentUserPrincipal();
        log.info("创建用户请求，用户名: {}，操作人: {}", request.getUsername(), principal.getUserId());

        // 检查用户名在当前租户内是否唯一（多租户拦截器自动注入 tenant_id）
        Long usernameCount = userMapper.selectCount(
                new LambdaQueryWrapper<User>().eq(User::getUsername, request.getUsername())
        );
        if (usernameCount > 0) {
            log.warn("创建用户失败，用户名已存在: {}", request.getUsername());
            throw new BusinessException(400, "用户名已存在");
        }

        // 检查邮箱在当前租户内是否唯一
        Long emailCount = userMapper.selectCount(
                new LambdaQueryWrapper<User>().eq(User::getEmail, request.getEmail())
        );
        if (emailCount > 0) {
            log.warn("创建用户失败，邮箱已被使用: {}", request.getEmail());
            throw new BusinessException(400, "邮箱已被使用");
        }

        // 创建用户
        User user = new User();
        user.setUsername(request.getUsername());
        user.setEmail(request.getEmail());
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setPhone(request.getPhone());
        user.setStatus(UserStatus.ACTIVE);
        user.setUserType(UserType.TENANT_USER);
        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());
        userMapper.insert(user);
        log.info("用户创建成功，用户 ID: {}，用户名: {}", user.getId(), user.getUsername());

        // 分配 TENANT_MEMBER 角色
        Role memberRole = roleMapper.selectOne(
                new LambdaQueryWrapper<Role>().eq(Role::getCode, "TENANT_MEMBER")
        );
        if (memberRole != null) {
            UserRole userRole = new UserRole();
            userRole.setUserId(user.getId());
            userRole.setRoleId(memberRole.getId());
            userRoleMapper.insert(userRole);
            log.info("为用户 {} 分配默认角色: TENANT_MEMBER", user.getUsername());
        }

        List<String> roleCodes = getUserRoleCodes(user.getId());
        return toUserResponse(user, roleCodes);
    }

    /**
     * 删除用户（不能删除自己）
     *
     * @param id 用户 ID
     */
    @Transactional(rollbackFor = Exception.class)
    public void deleteUser(Long id) {
        UserPrincipal principal = getCurrentUserPrincipal();
        Long currentUserId = principal.getUserId();
        log.info("删除用户请求，目标用户 ID: {}，操作人: {}", id, currentUserId);

        // 不能删除自己
        if (currentUserId.equals(id)) {
            log.warn("删除用户失败，不能删除自己: {}", currentUserId);
            throw new BusinessException(400, "不能删除自己");
        }

        // 检查用户是否存在
        User user = userMapper.selectById(id);
        if (user == null) {
            log.warn("删除用户失败，用户不存在: {}", id);
            throw new BusinessException(404, "用户不存在");
        }

        // 删除用户角色关联
        userRoleMapper.delete(
                new LambdaQueryWrapper<UserRole>().eq(UserRole::getUserId, id)
        );

        // 删除用户
        userMapper.deleteById(id);
        log.info("用户删除成功，用户 ID: {}", id);
    }

    /**
     * 启用/停用用户
     *
     * @param id     用户 ID
     * @param status 目标状态字符串（ACTIVE / DISABLED）
     * @return 更新后的用户响应
     */
    public UserResponse updateUserStatus(Long id, String status) {
        log.info("更新用户状态，用户 ID: {}，目标状态: {}", id, status);

        User user = userMapper.selectById(id);
        if (user == null) {
            log.warn("更新用户状态失败，用户不存在: {}", id);
            throw new BusinessException(404, "用户不存在");
        }

        // 解析状态
        UserStatus targetStatus;
        try {
            targetStatus = UserStatus.valueOf(status);
        } catch (IllegalArgumentException e) {
            log.warn("更新用户状态失败，无效的状态值: {}", status);
            throw new BusinessException(400, "无效的用户状态: " + status);
        }

        user.setStatus(targetStatus);
        user.setUpdatedAt(LocalDateTime.now());
        userMapper.updateById(user);

        log.info("用户状态更新成功，用户 ID: {}，新状态: {}", id, status);
        List<String> roleCodes = getUserRoleCodes(id);
        return toUserResponse(user, roleCodes);
    }

    /**
     * 为用户分配角色（先删旧关联，再加新关联）
     *
     * @param id      用户 ID
     * @param roleIds 角色 ID 列表
     * @return 更新后的用户响应
     */
    @Transactional(rollbackFor = Exception.class)
    public UserResponse assignRoles(Long id, List<Long> roleIds) {
        log.info("分配用户角色，用户 ID: {}，角色 ID 列表: {}", id, roleIds);

        User user = userMapper.selectById(id);
        if (user == null) {
            log.warn("分配角色失败，用户不存在: {}", id);
            throw new BusinessException(404, "用户不存在");
        }

        // 删除旧的角色关联
        userRoleMapper.delete(
                new LambdaQueryWrapper<UserRole>().eq(UserRole::getUserId, id)
        );

        // 添加新的角色关联
        if (roleIds != null && !roleIds.isEmpty()) {
            for (Long roleId : roleIds) {
                UserRole userRole = new UserRole();
                userRole.setUserId(id);
                userRole.setRoleId(roleId);
                userRoleMapper.insert(userRole);
            }
        }

        log.info("用户角色分配成功，用户 ID: {}，角色数量: {}", id, roleIds != null ? roleIds.size() : 0);
        List<String> roleCodes = getUserRoleCodes(id);
        return toUserResponse(user, roleCodes);
    }

    /**
     * 从 SecurityContext 获取当前登录用户
     *
     * @return 当前用户认证主体
     */
    private UserPrincipal getCurrentUserPrincipal() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return (UserPrincipal) auth.getPrincipal();
    }

    /**
     * 查询用户的角色编码列表
     *
     * @param userId 用户 ID
     * @return 角色编码列表
     */
    private List<String> getUserRoleCodes(Long userId) {
        List<UserRole> userRoles = userRoleMapper.selectList(
                new LambdaQueryWrapper<UserRole>().eq(UserRole::getUserId, userId)
        );
        if (userRoles.isEmpty()) {
            return List.of();
        }
        List<Long> roleIds = userRoles.stream().map(UserRole::getRoleId).collect(Collectors.toList());
        return roleMapper.selectBatchIds(roleIds).stream()
                .map(Role::getCode)
                .collect(Collectors.toList());
    }

    /**
     * 将用户实体转换为响应 DTO
     *
     * @param user      用户实体
     * @param roleCodes 角色编码列表
     * @return 用户响应 DTO
     */
    private UserResponse toUserResponse(User user, List<String> roleCodes) {
        return UserResponse.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .phone(user.getPhone())
                .status(user.getStatus().name())
                .userType(user.getUserType().name())
                .tenantId(user.getTenantId())
                .createdAt(user.getCreatedAt())
                .roles(roleCodes)
                .build();
    }
}
