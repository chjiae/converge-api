package com.github.chjiae.gateway.governance;

/**
 * 网关运行时治理候选获取结果。
 *
 * @param status 获取状态
 * @param lease 获取成功时的 lease
 */
public record GatewayRuntimeAcquireResult(
        GatewayRuntimeLeaseAcquireStatus status,
        GatewayRuntimeLease lease
) {

    /**
     * 构造成功结果。
     *
     * @param lease lease
     * @return 获取结果
     */
    public static GatewayRuntimeAcquireResult granted(GatewayRuntimeLease lease) {
        GatewayRuntimeLeaseAcquireStatus status = lease.halfOpen()
                ? GatewayRuntimeLeaseAcquireStatus.HALF_OPEN_GRANTED
                : GatewayRuntimeLeaseAcquireStatus.GRANTED;
        return new GatewayRuntimeAcquireResult(status, lease);
    }

    /**
     * 构造拒绝结果。
     *
     * @param status 拒绝状态
     * @return 获取结果
     */
    public static GatewayRuntimeAcquireResult rejected(GatewayRuntimeLeaseAcquireStatus status) {
        return new GatewayRuntimeAcquireResult(status, null);
    }
}
