package com.github.chjiae.routing;

import com.github.chjiae.contract.gateway.GatewayExecutionResourceSnapshot;
import com.github.chjiae.contract.gateway.GatewayPublicModelSnapshot;
import com.github.chjiae.contract.gateway.GatewayResourceModelBindingSnapshot;
import com.github.chjiae.contract.gateway.GatewayResourcePoolMemberSnapshot;
import com.github.chjiae.contract.gateway.GatewayResourcePoolSnapshot;
import com.github.chjiae.contract.gateway.GatewayRoutePolicySnapshot;
import com.github.chjiae.contract.gateway.GatewayRouteTargetSnapshot;
import com.github.chjiae.contract.gateway.GatewaySecretEnvelope;
import com.github.chjiae.contract.gateway.GatewaySnapshotSchema;
import com.github.chjiae.contract.gateway.GatewayTenantSnapshot;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 静态路由核心测试。
 * 覆盖优先级、权重、精确模型绑定、禁用组件、DRAINING 资源和依赖边界。
 */
class StaticRoutePlanCompilerTest {

    @Test
    void compiler_高优先级优先且同优先级按Seed稳定预览() {
        GatewayTenantSnapshot snapshot = routeSnapshot();

        StaticRouteValidationResult result = new StaticRoutePlanCompiler()
                .compile(snapshot, "public-chat", "CHAT_COMPLETIONS");

        assertThat(result.valid()).isTrue();
        StaticRoutePlan plan = result.plan();
        assertThat(plan.routeTargetTiers()).extracting(StaticRouteTargetTier::priority)
                .containsExactly(100, 50);
        assertThat(plan.routeTargetTiers().getFirst().pools()).extracting(StaticRoutePoolCandidate::poolCode)
                .containsExactly("primary-pool", "burst-pool");
        assertThat(plan.routeTargetTiers().getFirst().pools().getFirst().memberTiers().getFirst().resources())
                .extracting(StaticRouteResourceCandidate::executionResourceId)
                .containsExactly("res-a", "res-b");

        StaticRoutePreview first = new StaticRoutePreviewSelector().select(plan, "seed-42");
        StaticRoutePreview second = new StaticRoutePreviewSelector().select(plan, "seed-42");
        assertThat(first.selectedPoolId()).isEqualTo(second.selectedPoolId());
        assertThat(first.selectedResourceId()).isEqualTo(second.selectedResourceId());
        assertThat(first.warnings()).containsExactly(
                "STATIC_CONFIGURATION_ONLY",
                "DYNAMIC_STATE_NOT_APPLIED",
                "NO_UPSTREAM_REQUEST_EXECUTED");
    }

    @Test
    void compiler_禁用组件Drain资源和模型不匹配不会成为候选() {
        GatewayTenantSnapshot snapshot = routeSnapshotWithOnlyInvalidMembers();

        StaticRouteValidationResult result = new StaticRoutePlanCompiler()
                .compile(snapshot, "public-chat", "CHAT_COMPLETIONS");

        assertThat(result.valid()).isFalse();
        assertThat(result.errorCategory()).isEqualTo("NO_ELIGIBLE_ROUTE_TARGET");
        assertThat(result.safeReasons()).allMatch(reason -> !reason.contains("secret"));
    }

    @Test
    void dependency_routingCore不依赖服务框架或存储客户端() throws Exception {
        String pom = Files.readString(Path.of("pom.xml"));

        assertThat(pom).doesNotContain("spring-boot");
        assertThat(pom).doesNotContain("io.vertx");
        assertThat(pom).doesNotContain("redis");
        assertThat(pom).doesNotContain("mybatis");
        assertThat(pom).doesNotContain("flyway");
        assertThat(pom).doesNotContain("postgres");
        assertThat(pom).doesNotContain("servlet");
    }

    private GatewayTenantSnapshot routeSnapshot() {
        GatewaySecretEnvelope envelope = new GatewaySecretEnvelope("k", "AES-256-GCM", "n", "c");
        return new GatewayTenantSnapshot(
                GatewaySnapshotSchema.VERSION_2,
                "tenant-1",
                9,
                1000,
                List.of(new GatewayPublicModelSnapshot("tenant-1", "model-1", "public-chat", "公开模型", "chat")),
                List.of(
                        resource("res-a", "ENABLED", envelope),
                        resource("res-b", "ENABLED", envelope),
                        resource("res-c", "ENABLED", envelope)
                ),
                List.of(
                        new GatewayResourcePoolSnapshot("tenant-1", "pool-primary", "primary-pool", "主池",
                                "ENABLED", "PRIORITY_WEIGHTED", List.of(
                                new GatewayResourcePoolMemberSnapshot("tenant-1", "pool-primary", "res-a",
                                        "ENABLED", 100, 70),
                                new GatewayResourcePoolMemberSnapshot("tenant-1", "pool-primary", "res-b",
                                        "ENABLED", 100, 30))),
                        new GatewayResourcePoolSnapshot("tenant-1", "pool-burst", "burst-pool", "突发池",
                                "ENABLED", "PRIORITY_WEIGHTED", List.of(
                                new GatewayResourcePoolMemberSnapshot("tenant-1", "pool-burst", "res-c",
                                        "ENABLED", 100, 100))),
                        new GatewayResourcePoolSnapshot("tenant-1", "pool-fallback", "fallback-pool", "后备池",
                                "ENABLED", "PRIORITY_WEIGHTED", List.of(
                                new GatewayResourcePoolMemberSnapshot("tenant-1", "pool-fallback", "res-c",
                                        "ENABLED", 100, 100)))
                ),
                List.of(
                        binding("res-a", "model-1", "CHAT_COMPLETIONS", "upstream-a", "ENABLED"),
                        binding("res-b", "model-1", "CHAT_COMPLETIONS", "upstream-b", "ENABLED"),
                        binding("res-c", "model-1", "CHAT_COMPLETIONS", "upstream-c", "ENABLED")
                ),
                List.of(new GatewayRoutePolicySnapshot("tenant-1", "policy-1", "model-1", "public-chat",
                        "CHAT_COMPLETIONS", "ENABLED", "PRIORITY_WEIGHTED", List.of(
                        new GatewayRouteTargetSnapshot("tenant-1", "policy-1", "pool-primary",
                                "ENABLED", 100, 60),
                        new GatewayRouteTargetSnapshot("tenant-1", "policy-1", "pool-burst",
                                "ENABLED", 100, 40),
                        new GatewayRouteTargetSnapshot("tenant-1", "policy-1", "pool-fallback",
                                "ENABLED", 50, 100))))
        );
    }

    private GatewayTenantSnapshot routeSnapshotWithOnlyInvalidMembers() {
        GatewaySecretEnvelope envelope = new GatewaySecretEnvelope("k", "AES-256-GCM", "n", "c");
        return new GatewayTenantSnapshot(
                GatewaySnapshotSchema.VERSION_2,
                "tenant-1",
                10,
                1000,
                List.of(new GatewayPublicModelSnapshot("tenant-1", "model-1", "public-chat", "公开模型", "chat")),
                List.of(
                        resource("res-disabled", "DISABLED", envelope),
                        resource("res-draining", "DRAINING", envelope),
                        resource("res-mismatch", "ENABLED", envelope)
                ),
                List.of(new GatewayResourcePoolSnapshot("tenant-1", "pool-1", "pool", "资源池",
                        "ENABLED", "PRIORITY_WEIGHTED", List.of(
                        new GatewayResourcePoolMemberSnapshot("tenant-1", "pool-1", "res-disabled",
                                "ENABLED", 100, 100),
                        new GatewayResourcePoolMemberSnapshot("tenant-1", "pool-1", "res-draining",
                                "ENABLED", 100, 100),
                        new GatewayResourcePoolMemberSnapshot("tenant-1", "pool-1", "res-mismatch",
                                "ENABLED", 100, 100)))),
                List.of(binding("res-mismatch", "model-1", "EMBEDDINGS", "embedding-model", "ENABLED")),
                List.of(new GatewayRoutePolicySnapshot("tenant-1", "policy-1", "model-1", "public-chat",
                        "CHAT_COMPLETIONS", "ENABLED", "PRIORITY_WEIGHTED", List.of(
                        new GatewayRouteTargetSnapshot("tenant-1", "policy-1", "pool-1",
                                "ENABLED", 100, 100))))
        );
    }

    private GatewayExecutionResourceSnapshot resource(String id, String status, GatewaySecretEnvelope envelope) {
        return new GatewayExecutionResourceSnapshot("tenant-1", id, "provider-1", "conn-" + id,
                "cred-" + id, "DIRECT_API", status, "OPENAI", "OPENAI_COMPATIBLE",
                "https://api.example.test/v1/", envelope);
    }

    private GatewayResourceModelBindingSnapshot binding(String resourceId, String modelId, String operation,
                                                        String upstreamModel, String status) {
        return new GatewayResourceModelBindingSnapshot("tenant-1", resourceId, modelId,
                operation, upstreamModel, status);
    }
}
