package com.github.chjiae.service.service.ai;

import com.github.chjiae.common.enums.AiCredentialType;
import com.github.chjiae.contract.gateway.GatewayAccessGroupModelGrantSnapshot;
import com.github.chjiae.contract.gateway.GatewayAccessGroupSnapshot;
import com.github.chjiae.contract.gateway.GatewayClientApiKeyAccessGroupSnapshot;
import com.github.chjiae.contract.gateway.GatewayClientApiKeySnapshot;
import com.github.chjiae.contract.gateway.GatewayExecutionResourceSnapshot;
import com.github.chjiae.contract.gateway.GatewayPublicModelSnapshot;
import com.github.chjiae.contract.gateway.GatewayResourceModelBindingSnapshot;
import com.github.chjiae.contract.gateway.GatewayResourcePoolMemberSnapshot;
import com.github.chjiae.contract.gateway.GatewayResourcePoolSnapshot;
import com.github.chjiae.contract.gateway.GatewayRoutePolicySnapshot;
import com.github.chjiae.contract.gateway.GatewayRouteTargetSnapshot;
import com.github.chjiae.contract.gateway.GatewaySecretEnvelope;
import com.github.chjiae.contract.gateway.GatewaySnapshotChangedEvent;
import com.github.chjiae.contract.gateway.GatewaySnapshotCrypto;
import com.github.chjiae.contract.gateway.GatewaySnapshotJson;
import com.github.chjiae.contract.gateway.GatewaySnapshotManifest;
import com.github.chjiae.contract.gateway.GatewaySnapshotRedisKeys;
import com.github.chjiae.contract.gateway.GatewaySnapshotSchema;
import com.github.chjiae.contract.gateway.GatewayTenantSnapshot;
import com.github.chjiae.service.config.GatewaySnapshotProperties;
import com.github.chjiae.service.entity.ai.AiGatewaySnapshotOutbox;
import com.github.chjiae.service.entity.ai.AiGatewaySnapshotRevision;
import com.github.chjiae.service.mapper.ai.AiGatewaySnapshotOutboxMapper;
import com.github.chjiae.service.mapper.ai.AiGatewaySnapshotRevisionMapper;
import com.github.chjiae.service.mapper.ai.GatewaySnapshotQueryMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 网关快照 outbox 投影器。
 * 后台领取 outbox 后构建租户级不可变快照并发布到 Redis；请求事务内不会直接写 Redis。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GatewaySnapshotOutboxProjector {

    /** outbox 处理中状态 */
    private static final String STATUS_PROCESSING = "PROCESSING";

    /** 不需要投影资源的租户状态 */
    private static final List<String> EMPTY_SNAPSHOT_TENANT_STATUSES = List.of("DISABLED", "EXPIRED", "DELETED");

    /** revision Mapper */
    private final AiGatewaySnapshotRevisionMapper revisionMapper;

    /** outbox Mapper */
    private final AiGatewaySnapshotOutboxMapper outboxMapper;

    /** 快照专用显式 tenant 查询 Mapper */
    private final GatewaySnapshotQueryMapper snapshotQueryMapper;

    /** 数据库凭据解密服务 */
    private final AiCredentialEncryptionService credentialEncryptionService;

    /** Redis 发布器 */
    private final GatewaySnapshotRedisPublisher redisPublisher;

    /** 快照配置 */
    private final GatewaySnapshotProperties properties;

    /**
     * 周期性处理待发布 outbox。
     */
    @Scheduled(fixedDelayString = "${ai.gateway.snapshot.outbox-projector-interval-ms:5000}")
    public void scheduledProjectPending() {
        projectPendingOnce("scheduler-" + Thread.currentThread().threadId());
    }

    /**
     * 周期性全租户重投影，用于 Redis 清空或短暂故障后的恢复。
     */
    @Scheduled(fixedDelayString = "${ai.gateway.snapshot.reproject-interval-ms:60000}",
            initialDelayString = "${ai.gateway.snapshot.reproject-initial-delay-ms:10000}")
    public void scheduledReprojectAllTenants() {
        reprojectAllTenantsOnce("reproject-" + Thread.currentThread().threadId());
    }

    /**
     * 处理一批待发布 outbox。
     *
     * @param lockOwner 锁拥有者
     * @return 投影结果
     */
    @Transactional(rollbackFor = Exception.class)
    public ProjectorResult projectPendingOnce(String lockOwner) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime staleBefore = now.minusNanos(properties.getOutboxLockTimeoutMs() * 1_000_000L);
        List<AiGatewaySnapshotOutbox> claimed = outboxMapper.claimDue(lockOwner, now, staleBefore,
                properties.getOutboxBatchSize());
        if (claimed.isEmpty()) {
            return new ProjectorResult(0, 0, 0);
        }

        Map<Long, List<AiGatewaySnapshotOutbox>> byTenant = claimed.stream()
                .collect(Collectors.groupingBy(AiGatewaySnapshotOutbox::getTenantId,
                        LinkedHashMap::new, Collectors.toList()));

        int published = 0;
        int failed = 0;
        for (Map.Entry<Long, List<AiGatewaySnapshotOutbox>> entry : byTenant.entrySet()) {
            Long tenantId = entry.getKey();
            List<AiGatewaySnapshotOutbox> tenantOutboxes = entry.getValue();
            List<Long> ids = tenantOutboxes.stream().map(AiGatewaySnapshotOutbox::getId).toList();
            try {
                GatewaySnapshotPublication publication = buildPublication(tenantId);
                redisPublisher.publish(publication);
                outboxMapper.markPublished(ids, LocalDateTime.now());
                published++;
            } catch (Exception e) {
                failed++;
                LocalDateTime nextAttemptAt = calculateNextAttemptAt(tenantOutboxes);
                outboxMapper.markFailed(ids, safeErrorSummary(e), nextAttemptAt, LocalDateTime.now());
                log.error("AI 网关快照发布失败，租户 ID: {}，outbox 数量: {}", tenantId, tenantOutboxes.size(), e);
            }
        }
        return new ProjectorResult(published, failed, claimed.size());
    }

    /**
     * 对所有已有 revision 事实的租户进行重投影。
     *
     * @param lockOwner 触发者
     * @return 投影结果
     */
    public ProjectorResult reprojectAllTenantsOnce(String lockOwner) {
        List<Long> tenantIds = revisionMapper.selectTenantIdsWithRevision();
        int published = 0;
        int failed = 0;
        for (Long tenantId : tenantIds) {
            try {
                redisPublisher.publish(buildPublication(tenantId));
                published++;
            } catch (Exception e) {
                failed++;
                log.error("AI 网关快照重投影失败，租户 ID: {}，触发者: {}", tenantId, lockOwner, e);
            }
        }
        return new ProjectorResult(published, failed, tenantIds.size());
    }

    /**
     * 构建租户快照发布包。
     *
     * @param tenantId 租户 ID
     * @return 发布包
     */
    private GatewaySnapshotPublication buildPublication(Long tenantId) {
        AiGatewaySnapshotRevision revisionEntity = revisionMapper.selectById(tenantId);
        if (revisionEntity == null) {
            throw new IllegalStateException("租户快照 revision 不存在");
        }
        long revision = revisionEntity.getCurrentRevision();
        String tenantIdString = tenantId.toString();
        String tenantStatus = snapshotQueryMapper.selectTenantStatus(tenantId);

        List<GatewayPublicModelSnapshot> publicModels = new ArrayList<>();
        List<GatewayExecutionResourceSnapshot> executionResources = new ArrayList<>();
        List<GatewayResourcePoolSnapshot> resourcePools = new ArrayList<>();
        List<GatewayResourceModelBindingSnapshot> resourceModelBindings = new ArrayList<>();
        List<GatewayRoutePolicySnapshot> routePolicies = new ArrayList<>();
        List<GatewayAccessGroupSnapshot> accessGroups = new ArrayList<>();
        List<GatewayAccessGroupModelGrantSnapshot> accessGroupModelGrants = new ArrayList<>();
        List<GatewayClientApiKeySnapshot> clientApiKeys = new ArrayList<>();
        List<GatewayClientApiKeyAccessGroupSnapshot> clientApiKeyAccessGroups = new ArrayList<>();
        if (!EMPTY_SNAPSHOT_TENANT_STATUSES.contains(tenantStatus)) {
            publicModels.addAll(snapshotQueryMapper.selectEnabledPublicModels(tenantId).stream()
                    .map(row -> new GatewayPublicModelSnapshot(
                            row.getTenantId().toString(),
                            row.getModelId().toString(),
                            row.getCode(),
                            row.getDisplayName(),
                            row.getModelFamily()))
                    .toList());
            executionResources.addAll(snapshotQueryMapper.selectEligibleExecutionResources(tenantId).stream()
                    .sorted(Comparator.comparing(GatewaySnapshotExecutionResourceRow::getResourceId))
                    .map(row -> toExecutionResourceSnapshot(row, revision))
                    .toList());
            Map<Long, List<GatewaySnapshotPoolMemberRow>> membersByPool = snapshotQueryMapper
                    .selectResourcePoolMembers(tenantId).stream()
                    .collect(Collectors.groupingBy(GatewaySnapshotPoolMemberRow::getPoolId));
            resourcePools.addAll(snapshotQueryMapper.selectResourcePools(tenantId).stream()
                    .map(row -> new GatewayResourcePoolSnapshot(tenantIdString, row.getPoolId().toString(),
                            row.getPoolCode(), row.getDisplayName(), row.getAdminStatus(), row.getSelectionPolicy(),
                            membersByPool.getOrDefault(row.getPoolId(), List.of()).stream()
                                    .map(member -> new GatewayResourcePoolMemberSnapshot(tenantIdString,
                                            member.getPoolId().toString(), member.getExecutionResourceId().toString(),
                                            member.getAdminStatus(), member.getPriority(), member.getWeight()))
                                    .toList()))
                    .toList());
            resourceModelBindings.addAll(snapshotQueryMapper.selectResourceModelBindings(tenantId).stream()
                    .map(row -> new GatewayResourceModelBindingSnapshot(tenantIdString,
                            row.getExecutionResourceId().toString(), row.getPublicModelId().toString(),
                            row.getCanonicalOperation(), row.getUpstreamModelName(), row.getAdminStatus()))
                    .toList());
            Map<Long, List<GatewaySnapshotRouteTargetRow>> targetsByPolicy = snapshotQueryMapper
                    .selectRouteTargets(tenantId).stream()
                    .collect(Collectors.groupingBy(GatewaySnapshotRouteTargetRow::getPolicyId));
            routePolicies.addAll(snapshotQueryMapper.selectRoutePolicies(tenantId).stream()
                    .map(row -> new GatewayRoutePolicySnapshot(tenantIdString, row.getPolicyId().toString(),
                            row.getPublicModelId().toString(), row.getPublicModelCode(),
                            row.getCanonicalOperation(), row.getAdminStatus(), row.getSelectionPolicy(),
                            targetsByPolicy.getOrDefault(row.getPolicyId(), List.of()).stream()
                                    .map(target -> new GatewayRouteTargetSnapshot(tenantIdString,
                                            target.getPolicyId().toString(), target.getPoolId().toString(),
                                            target.getAdminStatus(), target.getPriority(), target.getWeight()))
                                    .toList()))
                    .toList());
            accessGroups.addAll(snapshotQueryMapper.selectAccessGroups(tenantId).stream()
                    .map(row -> new GatewayAccessGroupSnapshot(tenantIdString,
                            row.getAccessGroupId().toString(), row.getCode(), row.getAdminStatus()))
                    .toList());
            accessGroupModelGrants.addAll(snapshotQueryMapper.selectAccessGroupModelGrants(tenantId).stream()
                    .map(row -> new GatewayAccessGroupModelGrantSnapshot(tenantIdString,
                            row.getGrantId().toString(), row.getAccessGroupId().toString(),
                            row.getPublicModelId().toString(), row.getPublicModelCode(),
                            row.getCanonicalOperation(), row.getAdminStatus()))
                    .toList());
            clientApiKeys.addAll(snapshotQueryMapper.selectClientApiKeys(tenantId).stream()
                    .map(row -> new GatewayClientApiKeySnapshot(tenantIdString,
                            row.getClientApiKeyId().toString(), row.getKeyId(), row.getAdminStatus(),
                            row.getSecretHashAlgorithm(),
                            Base64.getEncoder().encodeToString(row.getSecretVerifierSalt()),
                            Base64.getEncoder().encodeToString(row.getSecretVerifierHash()),
                            row.getKeyVersion(),
                            toEpochMillis(row.getExpiresAt())))
                    .toList());
            clientApiKeyAccessGroups.addAll(snapshotQueryMapper.selectClientApiKeyAccessGroups(tenantId).stream()
                    .map(row -> new GatewayClientApiKeyAccessGroupSnapshot(tenantIdString,
                            row.getBindingId().toString(), row.getClientApiKeyId().toString(),
                            row.getAccessGroupId().toString(), row.getAdminStatus()))
                    .toList());
        }

        GatewayTenantSnapshot snapshot = new GatewayTenantSnapshot(GatewaySnapshotSchema.CURRENT_VERSION,
                tenantIdString, revision, System.currentTimeMillis(), publicModels, executionResources,
                resourcePools, resourceModelBindings, routePolicies,
                accessGroups, accessGroupModelGrants, clientApiKeys, clientApiKeyAccessGroups);
        byte[] payloadBytes = GatewaySnapshotJson.toBytes(snapshot);
        String payloadKey = GatewaySnapshotRedisKeys.payloadKey(tenantIdString, revision);
        String payloadSha256 = GatewaySnapshotCrypto.sha256Hex(payloadBytes);
        GatewaySnapshotManifest unsignedManifest = new GatewaySnapshotManifest(GatewaySnapshotSchema.CURRENT_VERSION,
                tenantIdString, revision, payloadKey, payloadSha256, "",
                properties.getKeyId(), System.currentTimeMillis());
        String hmac = GatewaySnapshotCrypto.signManifest(unsignedManifest, properties.getSigningKeyBytes());
        GatewaySnapshotManifest manifest = new GatewaySnapshotManifest(unsignedManifest.schemaVersion(),
                unsignedManifest.tenantId(), unsignedManifest.revision(), unsignedManifest.payloadRedisKey(),
                unsignedManifest.payloadSha256Hex(), hmac, unsignedManifest.gatewayKeyId(),
                unsignedManifest.publishedAtEpochMillis());
        GatewaySnapshotChangedEvent event = new GatewaySnapshotChangedEvent(GatewaySnapshotSchema.CURRENT_VERSION,
                tenantIdString, revision, GatewaySnapshotRedisKeys.currentManifestKey(tenantIdString),
                System.currentTimeMillis());

        return new GatewaySnapshotPublication(tenantIdString, revision, payloadKey,
                new String(payloadBytes, java.nio.charset.StandardCharsets.UTF_8), manifest, event);
    }

    /**
     * 转换执行资源快照，并完成数据库凭据短暂解密和网关投递 envelope 重新封装。
     *
     * @param row 数据库查询行
     * @param revision 快照 revision
     * @return 执行资源快照
     */
    private GatewayExecutionResourceSnapshot toExecutionResourceSnapshot(GatewaySnapshotExecutionResourceRow row,
                                                                         long revision) {
        String tenantId = row.getTenantId().toString();
        String resourceId = row.getResourceId().toString();
        String credentialId = row.getCredentialId().toString();
        String plaintextSecret = credentialEncryptionService.decrypt(row.getEncryptedSecret(), row.getNonce(),
                row.getTenantId(), row.getProviderId(), row.getSecretReference(),
                AiCredentialType.valueOf(row.getCredentialType()));
        try {
            GatewaySecretEnvelope envelope = GatewaySnapshotCrypto.encryptSecret(plaintextSecret,
                    properties.getEncryptionKeyBytes(),
                    GatewaySnapshotCrypto.secretAad(GatewaySnapshotSchema.CURRENT_VERSION, tenantId,
                            resourceId, credentialId, revision, properties.getKeyId()),
                    properties.getKeyId());
            return new GatewayExecutionResourceSnapshot(tenantId, resourceId,
                    row.getProviderId().toString(), row.getConnectionId().toString(), credentialId,
                    row.getResourceType(), row.getAdminStatus(), row.getProviderKind(),
                    row.getProtocolType(), row.getBaseUrl(), envelope);
        } finally {
            plaintextSecret = null;
        }
    }

    /**
     * 计算下次重试时间。
     *
     * @param outboxes outbox 事件
     * @return 下次重试时间
     */
    private LocalDateTime calculateNextAttemptAt(List<AiGatewaySnapshotOutbox> outboxes) {
        int maxAttempt = outboxes.stream()
                .map(AiGatewaySnapshotOutbox::getAttemptCount)
                .filter(value -> value != null)
                .max(Integer::compareTo)
                .orElse(0);
        long seconds = Math.min(60L, 1L << Math.min(maxAttempt, 5));
        return LocalDateTime.now().plusSeconds(seconds);
    }

    /**
     * 构建安全错误摘要，避免将任何请求体或秘密写入 outbox。
     *
     * @param throwable 异常
     * @return 安全摘要
     */
    private String safeErrorSummary(Throwable throwable) {
        String message = throwable.getMessage() == null ? "" : throwable.getMessage();
        String sanitized = (throwable.getClass().getSimpleName() + ": " + message)
                .replaceAll("[\\r\\n\\t]", " ");
        return sanitized.length() > 240 ? sanitized.substring(0, 240) : sanitized;
    }

    private long toEpochMillis(LocalDateTime value) {
        if (value == null) {
            return 0L;
        }
        return value.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }

    /**
     * Projector 执行结果。
     *
     * @param publishedTenantCount 成功发布租户数
     * @param failedTenantCount 失败租户数
     * @param claimedOutboxCount 认领 outbox 数量
     */
    public record ProjectorResult(int publishedTenantCount, int failedTenantCount, int claimedOutboxCount) {
    }
}
