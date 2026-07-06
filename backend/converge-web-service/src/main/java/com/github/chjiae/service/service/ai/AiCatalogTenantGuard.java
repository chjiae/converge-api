package com.github.chjiae.service.service.ai;

import com.github.chjiae.common.exception.BusinessException;
import com.github.chjiae.service.tenant.TenantContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * AI 控制面租户上下文守卫。
 * 本阶段 AI 目录是租户私有数据，因此所有 Service 入口都必须显式要求 tenantId 非空。
 */
@Slf4j
@Component
public class AiCatalogTenantGuard {

    /**
     * 读取并校验当前租户 ID。
     *
     * @return 当前租户 ID
     * @throws BusinessException 当前请求没有租户上下文时抛出
     */
    public Long requireCurrentTenantId() {
        Long tenantId = TenantContext.getTenantId();
        if (tenantId == null) {
            log.warn("AI 控制面目录操作被拒绝，当前请求缺少租户上下文");
            throw new BusinessException(403, "AI 控制面目录操作需要租户上下文");
        }
        return tenantId;
    }
}
