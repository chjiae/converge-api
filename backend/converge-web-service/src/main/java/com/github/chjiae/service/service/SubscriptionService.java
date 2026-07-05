package com.github.chjiae.service.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.github.chjiae.common.enums.SubscriptionStatus;
import com.github.chjiae.common.enums.TenantStatus;
import com.github.chjiae.common.exception.BusinessException;
import com.github.chjiae.common.result.PageResult;
import com.github.chjiae.service.dto.subscription.CreateSubscriptionRequest;
import com.github.chjiae.service.dto.subscription.CreateRenewalRequest;
import com.github.chjiae.service.dto.subscription.SubscriptionResponse;
import com.github.chjiae.service.entity.Subscription;
import com.github.chjiae.service.entity.SubscriptionPlan;
import com.github.chjiae.service.entity.Tenant;
import com.github.chjiae.service.mapper.SubscriptionMapper;
import com.github.chjiae.service.mapper.TenantMapper;
import com.github.chjiae.service.security.UserPrincipal;
import com.github.chjiae.service.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 订阅管理服务，提供订阅创建、支付标记、列表查询和续费等功能。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SubscriptionService {

    /** 订阅数据访问层 */
    private final SubscriptionMapper subscriptionMapper;

    /** 租户数据访问层 */
    private final TenantMapper tenantMapper;

    /** 订阅套餐配置服务 */
    private final SubscriptionPlanService subscriptionPlanService;

    /**
     * 创建订阅记录（超管/运营为指定租户创建线下订阅）
     * 需要忽略多租户拦截器，手动指定租户 ID。
     *
     * @param request 创建订阅请求参数
     * @return 订阅响应
     */
    @Transactional(rollbackFor = Exception.class)
    public SubscriptionResponse createSubscription(CreateSubscriptionRequest request) {
        log.info("创建订阅请求，租户 ID: {}，套餐类型: {}", request.getTenantId(), request.getPlanType());

        // 忽略多租户过滤，操作指定租户的订阅
        TenantContext.setIgnoreTenant(true);

        // 验证租户是否存在
        Tenant tenant = tenantMapper.selectById(request.getTenantId());
        if (tenant == null) {
            log.warn("创建订阅失败，租户不存在: {}", request.getTenantId());
            throw new BusinessException(404, "租户不存在");
        }

        // 获取当前操作人
        UserPrincipal principal = getCurrentUser();

        // 创建订阅记录，初始状态为 PENDING
        Subscription subscription = new Subscription();
        subscription.setTenantId(request.getTenantId());
        subscription.setPlanType(request.getPlanType());
        subscription.setAmount(request.getAmount());
        subscription.setStartDate(request.getStartDate());
        subscription.setEndDate(request.getEndDate());
        subscription.setStatus(SubscriptionStatus.PENDING);
        subscription.setPaymentMethod(request.getPaymentMethod());
        subscription.setRemark(request.getRemark());
        subscription.setCreatedBy(principal.getUserId());
        subscription.setCreatedAt(LocalDateTime.now());
        subscription.setUpdatedAt(LocalDateTime.now());
        subscriptionMapper.insert(subscription);

        log.info("订阅创建成功，订阅 ID: {}，租户 ID: {}", subscription.getId(), request.getTenantId());
        return toSubscriptionResponse(subscription);
    }

    /**
     * 标记订阅为已支付
     * 1. 更新订阅状态为 ACTIVE
     * 2. 延长租户到期时间（endDate - startDate 的天数）
     * 3. 将租户状态设为 ACTIVE
     *
     * @param id 订阅 ID
     * @return 更新后的订阅响应
     */
    @Transactional(rollbackFor = Exception.class)
    public SubscriptionResponse markAsPaid(Long id) {
        log.info("标记订阅已支付，订阅 ID: {}", id);

        // 忽略多租户过滤
        TenantContext.setIgnoreTenant(true);

        // 查询订阅记录
        Subscription subscription = subscriptionMapper.selectById(id);
        if (subscription == null) {
            log.warn("标记支付失败，订阅不存在: {}", id);
            throw new BusinessException(404, "订阅不存在");
        }

        // 仅 PENDING 状态的订阅可以标记为已支付
        if (subscription.getStatus() != SubscriptionStatus.PENDING) {
            log.warn("标记支付失败，订阅状态不允许支付: {}，当前状态: {}", id, subscription.getStatus());
            throw new BusinessException(400, "当前订阅状态不允许标记支付");
        }

        // 1. 更新订阅状态为 ACTIVE
        subscription.setStatus(SubscriptionStatus.ACTIVE);
        subscription.setUpdatedAt(LocalDateTime.now());
        subscriptionMapper.updateById(subscription);

        // 2. 延长租户到期时间
        Tenant tenant = tenantMapper.selectById(subscription.getTenantId());
        if (tenant != null) {
            // 计算订阅天数
            long days = ChronoUnit.DAYS.between(subscription.getStartDate(), subscription.getEndDate());

            // 如果到期时间已过或为 null，则从现在开始计算
            LocalDateTime now = LocalDateTime.now();
            LocalDateTime baseDate;
            if (tenant.getExpiredAt() == null || tenant.getExpiredAt().isBefore(now)) {
                baseDate = now;
            } else {
                baseDate = tenant.getExpiredAt();
            }
            tenant.setExpiredAt(baseDate.plusDays(days));

            // 3. 将租户状态设为 ACTIVE
            tenant.setStatus(TenantStatus.ACTIVE);
            tenant.setUpdatedAt(LocalDateTime.now());
            tenantMapper.updateById(tenant);

            log.info("租户到期时间已延长，租户 ID: {}，新到期时间: {}", tenant.getId(), tenant.getExpiredAt());
        }

        log.info("订阅已标记为已支付，订阅 ID: {}", id);
        return toSubscriptionResponse(subscription);
    }

    /**
     * 分页查询全平台订阅列表（需忽略租户过滤）
     *
     * @param page 页码（从 1 开始）
     * @param size 每页数量
     * @return 分页订阅列表
     */
    public PageResult<SubscriptionResponse> listSubscriptions(int page, int size) {
        log.info("分页查询全平台订阅列表，页码: {}，每页数量: {}", page, size);

        // 忽略多租户过滤，查询所有订阅
        TenantContext.setIgnoreTenant(true);

        Page<Subscription> pageParam = new Page<>(page, size);
        Page<Subscription> resultPage = subscriptionMapper.selectPage(pageParam,
                new LambdaQueryWrapper<Subscription>()
                        .orderByDesc(Subscription::getCreatedAt)
        );

        List<SubscriptionResponse> list = resultPage.getRecords().stream()
                .map(this::toSubscriptionResponse)
                .collect(Collectors.toList());

        return PageResult.of(list, resultPage.getTotal(), page, size);
    }

    /**
     * 分页查询当前租户的订阅列表（多租户拦截器自动注入 tenant_id）
     *
     * @param page 页码（从 1 开始）
     * @param size 每页数量
     * @return 分页订阅列表
     */
    public PageResult<SubscriptionResponse> listMySubscriptions(int page, int size) {
        log.info("分页查询当前租户订阅列表，页码: {}，每页数量: {}", page, size);

        // 多租户拦截器自动注入 tenant_id，无需手动设置
        Page<Subscription> pageParam = new Page<>(page, size);
        Page<Subscription> resultPage = subscriptionMapper.selectPage(pageParam,
                new LambdaQueryWrapper<Subscription>()
                        .orderByDesc(Subscription::getCreatedAt)
        );

        List<SubscriptionResponse> list = resultPage.getRecords().stream()
                .map(this::toSubscriptionResponse)
                .collect(Collectors.toList());

        return PageResult.of(list, resultPage.getTotal(), page, size);
    }

    /**
     * 租户管理员发起续费（创建 PENDING 状态的订阅记录）
     * 与 createSubscription 类似，但 createdBy 为当前租户管理员。
     *
     * @param request 续费请求参数
     * @return 订阅响应
     */
    @Transactional(rollbackFor = Exception.class)
    public SubscriptionResponse initiateRenewal(CreateSubscriptionRequest request) {
        UserPrincipal principal = getCurrentUser();
        log.info("租户发起续费，租户 ID: {}，套餐类型: {}，操作人: {}", request.getTenantId(), request.getPlanType(), principal.getUserId());

        // 创建 PENDING 状态的订阅记录
        Subscription subscription = new Subscription();
        subscription.setTenantId(request.getTenantId());
        subscription.setPlanType(request.getPlanType());
        subscription.setAmount(request.getAmount());
        subscription.setStartDate(request.getStartDate());
        subscription.setEndDate(request.getEndDate());
        subscription.setStatus(SubscriptionStatus.PENDING);
        subscription.setPaymentMethod(request.getPaymentMethod());
        subscription.setRemark(request.getRemark());
        subscription.setCreatedBy(principal.getUserId());
        subscription.setCreatedAt(LocalDateTime.now());
        subscription.setUpdatedAt(LocalDateTime.now());
        subscriptionMapper.insert(subscription);

        log.info("续费订阅创建成功，订阅 ID: {}，租户 ID: {}", subscription.getId(), request.getTenantId());
        return toSubscriptionResponse(subscription);
    }

    /**
     * 租户基于套餐配置发起续费。
     *
     * 前端只提交套餐 ID 和支付方式，金额、周期和租户 ID 均由后端计算，避免篡改。
     *
     * @param request 续费请求参数
     * @return 订阅响应
     */
    @Transactional(rollbackFor = Exception.class)
    public SubscriptionResponse createRenewalFromPlan(CreateRenewalRequest request) {
        UserPrincipal principal = getCurrentUser();
        if (principal.getTenantId() == null) {
            throw new BusinessException(400, "当前用户未关联租户，无法续费");
        }
        log.info("租户基于套餐发起续费，租户 ID: {}，套餐 ID: {}，支付方式: {}",
                principal.getTenantId(), request.getPlanId(), request.getPaymentMethod());

        SubscriptionPlan plan = subscriptionPlanService.getEnabledPlan(request.getPlanId());
        Tenant tenant = tenantMapper.selectById(principal.getTenantId());
        if (tenant == null) {
            throw new BusinessException(404, "租户不存在");
        }

        LocalDate startDate = calculateRenewalStartDate(tenant);
        LocalDate endDate = startDate.plusMonths(plan.getDurationMonths());

        Subscription subscription = new Subscription();
        subscription.setTenantId(principal.getTenantId());
        subscription.setPlanType(plan.getPlanType());
        subscription.setAmount(subscriptionPlanService.calculateFinalPrice(plan));
        subscription.setStartDate(startDate);
        subscription.setEndDate(endDate);
        subscription.setStatus(SubscriptionStatus.PENDING);
        subscription.setPaymentMethod(request.getPaymentMethod());
        subscription.setRemark(request.getRemark());
        subscription.setCreatedBy(principal.getUserId());
        subscription.setCreatedAt(LocalDateTime.now());
        subscription.setUpdatedAt(LocalDateTime.now());
        subscriptionMapper.insert(subscription);

        log.info("套餐续费订单创建成功，订阅 ID: {}，租户 ID: {}，周期: {} 至 {}",
                subscription.getId(), subscription.getTenantId(), startDate, endDate);
        return toSubscriptionResponse(subscription);
    }

    /**
     * 计算续费开始日期。
     *
     * 如果租户仍在有效期内，从当前有效期日期继续顺延；否则从今天开始。
     *
     * @param tenant 租户实体
     * @return 续费开始日期
     */
    private LocalDate calculateRenewalStartDate(Tenant tenant) {
        LocalDate today = LocalDate.now();
        if (tenant.getExpiredAt() == null || tenant.getExpiredAt().toLocalDate().isBefore(today)) {
            return today;
        }
        return tenant.getExpiredAt().toLocalDate();
    }

    /**
     * 从 SecurityContext 获取当前登录用户
     *
     * @return 当前用户认证主体
     */
    private UserPrincipal getCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return (UserPrincipal) auth.getPrincipal();
    }

    /**
     * 将订阅实体转换为响应 DTO
     *
     * @param subscription 订阅实体
     * @return 订阅响应 DTO
     */
    private SubscriptionResponse toSubscriptionResponse(Subscription subscription) {
        return SubscriptionResponse.builder()
                .id(subscription.getId())
                .tenantId(subscription.getTenantId())
                .planType(subscription.getPlanType().name())
                .amount(subscription.getAmount())
                .startDate(subscription.getStartDate())
                .endDate(subscription.getEndDate())
                .status(subscription.getStatus().name())
                .paymentMethod(subscription.getPaymentMethod() != null ? subscription.getPaymentMethod().name() : null)
                .paymentRef(subscription.getPaymentRef())
                .remark(subscription.getRemark())
                .createdBy(subscription.getCreatedBy())
                .createdAt(subscription.getCreatedAt())
                .build();
    }
}
