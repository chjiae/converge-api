package com.github.chjiae.service.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.github.chjiae.common.enums.ApplicationStatus;
import com.github.chjiae.common.enums.ApplicationType;
import com.github.chjiae.common.enums.TenantStatus;
import com.github.chjiae.common.enums.UserStatus;
import com.github.chjiae.common.enums.UserType;
import com.github.chjiae.common.exception.BusinessException;
import com.github.chjiae.common.result.PageResult;
import com.github.chjiae.service.dto.application.ApplicationResponse;
import com.github.chjiae.service.dto.application.CreateApplicationRequest;
import com.github.chjiae.service.entity.Role;
import com.github.chjiae.service.entity.Tenant;
import com.github.chjiae.service.entity.TenantApplication;
import com.github.chjiae.service.entity.User;
import com.github.chjiae.service.entity.UserRole;
import com.github.chjiae.service.mapper.RoleMapper;
import com.github.chjiae.service.mapper.TenantApplicationMapper;
import com.github.chjiae.service.mapper.TenantMapper;
import com.github.chjiae.service.mapper.UserMapper;
import com.github.chjiae.service.mapper.UserRoleMapper;
import com.github.chjiae.service.security.UserPrincipal;
import com.github.chjiae.service.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 申请审核服务，提供租户注册申请的提交、查询、审核通过和拒绝功能。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ApplicationService {

    /** 租户申请数据访问层 */
    private final TenantApplicationMapper applicationMapper;

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

    /**
     * 提交租户申请（公开接口）
     * 创建申请记录，状态为 PENDING
     *
     * @param request 创建申请请求参数
     * @return 申请响应
     */
    public ApplicationResponse submitApplication(CreateApplicationRequest request) {
        log.info("提交租户申请，公司名称: {}，申请类型: {}", request.getCompanyName(), request.getApplicationType());

        // 解析申请类型
        ApplicationType appType;
        try {
            appType = ApplicationType.valueOf(request.getApplicationType());
        } catch (IllegalArgumentException e) {
            log.warn("提交申请失败，申请类型无效: {}", request.getApplicationType());
            throw new BusinessException(400, "申请类型无效，仅支持 REGISTER 或 TRIAL");
        }

        TenantApplication application = new TenantApplication();
        application.setCompanyName(request.getCompanyName());
        application.setContactName(request.getContactName());
        application.setContactEmail(request.getContactEmail());
        application.setContactPhone(request.getContactPhone());
        application.setDescription(request.getDescription());
        application.setAdminUsername(request.getAdminUsername());
        application.setAdminEmail(request.getAdminEmail());
        application.setAdminPassword(request.getAdminPassword());
        application.setApplicationType(appType);
        application.setStatus(ApplicationStatus.PENDING);
        application.setCreatedAt(LocalDateTime.now());
        application.setUpdatedAt(LocalDateTime.now());
        applicationMapper.insert(application);

        log.info("租户申请提交成功，申请 ID: {}", application.getId());
        return toApplicationResponse(application);
    }

    /**
     * 根据 ID 查询申请状态（公开接口）
     *
     * @param id 申请 ID
     * @return 申请响应
     */
    public ApplicationResponse getApplicationById(Long id) {
        log.info("查询申请状态，申请 ID: {}", id);

        TenantApplication application = applicationMapper.selectById(id);
        if (application == null) {
            log.warn("查询申请失败，申请不存在，ID: {}", id);
            throw new BusinessException(404, "申请不存在");
        }

        return toApplicationResponse(application);
    }

    /**
     * 分页查询申请列表（需忽略租户过滤）
     *
     * @param page 页码（从 1 开始）
     * @param size 每页数量
     * @return 分页申请列表
     */
    public PageResult<ApplicationResponse> listApplications(int page, int size) {
        log.info("分页查询申请列表，页码: {}，每页数量: {}", page, size);

        // 申请表无 tenant_id 字段，忽略租户过滤
        TenantContext.setIgnoreTenant(true);

        Page<TenantApplication> pageParam = new Page<>(page, size);
        Page<TenantApplication> resultPage = applicationMapper.selectPage(pageParam,
                new LambdaQueryWrapper<TenantApplication>()
                        .orderByDesc(TenantApplication::getCreatedAt)
        );

        List<ApplicationResponse> list = resultPage.getRecords().stream()
                .map(this::toApplicationResponse)
                .collect(Collectors.toList());

        return PageResult.of(list, resultPage.getTotal(), page, size);
    }

    /**
     * 审核通过申请（事务方法）
     * 1. 检查申请状态为 PENDING
     * 2. 创建租户（TRIAL→TRIAL 状态 + expiredAt=now+7天，REGISTER→PENDING 待充值）
     * 3. 创建管理员用户 + 内置角色 + 分配 TENANT_OWNER
     * 4. 更新申请状态为 APPROVED
     *
     * @param id 申请 ID
     * @return 更新后的申请响应
     */
    @Transactional(rollbackFor = Exception.class)
    public ApplicationResponse approveApplication(Long id) {
        log.info("审核通过申请，申请 ID: {}", id);

        // 操作跨表时忽略租户过滤
        TenantContext.setIgnoreTenant(true);

        // 1. 查询并检查申请状态
        TenantApplication application = applicationMapper.selectById(id);
        if (application == null) {
            log.warn("审核失败，申请不存在，ID: {}", id);
            throw new BusinessException(404, "申请不存在");
        }
        if (application.getStatus() != ApplicationStatus.PENDING) {
            log.warn("审核失败，申请状态不是待审核，ID: {}，状态: {}", id, application.getStatus());
            throw new BusinessException(400, "申请已被处理，无法重复审核");
        }

        // 2. 创建租户（根据申请类型设置状态）
        Tenant tenant = new Tenant();
        tenant.setCode(generateTenantCode(application));
        tenant.setName(application.getCompanyName());
        tenant.setDescription(application.getDescription());
        tenant.setTrialUsed(application.getApplicationType() == ApplicationType.TRIAL);

        if (application.getApplicationType() == ApplicationType.TRIAL) {
            // 试用类型：状态 TRIAL，到期时间为 7 天后
            tenant.setStatus(TenantStatus.TRIAL);
            tenant.setExpiredAt(LocalDateTime.now().plusDays(7));
        } else {
            // 注册充值类型：状态 PENDING，待充值激活
            tenant.setStatus(TenantStatus.PENDING);
        }

        tenant.setCreatedBy(getCurrentUserId());
        tenant.setCreatedAt(LocalDateTime.now());
        tenant.setUpdatedAt(LocalDateTime.now());
        tenantMapper.insert(tenant);
        log.info("审核通过，租户创建成功，ID: {}，编码: {}", tenant.getId(), tenant.getCode());

        // 3. 创建管理员用户（使用申请时提交的管理员信息）
        User adminUser = new User();
        adminUser.setTenantId(tenant.getId());
        adminUser.setUsername(application.getAdminUsername());
        adminUser.setEmail(application.getAdminEmail());
        adminUser.setPasswordHash(passwordEncoder.encode(application.getAdminPassword()));
        adminUser.setStatus(UserStatus.ACTIVE);
        adminUser.setUserType(UserType.TENANT_USER);
        adminUser.setCreatedAt(LocalDateTime.now());
        adminUser.setUpdatedAt(LocalDateTime.now());
        userMapper.insert(adminUser);
        log.info("审核通过，管理员用户创建成功，用户 ID: {}，用户名: {}", adminUser.getId(), adminUser.getUsername());

        // 4. 创建三个内置角色
        Role ownerRole = createBuiltinRole(tenant.getId(), "TENANT_OWNER", "租户所有者", "拥有租户内全部权限");
        createBuiltinRole(tenant.getId(), "TENANT_ADMIN", "租户管理员", "租户管理权限");
        createBuiltinRole(tenant.getId(), "TENANT_MEMBER", "租户成员", "租户基本使用权限");

        // 5. 将管理员用户关联 TENANT_OWNER 角色
        UserRole userRole = new UserRole();
        userRole.setUserId(adminUser.getId());
        userRole.setRoleId(ownerRole.getId());
        userRoleMapper.insert(userRole);
        log.info("管理员用户已分配 TENANT_OWNER 角色");

        // 6. 更新申请状态为 APPROVED
        application.setStatus(ApplicationStatus.APPROVED);
        application.setReviewedBy(getCurrentUserId());
        application.setReviewedAt(LocalDateTime.now());
        application.setUpdatedAt(LocalDateTime.now());
        applicationMapper.updateById(application);

        log.info("申请审核通过，申请 ID: {}", id);
        return toApplicationResponse(application);
    }

    /**
     * 审核拒绝申请
     *
     * @param id           申请 ID
     * @param rejectReason 拒绝原因
     * @return 更新后的申请响应
     */
    public ApplicationResponse rejectApplication(Long id, String rejectReason) {
        log.info("审核拒绝申请，申请 ID: {}", id);

        // 操作跨表时忽略租户过滤
        TenantContext.setIgnoreTenant(true);

        // 查询并检查申请状态
        TenantApplication application = applicationMapper.selectById(id);
        if (application == null) {
            log.warn("拒绝失败，申请不存在，ID: {}", id);
            throw new BusinessException(404, "申请不存在");
        }
        if (application.getStatus() != ApplicationStatus.PENDING) {
            log.warn("拒绝失败，申请状态不是待审核，ID: {}，状态: {}", id, application.getStatus());
            throw new BusinessException(400, "申请已被处理，无法重复审核");
        }

        // 更新申请状态为 REJECTED
        application.setStatus(ApplicationStatus.REJECTED);
        application.setRejectReason(rejectReason);
        application.setReviewedBy(getCurrentUserId());
        application.setReviewedAt(LocalDateTime.now());
        application.setUpdatedAt(LocalDateTime.now());
        applicationMapper.updateById(application);

        log.info("申请审核拒绝，申请 ID: {}", id);
        return toApplicationResponse(application);
    }

    /**
     * 生成租户编码（基于公司名称自动生成）
     *
     * @param application 申请实体
     * @return 租户编码
     */
    private String generateTenantCode(TenantApplication application) {
        // 使用公司名称拼音首字母或随机编码，这里简化处理
        // 实际生产中可能需要更复杂的编码生成策略
        String base = application.getCompanyName().replaceAll("[^a-zA-Z0-9]", "").toLowerCase();
        if (base.isEmpty()) {
            base = "tenant";
        }
        // 添加时间戳后缀确保唯一性
        String code = base + "_" + System.currentTimeMillis();
        // 截断过长编码
        if (code.length() > 32) {
            code = code.substring(0, 32);
        }
        return code;
    }

    /**
     * 获取当前登录用户 ID
     *
     * @return 用户 ID，未登录时返回 null
     */
    private Long getCurrentUserId() {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (principal instanceof UserPrincipal) {
            return ((UserPrincipal) principal).getUserId();
        }
        return null;
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
     * 将申请实体转换为响应 DTO
     *
     * @param application 申请实体
     * @return 申请响应 DTO
     */
    private ApplicationResponse toApplicationResponse(TenantApplication application) {
        return ApplicationResponse.builder()
                .id(application.getId())
                .companyName(application.getCompanyName())
                .contactName(application.getContactName())
                .contactEmail(application.getContactEmail())
                .contactPhone(application.getContactPhone())
                .description(application.getDescription())
                .applicationType(application.getApplicationType().name())
                .status(application.getStatus().name())
                .rejectReason(application.getRejectReason())
                .reviewedBy(application.getReviewedBy())
                .reviewedAt(application.getReviewedAt())
                .createdAt(application.getCreatedAt())
                .updatedAt(application.getUpdatedAt())
                .build();
    }
}
