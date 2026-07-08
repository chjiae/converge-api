package com.github.chjiae.routing;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 真实请求静态候选规划器。
 * 规划器只展开静态拓扑候选顺序，动态健康、并发、冷却和 lease 判断由网关运行时完成。
 */
public class StaticRouteCandidatePlanner {

    /**
     * 生成按静态优先级和权重排列的候选列表。
     *
     * @param plan 已编译静态路由计划
     * @param seed 请求安全 seed，不得包含 raw key、Authorization、Cookie 或上游秘密
     * @return 候选列表
     */
    public List<StaticRouteCandidate> plan(StaticRoutePlan plan, String seed) {
        if (plan == null || plan.routeTargetTiers().isEmpty()) {
            throw new IllegalArgumentException("静态路由计划不能为空");
        }
        List<StaticRouteCandidate> candidates = new ArrayList<>();
        Set<String> emittedResources = new LinkedHashSet<>();
        for (StaticRouteTargetTier targetTier : plan.routeTargetTiers()) {
            List<StaticRoutePoolCandidate> orderedPools = weightedOrder(
                    targetTier.pools(), seed + "|target|" + targetTier.priority(), StaticRoutePoolCandidate::weight);
            for (StaticRoutePoolCandidate pool : orderedPools) {
                if (pool.memberTiers().isEmpty()) {
                    continue;
                }
                StaticRouteMemberTier memberTier = pool.memberTiers().getFirst();
                List<StaticRouteResourceCandidate> orderedResources = weightedOrder(
                        memberTier.resources(),
                        seed + "|pool|" + pool.poolId() + "|member|" + memberTier.priority(),
                        StaticRouteResourceCandidate::weight);
                for (StaticRouteResourceCandidate resource : orderedResources) {
                    if (!emittedResources.add(resource.executionResourceId())) {
                        continue;
                    }
                    candidates.add(new StaticRouteCandidate(plan.tenantId(), plan.publicModelCode(),
                            plan.canonicalOperation(), plan.policyId(), plan.snapshotRevision(),
                            targetTier.priority(), pool.poolId(), pool.poolCode(), pool.weight(),
                            memberTier.priority(), resource.executionResourceId(), resource.weight(),
                            resource.upstreamModelName()));
                }
            }
        }
        return List.copyOf(candidates);
    }

    private <T> List<T> weightedOrder(List<T> input, String seed, WeightReader<T> weightReader) {
        if (input == null || input.isEmpty()) {
            return List.of();
        }
        List<T> remaining = new ArrayList<>(input);
        List<T> ordered = new ArrayList<>();
        int round = 0;
        while (!remaining.isEmpty()) {
            int totalWeight = 0;
            for (T item : remaining) {
                int weight = weightReader.weight(item);
                if (weight <= 0) {
                    throw new IllegalArgumentException("静态路由权重必须大于 0");
                }
                totalWeight += weight;
            }
            int selected = positiveHash(seed + "|" + round) % totalWeight;
            int cursor = 0;
            int selectedIndex = remaining.size() - 1;
            for (int i = 0; i < remaining.size(); i++) {
                T item = remaining.get(i);
                cursor += weightReader.weight(item);
                if (selected < cursor) {
                    selectedIndex = i;
                    break;
                }
            }
            ordered.add(remaining.remove(selectedIndex));
            round++;
        }
        return ordered;
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

    private interface WeightReader<T> {

        /**
         * 读取候选权重。
         *
         * @param item 候选
         * @return 权重
         */
        int weight(T item);
    }
}
