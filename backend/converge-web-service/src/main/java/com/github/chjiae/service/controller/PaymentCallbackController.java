package com.github.chjiae.service.controller;

import com.github.chjiae.service.service.PaymentService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;

/**
 * 支付回调控制器，接收支付宝和微信支付的异步通知。
 * <p>
 * 这些端点为公开接口，不携带 JWT，由支付平台服务器直接回调。
 * 安全验证由各支付网关的验签逻辑保证。
 * </p>
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/payment")
@RequiredArgsConstructor
public class PaymentCallbackController {

    /** 支付编排服务 */
    private final PaymentService paymentService;

    /**
     * 支付宝异步回调通知
     * <p>
     * 支付宝以 application/x-www-form-urlencoded 格式 POST 回调参数，
     * 提取所有表单参数后交由支付服务处理。
     * 处理成功返回纯文本 "success"，失败返回 "fail"。
     * </p>
     *
     * @param request HTTP 请求，用于提取表单参数
     * @return 纯文本 "success" 或 "fail"
     */
    @PostMapping(value = "/alipay/notify", produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> alipayNotify(HttpServletRequest request) {
        log.info("收到支付宝异步回调通知");

        try {
            // 提取支付宝回调的所有表单参数
            Map<String, String> params = new HashMap<>();
            Map<String, String[]> requestParams = request.getParameterMap();
            for (Map.Entry<String, String[]> entry : requestParams.entrySet()) {
                String[] values = entry.getValue();
                StringBuilder valueStr = new StringBuilder();
                for (int i = 0; i < values.length; i++) {
                    if (i > 0) {
                        valueStr.append(",");
                    }
                    valueStr.append(values[i]);
                }
                params.put(entry.getKey(), valueStr.toString());
            }

            log.info("支付宝回调参数: out_trade_no={}, trade_status={}", params.get("out_trade_no"), params.get("trade_status"));

            // 交由支付服务处理回调
            paymentService.handlePaymentCallback("ALIPAY", params);

            log.info("支付宝回调处理成功");
            return ResponseEntity.ok("success");
        } catch (Exception e) {
            log.error("支付宝回调处理失败", e);
            return ResponseEntity.ok("fail");
        }
    }

    /**
     * 微信支付异步回调通知
     * <p>
     * 微信支付以 JSON 格式 POST 回调请求体，并在 HTTP 头部携带签名信息。
     * 提取请求体和签名相关头部后交由支付服务处理。
     * 处理成功返回 JSON {@code {"code":"SUCCESS","message":""}}，
     * 失败返回 {@code {"code":"FAIL","message":"..."}}。
     * </p>
     *
     * @param request HTTP 请求，用于读取请求体和签名头部
     * @return JSON 格式的响应结果
     */
    @PostMapping(value = "/wechat/notify", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, String>> wechatNotify(HttpServletRequest request) {
        log.info("收到微信支付异步回调通知");

        Map<String, String> response = new HashMap<>();

        try {
            // 读取微信回调的请求体
            String body = readRequestBody(request);

            // 构建参数 Map，包含请求体和签名相关的 HTTP 头部
            Map<String, String> params = new HashMap<>();
            params.put("body", body);
            params.put("Wechatpay-Signature", request.getHeader("Wechatpay-Signature"));
            params.put("Wechatpay-Timestamp", request.getHeader("Wechatpay-Timestamp"));
            params.put("Wechatpay-Nonce", request.getHeader("Wechatpay-Nonce"));
            params.put("Wechatpay-Serial", request.getHeader("Wechatpay-Serial"));
            params.put("Wechatpay-Signature-Type", request.getHeader("Wechatpay-Signature-Type"));

            log.info("微信支付回调请求体已接收，签名头部已提取");

            // 交由支付服务处理回调
            paymentService.handlePaymentCallback("WECHAT", params);

            log.info("微信支付回调处理成功");
            response.put("code", "SUCCESS");
            response.put("message", "");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("微信支付回调处理失败", e);
            response.put("code", "FAIL");
            response.put("message", e.getMessage());
            return ResponseEntity.ok(response);
        }
    }

    /**
     * 读取 HTTP 请求体为字符串
     *
     * @param request HTTP 请求
     * @return 请求体内容
     * @throws IOException 读取请求体时发生异常
     */
    private String readRequestBody(HttpServletRequest request) throws IOException {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = request.getReader()) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
        }
        return sb.toString();
    }
}
