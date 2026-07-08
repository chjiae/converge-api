package com.github.chjiae.gateway.governance;

/**
 * 网关运行时 lease 完成结果分类。
 * 分类只描述安全结果，不包含请求体、响应体、上游地址、模型名或任何秘密。
 */
public enum GatewayRuntimeLeaseOutcome {

    /** 上游请求成功完成 */
    SUCCESS,

    /** 上游可达且拒绝了客户端请求 */
    REACHABLE_CLIENT_REJECTION,

    /** 上游返回 429 */
    UPSTREAM_RATE_LIMITED,

    /** 上游认证失败 */
    UPSTREAM_AUTH_FAILURE,

    /** 上游连接失败 */
    UPSTREAM_CONNECTION_FAILURE,

    /** 上游超时 */
    UPSTREAM_TIMEOUT,

    /** 上游服务端失败 */
    UPSTREAM_SERVER_FAILURE,

    /** 上游协议不符合预期 */
    UPSTREAM_PROTOCOL_FAILURE,

    /** 客户端主动断开 */
    CLIENT_CANCELLED,

    /** 网关关闭导致结束 */
    GATEWAY_SHUTDOWN,

    /** lease 丢失 */
    LEASE_LOST
}
