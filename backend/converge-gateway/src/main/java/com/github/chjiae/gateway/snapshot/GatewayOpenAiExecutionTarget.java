package com.github.chjiae.gateway.snapshot;

import java.net.URI;

/**
 * OpenAI Compatible Direct API 执行目标。
 * 该对象包含 runtime secret，禁止使用 record 或自动 toString。
 */
public final class GatewayOpenAiExecutionTarget {

    /** 租户 ID */
    private final String tenantId;

    /** 公开模型编码 */
    private final String publicModelCode;

    /** 快照 revision */
    private final long snapshotRevision;

    /** 路由策略 ID */
    private final String routePolicyId;

    /** 执行资源 ID */
    private final String executionResourceId;

    /** 上游 base URL */
    private final String baseUrl;

    /** 上游模型名 */
    private final String upstreamModelName;

    /** 运行时上游 secret */
    private final String runtimeSecret;

    /**
     * 创建执行目标。
     */
    public GatewayOpenAiExecutionTarget(String tenantId, String publicModelCode, long snapshotRevision,
                                        String routePolicyId, String executionResourceId, String baseUrl,
                                        String upstreamModelName, String runtimeSecret) {
        this.tenantId = requireText(tenantId, "租户 ID 不能为空");
        this.publicModelCode = requireText(publicModelCode, "公开模型不能为空");
        this.snapshotRevision = snapshotRevision;
        this.routePolicyId = requireText(routePolicyId, "路由策略 ID 不能为空");
        this.executionResourceId = requireText(executionResourceId, "执行资源 ID 不能为空");
        this.baseUrl = validateBaseUrl(baseUrl);
        this.upstreamModelName = requireText(upstreamModelName, "上游模型名不能为空");
        this.runtimeSecret = requireText(runtimeSecret, "运行时 secret 不能为空");
    }

    /**
     * 获取租户 ID。
     *
     * @return 租户 ID
     */
    public String tenantId() {
        return tenantId;
    }

    /**
     * 获取公开模型编码。
     *
     * @return 公开模型编码
     */
    public String publicModelCode() {
        return publicModelCode;
    }

    /**
     * 获取快照 revision。
     *
     * @return 快照 revision
     */
    public long snapshotRevision() {
        return snapshotRevision;
    }

    /**
     * 获取路由策略 ID。
     *
     * @return 路由策略 ID
     */
    public String routePolicyId() {
        return routePolicyId;
    }

    /**
     * 获取执行资源 ID。
     *
     * @return 执行资源 ID
     */
    public String executionResourceId() {
        return executionResourceId;
    }

    /**
     * 获取上游 base URL。
     *
     * @return 上游 base URL
     */
    public String baseUrl() {
        return baseUrl;
    }

    /**
     * 获取上游模型名。
     *
     * @return 上游模型名
     */
    public String upstreamModelName() {
        return upstreamModelName;
    }

    /**
     * 获取运行时上游 secret。
     *
     * @return 运行时上游 secret
     */
    public String runtimeSecret() {
        return runtimeSecret;
    }

    /**
     * 拼接 OpenAI Chat Completions 上游 URI。
     *
     * @return 上游 URI
     */
    public URI chatCompletionsUri() {
        return URI.create(baseUrl).resolve("chat/completions");
    }

    @Override
    public String toString() {
        return "GatewayOpenAiExecutionTarget{"
                + "tenantId='" + tenantId + '\''
                + ", publicModelCode='" + publicModelCode + '\''
                + ", snapshotRevision=" + snapshotRevision
                + ", routePolicyId='" + routePolicyId + '\''
                + ", executionResourceId='***'"
                + ", baseUrl='***'"
                + ", upstreamModelName='***'"
                + ", runtimeSecret='***'"
                + '}';
    }

    private String validateBaseUrl(String value) {
        String text = requireText(value, "上游 baseUrl 不能为空");
        URI uri = URI.create(text);
        String scheme = uri.getScheme();
        if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) {
            throw new IllegalArgumentException("上游 baseUrl 协议不支持");
        }
        if (uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null) {
            throw new IllegalArgumentException("上游 baseUrl 不允许包含用户信息、query 或 fragment");
        }
        if (uri.getHost() == null || uri.getHost().isBlank()) {
            throw new IllegalArgumentException("上游 baseUrl host 不能为空");
        }
        return text.endsWith("/") ? text : text + "/";
    }

    private static String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return value;
    }
}
