package com.github.chjiae.service.mapper.ai;

import com.github.chjiae.service.service.ai.GatewaySnapshotExecutionResourceRow;
import com.github.chjiae.service.service.ai.GatewaySnapshotPublicModelRow;
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
}
