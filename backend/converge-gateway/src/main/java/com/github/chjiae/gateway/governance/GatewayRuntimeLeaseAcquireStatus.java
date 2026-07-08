package com.github.chjiae.gateway.governance;

/**
 * 网关运行时 lease 获取状态。
 */
public enum GatewayRuntimeLeaseAcquireStatus {

    /** 获取成功 */
    GRANTED,

    /** 半开探测获取成功 */
    HALF_OPEN_GRANTED,

    /** 并发已满 */
    CONCURRENCY_FULL,

    /** 熔断打开 */
    CIRCUIT_OPEN,

    /** 半开探测已被其他请求占用 */
    HALF_OPEN_BUSY,

    /** Redis 运行时状态不可用 */
    RUNTIME_STATE_UNAVAILABLE
}
