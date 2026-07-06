package com.github.chjiae.gateway.http;

import io.vertx.core.Handler;
import io.vertx.ext.web.RoutingContext;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.regex.Pattern;

/**
 * 请求 ID 处理器。
 *
 * 若请求携带合法 `X-Request-Id` 则透传；若缺失则生成；若非法或过长则返回统一 JSON 错误，
 * 同时生成安全 requestId 用于响应和日志，避免非法输入进入日志。
 */
public class RequestIdHandler implements Handler<RoutingContext> {

    /** 请求 ID 响应头名称 */
    public static final String REQUEST_ID_HEADER = "X-Request-Id";

    /** 路由上下文中的请求 ID 键 */
    public static final String REQUEST_ID_KEY = "requestId";

    /** 合法请求 ID 格式 */
    private static final Pattern REQUEST_ID_PATTERN = Pattern.compile("^[A-Za-z0-9._-]{8,128}$");

    /** 请求 ID 随机源 */
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    /** Base64 URL 编码器 */
    private static final Base64.Encoder REQUEST_ID_ENCODER = Base64.getUrlEncoder().withoutPadding();

    @Override
    public void handle(RoutingContext context) {
        String incomingRequestId = context.request().getHeader(REQUEST_ID_HEADER);
        if (incomingRequestId == null || incomingRequestId.isBlank()) {
            bindRequestId(context, generateRequestId());
            context.next();
            return;
        }

        String trimmedRequestId = incomingRequestId.trim();
        if (!isValid(trimmedRequestId)) {
            bindRequestId(context, generateRequestId());
            GatewayErrorHandler.writeError(context, 400, "非法 X-Request-Id");
            return;
        }

        bindRequestId(context, trimmedRequestId);
        context.next();
    }

    /**
     * 获取当前上下文中的请求 ID。
     *
     * @param context 路由上下文
     * @return 请求 ID
     */
    public static String currentRequestId(RoutingContext context) {
        String requestId = context.get(REQUEST_ID_KEY);
        if (requestId == null || requestId.isBlank()) {
            requestId = generateRequestId();
            bindRequestId(context, requestId);
        }
        return requestId;
    }

    /**
     * 判断请求 ID 是否合法。
     *
     * @param requestId 请求 ID
     * @return true 表示合法
     */
    private static boolean isValid(String requestId) {
        return REQUEST_ID_PATTERN.matcher(requestId).matches();
    }

    /**
     * 将请求 ID 写入上下文和响应头。
     *
     * @param context 路由上下文
     * @param requestId 请求 ID
     */
    private static void bindRequestId(RoutingContext context, String requestId) {
        context.put(REQUEST_ID_KEY, requestId);
        context.response().putHeader(REQUEST_ID_HEADER, requestId);
    }

    /**
     * 生成安全请求 ID。
     *
     * @return 请求 ID
     */
    private static String generateRequestId() {
        byte[] bytes = new byte[18];
        SECURE_RANDOM.nextBytes(bytes);
        return REQUEST_ID_ENCODER.encodeToString(bytes);
    }
}
