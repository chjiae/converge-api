package com.github.chjiae.service.tenant;

import com.baomidou.mybatisplus.extension.plugins.handler.TenantLineHandler;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.LongValue;
import net.sf.jsqlparser.expression.NullValue;

import java.util.Set;

/**
 * 多租户行处理器，从 TenantContext 获取当前租户 ID。
 * 自动为 SQL 语句注入 tenant_id 条件，实现行级数据隔离。
 */
public class ConvergeTenantLineHandler implements TenantLineHandler {

    /** 不需要租户过滤的表（平台级数据表） */
    private static final Set<String> IGNORE_TABLES = Set.of(
        "tenant",              // 租户表本身
        "tenant_application",  // 租户申请（无 tenant_id）
        "permission",          // 权限表（全局共享）
        "audit_log"            // 审计日志（查询时需跨租户）
    );

    @Override
    public Expression getTenantId() {
        Long tenantId = TenantContext.getTenantId();
        if (tenantId == null) {
            return new NullValue();
        }
        return new LongValue(tenantId);
    }

    @Override
    public String getTenantIdColumn() {
        return "tenant_id";
    }

    @Override
    public boolean ignoreTable(String tableName) {
        return IGNORE_TABLES.contains(tableName);
    }
}
