package com.github.chjiae.routing;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;

/**
 * 带显式 seed 的确定性加权预览选择器。
 * 只基于静态配置做选择，不读取动态状态，也不执行任何上游请求。
 */
public class StaticRoutePreviewSelector {

    /** 固定预览警告 */
    private static final List<String> WARNINGS = List.of(
            "STATIC_CONFIGURATION_ONLY",
            "DYNAMIC_STATE_NOT_APPLIED",
            "NO_UPSTREAM_REQUEST_EXECUTED"
    );

    /**
     * 从路由计划中按 seed 做确定性预览选择。
     *
     * @param plan 静态路由计划
     * @param seed 显式 seed，可为空
     * @return 预览结果
     */
    public StaticRoutePreview select(StaticRoutePlan plan, String seed) {
        StaticRouteTargetTier targetTier = plan.routeTargetTiers().getFirst();
        StaticRoutePoolCandidate pool = chooseWeightedPool(targetTier.pools(), seed + "|pool");
        StaticRouteMemberTier memberTier = pool.memberTiers().getFirst();
        StaticRouteResourceCandidate resource = chooseWeightedResource(memberTier.resources(),
                seed + "|resource|" + pool.poolId());
        return new StaticRoutePreview(pool.poolId(), pool.poolCode(), resource.executionResourceId(),
                resource.upstreamModelName(), WARNINGS);
    }

    private StaticRoutePoolCandidate chooseWeightedPool(List<StaticRoutePoolCandidate> candidates, String seed) {
        int total = candidates.stream().mapToInt(StaticRoutePoolCandidate::weight).sum();
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
        int total = candidates.stream().mapToInt(StaticRouteResourceCandidate::weight).sum();
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
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(String.valueOf(seed).getBytes(StandardCharsets.UTF_8));
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
