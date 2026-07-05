package com.github.chjiae.service.controller;

import com.github.chjiae.common.result.PageResult;
import com.github.chjiae.common.result.Result;
import com.github.chjiae.service.dto.subscription.CreateRenewalRequest;
import com.github.chjiae.service.dto.subscription.CreateSubscriptionRequest;
import com.github.chjiae.service.dto.subscription.SubscriptionResponse;
import com.github.chjiae.service.security.UserPrincipal;
import com.github.chjiae.service.dto.payment.PaymentInitiateRequest;
import com.github.chjiae.service.dto.payment.PaymentInitiateResponse;
import com.github.chjiae.service.service.PaymentService;
import com.github.chjiae.service.service.SubscriptionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

/**
 * 订阅管理控制器，提供订阅创建、支付标记、列表查询和续费接口。
 */
@Slf4j
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class SubscriptionController {

    /** 订阅管理服务 */
    private final SubscriptionService subscriptionService;

    /** 支付编排服务 */
    private final PaymentService paymentService;

    /**
     * 所有订阅列表（分页），仅超管/运营可访问
     *
     * @param page 页码，默认 1
     * @param size 每页数量，默认 10
     * @return 分页订阅列表
     */
    @GetMapping("/subscriptions")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'PLATFORM_OPERATOR')")
    public Result<PageResult<SubscriptionResponse>> listSubscriptions(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        log.info("全平台订阅列表接口调用，页码: {}，每页数量: {}", page, size);
        PageResult<SubscriptionResponse> response = subscriptionService.listSubscriptions(page, size);
        return Result.ok(response);
    }

    /**
     * 创建订阅（超管/运营为指定租户创建线下订阅）
     *
     * @param request 创建订阅请求参数
     * @return 创建的订阅信息
     */
    @PostMapping("/subscriptions")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'PLATFORM_OPERATOR')")
    public Result<SubscriptionResponse> createSubscription(@Valid @RequestBody CreateSubscriptionRequest request) {
        log.info("创建订阅接口调用，租户 ID: {}", request.getTenantId());
        SubscriptionResponse response = subscriptionService.createSubscription(request);
        return Result.ok(response);
    }

    /**
     * 标记订阅已支付（超管/运营操作）
     *
     * @param id 订阅 ID
     * @return 更新后的订阅信息
     */
    @PutMapping("/subscriptions/{id}/pay")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'PLATFORM_OPERATOR')")
    public Result<SubscriptionResponse> markAsPaid(@PathVariable Long id) {
        log.info("标记订阅已支付接口调用，订阅 ID: {}", id);
        SubscriptionResponse response = subscriptionService.markAsPaid(id);
        return Result.ok(response);
    }

    /**
     * 我的订阅列表（当前租户的订阅列表，分页）
     *
     * @param page 页码，默认 1
     * @param size 每页数量，默认 10
     * @return 分页订阅列表
     */
    @GetMapping("/my-subscriptions")
    @PreAuthorize("isAuthenticated()")
    public Result<PageResult<SubscriptionResponse>> listMySubscriptions(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        log.info("我的订阅列表接口调用，页码: {}，每页数量: {}", page, size);
        PageResult<SubscriptionResponse> response = subscriptionService.listMySubscriptions(page, size);
        return Result.ok(response);
    }

    /**
     * 发起续费（租户管理员创建 PENDING 状态的续费订阅）
     *
     * @param request 续费请求参数
     * @return 创建的续费订阅信息
     */
    @PostMapping("/my-subscriptions")
    @PreAuthorize("isAuthenticated()")
    public Result<SubscriptionResponse> initiateRenewal(@Valid @RequestBody CreateSubscriptionRequest request) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        UserPrincipal principal = (UserPrincipal) auth.getPrincipal();
        log.info("发起续费接口调用，租户 ID: {}，操作人: {}", principal.getTenantId(), principal.getUserId());
        SubscriptionResponse response = subscriptionService.initiateRenewal(request);
        return Result.ok(response);
    }

    /**
     * 基于套餐配置发起续费。
     *
     * @param request 续费请求参数，仅包含套餐 ID、支付方式和备注
     * @return 创建的续费订阅信息
     */
    @PostMapping("/my-subscriptions/renewals")
    @PreAuthorize("isAuthenticated()")
    public Result<SubscriptionResponse> createRenewalFromPlan(@Valid @RequestBody CreateRenewalRequest request) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        UserPrincipal principal = (UserPrincipal) auth.getPrincipal();
        log.info("套餐续费接口调用，租户 ID: {}，套餐 ID: {}，操作人: {}",
                principal.getTenantId(), request.getPlanId(), principal.getUserId());
        SubscriptionResponse response = subscriptionService.createRenewalFromPlan(request);
        return Result.ok(response);
    }

    /**
     * 发起在线支付（租户管理员）
     *
     * @param request 发起支付请求参数
     * @return 支付链接信息
     */
    @PostMapping("/my-subscriptions/pay")
    @PreAuthorize("isAuthenticated()")
    public Result<PaymentInitiateResponse> initiatePayment(@Valid @RequestBody PaymentInitiateRequest request) {
        log.info("发起在线支付接口调用，订阅 ID: {}，支付方式: {}", request.getSubscriptionId(), request.getPaymentMethod());
        PaymentInitiateResponse response = paymentService.initiatePayment(request);
        return Result.ok(response);
    }
}
