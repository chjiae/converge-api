package com.github.chjiae.routing;

import java.util.List;

/**
 * RouteTarget 同优先级的资源池候选层。
 *
 * @param priority 目标优先级
 * @param pools 资源池候选
 */
public record StaticRouteTargetTier(
        int priority,
        List<StaticRoutePoolCandidate> pools
) {

    /**
     * 复制集合，保持不可变。
     */
    public StaticRouteTargetTier {
        pools = List.copyOf(pools == null ? List.of() : pools);
    }
}
