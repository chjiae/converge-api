package com.github.chjiae.service.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.github.chjiae.common.exception.BusinessException;
import com.github.chjiae.service.dto.subscription.SubscriptionPlanResponse;
import com.github.chjiae.service.dto.subscription.UpdateSubscriptionPlanRequest;
import com.github.chjiae.service.entity.SubscriptionPlan;
import com.github.chjiae.service.mapper.SubscriptionPlanMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

/**
 * 订阅套餐配置服务。
 * 提供套餐展示、超管配置和折扣价计算能力。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SubscriptionPlanService {

    /** 订阅套餐数据访问层 */
    private final SubscriptionPlanMapper subscriptionPlanMapper;

    /**
     * 查询全部套餐配置。
     *
     * @return 套餐响应列表
     */
    public List<SubscriptionPlanResponse> listAll() {
        log.info("查询全部订阅套餐配置");
        return subscriptionPlanMapper.selectList(baseOrderQuery()).stream()
                .map(this::toResponse)
                .toList();
    }

    /**
     * 查询启用中的套餐。
     *
     * @return 启用套餐响应列表
     */
    public List<SubscriptionPlanResponse> listEnabled() {
        log.info("查询启用订阅套餐配置");
        return subscriptionPlanMapper.selectList(baseOrderQuery()
                        .eq(SubscriptionPlan::getEnabled, true))
                .stream()
                .map(this::toResponse)
                .toList();
    }

    /**
     * 更新套餐配置。
     *
     * @param id      套餐 ID
     * @param request 更新请求
     * @return 更新后的套餐响应
     */
    @Transactional(rollbackFor = Exception.class)
    public SubscriptionPlanResponse update(Long id, UpdateSubscriptionPlanRequest request) {
        log.info("更新订阅套餐配置，套餐 ID: {}", id);
        SubscriptionPlan plan = subscriptionPlanMapper.selectById(id);
        if (plan == null) {
            log.warn("更新订阅套餐失败，套餐不存在: {}", id);
            throw new BusinessException(404, "套餐不存在");
        }
        validateDiscount(request);

        plan.setName(request.getName());
        plan.setPlanType(request.getPlanType());
        plan.setDurationMonths(request.getDurationMonths());
        plan.setOriginalPrice(request.getOriginalPrice());
        plan.setDiscountName(request.getDiscountName());
        plan.setDiscountPrice(request.getDiscountPrice());
        plan.setDiscountStartAt(request.getDiscountStartAt());
        plan.setDiscountEndAt(request.getDiscountEndAt());
        plan.setBenefits(request.getBenefits());
        plan.setEnabled(request.getEnabled());
        plan.setRecommended(request.getRecommended());
        plan.setSortOrder(request.getSortOrder());
        plan.setUpdatedAt(LocalDateTime.now());
        subscriptionPlanMapper.updateById(plan);

        log.info("订阅套餐配置更新成功，套餐 ID: {}", id);
        return toResponse(plan);
    }

    /**
     * 查询可购买套餐。
     *
     * @param id 套餐 ID
     * @return 套餐实体
     */
    public SubscriptionPlan getEnabledPlan(Long id) {
        SubscriptionPlan plan = subscriptionPlanMapper.selectById(id);
        if (plan == null || !Boolean.TRUE.equals(plan.getEnabled())) {
            log.warn("套餐不可用，套餐 ID: {}", id);
            throw new BusinessException(404, "套餐不存在或已下架");
        }
        return plan;
    }

    /**
     * 计算套餐当前成交价。
     *
     * @param plan 套餐实体
     * @return 当前成交价
     */
    public BigDecimal calculateFinalPrice(SubscriptionPlan plan) {
        if (hasActiveDiscount(plan)) {
            return plan.getDiscountPrice();
        }
        return plan.getOriginalPrice();
    }

    /**
     * 判断套餐折扣当前是否生效。
     *
     * @param plan 套餐实体
     * @return true 表示当前折扣生效
     */
    public boolean hasActiveDiscount(SubscriptionPlan plan) {
        LocalDateTime now = LocalDateTime.now();
        return plan.getDiscountPrice() != null
                && (plan.getDiscountStartAt() == null || !plan.getDiscountStartAt().isAfter(now))
                && (plan.getDiscountEndAt() == null || !plan.getDiscountEndAt().isBefore(now));
    }

    /**
     * 构建默认排序查询条件。
     *
     * @return 查询条件
     */
    private LambdaQueryWrapper<SubscriptionPlan> baseOrderQuery() {
        return new LambdaQueryWrapper<SubscriptionPlan>()
                .orderByAsc(SubscriptionPlan::getSortOrder)
                .orderByAsc(SubscriptionPlan::getId);
    }

    /**
     * 校验折扣参数。
     *
     * @param request 更新请求
     */
    private void validateDiscount(UpdateSubscriptionPlanRequest request) {
        if (request.getDiscountPrice() != null
                && request.getDiscountPrice().compareTo(request.getOriginalPrice()) >= 0) {
            throw new BusinessException(400, "折扣价必须低于原价");
        }
        if (request.getDiscountStartAt() != null
                && request.getDiscountEndAt() != null
                && !request.getDiscountEndAt().isAfter(request.getDiscountStartAt())) {
            throw new BusinessException(400, "折扣结束时间必须晚于开始时间");
        }
    }

    /**
     * 转换为套餐响应 DTO。
     *
     * @param plan 套餐实体
     * @return 套餐响应
     */
    private SubscriptionPlanResponse toResponse(SubscriptionPlan plan) {
        boolean activeDiscount = hasActiveDiscount(plan);
        return SubscriptionPlanResponse.builder()
                .id(plan.getId())
                .code(plan.getCode())
                .name(plan.getName())
                .planType(plan.getPlanType().name())
                .durationMonths(plan.getDurationMonths())
                .originalPrice(plan.getOriginalPrice())
                .finalPrice(calculateFinalPrice(plan))
                .discountName(plan.getDiscountName())
                .discountPrice(plan.getDiscountPrice())
                .discountStartAt(plan.getDiscountStartAt())
                .discountEndAt(plan.getDiscountEndAt())
                .hasActiveDiscount(activeDiscount)
                .benefits(splitBenefits(plan.getBenefits()))
                .enabled(plan.getEnabled())
                .recommended(plan.getRecommended())
                .sortOrder(plan.getSortOrder())
                .build();
    }

    /**
     * 将权益说明文本拆分为列表。
     *
     * @param benefits 权益说明文本
     * @return 权益说明列表
     */
    private List<String> splitBenefits(String benefits) {
        if (benefits == null || benefits.isBlank()) {
            return List.of();
        }
        return Arrays.stream(benefits.split("\\R"))
                .map(String::trim)
                .filter(line -> !line.isBlank())
                .toList();
    }
}
