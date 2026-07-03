package com.github.chjiae.service.payment;

import com.github.chjiae.service.config.PaymentProperties;
import com.wechat.pay.java.core.RSAAutoCertificateConfig;
import com.wechat.pay.java.core.notification.NotificationParser;
import com.wechat.pay.java.core.notification.RequestParam;
import com.wechat.pay.java.service.payments.model.Transaction;
import com.wechat.pay.java.service.payments.nativepay.NativePayService;
import com.wechat.pay.java.service.payments.nativepay.model.Amount;
import com.wechat.pay.java.service.payments.nativepay.model.PrepayRequest;
import com.wechat.pay.java.service.payments.nativepay.model.PrepayResponse;
import com.wechat.pay.java.service.payments.nativepay.model.QueryOrderByOutTradeNoRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.File;
import java.math.BigDecimal;
import java.util.Map;

/**
 * 微信支付网关实现，基于微信 Native 扫码支付产品。
 * <p>
 * 仅在配置了 {@code payment.wechat.app-id} 时自动注册为 Spring Bean，
 * 适用于通过 application.yml 或环境变量按需启用微信支付渠道。
 * </p>
 * <p>
 * 使用 {@code wechatpay-java} SDK（APIv3），通过 {@link RSAAutoCertificateConfig}
 * 自动下载和更新微信平台证书，简化证书管理。
 * </p>
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "payment.wechat", name = "app-id")
public class WechatPayGateway implements PaymentGateway {

    /** 支付配置属性 */
    private final PaymentProperties paymentProperties;

    /** 微信支付 Native 扫码支付服务（延迟初始化） */
    private NativePayService nativePayService;

    /** 微信支付通知解析器（延迟初始化，用于回调验签和解密） */
    private NotificationParser notificationParser;

    /** 微信支付配置（延迟初始化，同时作为 SDK Config 和 NotificationConfig 使用） */
    private RSAAutoCertificateConfig wechatPayConfig;

    /**
     * 构造方法，注入支付配置
     *
     * @param paymentProperties 支付配置属性
     */
    public WechatPayGateway(PaymentProperties paymentProperties) {
        this.paymentProperties = paymentProperties;
    }

    /**
     * 延迟初始化微信支付 SDK 配置和服务。
     * <p>
     * 首次调用时读取商户私钥文件、构建 {@link RSAAutoCertificateConfig}，
     * 并创建 {@link NativePayService} 和 {@link NotificationParser} 实例。
     * 如果私钥文件不存在，记录警告日志但不抛出异常，后续调用时再次尝试初始化。
     * </p>
     *
     * @return 初始化是否成功
     */
    private synchronized boolean ensureInitialized() {
        if (nativePayService != null) {
            return true;
        }

        PaymentProperties.WechatProperties wechat = paymentProperties.getWechat();
        String privateKeyPath = wechat.getPrivateKeyPath();

        // 检查私钥文件是否存在
        if (privateKeyPath == null || privateKeyPath.isBlank()) {
            log.warn("微信支付初始化失败: 未配置商户私钥文件路径(payment.wechat.private-key-path)");
            return false;
        }

        File privateKeyFile = new File(privateKeyPath);
        if (!privateKeyFile.exists()) {
            log.warn("微信支付初始化失败: 商户私钥文件不存在, path={}", privateKeyPath);
            return false;
        }

        log.info("初始化微信支付服务, appId={}, mchId={}", wechat.getAppId(), wechat.getMchId());

        // 构建 RSA 自动证书配置（自动下载和更新微信平台证书）
        this.wechatPayConfig = new RSAAutoCertificateConfig.Builder()
                .merchantId(wechat.getMchId())
                .privateKeyFromPath(privateKeyPath)
                .merchantSerialNumber(wechat.getMchSerialNo())
                .apiV3Key(wechat.getApiV3Key())
                .build();

        // 构建 Native 扫码支付服务
        this.nativePayService = new NativePayService.Builder()
                .config(wechatPayConfig)
                .build();

        // 构建通知解析器（用于回调验签和解密，RSAAutoCertificateConfig 实现了 NotificationConfig 接口）
        this.notificationParser = new NotificationParser(wechatPayConfig);

        log.info("微信支付服务初始化完成");
        return true;
    }

    /**
     * {@inheritDoc}
     *
     * @return 固定返回 "WECHAT"
     */
    @Override
    public String getName() {
        return "WECHAT";
    }

    /**
     * 创建微信 Native 扫码支付订单。
     * <p>
     * 调用微信下单接口生成预支付订单，返回 {@code code_url}（二维码链接），
     * 前端可将该链接生成二维码图片供用户扫码支付。
     * </p>
     * <p>
     * 注意：微信支付金额单位为分，需将元转换为分（乘以 100）。
     * </p>
     *
     * @param order 支付订单参数，包含商户订单号、金额、标题和回跳地址
     * @return 支付结果，success=true 时 paymentUrl 包含二维码链接（code_url）
     */
    @Override
    public PaymentResult createOrder(PaymentOrder order) {
        log.info("微信支付创建订单, outTradeNo={}, amount={}, subject={}",
                order.getOutTradeNo(), order.getAmount(), order.getSubject());

        if (!ensureInitialized()) {
            return PaymentResult.builder()
                    .success(false)
                    .outTradeNo(order.getOutTradeNo())
                    .message("微信支付服务未初始化，请检查配置")
                    .build();
        }

        try {
            PaymentProperties.WechatProperties wechat = paymentProperties.getWechat();

            // 构建下单请求
            PrepayRequest request = new PrepayRequest();
            request.setAppid(wechat.getAppId());
            request.setMchid(wechat.getMchId());
            request.setDescription(order.getSubject());
            request.setOutTradeNo(order.getOutTradeNo());
            request.setNotifyUrl(wechat.getNotifyUrl());

            // 设置金额（微信支付单位为分，需将元转换为分）
            Amount amount = new Amount();
            amount.setTotal(order.getAmount().multiply(new BigDecimal("100")).intValue());
            request.setAmount(amount);

            // 调用 Native 下单接口，获取二维码链接
            PrepayResponse response = nativePayService.prepay(request);

            log.info("微信支付创建订单成功, outTradeNo={}, codeUrl={}", order.getOutTradeNo(), response.getCodeUrl());
            return PaymentResult.builder()
                    .success(true)
                    .paymentUrl(response.getCodeUrl())
                    .outTradeNo(order.getOutTradeNo())
                    .message("订单创建成功")
                    .build();
        } catch (Exception e) {
            log.error("微信支付创建订单异常, outTradeNo={}", order.getOutTradeNo(), e);
            return PaymentResult.builder()
                    .success(false)
                    .outTradeNo(order.getOutTradeNo())
                    .message("微信支付创建订单异常: " + e.getMessage())
                    .build();
        }
    }

    /**
     * 处理微信支付异步回调通知。
     * <p>
     * 从传入的参数 Map 中提取请求体和 HTTP 头部信息，构建 {@link RequestParam}，
     * 通过 {@link NotificationParser} 验证签名并解密通知内容，解析为 {@link Transaction} 对象，
     * 根据交易状态判断支付结果。
     * </p>
     * <p>
     * params Map 应包含以下键：
     * <ul>
     *   <li>{@code body} - 原始请求体</li>
     *   <li>{@code Wechatpay-Serial} - 微信平台证书序列号</li>
     *   <li>{@code Wechatpay-Nonce} - 随机串</li>
     *   <li>{@code Wechatpay-Signature} - 签名</li>
     *   <li>{@code Wechatpay-Timestamp} - 时间戳</li>
     * </ul>
     * </p>
     *
     * @param params 回调参数（包含请求体和签名相关的 HTTP 头部）
     * @return 处理结果，paid=true 表示支付成功
     */
    @Override
    public PaymentResult handleCallback(Map<String, String> params) {
        log.info("微信支付回调处理开始");

        if (!ensureInitialized()) {
            return PaymentResult.builder()
                    .success(false)
                    .message("微信支付服务未初始化，请检查配置")
                    .build();
        }

        try {
            // 从参数 Map 中提取回调验签所需信息
            String body = params.get("body");
            String serialNumber = params.get("Wechatpay-Serial");
            String nonce = params.get("Wechatpay-Nonce");
            String signature = params.get("Wechatpay-Signature");
            String timestamp = params.get("Wechatpay-Timestamp");

            // 构建回调请求参数
            RequestParam requestParam = new RequestParam.Builder()
                    .serialNumber(serialNumber)
                    .nonce(nonce)
                    .signature(signature)
                    .timestamp(timestamp)
                    .body(body)
                    .build();

            // 验签并解密通知内容，解析为交易对象
            Transaction transaction = notificationParser.parse(requestParam, Transaction.class);
            String outTradeNo = transaction.getOutTradeNo();
            Transaction.TradeStateEnum tradeState = transaction.getTradeState();

            log.info("微信支付回调解密成功, outTradeNo={}, tradeState={}", outTradeNo, tradeState);

            // SUCCESS 表示支付成功
            boolean paid = Transaction.TradeStateEnum.SUCCESS.equals(tradeState);

            return PaymentResult.builder()
                    .success(true)
                    .outTradeNo(outTradeNo)
                    .paid(paid)
                    .message(paid ? "支付成功" : "支付未完成, tradeState=" + tradeState)
                    .build();
        } catch (Exception e) {
            log.error("微信支付回调处理异常", e);
            return PaymentResult.builder()
                    .success(false)
                    .message("微信支付回调处理异常: " + e.getMessage())
                    .build();
        }
    }

    /**
     * 主动查询微信支付订单的支付状态。
     * <p>
     * 调用微信订单查询接口，根据商户订单号查询订单详情，
     * 通过 {@link Transaction.TradeStateEnum} 判断是否已支付。
     * </p>
     *
     * @param outTradeNo 商户订单号
     * @return 查询结果，paid=true 表示已支付
     */
    @Override
    public PaymentResult queryPaymentStatus(String outTradeNo) {
        log.info("微信支付订单查询, outTradeNo={}", outTradeNo);

        if (!ensureInitialized()) {
            return PaymentResult.builder()
                    .success(false)
                    .outTradeNo(outTradeNo)
                    .message("微信支付服务未初始化，请检查配置")
                    .build();
        }

        try {
            PaymentProperties.WechatProperties wechat = paymentProperties.getWechat();

            // 构建查询请求
            QueryOrderByOutTradeNoRequest queryRequest = new QueryOrderByOutTradeNoRequest();
            queryRequest.setMchid(wechat.getMchId());
            queryRequest.setOutTradeNo(outTradeNo);

            // 调用订单查询接口
            Transaction transaction = nativePayService.queryOrderByOutTradeNo(queryRequest);
            Transaction.TradeStateEnum tradeState = transaction.getTradeState();

            // SUCCESS 表示支付成功
            boolean paid = Transaction.TradeStateEnum.SUCCESS.equals(tradeState);

            log.info("微信支付订单查询成功, outTradeNo={}, tradeState={}, paid={}", outTradeNo, tradeState, paid);
            return PaymentResult.builder()
                    .success(true)
                    .outTradeNo(outTradeNo)
                    .paid(paid)
                    .message("查询成功, tradeState=" + tradeState)
                    .build();
        } catch (Exception e) {
            log.error("微信支付订单查询异常, outTradeNo={}", outTradeNo, e);
            return PaymentResult.builder()
                    .success(false)
                    .outTradeNo(outTradeNo)
                    .message("微信支付订单查询异常: " + e.getMessage())
                    .build();
        }
    }
}
