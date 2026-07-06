package com.github.chjiae.service.service.ai;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.github.chjiae.common.enums.AiCatalogStatus;
import com.github.chjiae.common.enums.AiResourceStatus;
import com.github.chjiae.common.enums.AiResourceType;
import com.github.chjiae.common.exception.BusinessException;
import com.github.chjiae.common.result.PageResult;
import com.github.chjiae.service.dto.ai.AiExecutionResourceCreateRequest;
import com.github.chjiae.service.dto.ai.AiExecutionResourceResponse;
import com.github.chjiae.service.dto.ai.AiExecutionResourceUpdateRequest;
import com.github.chjiae.service.entity.ai.AiCredential;
import com.github.chjiae.service.entity.ai.AiExecutionResource;
import com.github.chjiae.service.entity.ai.AiUpstreamConnection;
import com.github.chjiae.service.mapper.ai.AiExecutionResourceMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * AI 可执行资源控制面服务。
 * 管理 AiUpstreamConnection 与 AiCredential 的静态绑定，
 * 强制四方一致性（tenant_id + provider_id），绑定一经创建不可修改。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiExecutionResourceService {

    /** 单页最大数量 */
    private static final int MAX_PAGE_SIZE = 100;

    /** AI 可执行资源数据访问层 */
    private final AiExecutionResourceMapper aiExecutionResourceMapper;

    /** AI 上游连接服务（用于校验连接归属） */
    private final AiUpstreamConnectionService aiUpstreamConnectionService;

    /** AI 凭据服务（用于校验凭据归属） */
    private final AiCredentialService aiCredentialService;

    /** AI 供应商服务（用于校验供应商归属） */
    private final AiProviderService aiProviderService;

    /** AI 目录租户守卫 */
    private final AiCatalogTenantGuard tenantGuard;

    /**
     * 创建可执行资源。
     * 校验四方一致性：resource.tenant_id = connection.tenant_id = credential.tenant_id = provider.tenant_id，
     * resource.provider_id = connection.provider_id = credential.provider_id。
     *
     * @param request 创建请求
     * @return 资源响应
     */
    @Transactional(rollbackFor = Exception.class)
    public AiExecutionResourceResponse createResource(AiExecutionResourceCreateRequest request) {
        Long tenantId = tenantGuard.requireCurrentTenantId();
        log.info("创建 AI 可执行资源，租户 ID: {}，连接 ID: {}，凭据 ID: {}，编码: {}",
                tenantId, request.getUpstreamConnectionId(), request.getCredentialId(), request.getCode());

        // 查询连接和凭据，校验归属
        AiUpstreamConnection connection = aiUpstreamConnectionService
                .findConnectionOrThrow(tenantId, request.getUpstreamConnectionId());
        AiCredential credential = aiCredentialService
                .findCredentialOrThrow(tenantId, request.getCredentialId());

        // 校验 provider_id 一致性
        if (!connection.getProviderId().equals(credential.getProviderId())) {
            log.warn("创建资源失败，连接和凭据的供应商不一致，连接供应商 ID: {}，凭据供应商 ID: {}",
                    connection.getProviderId(), credential.getProviderId());
            throw new BusinessException(400, "上游连接和凭据必须属于同一供应商");
        }

        Long providerId = connection.getProviderId();

        // 校验供应商归属
        aiProviderService.findProviderOrThrow(tenantId, providerId);

        // 校验连接和凭据均为启用状态
        if (connection.getStatus() != AiCatalogStatus.ENABLED) {
            log.warn("创建资源失败，上游连接未启用，连接 ID: {}，状态: {}", connection.getId(), connection.getStatus());
            throw new BusinessException(400, "上游连接必须为启用状态才能创建资源");
        }
        if (credential.getAdminStatus() != AiCatalogStatus.ENABLED) {
            log.warn("创建资源失败，凭据未启用，凭据 ID: {}，状态: {}", credential.getId(), credential.getAdminStatus());
            throw new BusinessException(400, "凭据必须为启用状态才能创建资源");
        }

        // 校验编码唯一性
        ensureCodeAvailable(tenantId, request.getCode(), null);

        // 校验同一连接 + 同一凭据不重复
        ensureBindingAvailable(tenantId, request.getUpstreamConnectionId(), request.getCredentialId(), null);

        // 构建实体
        LocalDateTime now = LocalDateTime.now();
        AiExecutionResource resource = new AiExecutionResource();
        resource.setTenantId(tenantId);
        resource.setProviderId(providerId);
        resource.setUpstreamConnectionId(request.getUpstreamConnectionId());
        resource.setCredentialId(request.getCredentialId());
        resource.setResourceType(AiResourceType.DIRECT_API);
        resource.setCode(request.getCode());
        resource.setDisplayName(request.getDisplayName());
        resource.setDescription(request.getDescription());
        resource.setAdminStatus(request.getAdminStatus());
        resource.setCreatedAt(now);
        resource.setUpdatedAt(now);

        aiExecutionResourceMapper.insert(resource);

        log.info("AI 可执行资源创建成功，租户 ID: {}，资源 ID: {}，编码: {}", tenantId, resource.getId(), resource.getCode());
        return toResponse(resource);
    }

    /**
     * 分页查询可执行资源列表。
     *
     * @param page    页码
     * @param size    每页数量
     * @param keyword 关键字，可选
     * @param status  状态过滤，可选
     * @return 分页资源列表
     */
    public PageResult<AiExecutionResourceResponse> listResources(int page, int size,
                                                                   String keyword, AiResourceStatus status) {
        Long tenantId = tenantGuard.requireCurrentTenantId();
        int safePage = normalizePage(page);
        int safeSize = normalizeSize(size);
        log.info("分页查询 AI 可执行资源，租户 ID: {}，页码: {}，每页数量: {}", tenantId, safePage, safeSize);

        Page<AiExecutionResource> pageParam = new Page<>(safePage, safeSize);
        LambdaQueryWrapper<AiExecutionResource> query = new LambdaQueryWrapper<AiExecutionResource>()
                .eq(AiExecutionResource::getTenantId, tenantId)
                .orderByDesc(AiExecutionResource::getCreatedAt)
                .orderByDesc(AiExecutionResource::getId);

        if (keyword != null && !keyword.isBlank()) {
            String trimmed = keyword.trim();
            query.and(w -> w.like(AiExecutionResource::getCode, trimmed)
                    .or()
                    .like(AiExecutionResource::getDisplayName, trimmed));
        }
        if (status != null) {
            query.eq(AiExecutionResource::getAdminStatus, status);
        }

        Page<AiExecutionResource> resultPage = aiExecutionResourceMapper.selectPage(pageParam, query);
        List<AiExecutionResourceResponse> list = resultPage.getRecords().stream()
                .map(this::toResponse)
                .toList();
        return PageResult.of(list, resultPage.getTotal(), safePage, safeSize);
    }

    /**
     * 查询资源详情。
     *
     * @param id 资源 ID
     * @return 资源响应
     */
    public AiExecutionResourceResponse getResource(Long id) {
        Long tenantId = tenantGuard.requireCurrentTenantId();
        AiExecutionResource resource = findResourceOrThrow(tenantId, id);
        return toResponse(resource);
    }

    /**
     * 更新资源管理元数据（不允许变更绑定关系）。
     *
     * @param id      资源 ID
     * @param request 更新请求
     * @return 更新后的资源响应
     */
    @Transactional(rollbackFor = Exception.class)
    public AiExecutionResourceResponse updateResource(Long id, AiExecutionResourceUpdateRequest request) {
        Long tenantId = tenantGuard.requireCurrentTenantId();
        log.info("更新 AI 可执行资源，租户 ID: {}，资源 ID: {}", tenantId, id);

        AiExecutionResource resource = findResourceOrThrow(tenantId, id);
        ensureCodeAvailable(tenantId, request.getCode(), id);

        // 仅更新管理元数据，不变更 connectionId / credentialId / providerId
        resource.setCode(request.getCode());
        resource.setDisplayName(request.getDisplayName());
        resource.setDescription(request.getDescription());
        resource.setUpdatedAt(LocalDateTime.now());
        aiExecutionResourceMapper.updateById(resource);

        log.info("AI 可执行资源更新成功，租户 ID: {}，资源 ID: {}", tenantId, id);
        return toResponse(resource);
    }

    /**
     * 启用资源。
     *
     * @param id 资源 ID
     * @return 启用后的资源响应
     */
    @Transactional(rollbackFor = Exception.class)
    public AiExecutionResourceResponse enableResource(Long id) {
        return updateStatus(id, AiResourceStatus.ENABLED);
    }

    /**
     * 停用资源。
     *
     * @param id 资源 ID
     * @return 停用后的资源响应
     */
    @Transactional(rollbackFor = Exception.class)
    public AiExecutionResourceResponse disableResource(Long id) {
        return updateStatus(id, AiResourceStatus.DISABLED);
    }

    /**
     * 排空资源（为后续阶段预留）。
     *
     * @param id 资源 ID
     * @return 排空后的资源响应
     */
    @Transactional(rollbackFor = Exception.class)
    public AiExecutionResourceResponse drainResource(Long id) {
        return updateStatus(id, AiResourceStatus.DRAINING);
    }

    private AiExecutionResource findResourceOrThrow(Long tenantId, Long resourceId) {
        AiExecutionResource resource = aiExecutionResourceMapper.selectOne(
                new LambdaQueryWrapper<AiExecutionResource>()
                        .eq(AiExecutionResource::getTenantId, tenantId)
                        .eq(AiExecutionResource::getId, resourceId));
        if (resource == null) {
            log.warn("AI 可执行资源不存在或不属于当前租户，租户 ID: {}，资源 ID: {}", tenantId, resourceId);
            throw new BusinessException(404, "AI 可执行资源不存在");
        }
        return resource;
    }

    private AiExecutionResourceResponse updateStatus(Long id, AiResourceStatus status) {
        Long tenantId = tenantGuard.requireCurrentTenantId();
        log.info("更新 AI 可执行资源状态，租户 ID: {}，资源 ID: {}，目标状态: {}", tenantId, id, status);

        AiExecutionResource resource = findResourceOrThrow(tenantId, id);
        resource.setAdminStatus(status);
        resource.setUpdatedAt(LocalDateTime.now());
        aiExecutionResourceMapper.updateById(resource);

        log.info("AI 可执行资源状态更新成功，租户 ID: {}，资源 ID: {}，状态: {}", tenantId, id, status);
        return toResponse(resource);
    }

    private void ensureCodeAvailable(Long tenantId, String code, Long excludeId) {
        LambdaQueryWrapper<AiExecutionResource> query = new LambdaQueryWrapper<AiExecutionResource>()
                .eq(AiExecutionResource::getTenantId, tenantId)
                .eq(AiExecutionResource::getCode, code);
        if (excludeId != null) {
            query.ne(AiExecutionResource::getId, excludeId);
        }
        if (aiExecutionResourceMapper.selectCount(query) > 0) {
            log.warn("AI 可执行资源编码已存在，租户 ID: {}，编码: {}", tenantId, code);
            throw new BusinessException(400, "AI 可执行资源编码已存在");
        }
    }

    private void ensureBindingAvailable(Long tenantId, Long connectionId, Long credentialId, Long excludeId) {
        LambdaQueryWrapper<AiExecutionResource> query = new LambdaQueryWrapper<AiExecutionResource>()
                .eq(AiExecutionResource::getTenantId, tenantId)
                .eq(AiExecutionResource::getUpstreamConnectionId, connectionId)
                .eq(AiExecutionResource::getCredentialId, credentialId);
        if (excludeId != null) {
            query.ne(AiExecutionResource::getId, excludeId);
        }
        if (aiExecutionResourceMapper.selectCount(query) > 0) {
            log.warn("同一连接和凭据的资源已存在，租户 ID: {}，连接 ID: {}，凭据 ID: {}",
                    tenantId, connectionId, credentialId);
            throw new BusinessException(400, "同一连接和凭据的组合已存在");
        }
    }

    private int normalizePage(int page) { return Math.max(page, 1); }

    private int normalizeSize(int size) {
        if (size <= 0) { return 10; }
        return Math.min(size, MAX_PAGE_SIZE);
    }

    private AiExecutionResourceResponse toResponse(AiExecutionResource resource) {
        return AiExecutionResourceResponse.builder()
                .id(resource.getId())
                .tenantId(resource.getTenantId())
                .providerId(resource.getProviderId())
                .upstreamConnectionId(resource.getUpstreamConnectionId())
                .credentialId(resource.getCredentialId())
                .resourceType(resource.getResourceType())
                .code(resource.getCode())
                .displayName(resource.getDisplayName())
                .description(resource.getDescription())
                .adminStatus(resource.getAdminStatus())
                .createdAt(resource.getCreatedAt())
                .updatedAt(resource.getUpdatedAt())
                .build();
    }
}
