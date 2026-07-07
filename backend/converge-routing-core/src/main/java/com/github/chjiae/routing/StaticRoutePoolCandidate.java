package com.github.chjiae.routing;

import java.util.List;

/**
 * 静态路由中的资源池候选。
 *
 * @param poolId 资源池 ID
 * @param poolCode 资源池编码
 * @param weight 同优先级目标层内权重
 * @param memberTiers 池内资源成员优先级层
 */
public record StaticRoutePoolCandidate(
        String poolId,
        String poolCode,
        int weight,
        List<StaticRouteMemberTier> memberTiers
) {

    /**
     * 复制集合，保持不可变。
     */
    public StaticRoutePoolCandidate {
        memberTiers = List.copyOf(memberTiers == null ? List.of() : memberTiers);
    }
}
