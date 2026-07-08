package com.github.chjiae.gateway.governance;

/**
 * 网关运行时 lease 获取结果。
 *
 * @param status 获取状态
 * @param leaseId 获取成功时的 lease ID，失败时为空
 * @param halfOpen 是否半开探测 lease
 */
public record GatewayRuntimeLeaseAcquireResult(
        GatewayRuntimeLeaseAcquireStatus status,
        String leaseId,
        boolean halfOpen
) {

    /**
     * 构造成功结果。
     *
     * @param status 成功状态
     * @param leaseId lease ID
     * @return 获取结果
     */
    public static GatewayRuntimeLeaseAcquireResult granted(GatewayRuntimeLeaseAcquireStatus status, String leaseId) {
        return new GatewayRuntimeLeaseAcquireResult(status, leaseId,
                status == GatewayRuntimeLeaseAcquireStatus.HALF_OPEN_GRANTED);
    }

    /**
     * 构造拒绝结果。
     *
     * @param status 拒绝状态
     * @return 获取结果
     */
    public static GatewayRuntimeLeaseAcquireResult rejected(GatewayRuntimeLeaseAcquireStatus status) {
        return new GatewayRuntimeLeaseAcquireResult(status, "", false);
    }
}
