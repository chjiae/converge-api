package com.github.chjiae.service.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.github.chjiae.common.enums.CardKeyStatus;
import com.github.chjiae.common.enums.NotificationType;
import com.github.chjiae.common.enums.PaymentMethod;
import com.github.chjiae.common.enums.SubscriptionPlanType;
import com.github.chjiae.common.enums.SubscriptionStatus;
import com.github.chjiae.common.exception.BusinessException;
import com.github.chjiae.common.result.PageResult;
import com.github.chjiae.service.dto.cardkey.CardKeyResponse;
import com.github.chjiae.service.dto.cardkey.GenerateCardKeyRequest;
import com.github.chjiae.service.entity.CardKey;
import com.github.chjiae.service.entity.Subscription;
import com.github.chjiae.service.mapper.CardKeyMapper;
import com.github.chjiae.service.mapper.SubscriptionMapper;
import com.github.chjiae.service.security.UserPrincipal;
import com.github.chjiae.service.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 卡密管理服务，提供卡密生成、兑换和查询功能。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CardKeyService {

    /** 卡密编码字符集：大写字母 + 数字，排除易混淆字符 I、O、0、1 */
    private static final String CODE_CHARSET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

    /** 卡密编码长度 */
    private static final int CODE_LENGTH = 16;

    /** 卡密默认有效期（从生成时间起 1 年） */
    private static final int DEFAULT_EXPIRE_YEARS = 1;

    /** 卡密数据访问层 */
    private final CardKeyMapper cardKeyMapper;

    /** 订阅数据访问层 */
    private final SubscriptionMapper subscriptionMapper;

    /** 订阅管理服务 */
    private final SubscriptionService subscriptionService;

    /** 站内信服务 */
    private final NotificationService notificationService;

    /** 安全随机数生成器 */
    private final SecureRandom secureRandom = new SecureRandom();

    /**
     * 批量生成卡密
     * 生成指定数量的随机编码卡密，编码唯一且有效期为 1 年。
     *
     * @param request 生成卡密请求参数
     * @return 生成的卡密响应列表
     */
    @Transactional(rollbackFor = Exception.class)
    public List<CardKeyResponse> generateCardKeys(GenerateCardKeyRequest request) {
        UserPrincipal principal = getCurrentUser();
        log.info("开始生成卡密，数量: {}，套餐类型: {}，操作人: {}", request.getCount(), request.getPlanType(), principal.getUserId());

        // 忽略多租户过滤，卡密生成不关联特定租户
        TenantContext.setIgnoreTenant(true);

        SubscriptionPlanType planType = SubscriptionPlanType.valueOf(request.getPlanType());
        LocalDateTime expiredAt = LocalDateTime.now().plusYears(DEFAULT_EXPIRE_YEARS);
        List<CardKey> cardKeys = new ArrayList<>();

        for (int i = 0; i < request.getCount(); i++) {
            // 生成唯一编码，重复则重新生成
            String code;
            do {
                code = generateRandomCode();
            } while (existsByCode(code));

            CardKey cardKey = new CardKey();
            cardKey.setCode(code);
            cardKey.setPlanType(planType);
            cardKey.setDurationDays(request.getDurationDays());
            cardKey.setAmount(request.getAmount());
            cardKey.setStatus(CardKeyStatus.UNUSED);
            cardKey.setGeneratedBy(principal.getUserId());
            cardKey.setExpiredAt(expiredAt);
            cardKey.setCreatedAt(LocalDateTime.now());

            cardKeyMapper.insert(cardKey);
            cardKeys.add(cardKey);
        }

        log.info("卡密生成完成，数量: {}，操作人: {}", cardKeys.size(), principal.getUserId());

        return cardKeys.stream()
                .map(this::toCardKeyResponse)
                .collect(Collectors.toList());
    }

    /**
     * 兑换卡密
     * 验证卡密有效性后，自动创建订阅并激活，延长租户有效期。
     *
     * @param code 卡密编码
     * @return 兑换后的卡密响应
     */
    @Transactional(rollbackFor = Exception.class)
    public CardKeyResponse redeemCardKey(String code) {
        UserPrincipal principal = getCurrentUser();
        log.info("卡密兑换请求，编码: {}，用户 ID: {}，租户 ID: {}", code, principal.getUserId(), principal.getTenantId());

        // 忽略多租户过滤，卡密查询和兑换需要跨租户操作
        TenantContext.setIgnoreTenant(true);

        // 1. 查找卡密
        CardKey cardKey = cardKeyMapper.selectOne(
                new LambdaQueryWrapper<CardKey>().eq(CardKey::getCode, code)
        );
        if (cardKey == null) {
            log.warn("卡密兑换失败，卡密不存在: {}", code);
            throw new BusinessException(404, "卡密不存在");
        }

        // 2. 验证卡密状态
        if (cardKey.getStatus() != CardKeyStatus.UNUSED) {
            log.warn("卡密兑换失败，卡密已被使用或过期: {}，当前状态: {}", code, cardKey.getStatus());
            throw new BusinessException(400, "该卡密已被使用或已过期");
        }

        // 3. 验证卡密是否过期
        if (cardKey.getExpiredAt() != null && cardKey.getExpiredAt().isBefore(LocalDateTime.now())) {
            // 更新状态为已过期
            cardKey.setStatus(CardKeyStatus.EXPIRED);
            cardKeyMapper.updateById(cardKey);
            log.warn("卡密兑换失败，卡密已过期: {}，过期时间: {}", code, cardKey.getExpiredAt());
            throw new BusinessException(400, "该卡密已过期");
        }

        // 4. 验证兑换人是否有租户
        Long tenantId = principal.getTenantId();
        if (tenantId == null) {
            log.warn("卡密兑换失败，当前用户无租户关联，用户 ID: {}", principal.getUserId());
            throw new BusinessException(400, "当前用户未关联租户，无法兑换卡密");
        }

        // 5. 创建订阅记录（PENDING 状态）
        LocalDate startDate = LocalDate.now();
        LocalDate endDate = startDate.plusDays(cardKey.getDurationDays());

        Subscription subscription = new Subscription();
        subscription.setTenantId(tenantId);
        subscription.setPlanType(cardKey.getPlanType());
        subscription.setAmount(cardKey.getAmount());
        subscription.setStartDate(startDate);
        subscription.setEndDate(endDate);
        subscription.setStatus(SubscriptionStatus.PENDING);
        subscription.setPaymentMethod(PaymentMethod.CARD_KEY);
        subscription.setPaymentRef("CARD-" + cardKey.getCode());
        subscription.setRemark("卡密兑换自动创建");
        subscription.setCreatedBy(principal.getUserId());
        subscription.setCreatedAt(LocalDateTime.now());
        subscription.setUpdatedAt(LocalDateTime.now());
        subscriptionMapper.insert(subscription);

        log.info("卡密兑换创建订阅成功，订阅 ID: {}，卡密编码: {}", subscription.getId(), code);

        // 6. 调用 markAsPaid 激活订阅（延长租户有效期、更新状态）
        subscriptionService.markAsPaid(subscription.getId());

        // 7. 更新卡密状态为已兑换
        cardKey.setStatus(CardKeyStatus.REDEEMED);
        cardKey.setRedeemedBy(principal.getUserId());
        cardKey.setRedeemedAt(LocalDateTime.now());
        cardKey.setTenantId(tenantId);
        cardKeyMapper.updateById(cardKey);

        // 8. 发送兑换成功通知
        notificationService.sendToTenant(
                tenantId,
                "卡密兑换成功",
                String.format("卡密 %s 已成功兑换，套餐类型: %s，有效天数: %d 天", code, cardKey.getPlanType().getDescription(), cardKey.getDurationDays()),
                NotificationType.SUBSCRIPTION
        );

        log.info("卡密兑换成功，编码: {}，租户 ID: {}，用户 ID: {}", code, tenantId, principal.getUserId());

        return toCardKeyResponse(cardKey);
    }

    /**
     * 分页查询卡密列表（超管专用）
     *
     * @param page   页码（从 1 开始）
     * @param size   每页数量
     * @param status 卡密状态过滤（可选）
     * @return 分页卡密列表
     */
    public PageResult<CardKeyResponse> listCardKeys(int page, int size, String status) {
        log.info("查询卡密列表，页码: {}，每页数量: {}，状态过滤: {}", page, size, status);

        // 忽略多租户过滤，超管查询所有卡密
        TenantContext.setIgnoreTenant(true);

        Page<CardKey> pageParam = new Page<>(page, size);
        LambdaQueryWrapper<CardKey> queryWrapper = new LambdaQueryWrapper<>();

        // 可选状态过滤
        if (status != null && !status.isBlank()) {
            queryWrapper.eq(CardKey::getStatus, CardKeyStatus.valueOf(status));
        }

        // 按创建时间倒序
        queryWrapper.orderByDesc(CardKey::getCreatedAt);

        Page<CardKey> resultPage = cardKeyMapper.selectPage(pageParam, queryWrapper);

        List<CardKeyResponse> list = resultPage.getRecords().stream()
                .map(this::toCardKeyResponse)
                .collect(Collectors.toList());

        return PageResult.of(list, resultPage.getTotal(), page, size);
    }

    /**
     * 生成随机卡密编码
     * 使用 SecureRandom 从安全字符集中随机选取字符，生成长度为 CODE_LENGTH 的编码。
     *
     * @return 随机编码字符串
     */
    private String generateRandomCode() {
        StringBuilder sb = new StringBuilder(CODE_LENGTH);
        for (int i = 0; i < CODE_LENGTH; i++) {
            int index = secureRandom.nextInt(CODE_CHARSET.length());
            sb.append(CODE_CHARSET.charAt(index));
        }
        return sb.toString();
    }

    /**
     * 检查卡密编码是否已存在
     *
     * @param code 待检查的编码
     * @return 存在返回 true
     */
    private boolean existsByCode(String code) {
        return cardKeyMapper.selectCount(
                new LambdaQueryWrapper<CardKey>().eq(CardKey::getCode, code)
        ) > 0;
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
     * 将卡密实体转换为响应 DTO
     *
     * @param cardKey 卡密实体
     * @return 卡密响应 DTO
     */
    private CardKeyResponse toCardKeyResponse(CardKey cardKey) {
        return CardKeyResponse.builder()
                .id(cardKey.getId())
                .code(cardKey.getCode())
                .planType(cardKey.getPlanType().name())
                .durationDays(cardKey.getDurationDays())
                .amount(cardKey.getAmount())
                .status(cardKey.getStatus().name())
                .generatedBy(cardKey.getGeneratedBy())
                .redeemedBy(cardKey.getRedeemedBy())
                .redeemedAt(cardKey.getRedeemedAt())
                .tenantId(cardKey.getTenantId())
                .expiredAt(cardKey.getExpiredAt())
                .createdAt(cardKey.getCreatedAt())
                .build();
    }
}
