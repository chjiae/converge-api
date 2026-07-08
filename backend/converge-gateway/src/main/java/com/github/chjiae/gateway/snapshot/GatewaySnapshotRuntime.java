package com.github.chjiae.gateway.snapshot;

import com.github.chjiae.contract.gateway.GatewayExecutionResourceSnapshot;
import com.github.chjiae.contract.gateway.GatewayExecutionResourceRuntimePolicySnapshot;
import com.github.chjiae.contract.gateway.GatewayAccessGroupModelGrantSnapshot;
import com.github.chjiae.contract.gateway.GatewayAccessGroupSnapshot;
import com.github.chjiae.contract.gateway.GatewayClientApiKeyAccessGroupSnapshot;
import com.github.chjiae.contract.gateway.GatewayClientApiKeySnapshot;
import com.github.chjiae.contract.gateway.GatewayClientKeyAuthenticationResult;
import com.github.chjiae.contract.gateway.GatewayClientKeyCrypto;
import com.github.chjiae.contract.gateway.GatewayClientPrincipal;
import com.github.chjiae.contract.gateway.GatewaySnapshotCrypto;
import com.github.chjiae.contract.gateway.GatewaySnapshotJson;
import com.github.chjiae.contract.gateway.GatewaySnapshotManifest;
import com.github.chjiae.contract.gateway.GatewaySnapshotRedisKeys;
import com.github.chjiae.contract.gateway.GatewaySnapshotSchema;
import com.github.chjiae.contract.gateway.GatewaySnapshotSyncState;
import com.github.chjiae.contract.gateway.GatewayTenantSnapshot;
import com.github.chjiae.gateway.config.GatewaySnapshotConfig;
import com.github.chjiae.routing.StaticRouteCandidate;
import com.github.chjiae.routing.StaticRouteCandidatePlanner;
import com.github.chjiae.routing.StaticRoutePlan;
import com.github.chjiae.routing.StaticRouteRequestSelector;
import com.github.chjiae.routing.StaticRouteSelection;
import com.github.chjiae.routing.StaticRouteValidationResult;
import com.github.chjiae.routing.StaticTopologyValidator;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.redis.client.Command;
import io.vertx.redis.client.Redis;
import io.vertx.redis.client.RedisConnection;
import io.vertx.redis.client.RedisOptions;
import io.vertx.redis.client.Request;
import io.vertx.redis.client.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 网关快照运行时。
 * 通过 Redis initial/periodic reconciliation 与 Pub/Sub 提示加载快照，
 * 校验通过后以 copy-on-write 方式原子替换本地不可变租户快照。
 */
public class GatewaySnapshotRuntime {

    /** 日志记录器 */
    private static final Logger log = LoggerFactory.getLogger(GatewaySnapshotRuntime.class);

    /** Vert.x 实例 */
    private final Vertx vertx;

    /** 快照配置 */
    private final GatewaySnapshotConfig config;

    /** Redis client */
    private final Redis redis;

    /** 本地租户快照 */
    private final AtomicReference<Map<String, GatewayLoadedTenantSnapshot>> localSnapshots =
            new AtomicReference<>(Map.of());

    /** 全局 Client API Key 索引，key 为 keyId */
    private final AtomicReference<Map<String, GatewayClientKeyIndexEntry>> clientKeyIndex =
            new AtomicReference<>(Map.of());

    /** 命令连接 */
    private RedisConnection commandConnection;

    /** Pub/Sub 连接 */
    private RedisConnection subscriberConnection;

    /** 周期对账 timer ID */
    private long periodicTimerId = -1;

    /** 已完成首次成功对账 */
    private volatile boolean firstReconcileSucceeded;

    /** 当前 Redis 中 index 租户数量 */
    private volatile int indexTenantCount;

    /** 最近成功对账时间 */
    private volatile long lastSuccessfulReconcileEpochMillis;

    /** 最近 Redis 失败时间 */
    private volatile long lastRedisFailureEpochMillis;

    /** 当前是否存在损坏的 current 快照 */
    private volatile boolean currentSnapshotCorrupted;

    /** 最近错误分类 */
    private volatile String latestErrorCategory;

    /** 静态拓扑验证器 */
    private final StaticTopologyValidator topologyValidator = new StaticTopologyValidator();

    /** 真实请求静态选择器 */
    private final StaticRouteRequestSelector requestSelector = new StaticRouteRequestSelector();

    /** 动态治理候选规划器 */
    private final StaticRouteCandidatePlanner candidatePlanner = new StaticRouteCandidatePlanner();

    /**
     * 创建网关快照运行时。
     *
     * @param vertx Vert.x 实例
     * @param config 快照配置
     */
    public GatewaySnapshotRuntime(Vertx vertx, GatewaySnapshotConfig config) {
        this.vertx = vertx;
        this.config = config;
        this.redis = Redis.createClient(vertx, new RedisOptions().setConnectionString(config.redisUri()));
    }

    /**
     * 启动快照运行时。
     *
     * @return 启动结果
     */
    public Future<Void> start() {
        reconcileAll("INITIAL");
        startSubscription();
        periodicTimerId = vertx.setPeriodic(config.reconcileIntervalMs(), ignored -> reconcileAll("PERIODIC"));
        return Future.succeededFuture();
    }

    /**
     * 关闭快照运行时。
     *
     * @return 关闭结果
     */
    public Future<Void> close() {
        if (periodicTimerId != -1) {
            vertx.cancelTimer(periodicTimerId);
        }
        List<Future<?>> futures = new ArrayList<>();
        if (subscriberConnection != null) {
            futures.add(subscriberConnection.close());
        }
        if (commandConnection != null) {
            futures.add(commandConnection.close());
        }
        redis.close();
        return Future.all(futures).mapEmpty();
    }

    /**
     * 获取安全运行时状态。
     *
     * @return 快照状态
     */
    public GatewaySnapshotRuntimeStatus status() {
        Map<String, GatewayLoadedTenantSnapshot> snapshotMap = localSnapshots.get();
        List<GatewaySnapshotRuntimeStatus.TenantRevision> tenants = snapshotMap.entrySet().stream()
                .map(entry -> new GatewaySnapshotRuntimeStatus.TenantRevision(entry.getKey(),
                        entry.getValue().snapshot().revision(),
                        entry.getValue().snapshot().schemaVersion(),
                        entry.getValue().routePlans().size(),
                        entry.getValue().snapshot().clientApiKeys().size()))
                .sorted(java.util.Comparator.comparing(GatewaySnapshotRuntimeStatus.TenantRevision::tenantId))
                .toList();
        int routePlanCount = snapshotMap.values().stream()
                .mapToInt(loaded -> loaded.routePlans().size())
                .sum();
        int loadedClientKeyCount = clientKeyIndex.get().size();
        int invalidRouteTenantCount = "STATIC_ROUTE_INVALID".equals(latestErrorCategory) ? 1 : 0;
        return new GatewaySnapshotRuntimeStatus(syncState(), indexTenantCount, snapshotMap.size(),
                routePlanCount, loadedClientKeyCount, invalidRouteTenantCount,
                lastSuccessfulReconcileEpochMillis, latestErrorCategory, tenants);
    }

    /**
     * 认证 Client API Key。
     *
     * @param rawKey 请求中的 raw key
     * @return 认证结果
     */
    public GatewayClientKeyAuthenticationResult authenticateClientKey(String rawKey) {
        GatewayClientKeyCrypto.ParsedClientKey parsed = GatewayClientKeyCrypto.parse(rawKey);
        if (!parsed.valid()) {
            return GatewayClientKeyAuthenticationResult.failure("invalid_api_key");
        }
        GatewayClientKeyIndexEntry entry = clientKeyIndex.get().get(parsed.keyId());
        if (entry == null || !"ENABLED".equals(entry.adminStatus())) {
            return GatewayClientKeyAuthenticationResult.failure("invalid_api_key");
        }
        long expiresAt = entry.expiresAtEpochMillis();
        if (expiresAt > 0 && System.currentTimeMillis() >= expiresAt) {
            return GatewayClientKeyAuthenticationResult.failure("invalid_api_key");
        }
        boolean verified = GatewayClientKeyCrypto.verify(rawKey, entry.keyId(), entry.keyVersion(),
                entry.salt(), entry.verifierHash());
        if (!verified) {
            return GatewayClientKeyAuthenticationResult.failure("invalid_api_key");
        }
        return GatewayClientKeyAuthenticationResult.success(entry.principal());
    }

    /**
     * 查询主体可访问且具备静态路由计划的公开模型编码。
     *
     * @param principal 已认证主体
     * @return 模型编码列表
     */
    public List<String> authorizedModelCodes(GatewayClientPrincipal principal) {
        GatewayLoadedTenantSnapshot loaded = localSnapshots.get().get(principal.tenantId());
        if (loaded == null) {
            return List.of();
        }
        Set<String> codes = new HashSet<>();
        for (GatewayAccessGroupModelGrantSnapshot grant : principal.effectiveGrants()) {
            String routeKey = grant.publicModelCode() + "|" + grant.canonicalOperation();
            if (loaded.routePlans().containsKey(routeKey)) {
                codes.add(grant.publicModelCode());
            }
        }
        return codes.stream()
                .sorted()
                .toList();
    }

    /**
     * 解析 OpenAI Chat Completions 执行目标。
     * 只读取本地快照、路由计划和已解封装 runtime secret，不访问 Redis 或数据库。
     *
     * @param principal 已认证主体
     * @param publicModelCode 下游公开模型编码
     * @param requestId 请求 ID，用于构造安全选择 seed
     * @return OpenAI 执行目标
     */
    public GatewayOpenAiExecutionTarget resolveOpenAiChatExecution(GatewayClientPrincipal principal,
                                                                   String publicModelCode,
                                                                   String requestId) {
        if (syncState() == GatewaySnapshotSyncState.NOT_READY) {
            throw new GatewayOpenAiExecutionException(503, "gateway_not_ready", "Gateway not ready");
        }
        if (!hasGrant(principal, publicModelCode, "CHAT_COMPLETIONS")) {
            throw new GatewayOpenAiExecutionException(403, "model_access_denied", "Model access denied");
        }
        GatewayLoadedTenantSnapshot loaded = localSnapshots.get().get(principal.tenantId());
        if (loaded == null) {
            throw new GatewayOpenAiExecutionException(404, "model_not_found", "Model not found");
        }
        String routeKey = publicModelCode + "|CHAT_COMPLETIONS";
        StaticRoutePlan plan = loaded.routePlans().get(routeKey);
        if (plan == null) {
            throw new GatewayOpenAiExecutionException(404, "model_not_found", "Model not found");
        }
        String seed = requestId + "|" + principal.tenantId() + "|" + publicModelCode
                + "|CHAT_COMPLETIONS|" + loaded.snapshot().revision();
        StaticRouteSelection selection = requestSelector.select(plan, seed);
        GatewayExecutionResourceSnapshot resource = findResource(loaded.snapshot(), selection.executionResourceId());
        if (resource == null) {
            throw new GatewayOpenAiExecutionException(502, "upstream_protocol_error", "Upstream protocol error");
        }
        if (!"DIRECT_API".equals(resource.resourceType())
                || !"OPENAI_COMPATIBLE".equals(resource.protocolType())
                || !"ENABLED".equals(resource.adminStatus())) {
            throw new GatewayOpenAiExecutionException(502, "upstream_protocol_error", "Upstream protocol error");
        }
        String runtimeSecret = loaded.runtimeSecrets().get(resource.resourceId());
        if (runtimeSecret == null || runtimeSecret.isBlank()) {
            throw new GatewayOpenAiExecutionException(502, "upstream_protocol_error", "Upstream protocol error");
        }
        try {
            return new GatewayOpenAiExecutionTarget(loaded.snapshot().tenantId(), publicModelCode,
                    loaded.snapshot().revision(), selection.routePolicyId(), resource.resourceId(),
                    resource.baseUrl(), selection.upstreamModelName(), runtimeSecret,
                    loaded.runtimePolicies().get(resource.resourceId()));
        } catch (IllegalArgumentException e) {
            throw new GatewayOpenAiExecutionException(502, "upstream_protocol_error", "Upstream protocol error");
        }
    }

    /**
     * 解析 OpenAI Chat Completions 动态治理候选执行目标。
     * 只读取本地快照、路由计划和已解封装 runtime secret，不访问 Redis 或数据库。
     *
     * @param principal 已认证主体
     * @param publicModelCode 下游公开模型编码
     * @param requestId 请求 ID，用于构造安全选择 seed
     * @param maxCandidates 最大候选数量
     * @return 执行目标候选列表
     */
    public List<GatewayOpenAiExecutionTarget> resolveOpenAiChatExecutionCandidates(GatewayClientPrincipal principal,
                                                                                   String publicModelCode,
                                                                                   String requestId,
                                                                                   int maxCandidates) {
        if (syncState() == GatewaySnapshotSyncState.NOT_READY) {
            throw new GatewayOpenAiExecutionException(503, "gateway_not_ready", "Gateway not ready");
        }
        if (!hasGrant(principal, publicModelCode, "CHAT_COMPLETIONS")) {
            throw new GatewayOpenAiExecutionException(403, "model_access_denied", "Model access denied");
        }
        GatewayLoadedTenantSnapshot loaded = localSnapshots.get().get(principal.tenantId());
        if (loaded == null) {
            throw new GatewayOpenAiExecutionException(404, "model_not_found", "Model not found");
        }
        String routeKey = publicModelCode + "|CHAT_COMPLETIONS";
        StaticRoutePlan plan = loaded.routePlans().get(routeKey);
        if (plan == null) {
            throw new GatewayOpenAiExecutionException(404, "model_not_found", "Model not found");
        }
        String seed = requestId + "|" + principal.tenantId() + "|" + publicModelCode
                + "|CHAT_COMPLETIONS|" + loaded.snapshot().revision();
        List<StaticRouteCandidate> candidates = candidatePlanner.plan(plan, seed);
        List<GatewayOpenAiExecutionTarget> targets = new ArrayList<>();
        int limit = Math.min(Math.max(maxCandidates, 0), candidates.size());
        for (int i = 0; i < limit; i++) {
            GatewayOpenAiExecutionTarget target = toExecutionTarget(loaded, publicModelCode, candidates.get(i));
            if (target != null) {
                targets.add(target);
            }
        }
        if (targets.isEmpty()) {
            throw new GatewayOpenAiExecutionException(503,
                    "no_runtime_eligible_resource", "No runtime eligible resource");
        }
        return List.copyOf(targets);
    }

    private GatewayOpenAiExecutionTarget toExecutionTarget(GatewayLoadedTenantSnapshot loaded,
                                                           String publicModelCode,
                                                           StaticRouteCandidate candidate) {
        GatewayExecutionResourceSnapshot resource = findResource(loaded.snapshot(), candidate.executionResourceId());
        if (resource == null) {
            return null;
        }
        if (!"DIRECT_API".equals(resource.resourceType())
                || !"OPENAI_COMPATIBLE".equals(resource.protocolType())
                || !"ENABLED".equals(resource.adminStatus())) {
            return null;
        }
        String runtimeSecret = loaded.runtimeSecrets().get(resource.resourceId());
        GatewayExecutionResourceRuntimePolicySnapshot runtimePolicy =
                loaded.runtimePolicies().get(resource.resourceId());
        if (runtimeSecret == null || runtimeSecret.isBlank() || runtimePolicy == null) {
            return null;
        }
        try {
            return new GatewayOpenAiExecutionTarget(loaded.snapshot().tenantId(), publicModelCode,
                    loaded.snapshot().revision(), candidate.policyId(), resource.resourceId(),
                    resource.baseUrl(), candidate.upstreamModelName(), runtimeSecret, runtimePolicy);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private boolean hasGrant(GatewayClientPrincipal principal, String publicModelCode, String operation) {
        for (GatewayAccessGroupModelGrantSnapshot grant : principal.effectiveGrants()) {
            if (publicModelCode.equals(grant.publicModelCode())
                    && operation.equals(grant.canonicalOperation())) {
                return true;
            }
        }
        return false;
    }

    private GatewayExecutionResourceSnapshot findResource(GatewayTenantSnapshot snapshot, String executionResourceId) {
        for (GatewayExecutionResourceSnapshot resource : snapshot.executionResources()) {
            if (executionResourceId.equals(resource.resourceId())) {
                return resource;
            }
        }
        return null;
    }

    /**
     * 执行全量对账。
     *
     * @param reason 触发原因
     */
    private void reconcileAll(String reason) {
        smembers(GatewaySnapshotRedisKeys.tenantIndexKey())
                .compose(tenantIds -> {
                    indexTenantCount = tenantIds.size();
                    return loadAllTenants(tenantIds);
                })
                .onSuccess(ignored -> {
                    firstReconcileSucceeded = true;
                    currentSnapshotCorrupted = false;
                    latestErrorCategory = null;
                    lastSuccessfulReconcileEpochMillis = System.currentTimeMillis();
                    log.info("网关快照对账成功，原因: {}，index 租户数: {}，本地租户数: {}",
                            reason, indexTenantCount, localSnapshots.get().size());
                })
                .onFailure(throwable -> {
                    if (throwable instanceof GatewaySnapshotValidationException validationException) {
                        currentSnapshotCorrupted = true;
                        latestErrorCategory = validationException.category();
                    } else {
                        lastRedisFailureEpochMillis = System.currentTimeMillis();
                        latestErrorCategory = "REDIS_UNAVAILABLE";
                    }
                    log.warn("网关快照对账失败，原因: {}，错误分类: {}", reason, latestErrorCategory);
                });
    }

    /**
     * 加载所有租户快照。
     *
     * @param tenantIds 租户 ID 集合
     * @return 加载结果
     */
    private Future<Void> loadAllTenants(Set<String> tenantIds) {
        if (tenantIds.isEmpty()) {
            localSnapshots.set(Map.of());
            clientKeyIndex.set(Map.of());
            return Future.succeededFuture();
        }
        Promise<Void> promise = Promise.promise();
        Map<String, GatewayLoadedTenantSnapshot> current = localSnapshots.get();
        Map<String, GatewayLoadedTenantSnapshot> next = new HashMap<>();
        List<Throwable> failures = new ArrayList<>();
        loadTenantSequentially(new ArrayList<>(tenantIds), 0, current, next, failures, promise);
        return promise.future();
    }

    /**
     * 逐个加载租户，单个租户失败时保留旧版本，并继续尝试其他租户。
     */
    private void loadTenantSequentially(List<String> tenantIds, int index,
                                        Map<String, GatewayLoadedTenantSnapshot> current,
                                        Map<String, GatewayLoadedTenantSnapshot> next,
                                        List<Throwable> failures,
                                        Promise<Void> promise) {
        if (index >= tenantIds.size()) {
            Map<String, GatewayLoadedTenantSnapshot> immutableNext = Map.copyOf(next);
            localSnapshots.set(immutableNext);
            clientKeyIndex.set(buildClientKeyIndex(immutableNext));
            if (failures.isEmpty()) {
                promise.complete();
            } else {
                promise.fail(failures.getFirst());
            }
            return;
        }
        String tenantId = tenantIds.get(index);
        loadTenant(tenantId)
                .onSuccess(loaded -> {
                    next.put(tenantId, loaded);
                    loadTenantSequentially(tenantIds, index + 1, current, next, failures, promise);
                })
                .onFailure(throwable -> {
                    if (current.containsKey(tenantId)) {
                        next.put(tenantId, current.get(tenantId));
                    }
                    failures.add(throwable);
                    if (throwable instanceof GatewaySnapshotValidationException validationException) {
                        currentSnapshotCorrupted = true;
                        latestErrorCategory = validationException.category();
                    }
                    loadTenantSequentially(tenantIds, index + 1, current, next, failures, promise);
                });
    }

    /**
     * 加载单个租户快照。
     *
     * @param tenantId 租户 ID
     * @return 已加载快照
     */
    private Future<GatewayLoadedTenantSnapshot> loadTenant(String tenantId) {
        return get(GatewaySnapshotRedisKeys.currentManifestKey(tenantId))
                .compose(manifestJson -> {
                    if (manifestJson == null || manifestJson.isBlank()) {
                        return Future.failedFuture(new GatewaySnapshotValidationException("MANIFEST_MISSING",
                                "租户 current manifest 不存在"));
                    }
                    GatewaySnapshotManifest manifest = parseManifest(manifestJson);
                    validateManifest(manifest, tenantId);
                    return get(manifest.payloadRedisKey())
                            .map(payloadJson -> validatePayload(manifest, payloadJson));
                });
    }

    /**
     * 解析 Manifest。
     */
    private GatewaySnapshotManifest parseManifest(String manifestJson) {
        try {
            return GatewaySnapshotJson.fromJson(manifestJson, GatewaySnapshotManifest.class);
        } catch (Exception e) {
            throw new GatewaySnapshotValidationException("MANIFEST_JSON_INVALID", "Manifest JSON 不合法");
        }
    }

    /**
     * 验证 Manifest。
     */
    private void validateManifest(GatewaySnapshotManifest manifest, String expectedTenantId) {
        if (manifest.schemaVersion() < GatewaySnapshotSchema.MIN_SUPPORTED_VERSION
                || manifest.schemaVersion() > GatewaySnapshotSchema.MAX_SUPPORTED_VERSION) {
            throw new GatewaySnapshotValidationException("SCHEMA_UNSUPPORTED", "Manifest schema 不兼容");
        }
        if (!expectedTenantId.equals(manifest.tenantId())) {
            throw new GatewaySnapshotValidationException("TENANT_REVISION_MISMATCH", "Manifest 租户不匹配");
        }
        if (!config.keyId().equals(manifest.gatewayKeyId())) {
            throw new GatewaySnapshotValidationException("MANIFEST_KEY_ID_MISMATCH", "Manifest keyId 不匹配");
        }
        if (!GatewaySnapshotCrypto.verifyManifest(manifest, config.signingKeyBytes())) {
            throw new GatewaySnapshotValidationException("MANIFEST_HMAC_INVALID", "Manifest HMAC 不匹配");
        }
    }

    /**
     * 验证 payload 并解封装秘密。
     */
    private GatewayLoadedTenantSnapshot validatePayload(GatewaySnapshotManifest manifest, String payloadJson) {
        if (payloadJson == null || payloadJson.isBlank()) {
            throw new GatewaySnapshotValidationException("PAYLOAD_MISSING", "Payload 不存在");
        }
        byte[] payloadBytes = payloadJson.getBytes(StandardCharsets.UTF_8);
        if (!MessageDigest.isEqual(GatewaySnapshotCrypto.sha256Hex(payloadBytes).getBytes(StandardCharsets.UTF_8),
                manifest.payloadSha256Hex().getBytes(StandardCharsets.UTF_8))) {
            throw new GatewaySnapshotValidationException("PAYLOAD_SHA_INVALID", "Payload SHA 不匹配");
        }
        GatewayTenantSnapshot snapshot;
        try {
            snapshot = GatewaySnapshotJson.fromJson(payloadJson, GatewayTenantSnapshot.class);
        } catch (Exception e) {
            throw new GatewaySnapshotValidationException("PAYLOAD_JSON_INVALID", "Payload JSON 不合法");
        }
        if (snapshot.schemaVersion() < GatewaySnapshotSchema.MIN_SUPPORTED_VERSION
                || snapshot.schemaVersion() > GatewaySnapshotSchema.MAX_SUPPORTED_VERSION
                || !manifest.tenantId().equals(snapshot.tenantId())
                || manifest.revision() != snapshot.revision()) {
            throw new GatewaySnapshotValidationException("TENANT_REVISION_MISMATCH", "Payload 与 Manifest 不匹配");
        }
        Map<String, String> secrets = new HashMap<>();
        for (GatewayExecutionResourceSnapshot resource : snapshot.executionResources()) {
            try {
                String secret = GatewaySnapshotCrypto.decryptSecret(resource.secretEnvelope(),
                        config.encryptionKeyBytes(),
                        GatewaySnapshotCrypto.secretAad(snapshot.schemaVersion(), snapshot.tenantId(),
                                resource.resourceId(), resource.credentialId(), snapshot.revision(), config.keyId()),
                        config.keyId());
                secrets.put(resource.resourceId(), secret);
            } catch (Exception e) {
                throw new GatewaySnapshotValidationException("SECRET_DECRYPT_FAILED", "秘密 envelope 解封装失败");
            }
        }
        Map<String, StaticRoutePlan> routePlans = compileRoutePlans(snapshot);
        validateClientAccess(snapshot);
        Map<String, GatewayExecutionResourceRuntimePolicySnapshot> runtimePolicies = validateRuntimePolicies(snapshot);
        return new GatewayLoadedTenantSnapshot(snapshot, secrets, routePlans, runtimePolicies);
    }

    /**
     * 校验 V4 执行资源运行时治理策略。
     */
    private Map<String, GatewayExecutionResourceRuntimePolicySnapshot> validateRuntimePolicies(
            GatewayTenantSnapshot snapshot) {
        if (snapshot.schemaVersion() < GatewaySnapshotSchema.VERSION_4) {
            return Map.of();
        }
        Map<String, GatewayExecutionResourceRuntimePolicySnapshot> policies = new HashMap<>();
        for (GatewayExecutionResourceRuntimePolicySnapshot policy : snapshot.executionResourceRuntimePolicies()) {
            if (!snapshot.tenantId().equals(policy.tenantId())
                    || policy.executionResourceId() == null
                    || policy.executionResourceId().isBlank()
                    || policy.policyVersion() < 1
                    || policy.maxConcurrentRequests() < 0
                    || policy.consecutiveFailureThreshold() <= 0
                    || policy.failureResetAfterMs() <= 0
                    || policy.failureCooldownMs() <= 0
                    || policy.rateLimitCooldownMs() <= 0
                    || policies.containsKey(policy.executionResourceId())) {
                throw new GatewaySnapshotValidationException("RUNTIME_POLICY_INVALID",
                        "执行资源运行时治理策略不合法");
            }
            policies.put(policy.executionResourceId(), policy);
        }
        for (GatewayExecutionResourceSnapshot resource : snapshot.executionResources()) {
            if (!policies.containsKey(resource.resourceId())) {
                throw new GatewaySnapshotValidationException("RUNTIME_POLICY_MISSING",
                        "执行资源运行时治理策略缺失");
            }
        }
        return Map.copyOf(policies);
    }

    /**
     * 编译 V2 静态路由计划。
     * V1 快照不包含路由拓扑，兼容加载但不会产生 route plan。
     */
    private Map<String, StaticRoutePlan> compileRoutePlans(GatewayTenantSnapshot snapshot) {
        if (snapshot.schemaVersion() < GatewaySnapshotSchema.VERSION_2) {
            return Map.of();
        }
        Map<String, StaticRoutePlan> plans = new HashMap<>();
        List<StaticRouteValidationResult> results = topologyValidator.validateEnabledPolicies(snapshot);
        for (StaticRouteValidationResult result : results) {
            if (!result.valid()) {
                throw new GatewaySnapshotValidationException("STATIC_ROUTE_INVALID",
                        "静态路由拓扑校验失败: " + result.errorCategory());
            }
            StaticRoutePlan plan = result.plan();
            plans.put(plan.publicModelCode() + "|" + plan.canonicalOperation(), plan);
        }
        return plans;
    }

    /**
     * 校验 V3 Client API Key verifier 元数据。
     */
    private void validateClientAccess(GatewayTenantSnapshot snapshot) {
        if (snapshot.schemaVersion() < GatewaySnapshotSchema.VERSION_3) {
            return;
        }
        for (GatewayClientApiKeySnapshot key : snapshot.clientApiKeys()) {
            if (!GatewayClientKeyCrypto.HASH_ALGORITHM.equals(key.secretHashAlgorithm())) {
                throw new GatewaySnapshotValidationException("CLIENT_ACCESS_INVALID", "Client API Key 摘要算法不支持");
            }
            byte[] salt = decodeVerifierBytes(key.secretVerifierSaltBase64(),
                    GatewayClientKeyCrypto.SALT_BYTES, "CLIENT_ACCESS_INVALID");
            byte[] hash = decodeVerifierBytes(key.secretVerifierHashBase64(),
                    GatewayClientKeyCrypto.HASH_BYTES, "CLIENT_ACCESS_INVALID");
            if (key.keyId() == null || key.keyId().isBlank()
                    || key.clientApiKeyId() == null || key.clientApiKeyId().isBlank()
                    || key.keyVersion() < 1
                    || salt.length != GatewayClientKeyCrypto.SALT_BYTES
                    || hash.length != GatewayClientKeyCrypto.HASH_BYTES) {
                throw new GatewaySnapshotValidationException("CLIENT_ACCESS_INVALID", "Client API Key verifier 元数据不合法");
            }
        }
    }

    /**
     * 从已验证快照构建全局 Client API Key 索引。
     */
    private Map<String, GatewayClientKeyIndexEntry> buildClientKeyIndex(
            Map<String, GatewayLoadedTenantSnapshot> snapshots) {
        Map<String, GatewayClientKeyIndexEntry> nextIndex = new HashMap<>();
        for (GatewayLoadedTenantSnapshot loaded : snapshots.values()) {
            GatewayTenantSnapshot snapshot = loaded.snapshot();
            if (snapshot.schemaVersion() < GatewaySnapshotSchema.VERSION_3) {
                continue;
            }
            Map<String, GatewayAccessGroupSnapshot> enabledGroups = new HashMap<>();
            for (GatewayAccessGroupSnapshot group : snapshot.accessGroups()) {
                if ("ENABLED".equals(group.adminStatus())) {
                    enabledGroups.put(group.accessGroupId(), group);
                }
            }
            Map<String, List<GatewayAccessGroupModelGrantSnapshot>> grantsByGroup = new HashMap<>();
            for (GatewayAccessGroupModelGrantSnapshot grant : snapshot.accessGroupModelGrants()) {
                if ("ENABLED".equals(grant.adminStatus()) && enabledGroups.containsKey(grant.accessGroupId())) {
                    grantsByGroup.computeIfAbsent(grant.accessGroupId(), ignored -> new ArrayList<>()).add(grant);
                }
            }
            Map<String, List<String>> groupsByKey = new HashMap<>();
            for (GatewayClientApiKeyAccessGroupSnapshot binding : snapshot.clientApiKeyAccessGroups()) {
                if ("ENABLED".equals(binding.adminStatus()) && enabledGroups.containsKey(binding.accessGroupId())) {
                    groupsByKey.computeIfAbsent(binding.clientApiKeyId(), ignored -> new ArrayList<>())
                            .add(binding.accessGroupId());
                }
            }
            for (GatewayClientApiKeySnapshot key : snapshot.clientApiKeys()) {
                List<String> groupIds = groupsByKey.getOrDefault(key.clientApiKeyId(), List.of()).stream()
                        .sorted()
                        .toList();
                List<GatewayAccessGroupModelGrantSnapshot> grants = new ArrayList<>();
                for (String groupId : groupIds) {
                    grants.addAll(grantsByGroup.getOrDefault(groupId, List.of()));
                }
                List<GatewayAccessGroupModelGrantSnapshot> sortedGrants = grants.stream()
                        .sorted(Comparator.comparing(GatewayAccessGroupModelGrantSnapshot::publicModelCode)
                                .thenComparing(GatewayAccessGroupModelGrantSnapshot::canonicalOperation))
                        .toList();
                GatewayClientPrincipal principal = new GatewayClientPrincipal(snapshot.tenantId(),
                        key.clientApiKeyId(), key.keyId(), groupIds, sortedGrants);
                GatewayClientKeyIndexEntry entry = new GatewayClientKeyIndexEntry(key.keyId(), key.adminStatus(),
                        key.keyVersion(),
                        decodeVerifierBytes(key.secretVerifierSaltBase64(), GatewayClientKeyCrypto.SALT_BYTES,
                                "CLIENT_ACCESS_INVALID"),
                        decodeVerifierBytes(key.secretVerifierHashBase64(), GatewayClientKeyCrypto.HASH_BYTES,
                                "CLIENT_ACCESS_INVALID"),
                        key.expiresAtEpochMillis(), principal);
                nextIndex.put(key.keyId(), entry);
            }
        }
        return Map.copyOf(nextIndex);
    }

    private byte[] decodeVerifierBytes(String value, int expectedLength, String category) {
        try {
            byte[] decoded = Base64.getDecoder().decode(value);
            if (decoded.length != expectedLength) {
                throw new GatewaySnapshotValidationException(category, "Client API Key verifier 长度不合法");
            }
            return decoded;
        } catch (IllegalArgumentException e) {
            throw new GatewaySnapshotValidationException(category, "Client API Key verifier Base64 不合法");
        }
    }

    /**
     * 启动 Pub/Sub 订阅。
     */
    private void startSubscription() {
        redis.connect()
                .onSuccess(connection -> {
                    subscriberConnection = connection;
                    connection.handler(response -> reconcileAll("PUBSUB"));
                    connection.exceptionHandler(throwable -> scheduleSubscriptionReconnect());
                    connection.endHandler(ignored -> scheduleSubscriptionReconnect());
                    connection.send(Request.cmd(Command.SUBSCRIBE).arg(GatewaySnapshotRedisKeys.changedChannel()));
                })
                .onFailure(throwable -> {
                    latestErrorCategory = "REDIS_UNAVAILABLE";
                    scheduleSubscriptionReconnect();
                });
    }

    /**
     * 调度 Pub/Sub 重连。
     */
    private void scheduleSubscriptionReconnect() {
        subscriberConnection = null;
        vertx.setTimer(500, ignored -> startSubscription());
    }

    /**
     * 获取字符串 key。
     */
    private Future<String> get(String key) {
        return send(Command.GET, key).map(response -> response == null ? null : response.toString());
    }

    /**
     * 读取 Set 成员。
     */
    private Future<Set<String>> smembers(String key) {
        return send(Command.SMEMBERS, key).map(response -> {
            Set<String> values = new LinkedHashSet<>();
            if (response != null) {
                for (Response item : response) {
                    values.add(item.toString());
                }
            }
            return values;
        });
    }

    /**
     * 发送 Redis 命令。
     */
    private Future<Response> send(Command command, String... args) {
        return ensureCommandConnection().compose(connection -> {
            Request request = Request.cmd(command);
            for (String arg : args) {
                request.arg(arg);
            }
            return connection.send(request);
        });
    }

    /**
     * 确保命令连接可用。
     */
    private Future<RedisConnection> ensureCommandConnection() {
        if (commandConnection != null) {
            return Future.succeededFuture(commandConnection);
        }
        Promise<RedisConnection> promise = Promise.promise();
        redis.connect()
                .onSuccess(connection -> {
                    commandConnection = connection;
                    connection.exceptionHandler(throwable -> commandConnection = null);
                    connection.endHandler(ignored -> commandConnection = null);
                    promise.complete(connection);
                })
                .onFailure(promise::fail);
        return promise.future();
    }

    /**
     * 计算当前同步状态。
     */
    private GatewaySnapshotSyncState syncState() {
        if (!firstReconcileSucceeded) {
            return GatewaySnapshotSyncState.NOT_READY;
        }
        long now = System.currentTimeMillis();
        if (currentSnapshotCorrupted) {
            if (!localSnapshots.get().isEmpty() && now - lastSuccessfulReconcileEpochMillis <= config.maxStalenessMs()) {
                return GatewaySnapshotSyncState.DEGRADED;
            }
            return GatewaySnapshotSyncState.NOT_READY;
        }
        if (lastRedisFailureEpochMillis > lastSuccessfulReconcileEpochMillis) {
            if (!localSnapshots.get().isEmpty() && now - lastSuccessfulReconcileEpochMillis <= config.maxStalenessMs()) {
                return GatewaySnapshotSyncState.DEGRADED;
            }
            return GatewaySnapshotSyncState.NOT_READY;
        }
        if (now - lastSuccessfulReconcileEpochMillis > config.maxStalenessMs()) {
            return GatewaySnapshotSyncState.NOT_READY;
        }
        return GatewaySnapshotSyncState.READY;
    }
}
