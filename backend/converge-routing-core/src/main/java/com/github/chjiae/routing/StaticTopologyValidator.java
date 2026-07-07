package com.github.chjiae.routing;

import com.github.chjiae.contract.gateway.GatewayRoutePolicySnapshot;
import com.github.chjiae.contract.gateway.GatewaySnapshotSchema;
import com.github.chjiae.contract.gateway.GatewayTenantSnapshot;

import java.util.ArrayList;
import java.util.List;

/**
 * 静态拓扑验证器。
 * 用于控制面启用校验和网关加载 V2 快照时验证所有启用策略。
 */
public class StaticTopologyValidator {

    /** 启用状态 */
    private static final String ENABLED = "ENABLED";

    /** 编译器 */
    private final StaticRoutePlanCompiler compiler = new StaticRoutePlanCompiler();

    /**
     * 校验 V2 快照中所有启用路由策略。
     * V1 快照不包含路由拓扑，视为无 route plan 的兼容快照。
     *
     * @param snapshot 租户快照
     * @return 校验结果集合
     */
    public List<StaticRouteValidationResult> validateEnabledPolicies(GatewayTenantSnapshot snapshot) {
        if (snapshot.schemaVersion() < GatewaySnapshotSchema.VERSION_2) {
            return List.of();
        }
        List<StaticRouteValidationResult> results = new ArrayList<>();
        for (GatewayRoutePolicySnapshot policy : snapshot.routePolicies()) {
            if (ENABLED.equals(policy.adminStatus())) {
                results.add(compiler.compile(snapshot, policy.publicModelCode(), policy.canonicalOperation()));
            }
        }
        return List.copyOf(results);
    }
}
