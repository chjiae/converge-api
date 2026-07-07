package com.github.chjiae.service.service.ai;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.github.chjiae.common.enums.AiCanonicalOperation;
import com.github.chjiae.common.enums.AiCatalogStatus;
import com.github.chjiae.common.enums.AiResourceStatus;
import com.github.chjiae.common.enums.AiRoutePolicyStatus;
import com.github.chjiae.common.enums.AiSelectionPolicy;
import com.github.chjiae.common.exception.BusinessException;
import com.github.chjiae.common.result.PageResult;
import com.github.chjiae.contract.gateway.GatewayExecutionResourceSnapshot;
import com.github.chjiae.contract.gateway.GatewayPublicModelSnapshot;
import com.github.chjiae.contract.gateway.GatewayResourceModelBindingSnapshot;
import com.github.chjiae.contract.gateway.GatewayResourcePoolMemberSnapshot;
import com.github.chjiae.contract.gateway.GatewayResourcePoolSnapshot;
import com.github.chjiae.contract.gateway.GatewayRoutePolicySnapshot;
import com.github.chjiae.contract.gateway.GatewayRouteTargetSnapshot;
import com.github.chjiae.contract.gateway.GatewaySecretEnvelope;
import com.github.chjiae.contract.gateway.GatewaySnapshotSchema;
import com.github.chjiae.contract.gateway.GatewayTenantSnapshot;
import com.github.chjiae.routing.StaticRoutePlanCompiler;
import com.github.chjiae.routing.StaticRoutePreview;
import com.github.chjiae.routing.StaticRoutePreviewSelector;
import com.github.chjiae.routing.StaticRouteValidationResult;
import com.github.chjiae.service.dto.ai.AiResourceModelBindingCreateRequest;
import com.github.chjiae.service.dto.ai.AiResourceModelBindingResponse;
import com.github.chjiae.service.dto.ai.AiResourceModelBindingUpdateRequest;
import com.github.chjiae.service.dto.ai.AiResourcePoolCreateRequest;
import com.github.chjiae.service.dto.ai.AiResourcePoolMemberCreateRequest;
import com.github.chjiae.service.dto.ai.AiResourcePoolMemberResponse;
import com.github.chjiae.service.dto.ai.AiResourcePoolMemberUpdateRequest;
import com.github.chjiae.service.dto.ai.AiResourcePoolResponse;
import com.github.chjiae.service.dto.ai.AiResourcePoolUpdateRequest;
import com.github.chjiae.service.dto.ai.AiRoutePolicyCreateRequest;
import com.github.chjiae.service.dto.ai.AiRoutePolicyResponse;
import com.github.chjiae.service.dto.ai.AiRoutePolicyUpdateRequest;
import com.github.chjiae.service.dto.ai.AiRoutePreviewRequest;
import com.github.chjiae.service.dto.ai.AiRoutePreviewResponse;
import com.github.chjiae.service.dto.ai.AiRouteTargetCreateRequest;
import com.github.chjiae.service.dto.ai.AiRouteTargetResponse;
import com.github.chjiae.service.dto.ai.AiRouteTargetUpdateRequest;
import com.github.chjiae.service.entity.ai.AiCredential;
import com.github.chjiae.service.entity.ai.AiExecutionResource;
import com.github.chjiae.service.entity.ai.AiProvider;
import com.github.chjiae.service.entity.ai.AiPublicModel;
import com.github.chjiae.service.entity.ai.AiResourceModelBinding;
import com.github.chjiae.service.entity.ai.AiResourcePool;
import com.github.chjiae.service.entity.ai.AiResourcePoolMember;
import com.github.chjiae.service.entity.ai.AiRoutePolicy;
import com.github.chjiae.service.entity.ai.AiRouteTarget;
import com.github.chjiae.service.entity.ai.AiUpstreamConnection;
import com.github.chjiae.service.mapper.ai.AiCredentialMapper;
import com.github.chjiae.service.mapper.ai.AiExecutionResourceMapper;
import com.github.chjiae.service.mapper.ai.AiProviderMapper;
import com.github.chjiae.service.mapper.ai.AiPublicModelMapper;
import com.github.chjiae.service.mapper.ai.AiResourceModelBindingMapper;
import com.github.chjiae.service.mapper.ai.AiResourcePoolMapper;
import com.github.chjiae.service.mapper.ai.AiResourcePoolMemberMapper;
import com.github.chjiae.service.mapper.ai.AiRoutePolicyMapper;
import com.github.chjiae.service.mapper.ai.AiRouteTargetMapper;
import com.github.chjiae.service.mapper.ai.AiUpstreamConnectionMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * AI 静态路由控制面服务。
 * 管理资源池、成员、模型绑定、路由策略和目标池，并提供静态预览能力。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiStaticRoutingService {

    /** 单页最大数量 */
    private static final int MAX_PAGE_SIZE = 100;

    /** 固定预览警告 */
    private static final List<String> PREVIEW_WARNINGS = List.of(
            "STATIC_CONFIGURATION_ONLY",
            "DYNAMIC_STATE_NOT_APPLIED",
            "NO_UPSTREAM_REQUEST_EXECUTED"
    );

    /** 资源池 Mapper */
    private final AiResourcePoolMapper resourcePoolMapper;

    /** 资源池成员 Mapper */
    private final AiResourcePoolMemberMapper poolMemberMapper;

    /** 模型绑定 Mapper */
    private final AiResourceModelBindingMapper modelBindingMapper;

    /** 路由策略 Mapper */
    private final AiRoutePolicyMapper routePolicyMapper;

    /** 路由目标 Mapper */
    private final AiRouteTargetMapper routeTargetMapper;

    /** 公开模型 Mapper */
    private final AiPublicModelMapper publicModelMapper;

    /** 执行资源 Mapper */
    private final AiExecutionResourceMapper executionResourceMapper;

    /** 供应商 Mapper */
    private final AiProviderMapper providerMapper;

    /** 上游连接 Mapper */
    private final AiUpstreamConnectionMapper connectionMapper;

    /** 凭据 Mapper */
    private final AiCredentialMapper credentialMapper;

    /** 租户守卫 */
    private final AiCatalogTenantGuard tenantGuard;

    /** 快照变更记录器 */
    private final GatewaySnapshotChangeRecorder snapshotChangeRecorder;

    /** 静态路由编译器 */
    private final StaticRoutePlanCompiler routePlanCompiler = new StaticRoutePlanCompiler();

    /** 静态预览选择器 */
    private final StaticRoutePreviewSelector previewSelector = new StaticRoutePreviewSelector();

    /**
     * 创建资源池。
     *
     * @param request 创建请求
     * @return 资源池响应
     */
    @Transactional(rollbackFor = Exception.class)
    public AiResourcePoolResponse createResourcePool(AiResourcePoolCreateRequest request) {
        Long tenantId = tenantGuard.requireCurrentTenantId();
        ensurePoolCodeAvailable(tenantId, request.getCode(), null);
        LocalDateTime now = LocalDateTime.now();
        AiResourcePool pool = new AiResourcePool();
        pool.setTenantId(tenantId);
        pool.setCode(request.getCode());
        pool.setDisplayName(request.getDisplayName());
        pool.setDescription(request.getDescription());
        pool.setAdminStatus(request.getAdminStatus());
        pool.setSelectionPolicy(AiSelectionPolicy.PRIORITY_WEIGHTED);
        pool.setCreatedAt(now);
        pool.setUpdatedAt(now);
        resourcePoolMapper.insert(pool);
        snapshotChangeRecorder.recordChange(tenantId, GatewaySnapshotChangeTypes.AI_RESOURCE_POOL_CHANGED);
        log.info("AI 资源池创建成功，租户 ID: {}，资源池 ID: {}", tenantId, pool.getId());
        return toPoolResponse(pool);
    }

    /**
     * 分页查询资源池。
     */
    public PageResult<AiResourcePoolResponse> listResourcePools(int page, int size,
                                                                String keyword, AiCatalogStatus status) {
        Long tenantId = tenantGuard.requireCurrentTenantId();
        Page<AiResourcePool> pageParam = new Page<>(normalizePage(page), normalizeSize(size));
        LambdaQueryWrapper<AiResourcePool> query = new LambdaQueryWrapper<AiResourcePool>()
                .eq(AiResourcePool::getTenantId, tenantId)
                .orderByDesc(AiResourcePool::getCreatedAt)
                .orderByDesc(AiResourcePool::getId);
        if (keyword != null && !keyword.isBlank()) {
            query.and(w -> w.like(AiResourcePool::getCode, keyword.trim())
                    .or()
                    .like(AiResourcePool::getDisplayName, keyword.trim()));
        }
        if (status != null) {
            query.eq(AiResourcePool::getAdminStatus, status);
        }
        Page<AiResourcePool> result = resourcePoolMapper.selectPage(pageParam, query);
        return PageResult.of(result.getRecords().stream().map(this::toPoolResponse).toList(),
                result.getTotal(), (int) pageParam.getCurrent(), (int) pageParam.getSize());
    }

    /**
     * 查询资源池详情。
     */
    public AiResourcePoolResponse getResourcePool(Long id) {
        Long tenantId = tenantGuard.requireCurrentTenantId();
        return toPoolResponse(findPoolOrThrow(tenantId, id));
    }

    /**
     * 更新资源池。
     */
    @Transactional(rollbackFor = Exception.class)
    public AiResourcePoolResponse updateResourcePool(Long id, AiResourcePoolUpdateRequest request) {
        Long tenantId = tenantGuard.requireCurrentTenantId();
        AiResourcePool pool = findPoolOrThrow(tenantId, id);
        ensurePoolCodeAvailable(tenantId, request.getCode(), id);
        pool.setCode(request.getCode());
        pool.setDisplayName(request.getDisplayName());
        pool.setDescription(request.getDescription());
        pool.setAdminStatus(request.getAdminStatus());
        pool.setUpdatedAt(LocalDateTime.now());
        resourcePoolMapper.updateById(pool);
        snapshotChangeRecorder.recordChange(tenantId, GatewaySnapshotChangeTypes.AI_RESOURCE_POOL_CHANGED);
        return toPoolResponse(pool);
    }

    /**
     * 启用资源池。
     */
    @Transactional(rollbackFor = Exception.class)
    public AiResourcePoolResponse enableResourcePool(Long id) {
        return updatePoolStatus(id, AiCatalogStatus.ENABLED);
    }

    /**
     * 停用资源池。
     */
    @Transactional(rollbackFor = Exception.class)
    public AiResourcePoolResponse disableResourcePool(Long id) {
        return updatePoolStatus(id, AiCatalogStatus.DISABLED);
    }

    /**
     * 创建资源池成员。
     */
    @Transactional(rollbackFor = Exception.class)
    public AiResourcePoolMemberResponse createPoolMember(Long poolId, AiResourcePoolMemberCreateRequest request) {
        Long tenantId = tenantGuard.requireCurrentTenantId();
        findPoolOrThrow(tenantId, poolId);
        findResourceOrThrow(tenantId, request.getExecutionResourceId());
        ensurePoolMemberAvailable(tenantId, poolId, request.getExecutionResourceId(), null);
        LocalDateTime now = LocalDateTime.now();
        AiResourcePoolMember member = new AiResourcePoolMember();
        member.setTenantId(tenantId);
        member.setResourcePoolId(poolId);
        member.setExecutionResourceId(request.getExecutionResourceId());
        member.setAdminStatus(request.getAdminStatus());
        member.setPriority(request.getPriority());
        member.setWeight(request.getWeight());
        member.setCreatedAt(now);
        member.setUpdatedAt(now);
        poolMemberMapper.insert(member);
        snapshotChangeRecorder.recordChange(tenantId, GatewaySnapshotChangeTypes.AI_RESOURCE_POOL_MEMBER_CHANGED);
        return toMemberResponse(member);
    }

    /**
     * 查询资源池成员。
     */
    public PageResult<AiResourcePoolMemberResponse> listPoolMembers(Long poolId, int page, int size) {
        Long tenantId = tenantGuard.requireCurrentTenantId();
        findPoolOrThrow(tenantId, poolId);
        Page<AiResourcePoolMember> pageParam = new Page<>(normalizePage(page), normalizeSize(size));
        LambdaQueryWrapper<AiResourcePoolMember> query = new LambdaQueryWrapper<AiResourcePoolMember>()
                .eq(AiResourcePoolMember::getTenantId, tenantId)
                .eq(AiResourcePoolMember::getResourcePoolId, poolId)
                .orderByDesc(AiResourcePoolMember::getPriority)
                .orderByDesc(AiResourcePoolMember::getId);
        Page<AiResourcePoolMember> result = poolMemberMapper.selectPage(pageParam, query);
        return PageResult.of(result.getRecords().stream().map(this::toMemberResponse).toList(),
                result.getTotal(), (int) pageParam.getCurrent(), (int) pageParam.getSize());
    }

    /**
     * 更新资源池成员。
     */
    @Transactional(rollbackFor = Exception.class)
    public AiResourcePoolMemberResponse updatePoolMember(Long id, AiResourcePoolMemberUpdateRequest request) {
        Long tenantId = tenantGuard.requireCurrentTenantId();
        AiResourcePoolMember member = findMemberOrThrow(tenantId, id);
        member.setAdminStatus(request.getAdminStatus());
        member.setPriority(request.getPriority());
        member.setWeight(request.getWeight());
        member.setUpdatedAt(LocalDateTime.now());
        poolMemberMapper.updateById(member);
        snapshotChangeRecorder.recordChange(tenantId, GatewaySnapshotChangeTypes.AI_RESOURCE_POOL_MEMBER_CHANGED);
        return toMemberResponse(member);
    }

    /**
     * 启用资源池成员。
     */
    @Transactional(rollbackFor = Exception.class)
    public AiResourcePoolMemberResponse enablePoolMember(Long id) {
        return updateMemberStatus(id, AiCatalogStatus.ENABLED);
    }

    /**
     * 停用资源池成员。
     */
    @Transactional(rollbackFor = Exception.class)
    public AiResourcePoolMemberResponse disablePoolMember(Long id) {
        return updateMemberStatus(id, AiCatalogStatus.DISABLED);
    }

    /**
     * 创建资源模型绑定。
     */
    @Transactional(rollbackFor = Exception.class)
    public AiResourceModelBindingResponse createModelBinding(AiResourceModelBindingCreateRequest request) {
        Long tenantId = tenantGuard.requireCurrentTenantId();
        findResourceOrThrow(tenantId, request.getExecutionResourceId());
        findPublicModelOrThrow(tenantId, request.getPublicModelId());
        ensureExactUpstreamModelName(request.getUpstreamModelName());
        ensureBindingAvailable(tenantId, request.getExecutionResourceId(), request.getPublicModelId(),
                request.getCanonicalOperation(), null);
        LocalDateTime now = LocalDateTime.now();
        AiResourceModelBinding binding = new AiResourceModelBinding();
        binding.setTenantId(tenantId);
        binding.setExecutionResourceId(request.getExecutionResourceId());
        binding.setPublicModelId(request.getPublicModelId());
        binding.setCanonicalOperation(request.getCanonicalOperation());
        binding.setUpstreamModelName(request.getUpstreamModelName());
        binding.setAdminStatus(request.getAdminStatus());
        binding.setCreatedAt(now);
        binding.setUpdatedAt(now);
        modelBindingMapper.insert(binding);
        snapshotChangeRecorder.recordChange(tenantId, GatewaySnapshotChangeTypes.AI_RESOURCE_MODEL_BINDING_CHANGED);
        return toBindingResponse(binding);
    }

    /**
     * 分页查询模型绑定。
     */
    public PageResult<AiResourceModelBindingResponse> listModelBindings(int page, int size,
                                                                        Long publicModelId,
                                                                        AiCanonicalOperation operation) {
        Long tenantId = tenantGuard.requireCurrentTenantId();
        Page<AiResourceModelBinding> pageParam = new Page<>(normalizePage(page), normalizeSize(size));
        LambdaQueryWrapper<AiResourceModelBinding> query = new LambdaQueryWrapper<AiResourceModelBinding>()
                .eq(AiResourceModelBinding::getTenantId, tenantId)
                .orderByDesc(AiResourceModelBinding::getCreatedAt)
                .orderByDesc(AiResourceModelBinding::getId);
        if (publicModelId != null) {
            query.eq(AiResourceModelBinding::getPublicModelId, publicModelId);
        }
        if (operation != null) {
            query.eq(AiResourceModelBinding::getCanonicalOperation, operation);
        }
        Page<AiResourceModelBinding> result = modelBindingMapper.selectPage(pageParam, query);
        return PageResult.of(result.getRecords().stream().map(this::toBindingResponse).toList(),
                result.getTotal(), (int) pageParam.getCurrent(), (int) pageParam.getSize());
    }

    /**
     * 查询模型绑定详情。
     */
    public AiResourceModelBindingResponse getModelBinding(Long id) {
        Long tenantId = tenantGuard.requireCurrentTenantId();
        return toBindingResponse(findBindingOrThrow(tenantId, id));
    }

    /**
     * 更新模型绑定。
     */
    @Transactional(rollbackFor = Exception.class)
    public AiResourceModelBindingResponse updateModelBinding(Long id, AiResourceModelBindingUpdateRequest request) {
        Long tenantId = tenantGuard.requireCurrentTenantId();
        AiResourceModelBinding binding = findBindingOrThrow(tenantId, id);
        ensureExactUpstreamModelName(request.getUpstreamModelName());
        binding.setUpstreamModelName(request.getUpstreamModelName());
        binding.setAdminStatus(request.getAdminStatus());
        binding.setUpdatedAt(LocalDateTime.now());
        modelBindingMapper.updateById(binding);
        snapshotChangeRecorder.recordChange(tenantId, GatewaySnapshotChangeTypes.AI_RESOURCE_MODEL_BINDING_CHANGED);
        return toBindingResponse(binding);
    }

    /**
     * 启用模型绑定。
     */
    @Transactional(rollbackFor = Exception.class)
    public AiResourceModelBindingResponse enableModelBinding(Long id) {
        return updateBindingStatus(id, AiCatalogStatus.ENABLED);
    }

    /**
     * 停用模型绑定。
     */
    @Transactional(rollbackFor = Exception.class)
    public AiResourceModelBindingResponse disableModelBinding(Long id) {
        return updateBindingStatus(id, AiCatalogStatus.DISABLED);
    }

    /**
     * 创建路由策略，默认 DRAFT。
     */
    @Transactional(rollbackFor = Exception.class)
    public AiRoutePolicyResponse createRoutePolicy(AiRoutePolicyCreateRequest request) {
        Long tenantId = tenantGuard.requireCurrentTenantId();
        findPublicModelOrThrow(tenantId, request.getPublicModelId());
        ensureRoutePolicyAvailable(tenantId, request.getPublicModelId(), request.getCanonicalOperation(), null);
        LocalDateTime now = LocalDateTime.now();
        AiRoutePolicy policy = new AiRoutePolicy();
        policy.setTenantId(tenantId);
        policy.setPublicModelId(request.getPublicModelId());
        policy.setCanonicalOperation(request.getCanonicalOperation());
        policy.setDisplayName(request.getDisplayName());
        policy.setDescription(request.getDescription());
        policy.setAdminStatus(AiRoutePolicyStatus.DRAFT);
        policy.setSelectionPolicy(AiSelectionPolicy.PRIORITY_WEIGHTED);
        policy.setCreatedAt(now);
        policy.setUpdatedAt(now);
        routePolicyMapper.insert(policy);
        snapshotChangeRecorder.recordChange(tenantId, GatewaySnapshotChangeTypes.AI_ROUTE_POLICY_CHANGED);
        return toPolicyResponse(policy);
    }

    /**
     * 分页查询路由策略。
     */
    public PageResult<AiRoutePolicyResponse> listRoutePolicies(int page, int size,
                                                               Long publicModelId,
                                                               AiRoutePolicyStatus status) {
        Long tenantId = tenantGuard.requireCurrentTenantId();
        Page<AiRoutePolicy> pageParam = new Page<>(normalizePage(page), normalizeSize(size));
        LambdaQueryWrapper<AiRoutePolicy> query = new LambdaQueryWrapper<AiRoutePolicy>()
                .eq(AiRoutePolicy::getTenantId, tenantId)
                .orderByDesc(AiRoutePolicy::getCreatedAt)
                .orderByDesc(AiRoutePolicy::getId);
        if (publicModelId != null) {
            query.eq(AiRoutePolicy::getPublicModelId, publicModelId);
        }
        if (status != null) {
            query.eq(AiRoutePolicy::getAdminStatus, status);
        }
        Page<AiRoutePolicy> result = routePolicyMapper.selectPage(pageParam, query);
        return PageResult.of(result.getRecords().stream().map(this::toPolicyResponse).toList(),
                result.getTotal(), (int) pageParam.getCurrent(), (int) pageParam.getSize());
    }

    /**
     * 查询路由策略详情。
     */
    public AiRoutePolicyResponse getRoutePolicy(Long id) {
        Long tenantId = tenantGuard.requireCurrentTenantId();
        return toPolicyResponse(findPolicyOrThrow(tenantId, id));
    }

    /**
     * 更新路由策略管理元数据。
     */
    @Transactional(rollbackFor = Exception.class)
    public AiRoutePolicyResponse updateRoutePolicy(Long id, AiRoutePolicyUpdateRequest request) {
        Long tenantId = tenantGuard.requireCurrentTenantId();
        AiRoutePolicy policy = findPolicyOrThrow(tenantId, id);
        policy.setDisplayName(request.getDisplayName());
        policy.setDescription(request.getDescription());
        policy.setUpdatedAt(LocalDateTime.now());
        routePolicyMapper.updateById(policy);
        snapshotChangeRecorder.recordChange(tenantId, GatewaySnapshotChangeTypes.AI_ROUTE_POLICY_CHANGED);
        return toPolicyResponse(policy);
    }

    /**
     * 启用路由策略，启用前必须通过静态拓扑校验。
     */
    @Transactional(rollbackFor = Exception.class)
    public AiRoutePolicyResponse enableRoutePolicy(Long id) {
        Long tenantId = tenantGuard.requireCurrentTenantId();
        AiRoutePolicy policy = findPolicyOrThrow(tenantId, id);
        AiPublicModel model = findPublicModelOrThrow(tenantId, policy.getPublicModelId());
        GatewayTenantSnapshot snapshot = buildRoutingSnapshot(tenantId, policy.getId(), AiRoutePolicyStatus.ENABLED);
        StaticRouteValidationResult result = routePlanCompiler.compile(snapshot, model.getCode(),
                policy.getCanonicalOperation().name());
        if (!result.valid()) {
            log.warn("AI 路由策略启用失败，租户 ID: {}，策略 ID: {}，错误分类: {}",
                    tenantId, id, result.errorCategory());
            throw new BusinessException(400, "静态路由拓扑无有效候选: " + result.errorCategory());
        }
        policy.setAdminStatus(AiRoutePolicyStatus.ENABLED);
        policy.setUpdatedAt(LocalDateTime.now());
        routePolicyMapper.updateById(policy);
        snapshotChangeRecorder.recordChange(tenantId, GatewaySnapshotChangeTypes.AI_ROUTE_POLICY_CHANGED);
        return toPolicyResponse(policy);
    }

    /**
     * 停用路由策略。
     */
    @Transactional(rollbackFor = Exception.class)
    public AiRoutePolicyResponse disableRoutePolicy(Long id) {
        Long tenantId = tenantGuard.requireCurrentTenantId();
        AiRoutePolicy policy = findPolicyOrThrow(tenantId, id);
        policy.setAdminStatus(AiRoutePolicyStatus.DISABLED);
        policy.setUpdatedAt(LocalDateTime.now());
        routePolicyMapper.updateById(policy);
        snapshotChangeRecorder.recordChange(tenantId, GatewaySnapshotChangeTypes.AI_ROUTE_POLICY_CHANGED);
        return toPolicyResponse(policy);
    }

    /**
     * 创建路由目标池。
     */
    @Transactional(rollbackFor = Exception.class)
    public AiRouteTargetResponse createRouteTarget(Long policyId, AiRouteTargetCreateRequest request) {
        Long tenantId = tenantGuard.requireCurrentTenantId();
        findPolicyOrThrow(tenantId, policyId);
        findPoolOrThrow(tenantId, request.getResourcePoolId());
        ensureRouteTargetAvailable(tenantId, policyId, request.getResourcePoolId(), null);
        LocalDateTime now = LocalDateTime.now();
        AiRouteTarget target = new AiRouteTarget();
        target.setTenantId(tenantId);
        target.setRoutePolicyId(policyId);
        target.setResourcePoolId(request.getResourcePoolId());
        target.setAdminStatus(request.getAdminStatus());
        target.setPriority(request.getPriority());
        target.setWeight(request.getWeight());
        target.setCreatedAt(now);
        target.setUpdatedAt(now);
        routeTargetMapper.insert(target);
        snapshotChangeRecorder.recordChange(tenantId, GatewaySnapshotChangeTypes.AI_ROUTE_TARGET_CHANGED);
        return toTargetResponse(target);
    }

    /**
     * 查询策略下路由目标。
     */
    public PageResult<AiRouteTargetResponse> listRouteTargets(Long policyId, int page, int size) {
        Long tenantId = tenantGuard.requireCurrentTenantId();
        findPolicyOrThrow(tenantId, policyId);
        Page<AiRouteTarget> pageParam = new Page<>(normalizePage(page), normalizeSize(size));
        LambdaQueryWrapper<AiRouteTarget> query = new LambdaQueryWrapper<AiRouteTarget>()
                .eq(AiRouteTarget::getTenantId, tenantId)
                .eq(AiRouteTarget::getRoutePolicyId, policyId)
                .orderByDesc(AiRouteTarget::getPriority)
                .orderByDesc(AiRouteTarget::getId);
        Page<AiRouteTarget> result = routeTargetMapper.selectPage(pageParam, query);
        return PageResult.of(result.getRecords().stream().map(this::toTargetResponse).toList(),
                result.getTotal(), (int) pageParam.getCurrent(), (int) pageParam.getSize());
    }

    /**
     * 更新路由目标池。
     */
    @Transactional(rollbackFor = Exception.class)
    public AiRouteTargetResponse updateRouteTarget(Long id, AiRouteTargetUpdateRequest request) {
        Long tenantId = tenantGuard.requireCurrentTenantId();
        AiRouteTarget target = findTargetOrThrow(tenantId, id);
        target.setAdminStatus(request.getAdminStatus());
        target.setPriority(request.getPriority());
        target.setWeight(request.getWeight());
        target.setUpdatedAt(LocalDateTime.now());
        routeTargetMapper.updateById(target);
        snapshotChangeRecorder.recordChange(tenantId, GatewaySnapshotChangeTypes.AI_ROUTE_TARGET_CHANGED);
        return toTargetResponse(target);
    }

    /**
     * 启用路由目标池。
     */
    @Transactional(rollbackFor = Exception.class)
    public AiRouteTargetResponse enableRouteTarget(Long id) {
        return updateTargetStatus(id, AiCatalogStatus.ENABLED);
    }

    /**
     * 停用路由目标池。
     */
    @Transactional(rollbackFor = Exception.class)
    public AiRouteTargetResponse disableRouteTarget(Long id) {
        return updateTargetStatus(id, AiCatalogStatus.DISABLED);
    }

    /**
     * 预览静态路由。
     */
    public AiRoutePreviewResponse previewRoute(AiRoutePreviewRequest request) {
        Long tenantId = tenantGuard.requireCurrentTenantId();
        GatewayTenantSnapshot snapshot = buildRoutingSnapshot(tenantId, null, null);
        StaticRouteValidationResult result = routePlanCompiler.compile(snapshot, request.getPublicModelCode(),
                request.getCanonicalOperation().name());
        if (!result.valid()) {
            return AiRoutePreviewResponse.builder()
                    .validationStatus("INVALID")
                    .errorCategory(result.errorCategory())
                    .reasons(result.safeReasons())
                    .warnings(PREVIEW_WARNINGS)
                    .build();
        }
        StaticRoutePreview preview = previewSelector.select(result.plan(), request.getSelectionSeed());
        return AiRoutePreviewResponse.builder()
                .validationStatus("VALID")
                .routePlan(result.plan())
                .preview(preview)
                .warnings(PREVIEW_WARNINGS)
                .build();
    }

    /**
     * 构建当前租户静态路由快照，用于控制面校验与预览。
     */
    private GatewayTenantSnapshot buildRoutingSnapshot(Long tenantId, Long overridePolicyId,
                                                       AiRoutePolicyStatus overrideStatus) {
        List<AiPublicModel> models = publicModelMapper.selectList(new LambdaQueryWrapper<AiPublicModel>()
                .eq(AiPublicModel::getTenantId, tenantId)
                .eq(AiPublicModel::getStatus, AiCatalogStatus.ENABLED));
        List<AiExecutionResource> resources = executionResourceMapper.selectList(new LambdaQueryWrapper<AiExecutionResource>()
                .eq(AiExecutionResource::getTenantId, tenantId));
        List<AiResourcePool> pools = resourcePoolMapper.selectList(new LambdaQueryWrapper<AiResourcePool>()
                .eq(AiResourcePool::getTenantId, tenantId));
        List<AiResourcePoolMember> members = poolMemberMapper.selectList(new LambdaQueryWrapper<AiResourcePoolMember>()
                .eq(AiResourcePoolMember::getTenantId, tenantId));
        List<AiResourceModelBinding> bindings = modelBindingMapper.selectList(new LambdaQueryWrapper<AiResourceModelBinding>()
                .eq(AiResourceModelBinding::getTenantId, tenantId));
        List<AiRoutePolicy> policies = routePolicyMapper.selectList(new LambdaQueryWrapper<AiRoutePolicy>()
                .eq(AiRoutePolicy::getTenantId, tenantId));
        List<AiRouteTarget> targets = routeTargetMapper.selectList(new LambdaQueryWrapper<AiRouteTarget>()
                .eq(AiRouteTarget::getTenantId, tenantId));

        Map<Long, AiPublicModel> modelById = models.stream()
                .collect(Collectors.toMap(AiPublicModel::getId, Function.identity(), (a, b) -> a));
        Map<Long, List<AiResourcePoolMember>> membersByPool = members.stream()
                .collect(Collectors.groupingBy(AiResourcePoolMember::getResourcePoolId));
        Map<Long, List<AiRouteTarget>> targetsByPolicy = targets.stream()
                .collect(Collectors.groupingBy(AiRouteTarget::getRoutePolicyId));

        return new GatewayTenantSnapshot(GatewaySnapshotSchema.VERSION_2, tenantId.toString(), 0L,
                System.currentTimeMillis(),
                models.stream().map(model -> new GatewayPublicModelSnapshot(tenantId.toString(),
                        model.getId().toString(), model.getCode(), model.getDisplayName(), model.getModelFamily())).toList(),
                resources.stream().map(this::toPreviewResourceSnapshot).toList(),
                pools.stream().map(pool -> new GatewayResourcePoolSnapshot(tenantId.toString(), pool.getId().toString(),
                        pool.getCode(), pool.getDisplayName(), pool.getAdminStatus().name(),
                        pool.getSelectionPolicy().name(),
                        membersByPool.getOrDefault(pool.getId(), List.of()).stream()
                                .map(member -> new GatewayResourcePoolMemberSnapshot(tenantId.toString(),
                                        member.getResourcePoolId().toString(), member.getExecutionResourceId().toString(),
                                        member.getAdminStatus().name(), member.getPriority(), member.getWeight()))
                                .toList()))
                        .toList(),
                bindings.stream().map(binding -> new GatewayResourceModelBindingSnapshot(tenantId.toString(),
                        binding.getExecutionResourceId().toString(), binding.getPublicModelId().toString(),
                        binding.getCanonicalOperation().name(), binding.getUpstreamModelName(),
                        binding.getAdminStatus().name())).toList(),
                policies.stream().map(policy -> {
                    AiRoutePolicyStatus status = policy.getId().equals(overridePolicyId) && overrideStatus != null
                            ? overrideStatus : policy.getAdminStatus();
                    AiPublicModel model = modelById.get(policy.getPublicModelId());
                    String modelCode = model == null ? "" : model.getCode();
                    return new GatewayRoutePolicySnapshot(tenantId.toString(), policy.getId().toString(),
                            policy.getPublicModelId().toString(), modelCode, policy.getCanonicalOperation().name(),
                            status.name(), policy.getSelectionPolicy().name(),
                            targetsByPolicy.getOrDefault(policy.getId(), List.of()).stream()
                                    .map(target -> new GatewayRouteTargetSnapshot(tenantId.toString(),
                                            target.getRoutePolicyId().toString(), target.getResourcePoolId().toString(),
                                            target.getAdminStatus().name(), target.getPriority(), target.getWeight()))
                                    .toList());
                }).toList());
    }

    private GatewayExecutionResourceSnapshot toPreviewResourceSnapshot(AiExecutionResource resource) {
        AiProvider provider = providerMapper.selectOne(new LambdaQueryWrapper<AiProvider>()
                .eq(AiProvider::getTenantId, resource.getTenantId())
                .eq(AiProvider::getId, resource.getProviderId()));
        AiUpstreamConnection connection = connectionMapper.selectOne(new LambdaQueryWrapper<AiUpstreamConnection>()
                .eq(AiUpstreamConnection::getTenantId, resource.getTenantId())
                .eq(AiUpstreamConnection::getId, resource.getUpstreamConnectionId()));
        AiCredential credential = credentialMapper.selectOne(new LambdaQueryWrapper<AiCredential>()
                .eq(AiCredential::getTenantId, resource.getTenantId())
                .eq(AiCredential::getId, resource.getCredentialId()));
        boolean dependencyEnabled = provider != null && provider.getStatus() == AiCatalogStatus.ENABLED
                && connection != null && connection.getStatus() == AiCatalogStatus.ENABLED
                && credential != null && credential.getAdminStatus() == AiCatalogStatus.ENABLED;
        String status = dependencyEnabled ? resource.getAdminStatus().name() : AiResourceStatus.DISABLED.name();
        return new GatewayExecutionResourceSnapshot(resource.getTenantId().toString(), resource.getId().toString(),
                resource.getProviderId().toString(), resource.getUpstreamConnectionId().toString(),
                resource.getCredentialId().toString(), resource.getResourceType().name(), status,
                provider == null ? "" : provider.getProviderKind().name(),
                connection == null ? "" : connection.getProtocolType().name(),
                connection == null ? "" : connection.getBaseUrl(),
                new GatewaySecretEnvelope("preview", "AES-256-GCM", "", ""));
    }

    private AiResourcePoolResponse updatePoolStatus(Long id, AiCatalogStatus status) {
        Long tenantId = tenantGuard.requireCurrentTenantId();
        AiResourcePool pool = findPoolOrThrow(tenantId, id);
        pool.setAdminStatus(status);
        pool.setUpdatedAt(LocalDateTime.now());
        resourcePoolMapper.updateById(pool);
        snapshotChangeRecorder.recordChange(tenantId, GatewaySnapshotChangeTypes.AI_RESOURCE_POOL_CHANGED);
        return toPoolResponse(pool);
    }

    private AiResourcePoolMemberResponse updateMemberStatus(Long id, AiCatalogStatus status) {
        Long tenantId = tenantGuard.requireCurrentTenantId();
        AiResourcePoolMember member = findMemberOrThrow(tenantId, id);
        member.setAdminStatus(status);
        member.setUpdatedAt(LocalDateTime.now());
        poolMemberMapper.updateById(member);
        snapshotChangeRecorder.recordChange(tenantId, GatewaySnapshotChangeTypes.AI_RESOURCE_POOL_MEMBER_CHANGED);
        return toMemberResponse(member);
    }

    private AiResourceModelBindingResponse updateBindingStatus(Long id, AiCatalogStatus status) {
        Long tenantId = tenantGuard.requireCurrentTenantId();
        AiResourceModelBinding binding = findBindingOrThrow(tenantId, id);
        binding.setAdminStatus(status);
        binding.setUpdatedAt(LocalDateTime.now());
        modelBindingMapper.updateById(binding);
        snapshotChangeRecorder.recordChange(tenantId, GatewaySnapshotChangeTypes.AI_RESOURCE_MODEL_BINDING_CHANGED);
        return toBindingResponse(binding);
    }

    private AiRouteTargetResponse updateTargetStatus(Long id, AiCatalogStatus status) {
        Long tenantId = tenantGuard.requireCurrentTenantId();
        AiRouteTarget target = findTargetOrThrow(tenantId, id);
        target.setAdminStatus(status);
        target.setUpdatedAt(LocalDateTime.now());
        routeTargetMapper.updateById(target);
        snapshotChangeRecorder.recordChange(tenantId, GatewaySnapshotChangeTypes.AI_ROUTE_TARGET_CHANGED);
        return toTargetResponse(target);
    }

    private AiResourcePool findPoolOrThrow(Long tenantId, Long id) {
        AiResourcePool pool = resourcePoolMapper.selectOne(new LambdaQueryWrapper<AiResourcePool>()
                .eq(AiResourcePool::getTenantId, tenantId)
                .eq(AiResourcePool::getId, id));
        if (pool == null) {
            throw new BusinessException(404, "AI 资源池不存在");
        }
        return pool;
    }

    private AiResourcePoolMember findMemberOrThrow(Long tenantId, Long id) {
        AiResourcePoolMember member = poolMemberMapper.selectOne(new LambdaQueryWrapper<AiResourcePoolMember>()
                .eq(AiResourcePoolMember::getTenantId, tenantId)
                .eq(AiResourcePoolMember::getId, id));
        if (member == null) {
            throw new BusinessException(404, "AI 资源池成员不存在");
        }
        return member;
    }

    private AiResourceModelBinding findBindingOrThrow(Long tenantId, Long id) {
        AiResourceModelBinding binding = modelBindingMapper.selectOne(new LambdaQueryWrapper<AiResourceModelBinding>()
                .eq(AiResourceModelBinding::getTenantId, tenantId)
                .eq(AiResourceModelBinding::getId, id));
        if (binding == null) {
            throw new BusinessException(404, "AI 资源模型绑定不存在");
        }
        return binding;
    }

    private AiRoutePolicy findPolicyOrThrow(Long tenantId, Long id) {
        AiRoutePolicy policy = routePolicyMapper.selectOne(new LambdaQueryWrapper<AiRoutePolicy>()
                .eq(AiRoutePolicy::getTenantId, tenantId)
                .eq(AiRoutePolicy::getId, id));
        if (policy == null) {
            throw new BusinessException(404, "AI 路由策略不存在");
        }
        return policy;
    }

    private AiRouteTarget findTargetOrThrow(Long tenantId, Long id) {
        AiRouteTarget target = routeTargetMapper.selectOne(new LambdaQueryWrapper<AiRouteTarget>()
                .eq(AiRouteTarget::getTenantId, tenantId)
                .eq(AiRouteTarget::getId, id));
        if (target == null) {
            throw new BusinessException(404, "AI 路由目标不存在");
        }
        return target;
    }

    private AiExecutionResource findResourceOrThrow(Long tenantId, Long id) {
        AiExecutionResource resource = executionResourceMapper.selectOne(new LambdaQueryWrapper<AiExecutionResource>()
                .eq(AiExecutionResource::getTenantId, tenantId)
                .eq(AiExecutionResource::getId, id));
        if (resource == null) {
            throw new BusinessException(404, "AI 可执行资源不存在");
        }
        return resource;
    }

    private AiPublicModel findPublicModelOrThrow(Long tenantId, Long id) {
        AiPublicModel model = publicModelMapper.selectOne(new LambdaQueryWrapper<AiPublicModel>()
                .eq(AiPublicModel::getTenantId, tenantId)
                .eq(AiPublicModel::getId, id));
        if (model == null) {
            throw new BusinessException(404, "AI 公开模型不存在");
        }
        return model;
    }

    private void ensurePoolCodeAvailable(Long tenantId, String code, Long excludeId) {
        LambdaQueryWrapper<AiResourcePool> query = new LambdaQueryWrapper<AiResourcePool>()
                .eq(AiResourcePool::getTenantId, tenantId)
                .eq(AiResourcePool::getCode, code);
        if (excludeId != null) {
            query.ne(AiResourcePool::getId, excludeId);
        }
        if (resourcePoolMapper.selectCount(query) > 0) {
            throw new BusinessException(400, "AI 资源池编码已存在");
        }
    }

    private void ensurePoolMemberAvailable(Long tenantId, Long poolId, Long resourceId, Long excludeId) {
        LambdaQueryWrapper<AiResourcePoolMember> query = new LambdaQueryWrapper<AiResourcePoolMember>()
                .eq(AiResourcePoolMember::getTenantId, tenantId)
                .eq(AiResourcePoolMember::getResourcePoolId, poolId)
                .eq(AiResourcePoolMember::getExecutionResourceId, resourceId);
        if (excludeId != null) {
            query.ne(AiResourcePoolMember::getId, excludeId);
        }
        if (poolMemberMapper.selectCount(query) > 0) {
            throw new BusinessException(400, "AI 资源池成员已存在");
        }
    }

    private void ensureBindingAvailable(Long tenantId, Long resourceId, Long modelId,
                                        AiCanonicalOperation operation, Long excludeId) {
        LambdaQueryWrapper<AiResourceModelBinding> query = new LambdaQueryWrapper<AiResourceModelBinding>()
                .eq(AiResourceModelBinding::getTenantId, tenantId)
                .eq(AiResourceModelBinding::getExecutionResourceId, resourceId)
                .eq(AiResourceModelBinding::getPublicModelId, modelId)
                .eq(AiResourceModelBinding::getCanonicalOperation, operation);
        if (excludeId != null) {
            query.ne(AiResourceModelBinding::getId, excludeId);
        }
        if (modelBindingMapper.selectCount(query) > 0) {
            throw new BusinessException(400, "AI 资源模型绑定已存在");
        }
    }

    private void ensureRoutePolicyAvailable(Long tenantId, Long modelId,
                                            AiCanonicalOperation operation, Long excludeId) {
        LambdaQueryWrapper<AiRoutePolicy> query = new LambdaQueryWrapper<AiRoutePolicy>()
                .eq(AiRoutePolicy::getTenantId, tenantId)
                .eq(AiRoutePolicy::getPublicModelId, modelId)
                .eq(AiRoutePolicy::getCanonicalOperation, operation);
        if (excludeId != null) {
            query.ne(AiRoutePolicy::getId, excludeId);
        }
        if (routePolicyMapper.selectCount(query) > 0) {
            throw new BusinessException(400, "AI 路由策略已存在");
        }
    }

    private void ensureRouteTargetAvailable(Long tenantId, Long policyId, Long poolId, Long excludeId) {
        LambdaQueryWrapper<AiRouteTarget> query = new LambdaQueryWrapper<AiRouteTarget>()
                .eq(AiRouteTarget::getTenantId, tenantId)
                .eq(AiRouteTarget::getRoutePolicyId, policyId)
                .eq(AiRouteTarget::getResourcePoolId, poolId);
        if (excludeId != null) {
            query.ne(AiRouteTarget::getId, excludeId);
        }
        if (routeTargetMapper.selectCount(query) > 0) {
            throw new BusinessException(400, "AI 路由目标已存在");
        }
    }

    private void ensureExactUpstreamModelName(String upstreamModelName) {
        String value = upstreamModelName == null ? "" : upstreamModelName.trim();
        if (value.isBlank()
                || value.contains("*")
                || value.startsWith("^")
                || value.endsWith("$")
                || value.contains("(?")
                || value.contains("[")
                || value.contains("]")) {
            throw new BusinessException(400, "上游模型名称必须为精确字符串，不能使用 wildcard 或正则");
        }
    }

    private int normalizePage(int page) {
        return Math.max(page, 1);
    }

    private int normalizeSize(int size) {
        if (size <= 0) {
            return 10;
        }
        return Math.min(size, MAX_PAGE_SIZE);
    }

    private AiResourcePoolResponse toPoolResponse(AiResourcePool pool) {
        return AiResourcePoolResponse.builder()
                .id(pool.getId())
                .tenantId(pool.getTenantId())
                .code(pool.getCode())
                .displayName(pool.getDisplayName())
                .description(pool.getDescription())
                .adminStatus(pool.getAdminStatus())
                .selectionPolicy(pool.getSelectionPolicy())
                .createdAt(pool.getCreatedAt())
                .updatedAt(pool.getUpdatedAt())
                .build();
    }

    private AiResourcePoolMemberResponse toMemberResponse(AiResourcePoolMember member) {
        return AiResourcePoolMemberResponse.builder()
                .id(member.getId())
                .tenantId(member.getTenantId())
                .resourcePoolId(member.getResourcePoolId())
                .executionResourceId(member.getExecutionResourceId())
                .adminStatus(member.getAdminStatus())
                .priority(member.getPriority())
                .weight(member.getWeight())
                .createdAt(member.getCreatedAt())
                .updatedAt(member.getUpdatedAt())
                .build();
    }

    private AiResourceModelBindingResponse toBindingResponse(AiResourceModelBinding binding) {
        return AiResourceModelBindingResponse.builder()
                .id(binding.getId())
                .tenantId(binding.getTenantId())
                .executionResourceId(binding.getExecutionResourceId())
                .publicModelId(binding.getPublicModelId())
                .canonicalOperation(binding.getCanonicalOperation())
                .upstreamModelName(binding.getUpstreamModelName())
                .adminStatus(binding.getAdminStatus())
                .createdAt(binding.getCreatedAt())
                .updatedAt(binding.getUpdatedAt())
                .build();
    }

    private AiRoutePolicyResponse toPolicyResponse(AiRoutePolicy policy) {
        return AiRoutePolicyResponse.builder()
                .id(policy.getId())
                .tenantId(policy.getTenantId())
                .publicModelId(policy.getPublicModelId())
                .canonicalOperation(policy.getCanonicalOperation())
                .displayName(policy.getDisplayName())
                .description(policy.getDescription())
                .adminStatus(policy.getAdminStatus())
                .selectionPolicy(policy.getSelectionPolicy())
                .createdAt(policy.getCreatedAt())
                .updatedAt(policy.getUpdatedAt())
                .build();
    }

    private AiRouteTargetResponse toTargetResponse(AiRouteTarget target) {
        return AiRouteTargetResponse.builder()
                .id(target.getId())
                .tenantId(target.getTenantId())
                .routePolicyId(target.getRoutePolicyId())
                .resourcePoolId(target.getResourcePoolId())
                .adminStatus(target.getAdminStatus())
                .priority(target.getPriority())
                .weight(target.getWeight())
                .createdAt(target.getCreatedAt())
                .updatedAt(target.getUpdatedAt())
                .build();
    }
}
