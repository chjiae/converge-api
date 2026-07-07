package com.github.chjiae.routing;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;

/**
 * 真实请求静态路由选择器。
 * 选择器只读取已编译静态计划，遵守 priority/weight 语义，不读取健康、限流、并发或任何秘密。
 */
public class StaticRouteRequestSelector {

    /**
     * 基于请求安全 seed 选择执行资源。
     *
     * @param plan 已编译静态路由计划
     * @param seed 请求选择 seed，不得包含 raw key、Authorization、Cookie 或上游 secret
     * @return 静态选择结果
     */
    public StaticRouteSelection select(StaticRoutePlan plan, String seed) {
        if (plan == null || plan.routeTargetTiers().isEmpty()) {
            throw new IllegalArgumentException("静态路由计划不能为空");
        }
        StaticRouteTargetTier targetTier = plan.routeTargetTiers().getFirst();
        StaticRoutePoolCandidate pool = chooseWeightedPool(targetTier.pools(), seed + "|pool");
        if (pool.memberTiers().isEmpty()) {
            throw new IllegalArgumentException("静态路由资源池成员层不能为空");
        }
        StaticRouteMemberTier memberTier = pool.memberTiers().getFirst();
        StaticRouteResourceCandidate resource = chooseWeightedResource(memberTier.resources(),
                seed + "|resource|" + pool.poolId());
        return new StaticRouteSelection(plan.tenantId(), plan.publicModelCode(), plan.canonicalOperation(),
                plan.policyId(), plan.snapshotRevision(), targetTier.priority(), pool.poolId(),
                pool.poolCode(), memberTier.priority(), resource.executionResourceId(), resource.upstreamModelName());
    }

    private StaticRoutePoolCandidate chooseWeightedPool(List<StaticRoutePoolCandidate> candidates, String seed) {
        if (candidates == null || candidates.isEmpty()) {
            throw new IllegalArgumentException("静态路由资源池候选不能为空");
        }
        int total = 0;
        for (StaticRoutePoolCandidate candidate : candidates) {
            total += candidate.weight();
        }
        int value = positiveHash(seed) % total;
        int cursor = 0;
        for (StaticRoutePoolCandidate candidate : candidates) {
            cursor += candidate.weight();
            if (value < cursor) {
                return candidate;
            }
        }
        return candidates.getLast();
    }

    private StaticRouteResourceCandidate chooseWeightedResource(List<StaticRouteResourceCandidate> candidates,
                                                                String seed) {
        if (candidates == null || candidates.isEmpty()) {
            throw new IllegalArgumentException("静态路由资源候选不能为空");
        }
        int total = 0;
        for (StaticRouteResourceCandidate candidate : candidates) {
            total += candidate.weight();
        }
        int value = positiveHash(seed) % total;
        int cursor = 0;
        for (StaticRouteResourceCandidate candidate : candidates) {
            cursor += candidate.weight();
            if (value < cursor) {
                return candidate;
            }
        }
        return candidates.getLast();
    }

    private int positiveHash(String seed) {
        try {
            byte[] seedBytes = String.valueOf(seed).getBytes(StandardCharsets.UTF_8);
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(seedBytes);
            int value = ((digest[0] & 0xff) << 24)
                    | ((digest[1] & 0xff) << 16)
                    | ((digest[2] & 0xff) << 8)
                    | (digest[3] & 0xff);
            return value & 0x7fffffff;
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("JDK 缺少 SHA-256 算法", e);
        }
    }
}
