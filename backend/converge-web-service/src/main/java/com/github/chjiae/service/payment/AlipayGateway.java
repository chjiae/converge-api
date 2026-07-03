package com.github.chjiae.service.payment;

import com.alibaba.fastjson.JSON;
import com.alipay.api.AlipayClient;
import com.alipay.api.DefaultAlipayClient;
import com.alipay.api.internal.util.AlipaySignature;
import com.alipay.api.request.AlipayTradePagePayRequest;
import com.alipay.api.request.AlipayTradeQueryRequest;
import com.alipay.api.response.AlipayTradePagePayResponse;
import com.alipay.api.response.AlipayTradeQueryResponse;
import com.github.chjiae.service.config.PaymentProperties;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * 支付宝支付网关实现，基于支付宝电脑网站支付（即时到账）产品。
 * <p>
 * 仅在配置了 {@code payment.alipay.app-id} 时自动注册为 Spring Bean，
 * 适用于通过 application.yml 或环境变量按需启用支付宝渠道。
 * </p>
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "payment.alipay", name = "app-id")
public class AlipayGateway implements PaymentGateway {

    /** 支付配置属性 */
    private final PaymentProperties paymentProperties;

    /** 支付宝 SDK 客户端（延迟初始化） */
    private AlipayClient alipayClient;

    /**
     * 构造方法，注入支付配置
     *
     * @param paymentProperties 支付配置属性
     */
    public AlipayGateway(PaymentProperties paymentProperties) {
        this.paymentProperties = paymentProperties;
    }

    /**
     * 初始化支付宝客户端。
     * 使用配置中的应用 ID、私钥、支付宝公钥以及网关地址创建 {@link DefaultAlipayClient} 实例。
     */
    @PostConstruct
    public void init() {
        PaymentProperties.AlipayProperties alipay = paymentProperties.getAlipay();
        log.info("初始化支付宝客户端, appId={}, gateway={}", alipay.getAppId(), alipay.getGateway());
        this.alipayClient = new DefaultAlipayClient(
                alipay.getGateway(),
                alipay.getAppId(),
                alipay.getPrivateKey(),
                "json",
                "UTF-8",
                alipay.getAlipayPublicKey(),
                "RSA2"
        );
    }

    /**
     * {@inheritDoc}
     *
     * @return 固定返回 "ALIPAY"
     */
    @Override
    public String getName() {
        return "ALIPAY";
    }

    /**
     * 创建支付宝电脑网站支付订单。
     * <p>
     * 使用 {@code alipay.trade.page.pay} 接口生成即时到账支付表单 HTML，
     * 前端可直接将返回的表单渲染到页面以跳转至支付宝收银台。
     * </p>
     *
     * @param order 支付订单参数，包含商户订单号、金额、标题和回跳地址
     * @return 支付结果，success=true 时 paymentUrl 包含支付表单 HTML
     */
    @Override
    public PaymentResult createOrder(PaymentOrder order) {
        log.info("支付宝创建订单, outTradeNo={}, amount={}, subject={}",
                order.getOutTradeNo(), order.getAmount(), order.getSubject());
        try {
            AlipayTradePagePayRequest request = new AlipayTradePagePayRequest();

            // 设置异步通知地址（支付宝服务器主动回调商户服务器）
            request.setNotifyUrl(paymentProperties.getAlipay().getNotifyUrl());
            // 设置同步回跳地址（用户支付完成后浏览器跳转）
            if (order.getReturnUrl() != null) {
                request.setReturnUrl(order.getReturnUrl());
            }

            // 构建业务参数 JSON
            Map<String, Object> bizContent = new HashMap<>();
            bizContent.put("out_trade_no", order.getOutTradeNo());
            bizContent.put("total_amount", order.getAmount().toPlainString());
            bizContent.put("subject", order.getSubject());
            bizContent.put("product_code", "FAST_INSTANT_TRADE_PAY");
            request.setBizContent(JSON.toJSONString(bizContent));

            // 调用 pageExecute 返回表单 HTML（非 apiExecute）
            AlipayTradePagePayResponse response = alipayClient.pageExecute(request);

            if (response.isSuccess()) {
                log.info("支付宝创建订单成功, outTradeNo={}", order.getOutTradeNo());
                return PaymentResult.builder()
                        .success(true)
                        .paymentUrl(response.getBody())
                        .outTradeNo(order.getOutTradeNo())
                        .message("订单创建成功")
                        .build();
            } else {
                log.error("支付宝创建订单失败, outTradeNo={}, code={}, msg={}, subCode={}, subMsg={}",
                        order.getOutTradeNo(), response.getCode(), response.getMsg(),
                        response.getSubCode(), response.getSubMsg());
                return PaymentResult.builder()
                        .success(false)
                        .outTradeNo(order.getOutTradeNo())
                        .message("支付宝创建订单失败: " + response.getSubMsg())
                        .build();
            }
        } catch (Exception e) {
            log.error("支付宝创建订单异常, outTradeNo={}", order.getOutTradeNo(), e);
            return PaymentResult.builder()
                    .success(false)
                    .outTradeNo(order.getOutTradeNo())
                    .message("支付宝创建订单异常: " + e.getMessage())
                    .build();
        }
    }

    /**
     * 处理支付宝异步回调通知。
     * <p>
     * 先通过 {@link AlipaySignature#rsaCheckV1} 验证回调签名，确认请求来源合法，
     * 再根据 {@code trade_status} 判断支付结果。
     * </p>
     *
     * @param params 支付宝回调参数（表单键值对）
     * @return 处理结果，paid=true 表示交易成功或交易结束
     */
    @Override
    public PaymentResult handleCallback(Map<String, String> params) {
        log.info("支付宝回调验签, outTradeNo={}", params.get("out_trade_no"));
        try {
            // 验证回调签名，防止伪造通知
            boolean signVerified = AlipaySignature.rsaCheckV1(
                    params,
                    paymentProperties.getAlipay().getAlipayPublicKey(),
                    "UTF-8",
                    "RSA2"
            );

            if (!signVerified) {
                log.error("支付宝回调验签失败, params={}", params);
                return PaymentResult.builder()
                        .success(false)
                        .message("支付宝回调验签失败")
                        .build();
            }

            // 验签通过，提取业务参数
            String outTradeNo = params.get("out_trade_no");
            String tradeStatus = params.get("trade_status");

            log.info("支付宝回调验签成功, outTradeNo={}, tradeStatus={}", outTradeNo, tradeStatus);

            // TRADE_SUCCESS: 交易成功（支持退款）; TRADE_FINISHED: 交易结束（不可退款）
            boolean paid = "TRADE_SUCCESS".equals(tradeStatus) || "TRADE_FINISHED".equals(tradeStatus);

            return PaymentResult.builder()
                    .success(true)
                    .outTradeNo(outTradeNo)
                    .paid(paid)
                    .message(paid ? "支付成功" : "支付未完成, tradeStatus=" + tradeStatus)
                    .build();
        } catch (Exception e) {
            log.error("支付宝回调处理异常, params={}", params, e);
            return PaymentResult.builder()
                    .success(false)
                    .message("支付宝回调处理异常: " + e.getMessage())
                    .build();
        }
    }

    /**
     * 主动查询支付宝订单的支付状态。
     * <p>
     * 调用 {@code alipay.trade.query} 接口，适用于商户主动核实订单是否已完成支付。
     * </p>
     *
     * @param outTradeNo 商户订单号
     * @return 查询结果，paid=true 表示已支付
     */
    @Override
    public PaymentResult queryPaymentStatus(String outTradeNo) {
        log.info("支付宝订单查询, outTradeNo={}", outTradeNo);
        try {
            AlipayTradeQueryRequest request = new AlipayTradeQueryRequest();

            Map<String, Object> bizContent = new HashMap<>();
            bizContent.put("out_trade_no", outTradeNo);
            request.setBizContent(JSON.toJSONString(bizContent));

            AlipayTradeQueryResponse response = alipayClient.execute(request);

            if (response.isSuccess()) {
                String tradeStatus = response.getTradeStatus();
                // TRADE_SUCCESS: 交易成功; TRADE_FINISHED: 交易结束
                boolean paid = "TRADE_SUCCESS".equals(tradeStatus) || "TRADE_FINISHED".equals(tradeStatus);

                log.info("支付宝订单查询成功, outTradeNo={}, tradeStatus={}, paid={}", outTradeNo, tradeStatus, paid);
                return PaymentResult.builder()
                        .success(true)
                        .outTradeNo(outTradeNo)
                        .paid(paid)
                        .message("查询成功, tradeStatus=" + tradeStatus)
                        .build();
            } else {
                log.error("支付宝订单查询失败, outTradeNo={}, code={}, msg={}, subCode={}, subMsg={}",
                        outTradeNo, response.getCode(), response.getMsg(),
                        response.getSubCode(), response.getSubMsg());
                return PaymentResult.builder()
                        .success(false)
                        .outTradeNo(outTradeNo)
                        .message("支付宝订单查询失败: " + response.getSubMsg())
                        .build();
            }
        } catch (Exception e) {
            log.error("支付宝订单查询异常, outTradeNo={}", outTradeNo, e);
            return PaymentResult.builder()
                    .success(false)
                    .outTradeNo(outTradeNo)
                    .message("支付宝订单查询异常: " + e.getMessage())
                    .build();
        }
    }
}
