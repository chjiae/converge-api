package com.github.chjiae.service.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 审计日志注解，标注在 Controller 方法上自动记录审计日志。
 * 配合 {@link com.github.chjiae.service.aspect.AuditLogAspect} 使用，
 * 在方法执行成功后自动写入审计日志表。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Auditable {

    /**
     * 模块名称（如 auth, user, role, tenant, subscription 等）
     */
    String module();

    /**
     * 操作类型（如 create, update, delete, login, logout 等）
     */
    String action();

    /**
     * 操作对象描述（支持 SpEL 表达式），如 "user:123"
     */
    String target() default "";
}
