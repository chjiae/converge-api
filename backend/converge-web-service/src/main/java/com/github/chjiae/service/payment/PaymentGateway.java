package com.github.chjiae.service.payment;

import java.util.Map;

/**
 * 支付网关接口，定义支付渠道的核心操作方法。
 * 每个支付渠道（支付宝、微信支付等）需提供各自的实现。
 */
public interface PaymentGateway {

    /**
     * 获取网关标识名称（如 "ALIPAY"、"WECHAT"）
     *
     * @return 网关名称
     */
    String getName();

    /**
     * 创建支付订单，返回支付跳转链接或表单
     *
     * @param order 支付订单参数
     * @return 支付结果（包含支付链接）
     */
    PaymentResult createOrder(PaymentOrder order);

    /**
     * 处理支付平台的异步回调通知
     * 验证签名、提取支付状态
     *
     * @param params 回调参数（支付宝为表单参数 Map，微信为请求体解析后的 Map）
     * @return 处理结果（paid=true 表示支付成功）
     */
    PaymentResult handleCallback(Map<String, String> params);

    /**
     * 主动查询订单的支付状态
     *
     * @param outTradeNo 商户订单号
     * @return 查询结果（paid=true 表示已支付）
     */
    PaymentResult queryPaymentStatus(String outTradeNo);
}
