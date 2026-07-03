package com.github.chjiae.service.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.github.chjiae.common.enums.TenantStatus;
import com.github.chjiae.common.enums.UserStatus;
import com.github.chjiae.common.enums.UserType;
import com.github.chjiae.common.exception.BusinessException;
import com.github.chjiae.service.dto.auth.*;
import com.github.chjiae.service.entity.*;
import com.github.chjiae.service.mapper.*;
import com.github.chjiae.service.security.JwtTokenProvider;
import com.github.chjiae.service.security.UserPrincipal;
import com.github.chjiae.service.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 认证服务，处理登录、注册、Token 刷新等认证相关逻辑。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    /** 用户数据访问层 */
    private final UserMapper userMapper;

    /** 租户数据访问层 */
    private final TenantMapper tenantMapper;

    /** 用户角色关联数据访问层 */
    private final UserRoleMapper userRoleMapper;

    /** 角色数据访问层 */
    private final RoleMapper roleMapper;

    /** JWT 令牌提供者 */
    private final JwtTokenProvider jwtTokenProvider;

    /** 密码编码器 */
    private final PasswordEncoder passwordEncoder;

    /**
     * 用户登录
     * 1. 根据用户名查找用户
     * 2. 验证密码
     * 3. 检查租户状态（ACTIVE 或 TRIAL 允许登录）
     * 4. 生成 Token
     * 5. 返回 TokenResponse
     *
     * @param request 登录请求参数
     * @return 令牌响应
     */
    public TokenResponse login(LoginRequest request) {
        log.info("用户登录请求，用户名: {}", request.getUsername());

        // 登录时忽略租户过滤，允许查询所有用户（包括超管和租户用户）
        TenantContext.setIgnoreTenant(true);

        // 1. 根据用户名查找用户
        User user = userMapper.selectOne(
                new LambdaQueryWrapper<User>().eq(User::getUsername, request.getUsername())
        );
        if (user == null) {
            log.warn("登录失败，用户不存在: {}", request.getUsername());
            throw new BusinessException(401, "用户名或密码错误");
        }

        // 2. 验证密码
        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            log.warn("登录失败，密码错误，用户: {}", request.getUsername());
            throw new BusinessException(401, "用户名或密码错误");
        }

        // 检查用户状态
        if (UserStatus.DISABLED == user.getStatus()) {
            log.warn("登录失败，用户已停用: {}", request.getUsername());
            throw new BusinessException(403, "账号已停用，请联系管理员");
        }

        // 3. 检查租户状态（超管无租户，跳过检查）
        Tenant tenant = null;
        if (user.getTenantId() != null) {
            tenant = tenantMapper.selectById(user.getTenantId());
            validateTenantStatus(tenant);
        }

        // 4. 查询用户角色并生成 Token
        List<String> roleCodes = getUserRoleCodes(user.getId());
        UserPrincipal principal = new UserPrincipal(
                user.getId(), user.getTenantId(), user.getUsername(),
                user.getPasswordHash(), user.getUserType(), user.getStatus(), roleCodes
        );

        return buildTokenResponse(principal, user, tenant, roleCodes);
    }

    /**
     * 用户注册（加入指定租户）
     * 1. 根据 tenantCode 查找租户
     * 2. 检查租户状态（仅 ACTIVE 或 TRIAL 允许注册）
     * 3. 检查用户名和邮箱在租户内是否唯一
     * 4. 创建用户（密码 BCrypt 加密）
     * 5. 分配 TENANT_MEMBER 角色
     * 6. 生成 Token
     * 7. 返回 TokenResponse
     *
     * @param request 注册请求参数
     * @return 令牌响应
     */
    public TokenResponse register(RegisterRequest request) {
        log.info("用户注册请求，用户名: {}，租户编码: {}", request.getUsername(), request.getTenantCode());

        // 注册时忽略租户过滤，允许跨租户查询用户和角色
        TenantContext.setIgnoreTenant(true);

        // 1. 根据 tenantCode 查找租户（tenant 表已在忽略列表中，无需特殊处理）
        Tenant tenant = tenantMapper.selectOne(
                new LambdaQueryWrapper<Tenant>().eq(Tenant::getCode, request.getTenantCode())
        );
        if (tenant == null) {
            log.warn("注册失败，租户不存在: {}", request.getTenantCode());
            throw new BusinessException(404, "租户不存在");
        }

        // 2. 检查租户状态（仅 ACTIVE 或 TRIAL 允许注册）
        if (tenant.getStatus() != TenantStatus.ACTIVE && tenant.getStatus() != TenantStatus.TRIAL) {
            log.warn("注册失败，租户状态不允许注册: {}，状态: {}", request.getTenantCode(), tenant.getStatus());
            throw new BusinessException(403, "该租户当前状态不允许注册");
        }

        // 3. 检查用户名在租户内是否唯一
        Long usernameCount = userMapper.selectCount(
                new LambdaQueryWrapper<User>()
                        .eq(User::getUsername, request.getUsername())
                        .eq(User::getTenantId, tenant.getId())
        );
        if (usernameCount > 0) {
            log.warn("注册失败，用户名已存在: {}", request.getUsername());
            throw new BusinessException(400, "用户名已存在");
        }

        // 检查邮箱在租户内是否唯一
        Long emailCount = userMapper.selectCount(
                new LambdaQueryWrapper<User>()
                        .eq(User::getEmail, request.getEmail())
                        .eq(User::getTenantId, tenant.getId())
        );
        if (emailCount > 0) {
            log.warn("注册失败，邮箱已被使用: {}", request.getEmail());
            throw new BusinessException(400, "邮箱已被使用");
        }

        // 4. 创建用户
        User user = new User();
        user.setTenantId(tenant.getId());
        user.setUsername(request.getUsername());
        user.setEmail(request.getEmail());
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setStatus(UserStatus.ACTIVE);
        user.setUserType(UserType.TENANT_USER);
        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());
        userMapper.insert(user);
        log.info("用户注册成功，用户 ID: {}，租户: {}", user.getId(), request.getTenantCode());

        // 5. 分配 TENANT_MEMBER 角色
        Role memberRole = roleMapper.selectOne(
                new LambdaQueryWrapper<Role>()
                        .eq(Role::getCode, "TENANT_MEMBER")
                        .eq(Role::getTenantId, tenant.getId())
        );
        if (memberRole != null) {
            UserRole userRole = new UserRole();
            userRole.setUserId(user.getId());
            userRole.setRoleId(memberRole.getId());
            userRoleMapper.insert(userRole);
            log.info("为用户 {} 分配角色: TENANT_MEMBER", user.getUsername());
        }

        // 6. 生成 Token
        List<String> roleCodes = getUserRoleCodes(user.getId());
        UserPrincipal principal = new UserPrincipal(
                user.getId(), user.getTenantId(), user.getUsername(),
                user.getPasswordHash(), user.getUserType(), user.getStatus(), roleCodes
        );

        return buildTokenResponse(principal, user, tenant, roleCodes);
    }

    /**
     * 刷新访问令牌
     * 验证 refresh token 并生成新的 access token
     *
     * @param refreshToken 刷新令牌
     * @return 新的令牌响应
     */
    public TokenResponse refreshToken(String refreshToken) {
        log.info("刷新访问令牌请求");

        // 验证刷新令牌
        if (!jwtTokenProvider.validateToken(refreshToken)) {
            log.warn("刷新令牌无效或已过期");
            throw new BusinessException(401, "刷新令牌无效或已过期");
        }

        // 从刷新令牌中解析用户信息
        Long userId = jwtTokenProvider.getUserIdFromToken(refreshToken);
        String username = jwtTokenProvider.getUsernameFromToken(refreshToken);

        // 刷新令牌时忽略租户过滤，允许查询所有用户（包括超管和跨租户用户）
        TenantContext.setIgnoreTenant(true);

        // 查询用户最新信息
        User user = userMapper.selectById(userId);
        if (user == null) {
            log.warn("刷新令牌失败，用户不存在: {}", userId);
            throw new BusinessException(401, "用户不存在");
        }

        // 查询租户信息
        Tenant tenant = null;
        if (user.getTenantId() != null) {
            tenant = tenantMapper.selectById(user.getTenantId());
            validateTenantStatus(tenant);
        }

        // 查询角色并生成新令牌
        List<String> roleCodes = getUserRoleCodes(user.getId());
        UserPrincipal principal = new UserPrincipal(
                user.getId(), user.getTenantId(), user.getUsername(),
                user.getPasswordHash(), user.getUserType(), user.getStatus(), roleCodes
        );

        log.info("访问令牌刷新成功，用户: {}", username);
        return buildTokenResponse(principal, user, tenant, roleCodes);
    }

    /**
     * 验证租户状态是否允许登录/注册
     *
     * @param tenant 租户实体
     */
    private void validateTenantStatus(Tenant tenant) {
        if (tenant == null) {
            throw new BusinessException(404, "租户不存在");
        }

        switch (tenant.getStatus()) {
            case ACTIVE:
            case TRIAL:
                // 允许登录
                break;
            case PENDING:
                throw new BusinessException(403, "租户待激活，请先充值或申请试用");
            case DISABLED:
                throw new BusinessException(403, "租户已停用，请联系管理员");
            case EXPIRED:
                // 检查是否已过期
                if (tenant.getExpiredAt() != null && tenant.getExpiredAt().isBefore(LocalDateTime.now())) {
                    throw new BusinessException(403, "租户已过期，请续费");
                }
                // 未实际过期，允许登录
                break;
            case DELETED:
                throw new BusinessException(404, "租户不存在");
            default:
                throw new BusinessException(403, "租户状态异常");
        }
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
     * 构建令牌响应对象
     *
     * @param principal 用户认证主体
     * @param user      用户实体
     * @param tenant    租户实体（超管为 null）
     * @param roleCodes 角色编码列表
     * @return 令牌响应
     */
    private TokenResponse buildTokenResponse(UserPrincipal principal, User user, Tenant tenant, List<String> roleCodes) {
        String accessToken = jwtTokenProvider.generateAccessToken(principal);
        String refreshToken = jwtTokenProvider.generateRefreshToken(principal);

        UserInfoResponse userInfo = UserInfoResponse.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .phone(user.getPhone())
                .userType(user.getUserType().name())
                .tenantId(user.getTenantId())
                .tenantName(tenant != null ? tenant.getName() : null)
                .roles(roleCodes)
                .build();

        return TokenResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .userInfo(userInfo)
                .build();
    }
}
