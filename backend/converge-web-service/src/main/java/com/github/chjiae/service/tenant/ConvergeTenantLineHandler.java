package com.github.chjiae.service.tenant;

import com.baomidou.mybatisplus.extension.plugins.handler.TenantLineHandler;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.LongValue;

import java.util.Set;

/**
 * 多租户行处理器，从 TenantContext 获取当前租户 ID。
 * 自动为 SQL 语句注入 tenant_id 条件，实现行级数据隔离。
 */
public class ConvergeTenantLineHandler implements TenantLineHandler {

    /** 不需要租户过滤的表（平台级数据表或无 tenant_id 列的关联表） */
    private static final Set<String> IGNORE_TABLES = Set.of(
        "tenant",              // 租户表本身
        "tenant_application",  // 租户申请（无 tenant_id）
        "permission",          // 权限表（全局共享）
        "subscription_plan",    // 订阅套餐配置（全局共享）
        "audit_log",           // 审计日志（查询时需跨租户）
        "user_role",           // 用户角色关联表（无 tenant_id，通过 user/role 表隔离）
        "card_key"             // 卡密表（生成时不关联租户，兑换时手动设置 tenant_id）
    );

    @Override
    public Expression getTenantId() {
        Long tenantId = TenantContext.getTenantId();
        // 返回 Java null 时，MyBatis-Plus 跳过租户条件注入；
        // 超管或平台级操作无 tenantId，不应拼接 tenant_id = NULL
        if (tenantId == null) {
            return null;
        }
        return new LongValue(tenantId);
    }

    @Override
    public String getTenantIdColumn() {
        return "tenant_id";
    }

    @Override
    public boolean ignoreTable(String tableName) {
        // 如果当前上下文标记为忽略租户过滤，则跳过所有表的租户条件注入
        if (TenantContext.isIgnoreTenant()) {
            return true;
        }
        // 超管无 tenantId，应跳过所有租户条件注入（避免生成 tenant_id = null 的无效 SQL）
        if (TenantContext.getTenantId() == null) {
            return true;
        }
        return IGNORE_TABLES.contains(tableName);
    }
}
