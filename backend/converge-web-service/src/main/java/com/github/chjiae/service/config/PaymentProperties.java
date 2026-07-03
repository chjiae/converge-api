package com.github.chjiae.service.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 支付网关配置属性，包含支付宝和微信支付的配置信息。
 * 通过 application.yml 中的 payment.alipay.* 和 payment.wechat.* 配置。
 */
@Data
@Component
@ConfigurationProperties(prefix = "payment")
public class PaymentProperties {

    /** 支付宝配置 */
    private AlipayProperties alipay = new AlipayProperties();

    /** 微信支付配置 */
    private WechatProperties wechat = new WechatProperties();

    /**
     * 支付宝支付配置
     */
    @Data
    public static class AlipayProperties {
        /** 支付宝应用 ID */
        private String appId;
        /** 应用私钥 */
        private String privateKey;
        /** 支付宝公钥 */
        private String alipayPublicKey;
        /** 支付结果异步通知地址 */
        private String notifyUrl;
        /** 支付宝网关地址（默认使用沙箱网关，生产环境通过环境变量切换） */
        private String gateway = "https://openapi-sandbox.dl.alipaydev.com/gateway.do";
    }

    /**
     * 微信支付配置
     */
    @Data
    public static class WechatProperties {
        /** 微信应用 ID */
        private String appId;
        /** 商户号 */
        private String mchId;
        /** 商户证书序列号 */
        private String mchSerialNo;
        /** APIv3 密钥 */
        private String apiV3Key;
        /** 商户私钥文件路径 */
        private String privateKeyPath;
        /** 支付结果异步通知地址 */
        private String notifyUrl;
    }
}
