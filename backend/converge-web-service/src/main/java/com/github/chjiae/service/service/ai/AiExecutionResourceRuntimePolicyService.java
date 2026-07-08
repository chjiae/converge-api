package com.github.chjiae.service.service.ai;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.github.chjiae.common.exception.BusinessException;
import com.github.chjiae.service.dto.ai.AiExecutionResourceRuntimePolicyResponse;
import com.github.chjiae.service.dto.ai.AiExecutionResourceRuntimePolicyUpdateRequest;
import com.github.chjiae.service.entity.ai.AiExecutionResource;
import com.github.chjiae.service.entity.ai.AiExecutionResourceRuntimePolicy;
import com.github.chjiae.service.mapper.ai.AiExecutionResourceMapper;
import com.github.chjiae.service.mapper.ai.AiExecutionResourceRuntimePolicyMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * AI 执行资源运行时治理策略服务。
 * 所有读写均显式使用当前租户上下文，避免后台或超管无租户上下文访问租户数据。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiExecutionResourceRuntimePolicyService {

    /** 默认最大并发请求数，0 表示不限制 */
    public static final int DEFAULT_MAX_CONCURRENT_REQUESTS = 0;

    /** 默认连续失败阈值 */
    public static final int DEFAULT_CONSECUTIVE_FAILURE_THRESHOLD = 3;

    /** 默认失败计数重置窗口 */
    public static final long DEFAULT_FAILURE_RESET_AFTER_MS = 300_000L;

    /** 默认普通失败冷却时间 */
    public static final long DEFAULT_FAILURE_COOLDOWN_MS = 30_000L;

    /** 默认 429 冷却时间 */
    public static final long DEFAULT_RATE_LIMIT_COOLDOWN_MS = 60_000L;

    /** 运行时策略 Mapper */
    private final AiExecutionResourceRuntimePolicyMapper runtimePolicyMapper;

    /** 执行资源 Mapper */
    private final AiExecutionResourceMapper executionResourceMapper;

    /** AI 目录租户守卫 */
    private final AiCatalogTenantGuard tenantGuard;

    /** 网关快照变更记录器 */
    private final GatewaySnapshotChangeRecorder snapshotChangeRecorder;

    /**
     * 创建资源时写入默认运行时策略。
     * 调用方必须已开启事务，保证资源和默认策略同事务提交。
     *
     * @param tenantId 租户 ID
     * @param executionResourceId 执行资源 ID
     * @param now 当前时间
     */
    @Transactional(propagation = Propagation.MANDATORY, rollbackFor = Exception.class)
    public void createDefaultPolicy(Long tenantId, Long executionResourceId, LocalDateTime now) {
        AiExecutionResourceRuntimePolicy policy = new AiExecutionResourceRuntimePolicy();
        policy.setTenantId(tenantId);
        policy.setExecutionResourceId(executionResourceId);
        policy.setMaxConcurrentRequests(DEFAULT_MAX_CONCURRENT_REQUESTS);
        policy.setConsecutiveFailureThreshold(DEFAULT_CONSECUTIVE_FAILURE_THRESHOLD);
        policy.setFailureResetAfterMs(DEFAULT_FAILURE_RESET_AFTER_MS);
        policy.setFailureCooldownMs(DEFAULT_FAILURE_COOLDOWN_MS);
        policy.setRateLimitCooldownMs(DEFAULT_RATE_LIMIT_COOLDOWN_MS);
        policy.setPolicyVersion(1L);
        policy.setCreatedAt(now);
        policy.setUpdatedAt(now);
        runtimePolicyMapper.insert(policy);
        log.info("AI 执行资源默认运行时策略创建成功，租户 ID: {}，资源 ID: {}，策略 ID: {}",
                tenantId, executionResourceId, policy.getId());
    }

    /**
     * 查询执行资源运行时策略。
     *
     * @param executionResourceId 执行资源 ID
     * @return 策略响应
     */
    public AiExecutionResourceRuntimePolicyResponse getPolicy(Long executionResourceId) {
        Long tenantId = tenantGuard.requireCurrentTenantId();
        findResourceOrThrow(tenantId, executionResourceId);
        AiExecutionResourceRuntimePolicy policy = findPolicyOrThrow(tenantId, executionResourceId);
        return toResponse(policy);
    }

    /**
     * 更新执行资源运行时策略并写入网关快照 outbox。
     *
     * @param executionResourceId 执行资源 ID
     * @param request 更新请求
     * @return 更新后的策略响应
     */
    @Transactional(rollbackFor = Exception.class)
    public AiExecutionResourceRuntimePolicyResponse updatePolicy(Long executionResourceId,
                                                                 AiExecutionResourceRuntimePolicyUpdateRequest request) {
        Long tenantId = tenantGuard.requireCurrentTenantId();
        log.info("更新 AI 执行资源运行时策略，租户 ID: {}，资源 ID: {}", tenantId, executionResourceId);
        findResourceOrThrow(tenantId, executionResourceId);
        AiExecutionResourceRuntimePolicy policy = findPolicyOrThrow(tenantId, executionResourceId);
        policy.setMaxConcurrentRequests(request.getMaxConcurrentRequests());
        policy.setConsecutiveFailureThreshold(request.getConsecutiveFailureThreshold());
        policy.setFailureResetAfterMs(request.getFailureResetAfterMs());
        policy.setFailureCooldownMs(request.getFailureCooldownMs());
        policy.setRateLimitCooldownMs(request.getRateLimitCooldownMs());
        policy.setPolicyVersion(policy.getPolicyVersion() + 1);
        policy.setUpdatedAt(LocalDateTime.now());
        runtimePolicyMapper.updateById(policy);
        snapshotChangeRecorder.recordChange(tenantId,
                GatewaySnapshotChangeTypes.AI_EXECUTION_RESOURCE_RUNTIME_POLICY_CHANGED);
        log.info("AI 执行资源运行时策略更新成功，租户 ID: {}，资源 ID: {}，策略版本: {}",
                tenantId, executionResourceId, policy.getPolicyVersion());
        return toResponse(policy);
    }

    private AiExecutionResource findResourceOrThrow(Long tenantId, Long executionResourceId) {
        AiExecutionResource resource = executionResourceMapper.selectOne(
                new LambdaQueryWrapper<AiExecutionResource>()
                        .eq(AiExecutionResource::getTenantId, tenantId)
                        .eq(AiExecutionResource::getId, executionResourceId));
        if (resource == null) {
            log.warn("AI 可执行资源不存在，租户 ID: {}，资源 ID: {}", tenantId, executionResourceId);
            throw new BusinessException(404, "AI 可执行资源不存在");
        }
        return resource;
    }

    private AiExecutionResourceRuntimePolicy findPolicyOrThrow(Long tenantId, Long executionResourceId) {
        AiExecutionResourceRuntimePolicy policy = runtimePolicyMapper.selectOne(
                new LambdaQueryWrapper<AiExecutionResourceRuntimePolicy>()
                        .eq(AiExecutionResourceRuntimePolicy::getTenantId, tenantId)
                        .eq(AiExecutionResourceRuntimePolicy::getExecutionResourceId, executionResourceId));
        if (policy == null) {
            log.warn("AI 执行资源运行时策略不存在，租户 ID: {}，资源 ID: {}", tenantId, executionResourceId);
            throw new BusinessException(404, "AI 执行资源运行时策略不存在");
        }
        return policy;
    }

    private AiExecutionResourceRuntimePolicyResponse toResponse(AiExecutionResourceRuntimePolicy policy) {
        return AiExecutionResourceRuntimePolicyResponse.builder()
                .id(policy.getId())
                .tenantId(policy.getTenantId())
                .executionResourceId(policy.getExecutionResourceId())
                .maxConcurrentRequests(policy.getMaxConcurrentRequests())
                .consecutiveFailureThreshold(policy.getConsecutiveFailureThreshold())
                .failureResetAfterMs(policy.getFailureResetAfterMs())
                .failureCooldownMs(policy.getFailureCooldownMs())
                .rateLimitCooldownMs(policy.getRateLimitCooldownMs())
                .policyVersion(policy.getPolicyVersion())
                .createdAt(policy.getCreatedAt())
                .updatedAt(policy.getUpdatedAt())
                .build();
    }
}
