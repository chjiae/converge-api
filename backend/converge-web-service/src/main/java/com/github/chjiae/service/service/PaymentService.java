package com.github.chjiae.service.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.github.chjiae.common.enums.NotificationType;
import com.github.chjiae.common.enums.PaymentMethod;
import com.github.chjiae.common.enums.SubscriptionStatus;
import com.github.chjiae.common.exception.BusinessException;
import com.github.chjiae.service.dto.payment.PaymentInitiateRequest;
import com.github.chjiae.service.dto.payment.PaymentInitiateResponse;
import com.github.chjiae.service.entity.Subscription;
import com.github.chjiae.service.mapper.SubscriptionMapper;
import com.github.chjiae.service.payment.PaymentGateway;
import com.github.chjiae.service.payment.PaymentOrder;
import com.github.chjiae.service.payment.PaymentResult;
import com.github.chjiae.service.tenant.TenantContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 支付编排服务，负责发起支付、路由到对应支付网关以及处理支付回调。
 * <p>
 * 通过 Spring 自动收集所有 {@link PaymentGateway} 实现（支付宝、微信支付等），
 * 根据请求参数路由到正确的网关完成支付操作。
 * </p>
 */
@Slf4j
@Service
public class PaymentService {

    /** 所有已注册的支付网关实现（Spring 自动收集） */
    private final List<PaymentGateway> paymentGateways;

    /** 订阅管理服务 */
    private final SubscriptionService subscriptionService;

    /** 订阅数据访问层 */
    private final SubscriptionMapper subscriptionMapper;

    /** 站内信服务 */
    private final NotificationService notificationService;

    /** 审计日志服务 */
    private final AuditLogService auditLogService;

    /**
     * 构造方法，注入所有依赖
     *
     * @param paymentGateways    所有已注册的支付网关实现
     * @param subscriptionService 订阅管理服务
     * @param subscriptionMapper  订阅数据访问层
     * @param notificationService 站内信服务
     * @param auditLogService     审计日志服务
     */
    public PaymentService(List<PaymentGateway> paymentGateways,
                          SubscriptionService subscriptionService,
                          SubscriptionMapper subscriptionMapper,
                          NotificationService notificationService,
                          AuditLogService auditLogService) {
        this.paymentGateways = paymentGateways;
        this.subscriptionService = subscriptionService;
        this.subscriptionMapper = subscriptionMapper;
        this.notificationService = notificationService;
        this.auditLogService = auditLogService;
    }

    /**
     * 发起在线支付
     * <p>
     * 根据请求参数中的支付方式路由到对应的支付网关，创建支付订单并返回支付链接。
     * 同时更新订阅记录的支付凭证号和支付方式。
     * </p>
     *
     * @param request 发起支付请求参数，包含订阅 ID、支付方式和回跳地址
     * @return 支付响应，包含支付跳转链接和商户订单号
     * @throws BusinessException 订阅不存在、状态不允许支付、支付方式不支持或网关创建订单失败时抛出
     */
    @Transactional(rollbackFor = Exception.class)
    public PaymentInitiateResponse initiatePayment(PaymentInitiateRequest request) {
        log.info("发起在线支付，订阅 ID: {}，支付方式: {}", request.getSubscriptionId(), request.getPaymentMethod());

        // 忽略多租户过滤，跨租户查询订阅
        TenantContext.setIgnoreTenant(true);

        // 1. 查询订阅记录
        Subscription subscription = subscriptionMapper.selectById(request.getSubscriptionId());
        if (subscription == null) {
            log.warn("发起支付失败，订阅不存在: {}", request.getSubscriptionId());
            throw new BusinessException(404, "订阅不存在");
        }

        // 2. 验证订阅状态必须为 PENDING
        if (subscription.getStatus() != SubscriptionStatus.PENDING) {
            log.warn("发起支付失败，订阅状态不允许支付，订阅 ID: {}，当前状态: {}", request.getSubscriptionId(), subscription.getStatus());
            throw new BusinessException(400, "当前订阅状态不允许发起支付");
        }

        // 3. 生成商户订单号
        String outTradeNo = "CVG-" + System.currentTimeMillis() + "-" + request.getSubscriptionId();

        // 4. 查找匹配的支付网关
        PaymentGateway gateway = findGateway(request.getPaymentMethod());

        // 5. 构建支付订单并调用网关创建订单
        PaymentOrder order = PaymentOrder.builder()
                .outTradeNo(outTradeNo)
                .subject("Converge 平台订阅 - " + subscription.getPlanType().name())
                .amount(subscription.getAmount())
                .returnUrl(request.getReturnUrl())
                .build();

        PaymentResult result = gateway.createOrder(order);
        if (!result.isSuccess()) {
            log.error("支付网关创建订单失败，订阅 ID: {}，原因: {}", request.getSubscriptionId(), result.getMessage());
            throw new BusinessException(500, "支付网关创建订单失败: " + result.getMessage());
        }

        // 6. 更新订阅记录的支付凭证号和支付方式
        subscription.setPaymentRef(outTradeNo);
        subscription.setPaymentMethod(PaymentMethod.valueOf(request.getPaymentMethod()));
        subscription.setUpdatedAt(LocalDateTime.now());
        subscriptionMapper.updateById(subscription);

        log.info("在线支付发起成功，订阅 ID: {}，商户订单号: {}", request.getSubscriptionId(), outTradeNo);

        // 7. 返回支付响应
        return PaymentInitiateResponse.builder()
                .paymentUrl(result.getPaymentUrl())
                .outTradeNo(outTradeNo)
                .build();
    }

    /**
     * 处理支付平台异步回调通知
     * <p>
     * 根据渠道名称路由到对应的支付网关进行验签和结果解析。
     * 支付成功时自动激活订阅并延长租户有效期，同时发送站内通知和记录审计日志。
     * 支持幂等处理：如果订阅已处于 ACTIVE 状态，直接返回成功。
     * </p>
     *
     * @param channel 支付渠道标识（如 "ALIPAY"、"WECHAT"）
     * @param params  回调参数（支付宝为表单参数 Map，微信包含请求体和签名头部）
     * @throws BusinessException 渠道不支持、验签失败或订阅不存在时抛出
     */
    @Transactional(rollbackFor = Exception.class)
    public void handlePaymentCallback(String channel, Map<String, String> params) {
        log.info("收到支付回调，渠道: {}", channel);

        // 1. 查找对应的支付网关
        PaymentGateway gateway = findGateway(channel);

        // 2. 调用网关验签并解析回调结果
        PaymentResult result = gateway.handleCallback(params);
        if (!result.isSuccess()) {
            log.error("支付回调验签失败，渠道: {}，原因: {}", channel, result.getMessage());
            throw new BusinessException(400, "支付回调验签失败: " + result.getMessage());
        }

        // 3. 判断是否支付成功
        if (!result.isPaid()) {
            log.info("支付回调通知非支付成功状态，渠道: {}，消息: {}", channel, result.getMessage());
            return;
        }

        // 4. 根据商户订单号查找订阅记录
        String outTradeNo = result.getOutTradeNo();
        TenantContext.setIgnoreTenant(true);

        Subscription subscription = subscriptionMapper.selectOne(
                new LambdaQueryWrapper<Subscription>()
                        .eq(Subscription::getPaymentRef, outTradeNo)
        );

        if (subscription == null) {
            log.error("支付回调处理失败，未找到对应订阅记录，商户订单号: {}", outTradeNo);
            throw new BusinessException(404, "未找到对应的订阅记录，商户订单号: " + outTradeNo);
        }

        // 5. 幂等处理：如果订阅已经是 ACTIVE 状态，直接返回成功
        if (subscription.getStatus() == SubscriptionStatus.ACTIVE) {
            log.info("支付回调幂等处理，订阅已处于 ACTIVE 状态，订阅 ID: {}，商户订单号: {}", subscription.getId(), outTradeNo);
            return;
        }

        // 6. 调用订阅服务标记为已支付（内部会更新订阅状态为 ACTIVE、延长租户有效期、激活租户）
        subscriptionService.markAsPaid(subscription.getId());

        // 7. 发送站内通知给租户所有用户
        notificationService.sendToTenant(
                subscription.getTenantId(),
                "订阅支付成功",
                "您的订阅（" + subscription.getPlanType().name() + "）已支付成功，服务已自动激活。",
                NotificationType.SUBSCRIPTION
        );

        // 8. 记录审计日志
        auditLogService.log(
                "payment",
                "callback",
                "subscription:" + subscription.getId(),
                "支付回调处理成功，渠道: " + channel + "，商户订单号: " + outTradeNo
        );

        log.info("支付回调处理完成，订阅 ID: {}，渠道: {}，商户订单号: {}", subscription.getId(), channel, outTradeNo);
    }

    /**
     * 根据支付方式名称查找对应的支付网关
     *
     * @param paymentMethod 支付方式名称（如 "ALIPAY"、"WECHAT"）
     * @return 匹配的支付网关实例
     * @throws BusinessException 未找到匹配的网关时抛出
     */
    private PaymentGateway findGateway(String paymentMethod) {
        return paymentGateways.stream()
                .filter(gw -> gw.getName().equalsIgnoreCase(paymentMethod))
                .findFirst()
                .orElseThrow(() -> {
                    log.error("未找到对应的支付网关，支付方式: {}，已注册网关: {}", paymentMethod,
                            paymentGateways.stream().map(PaymentGateway::getName).toList());
                    return new BusinessException(400, "不支持的支付方式: " + paymentMethod);
                });
    }
}
