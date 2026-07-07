package com.github.chjiae.routing;

import java.util.List;

/**
 * 资源池内同优先级的成员候选层。
 *
 * @param priority 成员优先级
 * @param resources 资源候选
 */
public record StaticRouteMemberTier(
        int priority,
        List<StaticRouteResourceCandidate> resources
) {

    /**
     * 复制集合，保持不可变。
     */
    public StaticRouteMemberTier {
        resources = List.copyOf(resources == null ? List.of() : resources);
    }
}
