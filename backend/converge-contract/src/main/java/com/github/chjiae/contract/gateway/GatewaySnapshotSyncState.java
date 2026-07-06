package com.github.chjiae.contract.gateway;

/**
 * 网关快照同步状态。
 */
public enum GatewaySnapshotSyncState {

    /** 尚未完成首次成功对账或当前状态不可服务。 */
    NOT_READY,

    /** 已完成对账且当前快照状态可服务。 */
    READY,

    /** Redis 短暂异常但本地快照仍处于最大陈旧时间内。 */
    DEGRADED
}
