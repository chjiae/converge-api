package com.github.chjiae.service.aspect;

import com.github.chjiae.service.annotation.Auditable;
import com.github.chjiae.service.service.AuditLogService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;

/**
 * 审计日志切面，拦截 @Auditable 注解的方法，在方法执行成功后自动记录审计日志。
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class AuditLogAspect {

    /** 审计日志服务 */
    private final AuditLogService auditLogService;

    /** SpEL 表达式解析器 */
    private final ExpressionParser parser = new SpelExpressionParser();

    /** 参数名发现器，用于解析方法参数名 */
    private final ParameterNameDiscoverer nameDiscoverer = new DefaultParameterNameDiscoverer();

    /**
     * 环绕通知：拦截所有 @Auditable 注解的方法
     *
     * @param joinPoint 连接点
     * @param auditable 注解实例
     * @return 方法执行结果
     * @throws Throwable 方法执行异常
     */
    @Around("@annotation(auditable)")
    public Object audit(ProceedingJoinPoint joinPoint, Auditable auditable) throws Throwable {
        // 先执行目标方法
        Object result = joinPoint.proceed();

        // 方法执行成功后记录审计日志
        try {
            String module = auditable.module();
            String action = auditable.action();
            String target = resolveTarget(joinPoint, auditable.target());

            // 获取当前 HTTP 请求（可能为空，如异步任务场景）
            HttpServletRequest request = getCurrentRequest();

            auditLogService.log(module, action, target, null, request);
        } catch (Exception e) {
            // 审计日志记录失败不应影响主流程
            log.error("审计日志记录失败: {}", e.getMessage(), e);
        }

        return result;
    }

    /**
     * 解析 target 表达式（支持 SpEL）
     * 如果 target 是空字符串则直接返回空；
     * 如果 target 以 "#" 开头则作为 SpEL 表达式解析方法参数；
     * 否则直接返回原始字符串。
     *
     * @param joinPoint     连接点
     * @param targetExpr    target 表达式
     * @return 解析后的操作对象描述
     */
    private String resolveTarget(ProceedingJoinPoint joinPoint, String targetExpr) {
        if (targetExpr == null || targetExpr.isEmpty()) {
            return "";
        }

        // 不以 # 开头，直接返回原始字符串
        if (!targetExpr.contains("#")) {
            return targetExpr;
        }

        try {
            MethodSignature signature = (MethodSignature) joinPoint.getSignature();
            Method method = signature.getMethod();
            String[] paramNames = nameDiscoverer.getParameterNames(method);
            Object[] args = joinPoint.getArgs();

            // 构建 SpEL 上下文
            EvaluationContext context = new StandardEvaluationContext();
            if (paramNames != null) {
                for (int i = 0; i < paramNames.length; i++) {
                    ((StandardEvaluationContext) context).setVariable(paramNames[i], args[i]);
                }
            }

            Object value = parser.parseExpression(targetExpr).getValue(context);
            return value != null ? value.toString() : "";
        } catch (Exception e) {
            log.warn("SpEL 表达式解析失败: {}, 使用原始值", targetExpr);
            return targetExpr;
        }
    }

    /**
     * 获取当前 HTTP 请求
     *
     * @return 当前请求，不存在时返回 null
     */
    private HttpServletRequest getCurrentRequest() {
        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        return attrs != null ? attrs.getRequest() : null;
    }
}
