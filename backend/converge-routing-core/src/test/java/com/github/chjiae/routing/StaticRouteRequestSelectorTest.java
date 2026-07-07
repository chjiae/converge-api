package com.github.chjiae.routing;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 真实请求静态选择器测试。
 * 该选择器用于数据面请求，不复用管理端预览警告语义，也不读取动态状态或任何秘密。
 */
class StaticRouteRequestSelectorTest {

    @Test
    void select_只在最高RouteTarget优先级内按权重选择资源池() {
        StaticRoutePlan plan = planWithTwoTargetTiers();
        StaticRouteRequestSelector selector = new StaticRouteRequestSelector();

        boolean selectedHighPriorityPool = false;
        boolean selectedLowPriorityPool = false;
        for (int i = 0; i < 80; i++) {
            StaticRouteSelection selection = selector.select(plan, "request-" + i);
            if ("pool-high-a".equals(selection.resourcePoolId())
                    || "pool-high-b".equals(selection.resourcePoolId())) {
                selectedHighPriorityPool = true;
            }
            if ("pool-low".equals(selection.resourcePoolId())) {
                selectedLowPriorityPool = true;
            }
        }

        assertThat(selectedHighPriorityPool).isTrue();
        assertThat(selectedLowPriorityPool).isFalse();
    }

    @Test
    void select_在选中资源池内只使用最高成员优先级并按权重稳定选择资源() {
        StaticRoutePlan plan = planWithOnePoolAndTwoMemberTiers();
        StaticRouteRequestSelector selector = new StaticRouteRequestSelector();

        StaticRouteSelection first = selector.select(plan, "stable-seed");
        StaticRouteSelection second = selector.select(plan, "stable-seed");
        assertThat(first.executionResourceId()).isEqualTo(second.executionResourceId());
        assertThat(first.upstreamModelName()).isEqualTo(second.upstreamModelName());
        assertThat(first.memberPriority()).isEqualTo(100);

        boolean selectedHighPriorityResource = false;
        boolean selectedLowPriorityResource = false;
        for (int i = 0; i < 80; i++) {
            StaticRouteSelection selection = selector.select(plan, "member-" + i);
            if ("res-high-a".equals(selection.executionResourceId())
                    || "res-high-b".equals(selection.executionResourceId())) {
                selectedHighPriorityResource = true;
            }
            if ("res-low".equals(selection.executionResourceId())) {
                selectedLowPriorityResource = true;
            }
        }

        assertThat(selectedHighPriorityResource).isTrue();
        assertThat(selectedLowPriorityResource).isFalse();
    }

    @Test
    void select_输出不包含管理端预览警告或秘密字段() {
        StaticRoutePlan plan = planWithOnePoolAndTwoMemberTiers();

        StaticRouteSelection selection = new StaticRouteRequestSelector()
                .select(plan, "request-id|tenant|public-chat|CHAT_COMPLETIONS|1");

        assertThat(selection.routePolicyId()).isEqualTo("policy-1");
        assertThat(selection.publicModelCode()).isEqualTo("public-chat");
        assertThat(selection.canonicalOperation()).isEqualTo("CHAT_COMPLETIONS");
        assertThat(selection.snapshotRevision()).isEqualTo(1L);
        assertThat(selection.toString()).doesNotContain("runtime-secret");
        assertThat(selection.toString()).doesNotContain("STATIC_CONFIGURATION_ONLY");
    }

    private StaticRoutePlan planWithTwoTargetTiers() {
        StaticRouteResourceCandidate resourceA = resource("res-high-a", "upstream-a", 70);
        StaticRouteResourceCandidate resourceB = resource("res-high-b", "upstream-b", 30);
        StaticRouteResourceCandidate resourceLow = resource("res-low", "upstream-low", 100);

        StaticRoutePoolCandidate highA = new StaticRoutePoolCandidate("pool-high-a", "high-a", 70,
                List.of(new StaticRouteMemberTier(100, List.of(resourceA))));
        StaticRoutePoolCandidate highB = new StaticRoutePoolCandidate("pool-high-b", "high-b", 30,
                List.of(new StaticRouteMemberTier(100, List.of(resourceB))));
        StaticRoutePoolCandidate low = new StaticRoutePoolCandidate("pool-low", "low", 100,
                List.of(new StaticRouteMemberTier(100, List.of(resourceLow))));

        return new StaticRoutePlan("tenant-1", "public-chat", "CHAT_COMPLETIONS",
                "policy-1", 1, "VALID", List.of(
                new StaticRouteTargetTier(100, List.of(highA, highB)),
                new StaticRouteTargetTier(50, List.of(low))));
    }

    private StaticRoutePlan planWithOnePoolAndTwoMemberTiers() {
        StaticRouteResourceCandidate highA = resource("res-high-a", "upstream-a", 70);
        StaticRouteResourceCandidate highB = resource("res-high-b", "upstream-b", 30);
        StaticRouteResourceCandidate low = resource("res-low", "upstream-low", 100);

        StaticRoutePoolCandidate pool = new StaticRoutePoolCandidate("pool-1", "primary", 100, List.of(
                new StaticRouteMemberTier(100, List.of(highA, highB)),
                new StaticRouteMemberTier(10, List.of(low))));
        return new StaticRoutePlan("tenant-1", "public-chat", "CHAT_COMPLETIONS",
                "policy-1", 1, "VALID", List.of(new StaticRouteTargetTier(100, List.of(pool))));
    }

    private StaticRouteResourceCandidate resource(String id, String upstreamModelName, int weight) {
        return new StaticRouteResourceCandidate(id, "conn-1", "provider-1",
                "OPENAI_COMPATIBLE", "https://upstream.invalid/v1/", upstreamModelName, weight);
    }
}
