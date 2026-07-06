package com.github.chjiae.service.mapper.ai;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.github.chjiae.service.entity.ai.AiGatewaySnapshotRevision;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * AI 网关租户快照 revision Mapper。
 */
public interface AiGatewaySnapshotRevisionMapper extends BaseMapper<AiGatewaySnapshotRevision> {

    /**
     * 递增租户 revision 并返回新值。
     *
     * @param tenantId 租户 ID
     * @param now 当前时间
     * @return 递增后的 revision
     */
    Long incrementAndGetRevision(@Param("tenantId") Long tenantId, @Param("now") LocalDateTime now);

    /**
     * 查询所有存在 revision 事实的租户。
     *
     * @return 租户 ID 列表
     */
    List<Long> selectTenantIdsWithRevision();
}
