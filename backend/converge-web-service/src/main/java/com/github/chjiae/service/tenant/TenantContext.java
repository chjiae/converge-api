package com.github.chjiae.service.tenant;

/**
 * 租户上下文，基于 ThreadLocal 存储当前请求的租户 ID。
 * 在请求入口设置，请求结束时清理。
 * 支持设置忽略租户过滤，用于登录、注册等需要跨租户查询的场景。
 */
public class TenantContext {

    private static final ThreadLocal<Long> CURRENT_TENANT = new ThreadLocal<>();

    /** 是否忽略租户过滤（true 表示不注入 tenant_id 条件） */
    private static final ThreadLocal<Boolean> IGNORE_TENANT = ThreadLocal.withInitial(() -> false);

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
     * 设置是否忽略租户过滤
     * @param ignore true 表示不注入 tenant_id 条件
     */
    public static void setIgnoreTenant(boolean ignore) {
        IGNORE_TENANT.set(ignore);
    }

    /**
     * 获取是否忽略租户过滤
     * @return true 表示忽略租户过滤
     */
    public static boolean isIgnoreTenant() {
        return IGNORE_TENANT.get();
    }

    /**
     * 清理租户上下文，防止内存泄漏
     */
    public static void clear() {
        CURRENT_TENANT.remove();
        IGNORE_TENANT.remove();
    }
}
