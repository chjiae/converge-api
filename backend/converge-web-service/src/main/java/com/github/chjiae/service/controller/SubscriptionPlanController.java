package com.github.chjiae.service.controller;

import com.github.chjiae.common.result.Result;
import com.github.chjiae.service.dto.subscription.SubscriptionPlanResponse;
import com.github.chjiae.service.dto.subscription.UpdateSubscriptionPlanRequest;
import com.github.chjiae.service.service.SubscriptionPlanService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 订阅套餐配置控制器。
 * 提供租户可购买套餐查询和超管套餐配置接口。
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/subscription-plans")
@RequiredArgsConstructor
public class SubscriptionPlanController {

    /** 订阅套餐配置服务 */
    private final SubscriptionPlanService subscriptionPlanService;

    /**
     * 查询启用中的套餐。
     *
     * @return 启用套餐列表
     */
    @GetMapping("/enabled")
    @PreAuthorize("isAuthenticated()")
    public Result<List<SubscriptionPlanResponse>> listEnabled() {
        log.info("启用套餐列表接口调用");
        return Result.ok(subscriptionPlanService.listEnabled());
    }

    /**
     * 查询全部套餐配置，仅超管/运营可访问。
     *
     * @return 全部套餐列表
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'PLATFORM_OPERATOR')")
    public Result<List<SubscriptionPlanResponse>> listAll() {
        log.info("全部套餐配置列表接口调用");
        return Result.ok(subscriptionPlanService.listAll());
    }

    /**
     * 更新套餐配置，仅超管/运营可访问。
     *
     * @param id      套餐 ID
     * @param request 更新请求
     * @return 更新后的套餐
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'PLATFORM_OPERATOR')")
    public Result<SubscriptionPlanResponse> update(@PathVariable Long id,
                                                   @Valid @RequestBody UpdateSubscriptionPlanRequest request) {
        log.info("更新套餐配置接口调用，套餐 ID: {}", id);
        return Result.ok(subscriptionPlanService.update(id, request));
    }
}
