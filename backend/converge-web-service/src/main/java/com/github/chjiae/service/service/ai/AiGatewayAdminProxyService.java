package com.github.chjiae.service.service.ai;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.chjiae.common.exception.BusinessException;
import com.github.chjiae.service.config.GatewayAdminProperties;
import com.github.chjiae.service.dto.ai.AiGatewayChatMessageRequest;
import com.github.chjiae.service.dto.ai.AiGatewayChatTestRequest;
import com.github.chjiae.service.dto.ai.AiGatewayChatTestResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * AI Gateway 管理代理服务。
 * 负责控制面到 Gateway 内部接口的安全转发和脱敏，不向前端暴露内部地址。
 */
@Slf4j
@Service
public class AiGatewayAdminProxyService {

    /** Gateway 管理代理配置。 */
    private final GatewayAdminProperties properties;

    /** JSON 解析器。 */
    private final ObjectMapper objectMapper;

    /** 共享 HTTP Client。 */
    private final HttpClient httpClient;

    /** ready / snapshot 允许透传的字段。 */
    private final Set<String> snapshotSafeFields = new LinkedHashSet<>();

    /** runtime 允许透传的字段。 */
    private final Set<String> runtimeSafeFields = new LinkedHashSet<>();

    /** JSON 对象转普通 Map 的类型引用。 */
    private static final TypeReference<Map<String, Object>> STRING_OBJECT_MAP = new TypeReference<>() {
    };

    /**
     * 创建代理服务。
     *
     * @param properties Gateway 管理代理配置
     */
    public AiGatewayAdminProxyService(GatewayAdminProperties properties) {
        this.properties = properties;
        this.objectMapper = new ObjectMapper();
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(properties.getConnectTimeoutMs()))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        initSafeFields();
    }

    /**
     * 查询 Gateway ready 状态。
     *
     * @return 安全状态 JSON
     */
    public Map<String, Object> ready() {
        return getSafeJson("/internal/ready", snapshotSafeFields);
    }

    /**
     * 查询 Gateway snapshot 状态。
     *
     * @return 安全状态 JSON
     */
    public Map<String, Object> snapshotStatus() {
        return getSafeJson("/internal/snapshot-status", snapshotSafeFields);
    }

    /**
     * 查询 Gateway runtime 状态。
     *
     * @return 安全状态 JSON
     */
    public Map<String, Object> runtimeStatus() {
        return getSafeJson("/internal/runtime-status", runtimeSafeFields);
    }

    /**
     * 发起非流式 Chat Completions 测试。
     *
     * @param request 测试请求
     * @return 测试响应
     */
    public AiGatewayChatTestResponse testChatCompletions(AiGatewayChatTestRequest request) {
        ensureEnabled();
        long start = System.currentTimeMillis();
        try {
            String body = buildChatRequestBody(request);
            HttpRequest httpRequest = HttpRequest.newBuilder(resolve("/v1/chat/completions"))
                    .timeout(Duration.ofMillis(properties.getReadTimeoutMs()))
                    .header("Authorization", "Bearer " + request.getClientApiKey())
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            long latencyMs = System.currentTimeMillis() - start;
            return toChatResponse(response.statusCode(), latencyMs, response.body());
        } catch (IOException e) {
            log.warn("AI Gateway Chat 测试代理调用失败，原因: {}", e.getClass().getSimpleName());
            throw new BusinessException(503, "Gateway Chat 测试代理不可达");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException(503, "Gateway Chat 测试代理调用被中断");
        }
    }

    private Map<String, Object> getSafeJson(String path, Set<String> safeFields) {
        ensureEnabled();
        try {
            HttpRequest request = HttpRequest.newBuilder(resolve(path))
                    .timeout(Duration.ofMillis(properties.getReadTimeoutMs()))
                    .header("Accept", "application/json")
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new BusinessException(503, "Gateway 管理状态暂不可用");
            }
            JsonNode root = objectMapper.readTree(response.body());
            return filterSafeFields(root, safeFields);
        } catch (IOException e) {
            log.warn("AI Gateway 管理状态代理调用失败，原因: {}", e.getClass().getSimpleName());
            throw new BusinessException(503, "Gateway 管理状态代理不可达");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException(503, "Gateway 管理状态代理调用被中断");
        }
    }

    private void ensureEnabled() {
        if (!properties.isEnabled() || properties.getBaseUrl() == null || properties.getBaseUrl().isBlank()) {
            throw new BusinessException(503, "未配置 Gateway 管理代理，请联系管理员配置 AI_GATEWAY_ADMIN_BASE_URL");
        }
    }

    private URI resolve(String path) {
        String base = properties.getBaseUrl();
        if (base.endsWith("/")) {
            return URI.create(base.substring(0, base.length() - 1) + path);
        }
        return URI.create(base + path);
    }

    private Map<String, Object> filterSafeFields(JsonNode source, Set<String> safeFields) {
        Map<String, Object> filtered = new LinkedHashMap<>();
        for (String field : safeFields) {
            JsonNode value = source.get(field);
            if (value != null) {
                filtered.put(field, objectMapper.convertValue(value, Object.class));
            }
        }
        return filtered;
    }

    private String buildChatRequestBody(AiGatewayChatTestRequest request) throws IOException {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("model", request.getModel());
        root.put("stream", false);
        if (request.getTemperature() != null) {
            root.put("temperature", request.getTemperature());
        }
        if (request.getMaxTokens() != null) {
            root.put("max_tokens", request.getMaxTokens());
        }
        var messages = objectMapper.createArrayNode();
        for (AiGatewayChatMessageRequest message : request.getMessages()) {
            ObjectNode item = objectMapper.createObjectNode();
            item.put("role", message.getRole());
            item.put("content", message.getContent());
            messages.add(item);
        }
        root.set("messages", messages);
        return objectMapper.writeValueAsString(root);
    }

    private AiGatewayChatTestResponse toChatResponse(int status, long latencyMs, String body) throws IOException {
        JsonNode parsed = parseBody(body);
        if (status >= 200 && status < 300) {
            return AiGatewayChatTestResponse.builder()
                    .status(status)
                    .latencyMs(latencyMs)
                    .data(objectMapper.convertValue(parsed, STRING_OBJECT_MAP))
                    .build();
        }
        return AiGatewayChatTestResponse.builder()
                .status(status)
                .latencyMs(latencyMs)
                .errorCode(readErrorCode(parsed, status))
                .errorMessage(readErrorMessage(parsed, status))
                .build();
    }

    private JsonNode parseBody(String body) throws IOException {
        if (body == null || body.isBlank()) {
            return objectMapper.createObjectNode();
        }
        return objectMapper.readTree(body);
    }

    private String readErrorCode(JsonNode body, int status) {
        JsonNode errorCode = body.at("/error/code");
        if (errorCode.isTextual()) {
            return errorCode.asText();
        }
        if (status == 503) {
            return "gateway_not_ready";
        }
        if (status >= 500) {
            return "upstream_error";
        }
        return "gateway_error";
    }

    private String readErrorMessage(JsonNode body, int status) {
        JsonNode errorMessage = body.at("/error/message");
        if (errorMessage.isTextual()) {
            return errorMessage.asText();
        }
        return "Gateway 测试请求失败，HTTP 状态: " + status;
    }

    private void initSafeFields() {
        String[] snapshotFields = {
                "status", "service", "indexTenantCount", "tenantIndexCount", "loadedTenantCount",
                "compiledRoutePlanCount", "clientKeyCount", "loadedClientKeyCount", "loadedAccessGroupCount",
                "loadedGrantCount", "loadedRoutePlanCount", "loadedRuntimePolicyCount", "invalidRouteTenantCount",
                "snapshotRefreshTotalCount", "snapshotRefreshSkippedCount", "snapshotRefreshFailedCount",
                "tenantRefreshInFlightCount", "tenantRefreshPendingCount", "lastTenantRefreshEpochMillis",
                "lastFullReconcileEpochMillis", "lastRefreshDurationMs", "maxRefreshDurationMs",
                "estimatedSnapshotPayloadBytes", "lastSuccessfulReconcileEpochMillis", "latestErrorCategory"
        };
        for (String field : snapshotFields) {
            snapshotSafeFields.add(field);
        }
        String[] runtimeFields = {
                "state", "redisAvailable", "activeLocalLeases", "leaseAcquireGrantedCount",
                "leaseAcquireRejectedCount", "renewFailureCount", "runtimeStateUnavailableCount"
        };
        for (String field : runtimeFields) {
            runtimeSafeFields.add(field);
        }
    }
}
