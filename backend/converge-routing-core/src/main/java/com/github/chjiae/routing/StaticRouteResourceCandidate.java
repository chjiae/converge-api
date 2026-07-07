package com.github.chjiae.routing;

/**
 * 静态路由中的执行资源候选。
 *
 * @param executionResourceId 执行资源 ID
 * @param connectionId 上游连接 ID
 * @param providerId 供应商 ID
 * @param protocolType 上游协议类型
 * @param baseUrl 上游基础地址
 * @param upstreamModelName 精确上游模型名称
 * @param weight 同优先级资源层内权重
 */
public record StaticRouteResourceCandidate(
        String executionResourceId,
        String connectionId,
        String providerId,
        String protocolType,
        String baseUrl,
        String upstreamModelName,
        int weight
) {
}
