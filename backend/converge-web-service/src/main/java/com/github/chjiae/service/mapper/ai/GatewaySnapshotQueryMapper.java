package com.github.chjiae.service.mapper.ai;

import com.github.chjiae.service.service.ai.GatewaySnapshotExecutionResourceRow;
import com.github.chjiae.service.service.ai.GatewaySnapshotAccessGroupModelGrantRow;
import com.github.chjiae.service.service.ai.GatewaySnapshotAccessGroupRow;
import com.github.chjiae.service.service.ai.GatewaySnapshotClientApiKeyAccessGroupRow;
import com.github.chjiae.service.service.ai.GatewaySnapshotClientApiKeyRow;
import com.github.chjiae.service.service.ai.GatewaySnapshotModelBindingRow;
import com.github.chjiae.service.service.ai.GatewaySnapshotPoolMemberRow;
import com.github.chjiae.service.service.ai.GatewaySnapshotPublicModelRow;
import com.github.chjiae.service.service.ai.GatewaySnapshotResourcePoolRow;
import com.github.chjiae.service.service.ai.GatewaySnapshotRoutePolicyRow;
import com.github.chjiae.service.service.ai.GatewaySnapshotRouteTargetRow;
import com.github.chjiae.service.service.ai.GatewaySnapshotRuntimePolicyRow;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 网关快照专用查询 Mapper。
 * 所有 SQL 必须显式包含 tenant_id 条件，后台投影不得依赖 TenantContext。
 */
public interface GatewaySnapshotQueryMapper {

    /**
     * 查询租户状态。
     *
     * @param tenantId 租户 ID
     * @return 租户状态
     */
    String selectTenantStatus(@Param("tenantId") Long tenantId);

    /**
     * 查询租户内已启用公开模型。
     *
     * @param tenantId 租户 ID
     * @return 公开模型快照行
     */
    List<GatewaySnapshotPublicModelRow> selectEnabledPublicModels(@Param("tenantId") Long tenantId);

    /**
     * 查询租户内可投影 Direct API 执行资源。
     *
     * @param tenantId 租户 ID
     * @return 执行资源快照行
     */
    List<GatewaySnapshotExecutionResourceRow> selectEligibleExecutionResources(@Param("tenantId") Long tenantId);

    /**
     * 查询租户内资源池。
     *
     * @param tenantId 租户 ID
     * @return 资源池快照行
     */
    List<GatewaySnapshotResourcePoolRow> selectResourcePools(@Param("tenantId") Long tenantId);

    /**
     * 查询租户内资源池成员。
     *
     * @param tenantId 租户 ID
     * @return 资源池成员快照行
     */
    List<GatewaySnapshotPoolMemberRow> selectResourcePoolMembers(@Param("tenantId") Long tenantId);

    /**
     * 查询租户内资源模型绑定。
     *
     * @param tenantId 租户 ID
     * @return 模型绑定快照行
     */
    List<GatewaySnapshotModelBindingRow> selectResourceModelBindings(@Param("tenantId") Long tenantId);

    /**
     * 查询租户内路由策略。
     *
     * @param tenantId 租户 ID
     * @return 路由策略快照行
     */
    List<GatewaySnapshotRoutePolicyRow> selectRoutePolicies(@Param("tenantId") Long tenantId);

    /**
     * 查询租户内路由目标。
     *
     * @param tenantId 租户 ID
     * @return 路由目标快照行
     */
    List<GatewaySnapshotRouteTargetRow> selectRouteTargets(@Param("tenantId") Long tenantId);

    /**
     * 查询租户内访问组。
     *
     * @param tenantId 租户 ID
     * @return 访问组快照行
     */
    List<GatewaySnapshotAccessGroupRow> selectAccessGroups(@Param("tenantId") Long tenantId);

    /**
     * 查询租户内访问组模型授权。
     *
     * @param tenantId 租户 ID
     * @return 授权快照行
     */
    List<GatewaySnapshotAccessGroupModelGrantRow> selectAccessGroupModelGrants(@Param("tenantId") Long tenantId);

    /**
     * 查询租户内 Client API Key verifier 元数据。
     *
     * @param tenantId 租户 ID
     * @return Client API Key 快照行
     */
    List<GatewaySnapshotClientApiKeyRow> selectClientApiKeys(@Param("tenantId") Long tenantId);

    /**
     * 查询租户内 Client API Key 访问组绑定。
     *
     * @param tenantId 租户 ID
     * @return 绑定快照行
     */
    List<GatewaySnapshotClientApiKeyAccessGroupRow> selectClientApiKeyAccessGroups(@Param("tenantId") Long tenantId);

    /**
     * 查询租户内执行资源运行时治理策略。
     *
     * @param tenantId 租户 ID
     * @return 运行时治理策略快照行
     */
    List<GatewaySnapshotRuntimePolicyRow> selectExecutionResourceRuntimePolicies(@Param("tenantId") Long tenantId);
}
