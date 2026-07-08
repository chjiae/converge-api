package com.github.chjiae.routing;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 静态路由动态候选规划器测试。
 * 规划器只输出可尝试候选顺序，不读取健康、并发、Redis 或任何秘密。
 */
class StaticRouteCandidatePlannerTest {

    @Test
    void plan_先尝试高优先级目标层且每个池只使用最高成员层() {
        StaticRoutePlan plan = plan();
        StaticRouteCandidatePlanner planner = new StaticRouteCandidatePlanner();

        List<StaticRouteCandidate> candidates = planner.plan(plan, "stable-seed");

        assertThat(candidates)
                .extracting(StaticRouteCandidate::executionResourceId)
                .contains("res-a1", "res-a2", "res-b1");
        assertThat(candidates)
                .extracting(StaticRouteCandidate::executionResourceId)
                .doesNotContain("res-a-low");
        int firstLowTargetIndex = indexOf(candidates, "res-c1");
        int lastHighTargetIndex = Math.max(indexOf(candidates, "res-a1"),
                Math.max(indexOf(candidates, "res-a2"), indexOf(candidates, "res-b1")));
        assertThat(firstLowTargetIndex).isGreaterThan(lastHighTargetIndex);
    }

    @Test
    void plan_相同Seed稳定且不产生重复资源() {
        StaticRoutePlan plan = plan();
        StaticRouteCandidatePlanner planner = new StaticRouteCandidatePlanner();

        List<StaticRouteCandidate> first = planner.plan(plan, "same-seed");
        List<StaticRouteCandidate> second = planner.plan(plan, "same-seed");

        assertThat(first).isEqualTo(second);
        assertThat(first)
                .extracting(StaticRouteCandidate::executionResourceId)
                .doesNotHaveDuplicates();
    }

    private StaticRoutePlan plan() {
        return new StaticRoutePlan("tenant-1", "public-chat", "CHAT_COMPLETIONS",
                "policy-1", 7, "VALID", List.of(
                new StaticRouteTargetTier(200, List.of(
                        pool("pool-a", 20, List.of(
                                new StaticRouteMemberTier(100, List.of(
                                        resource("res-a1", 10),
                                        resource("res-a2", 30))),
                                new StaticRouteMemberTier(10, List.of(
                                        resource("res-a-low", 100))))),
                        pool("pool-b", 80, List.of(
                                new StaticRouteMemberTier(100, List.of(
                                        resource("res-b1", 100))))))),
                new StaticRouteTargetTier(100, List.of(
                        pool("pool-c", 100, List.of(
                                new StaticRouteMemberTier(100, List.of(
                                        resource("res-c1", 100)))))))));
    }

    private StaticRoutePoolCandidate pool(String id, int weight, List<StaticRouteMemberTier> tiers) {
        return new StaticRoutePoolCandidate(id, id, weight, tiers);
    }

    private StaticRouteResourceCandidate resource(String id, int weight) {
        return new StaticRouteResourceCandidate(id, "conn-" + id, "provider-1",
                "OPENAI_COMPATIBLE", "https://api.example.test/v1/", "upstream-" + id, weight);
    }

    private int indexOf(List<StaticRouteCandidate> candidates, String resourceId) {
        for (int i = 0; i < candidates.size(); i++) {
            if (resourceId.equals(candidates.get(i).executionResourceId())) {
                return i;
            }
        }
        return -1;
    }
}
