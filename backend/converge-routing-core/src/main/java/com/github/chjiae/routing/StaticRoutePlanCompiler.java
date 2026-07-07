package com.github.chjiae.routing;

import com.github.chjiae.contract.gateway.GatewayExecutionResourceSnapshot;
import com.github.chjiae.contract.gateway.GatewayPublicModelSnapshot;
import com.github.chjiae.contract.gateway.GatewayResourceModelBindingSnapshot;
import com.github.chjiae.contract.gateway.GatewayResourcePoolMemberSnapshot;
import com.github.chjiae.contract.gateway.GatewayResourcePoolSnapshot;
import com.github.chjiae.contract.gateway.GatewayRoutePolicySnapshot;
import com.github.chjiae.contract.gateway.GatewayRouteTargetSnapshot;
import com.github.chjiae.contract.gateway.GatewaySnapshotSchema;
import com.github.chjiae.contract.gateway.GatewayTenantSnapshot;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 静态路由计划编译器。
 * 输入已验证快照和路由键，输出不可变 route plan；不访问 Redis、数据库或上游网络。
 */
public class StaticRoutePlanCompiler {

    /** 当前唯一合法选择策略 */
    private static final String PRIORITY_WEIGHTED = "PRIORITY_WEIGHTED";

    /** 启用状态 */
    private static final String ENABLED = "ENABLED";

    /** 排空状态 */
    private static final String DRAINING = "DRAINING";

    /** 权重下限 */
    private static final int MIN_WEIGHT = 1;

    /** 权重上限 */
    private static final int MAX_WEIGHT = 100000;

    /** 优先级下限 */
    private static final int MIN_PRIORITY = -100000;

    /** 优先级上限 */
    private static final int MAX_PRIORITY = 100000;

    /**
     * 编译指定路由键的静态计划。
     *
     * @param snapshot 租户快照
     * @param publicModelCode 公开模型编码
     * @param canonicalOperation 规范化操作类型
     * @return 编译结果
     */
    public StaticRouteValidationResult compile(GatewayTenantSnapshot snapshot,
                                               String publicModelCode,
                                               String canonicalOperation) {
        List<String> reasons = new ArrayList<>();
        if (snapshot.schemaVersion() < GatewaySnapshotSchema.MIN_SUPPORTED_VERSION
                || snapshot.schemaVersion() > GatewaySnapshotSchema.MAX_SUPPORTED_VERSION) {
            return StaticRouteValidationResult.invalid("SNAPSHOT_SCHEMA_UNSUPPORTED",
                    List.of("快照 schema 不支持: " + snapshot.schemaVersion()));
        }
        if (snapshot.schemaVersion() < GatewaySnapshotSchema.VERSION_2) {
            return StaticRouteValidationResult.invalid("STATIC_ROUTE_NOT_AVAILABLE",
                    List.of("V1 快照不包含静态路由拓扑"));
        }

        GatewayPublicModelSnapshot publicModel = snapshot.publicModels().stream()
                .filter(model -> publicModelCode.equals(model.code()))
                .findFirst()
                .orElse(null);
        if (publicModel == null) {
            return StaticRouteValidationResult.invalid("PUBLIC_MODEL_NOT_FOUND",
                    List.of("公开模型不存在或未启用: " + publicModelCode));
        }

        GatewayRoutePolicySnapshot policy = snapshot.routePolicies().stream()
                .filter(item -> ENABLED.equals(item.adminStatus()))
                .filter(item -> publicModel.modelId().equals(item.publicModelId()))
                .filter(item -> publicModelCode.equals(item.publicModelCode()))
                .filter(item -> canonicalOperation.equals(item.canonicalOperation()))
                .findFirst()
                .orElse(null);
        if (policy == null) {
            return StaticRouteValidationResult.invalid("ROUTE_POLICY_NOT_FOUND",
                    List.of("未找到启用的静态路由策略: " + publicModelCode + "/" + canonicalOperation));
        }
        if (!PRIORITY_WEIGHTED.equals(policy.selectionPolicy())) {
            return StaticRouteValidationResult.invalid("SELECTION_POLICY_UNSUPPORTED",
                    List.of("路由策略选择策略不支持"));
        }

        Map<String, GatewayResourcePoolSnapshot> pools = snapshot.resourcePools().stream()
                .collect(Collectors.toMap(GatewayResourcePoolSnapshot::poolId, Function.identity(), (a, b) -> a));
        Map<String, GatewayExecutionResourceSnapshot> resources = snapshot.executionResources().stream()
                .collect(Collectors.toMap(GatewayExecutionResourceSnapshot::resourceId, Function.identity(), (a, b) -> a));
        Map<String, GatewayResourceModelBindingSnapshot> bindings = snapshot.resourceModelBindings().stream()
                .filter(item -> ENABLED.equals(item.adminStatus()))
                .filter(item -> publicModel.modelId().equals(item.publicModelId()))
                .filter(item -> canonicalOperation.equals(item.canonicalOperation()))
                .collect(Collectors.toMap(GatewayResourceModelBindingSnapshot::executionResourceId,
                        Function.identity(), (a, b) -> a));

        List<PoolWithTarget> validTargets = new ArrayList<>();
        for (GatewayRouteTargetSnapshot target : policy.targets()) {
            PoolWithTarget compiledTarget = compileTarget(target, pools, resources, bindings, reasons);
            if (compiledTarget != null) {
                validTargets.add(compiledTarget);
            }
        }
        if (validTargets.isEmpty()) {
            return StaticRouteValidationResult.invalid("NO_ELIGIBLE_ROUTE_TARGET",
                    reasons.isEmpty() ? List.of("路由策略没有可用目标池") : reasons);
        }

        List<StaticRouteTargetTier> targetTiers = validTargets.stream()
                .collect(Collectors.groupingBy(PoolWithTarget::targetPriority, LinkedHashMap::new, Collectors.toList()))
                .entrySet().stream()
                .map(entry -> new StaticRouteTargetTier(entry.getKey(),
                        entry.getValue().stream()
                                .map(PoolWithTarget::pool)
                                .toList()))
                .sorted(Comparator.comparing(StaticRouteTargetTier::priority).reversed())
                .toList();
        StaticRoutePlan plan = new StaticRoutePlan(snapshot.tenantId(), publicModelCode, canonicalOperation,
                policy.policyId(), snapshot.revision(), "VALID", targetTiers);
        return StaticRouteValidationResult.valid(plan);
    }

    private PoolWithTarget compileTarget(GatewayRouteTargetSnapshot target,
                                         Map<String, GatewayResourcePoolSnapshot> pools,
                                         Map<String, GatewayExecutionResourceSnapshot> resources,
                                         Map<String, GatewayResourceModelBindingSnapshot> bindings,
                                         List<String> reasons) {
        if (!ENABLED.equals(target.adminStatus())) {
            return null;
        }
        if (!validPriorityWeight(target.priority(), target.weight())) {
            reasons.add("路由目标优先级或权重非法: " + target.resourcePoolId());
            return null;
        }
        GatewayResourcePoolSnapshot pool = pools.get(target.resourcePoolId());
        if (pool == null || !ENABLED.equals(pool.adminStatus())) {
            reasons.add("资源池不存在或未启用: " + target.resourcePoolId());
            return null;
        }
        if (!PRIORITY_WEIGHTED.equals(pool.selectionPolicy())) {
            reasons.add("资源池选择策略不支持: " + target.resourcePoolId());
            return null;
        }
        List<StaticRouteResourceCandidate> memberCandidates = new ArrayList<>();
        for (GatewayResourcePoolMemberSnapshot member : pool.members()) {
            StaticRouteResourceCandidate candidate = compileMember(member, resources, bindings, reasons);
            if (candidate != null) {
                memberCandidates.add(candidate);
            }
        }
        if (memberCandidates.isEmpty()) {
            reasons.add("资源池没有可用成员: " + pool.poolId());
            return null;
        }
        Map<Integer, List<StaticRouteResourceCandidate>> byPriority = memberCandidates.stream()
                .collect(Collectors.groupingBy(candidatePriority(pool), LinkedHashMap::new, Collectors.toList()));
        List<StaticRouteMemberTier> memberTiers = byPriority.entrySet().stream()
                .map(entry -> new StaticRouteMemberTier(entry.getKey(),
                        entry.getValue().stream()
                                .sorted(Comparator.comparing(StaticRouteResourceCandidate::executionResourceId))
                                .toList()))
                .sorted(Comparator.comparing(StaticRouteMemberTier::priority).reversed())
                .toList();
        return new PoolWithTarget(target.priority(), pool.poolCode(),
                new StaticRoutePoolCandidate(pool.poolId(), pool.poolCode(), target.weight(), memberTiers));
    }

    private Function<StaticRouteResourceCandidate, Integer> candidatePriority(GatewayResourcePoolSnapshot pool) {
        Map<String, Integer> priorityByResource = pool.members().stream()
                .collect(Collectors.toMap(GatewayResourcePoolMemberSnapshot::executionResourceId,
                        GatewayResourcePoolMemberSnapshot::priority, (a, b) -> a));
        return candidate -> priorityByResource.getOrDefault(candidate.executionResourceId(), 0);
    }

    private StaticRouteResourceCandidate compileMember(GatewayResourcePoolMemberSnapshot member,
                                                       Map<String, GatewayExecutionResourceSnapshot> resources,
                                                       Map<String, GatewayResourceModelBindingSnapshot> bindings,
                                                       List<String> reasons) {
        if (!ENABLED.equals(member.adminStatus())) {
            return null;
        }
        if (!validPriorityWeight(member.priority(), member.weight())) {
            reasons.add("资源池成员优先级或权重非法: " + member.executionResourceId());
            return null;
        }
        GatewayExecutionResourceSnapshot resource = resources.get(member.executionResourceId());
        if (resource == null) {
            reasons.add("执行资源不存在: " + member.executionResourceId());
            return null;
        }
        if (DRAINING.equals(resource.adminStatus())) {
            reasons.add("排空资源不参与新请求候选: " + member.executionResourceId());
            return null;
        }
        if (!ENABLED.equals(resource.adminStatus())) {
            reasons.add("执行资源未启用: " + member.executionResourceId());
            return null;
        }
        GatewayResourceModelBindingSnapshot binding = bindings.get(member.executionResourceId());
        if (binding == null || binding.upstreamModelName() == null || binding.upstreamModelName().isBlank()) {
            reasons.add("缺少精确模型绑定: " + member.executionResourceId());
            return null;
        }
        return new StaticRouteResourceCandidate(resource.resourceId(), resource.connectionId(),
                resource.providerId(), resource.protocolType(), resource.baseUrl(),
                binding.upstreamModelName(), member.weight());
    }

    private boolean validPriorityWeight(int priority, int weight) {
        return priority >= MIN_PRIORITY && priority <= MAX_PRIORITY
                && weight >= MIN_WEIGHT && weight <= MAX_WEIGHT;
    }

    private record PoolWithTarget(int targetPriority, String poolCode, StaticRoutePoolCandidate pool) {
    }
}
