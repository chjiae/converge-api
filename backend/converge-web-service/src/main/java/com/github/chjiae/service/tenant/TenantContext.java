package com.github.chjiae.service.tenant;

/**
 * 租户上下文，基于 ThreadLocal 存储当前请求的租户 ID。
 * 在请求入口设置，请求结束时清理。
 */
public class TenantContext {

    private static final ThreadLocal<Long> CURRENT_TENANT = new ThreadLocal<>();

    /**
     * 设置当前租户 ID
     * @param tenantId 租户 ID
     */
    public static void setTenantId(Long tenantId) {
        CURRENT_TENANT.set(tenantId);
    }

    /**
     * 获取当前租户 ID
     * @return 租户 ID，未设置时返回 null
     */
    public static Long getTenantId() {
        return CURRENT_TENANT.get();
    }

    /**
     * 清理租户上下文，防止内存泄漏
     */
    public static void clear() {
        CURRENT_TENANT.remove();
    }
}
