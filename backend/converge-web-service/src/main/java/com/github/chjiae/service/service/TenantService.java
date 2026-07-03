package com.github.chjiae.service.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.github.chjiae.common.enums.TenantStatus;
import com.github.chjiae.common.enums.UserStatus;
import com.github.chjiae.common.enums.UserType;
import com.github.chjiae.common.exception.BusinessException;
import com.github.chjiae.common.result.PageResult;
import com.github.chjiae.service.dto.tenant.CreateTenantRequest;
import com.github.chjiae.service.dto.tenant.TenantResponse;
import com.github.chjiae.service.dto.tenant.UpdateTenantRequest;
import com.github.chjiae.service.entity.Role;
import com.github.chjiae.service.entity.Tenant;
import com.github.chjiae.service.entity.User;
import com.github.chjiae.service.entity.UserRole;
import com.github.chjiae.service.mapper.RoleMapper;
import com.github.chjiae.service.mapper.TenantMapper;
import com.github.chjiae.service.mapper.UserMapper;
import com.github.chjiae.service.mapper.UserRoleMapper;
import com.github.chjiae.service.cache.TenantCacheService;
import com.github.chjiae.service.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 租户管理服务，提供租户的创建、查询、更新、启停用等功能。
 * 所有操作均需超管权限（由 Controller 层 @PreAuthorize 控制）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TenantService {

    /** 租户数据访问层 */
    private final TenantMapper tenantMapper;

    /** 用户数据访问层 */
    private final UserMapper userMapper;

    /** 角色数据访问层 */
    private final RoleMapper roleMapper;

    /** 用户角色关联数据访问层 */
    private final UserRoleMapper userRoleMapper;

    /** 密码编码器 */
    private final PasswordEncoder passwordEncoder;

    /** 租户状态缓存服务 */
    private final TenantCacheService tenantCacheService;

    /**
     * 创建租户（事务方法）
     * 1. 检查租户编码唯一性
     * 2. 创建租户实体（状态 ACTIVE）
     * 3. 创建管理员用户（userType=TENANT_USER）
     * 4. 创建三个内置角色：TENANT_OWNER、TENANT_ADMIN、TENANT_MEMBER
     * 5. 将管理员用户关联 TENANT_OWNER 角色
     * 6. 返回 TenantResponse
     *
     * @param request 创建租户请求参数
     * @return 租户响应
     */
    @Transactional(rollbackFor = Exception.class)
    public TenantResponse createTenant(CreateTenantRequest request) {
        log.info("创建租户请求，编码: {}，名称: {}", request.getCode(), request.getName());

        // 操作租户相关表时忽略租户过滤
        TenantContext.setIgnoreTenant(true);

        // 1. 检查租户编码唯一性
        Long codeCount = tenantMapper.selectCount(
                new LambdaQueryWrapper<Tenant>().eq(Tenant::getCode, request.getCode())
        );
        if (codeCount > 0) {
            log.warn("创建租户失败，编码已存在: {}", request.getCode());
            throw new BusinessException(400, "租户编码已存在");
        }

        // 2. 创建租户实体
        Tenant tenant = new Tenant();
        tenant.setCode(request.getCode());
        tenant.setName(request.getName());
        tenant.setDescription(request.getDescription());
        tenant.setStatus(TenantStatus.ACTIVE);
        tenant.setTrialUsed(false);
        tenant.setCreatedAt(LocalDateTime.now());
        tenant.setUpdatedAt(LocalDateTime.now());
        tenantMapper.insert(tenant);
        log.info("租户创建成功，ID: {}，编码: {}", tenant.getId(), tenant.getCode());

        // 3. 创建管理员用户
        User adminUser = new User();
        adminUser.setTenantId(tenant.getId());
        adminUser.setUsername(request.getAdminUsername());
        adminUser.setEmail(request.getAdminEmail());
        adminUser.setPasswordHash(passwordEncoder.encode(request.getAdminPassword()));
        adminUser.setStatus(UserStatus.ACTIVE);
        adminUser.setUserType(UserType.TENANT_USER);
        adminUser.setCreatedAt(LocalDateTime.now());
        adminUser.setUpdatedAt(LocalDateTime.now());
        userMapper.insert(adminUser);
        log.info("租户管理员用户创建成功，用户 ID: {}，用户名: {}", adminUser.getId(), adminUser.getUsername());

        // 4. 创建三个内置角色
        Role ownerRole = createBuiltinRole(tenant.getId(), "TENANT_OWNER", "租户所有者", "拥有租户内全部权限");
        createBuiltinRole(tenant.getId(), "TENANT_ADMIN", "租户管理员", "租户管理权限");
        createBuiltinRole(tenant.getId(), "TENANT_MEMBER", "租户成员", "租户基本使用权限");

        // 5. 将管理员用户关联 TENANT_OWNER 角色
        UserRole userRole = new UserRole();
        userRole.setUserId(adminUser.getId());
        userRole.setRoleId(ownerRole.getId());
        userRoleMapper.insert(userRole);
        log.info("管理员用户 {} 已分配 TENANT_OWNER 角色", adminUser.getUsername());

        // 6. 返回响应
        return toTenantResponse(tenant);
    }

    /**
     * 分页查询所有租户
     *
     * @param page 页码（从 1 开始）
     * @param size 每页数量
     * @return 分页租户列表
     */
    public PageResult<TenantResponse> listTenants(int page, int size) {
        log.info("分页查询租户列表，页码: {}，每页数量: {}", page, size);

        // 查询租户时忽略租户过滤
        TenantContext.setIgnoreTenant(true);

        Page<Tenant> pageParam = new Page<>(page, size);
        Page<Tenant> resultPage = tenantMapper.selectPage(pageParam,
                new LambdaQueryWrapper<Tenant>()
                        .ne(Tenant::getStatus, TenantStatus.DELETED)
                        .orderByDesc(Tenant::getCreatedAt)
        );

        List<TenantResponse> list = resultPage.getRecords().stream()
                .map(this::toTenantResponse)
                .collect(Collectors.toList());

        return PageResult.of(list, resultPage.getTotal(), page, size);
    }

    /**
     * 获取租户详情
     *
     * @param id 租户 ID
     * @return 租户响应
     */
    public TenantResponse getTenant(Long id) {
        log.info("查询租户详情，ID: {}", id);

        // 查询租户时忽略租户过滤
        TenantContext.setIgnoreTenant(true);

        Tenant tenant = tenantMapper.selectById(id);
        if (tenant == null || tenant.getStatus() == TenantStatus.DELETED) {
            log.warn("租户不存在，ID: {}", id);
            throw new BusinessException(404, "租户不存在");
        }

        return toTenantResponse(tenant);
    }

    /**
     * 更新租户名称和描述
     *
     * @param id      租户 ID
     * @param request 更新请求参数
     * @return 更新后的租户响应
     */
    public TenantResponse updateTenant(Long id, UpdateTenantRequest request) {
        log.info("更新租户，ID: {}", id);

        // 操作租户相关表时忽略租户过滤
        TenantContext.setIgnoreTenant(true);

        Tenant tenant = tenantMapper.selectById(id);
        if (tenant == null || tenant.getStatus() == TenantStatus.DELETED) {
            log.warn("更新失败，租户不存在，ID: {}", id);
            throw new BusinessException(404, "租户不存在");
        }

        // 仅更新非空字段
        if (request.getName() != null) {
            tenant.setName(request.getName());
        }
        if (request.getDescription() != null) {
            tenant.setDescription(request.getDescription());
        }
        tenant.setUpdatedAt(LocalDateTime.now());
        tenantMapper.updateById(tenant);

        log.info("租户更新成功，ID: {}", id);
        // 清除租户缓存，确保下次查询获取最新状态
        tenantCacheService.evictTenantCache(id);
        return toTenantResponse(tenant);
    }

    /**
     * 软删除租户（设置状态为 DELETED）
     *
     * @param id 租户 ID
     */
    public void deleteTenant(Long id) {
        log.info("删除租户（软删除），ID: {}", id);

        // 操作租户相关表时忽略租户过滤
        TenantContext.setIgnoreTenant(true);

        Tenant tenant = tenantMapper.selectById(id);
        if (tenant == null || tenant.getStatus() == TenantStatus.DELETED) {
            log.warn("删除失败，租户不存在，ID: {}", id);
            throw new BusinessException(404, "租户不存在");
        }

        tenant.setStatus(TenantStatus.DELETED);
        tenant.setUpdatedAt(LocalDateTime.now());
        tenantMapper.updateById(tenant);

        log.info("租户已软删除，ID: {}", id);
        // 清除租户缓存，确保下次查询获取最新状态
        tenantCacheService.evictTenantCache(id);
    }

    /**
     * 启用租户（设置状态为 ACTIVE）
     *
     * @param id 租户 ID
     */
    public void enableTenant(Long id) {
        log.info("启用租户，ID: {}", id);

        // 操作租户相关表时忽略租户过滤
        TenantContext.setIgnoreTenant(true);

        Tenant tenant = tenantMapper.selectById(id);
        if (tenant == null || tenant.getStatus() == TenantStatus.DELETED) {
            log.warn("启用失败，租户不存在，ID: {}", id);
            throw new BusinessException(404, "租户不存在");
        }

        tenant.setStatus(TenantStatus.ACTIVE);
        tenant.setUpdatedAt(LocalDateTime.now());
        tenantMapper.updateById(tenant);

        log.info("租户已启用，ID: {}", id);
        // 清除租户缓存，确保下次查询获取最新状态
        tenantCacheService.evictTenantCache(id);
    }

    /**
     * 停用租户（设置状态为 DISABLED）
     *
     * @param id 租户 ID
     */
    public void disableTenant(Long id) {
        log.info("停用租户，ID: {}", id);

        // 操作租户相关表时忽略租户过滤
        TenantContext.setIgnoreTenant(true);

        Tenant tenant = tenantMapper.selectById(id);
        if (tenant == null || tenant.getStatus() == TenantStatus.DELETED) {
            log.warn("停用失败，租户不存在，ID: {}", id);
            throw new BusinessException(404, "租户不存在");
        }

        tenant.setStatus(TenantStatus.DISABLED);
        tenant.setUpdatedAt(LocalDateTime.now());
        tenantMapper.updateById(tenant);

        log.info("租户已停用，ID: {}", id);
        // 清除租户缓存，确保下次查询获取最新状态
        tenantCacheService.evictTenantCache(id);
    }

    /**
     * 创建内置角色
     *
     * @param tenantId    租户 ID
     * @param code        角色编码
     * @param name        角色名称
     * @param description 角色描述
     * @return 创建的角色实体
     */
    private Role createBuiltinRole(Long tenantId, String code, String name, String description) {
        Role role = new Role();
        role.setTenantId(tenantId);
        role.setCode(code);
        role.setName(name);
        role.setDescription(description);
        role.setIsSystem(true);
        role.setCreatedAt(LocalDateTime.now());
        role.setUpdatedAt(LocalDateTime.now());
        roleMapper.insert(role);
        log.info("内置角色创建成功: {}，租户 ID: {}", code, tenantId);
        return role;
    }

    /**
     * 将租户实体转换为响应 DTO
     *
     * @param tenant 租户实体
     * @return 租户响应 DTO
     */
    private TenantResponse toTenantResponse(Tenant tenant) {
        return TenantResponse.builder()
                .id(tenant.getId())
                .code(tenant.getCode())
                .name(tenant.getName())
                .description(tenant.getDescription())
                .status(tenant.getStatus().name())
                .trialUsed(tenant.getTrialUsed())
                .expiredAt(tenant.getExpiredAt())
                .createdAt(tenant.getCreatedAt())
                .updatedAt(tenant.getUpdatedAt())
                .createdBy(tenant.getCreatedBy())
                .build();
    }
}
