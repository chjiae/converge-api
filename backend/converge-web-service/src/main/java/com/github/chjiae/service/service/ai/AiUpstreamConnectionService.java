package com.github.chjiae.service.service.ai;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.github.chjiae.common.enums.AiCatalogStatus;
import com.github.chjiae.common.exception.BusinessException;
import com.github.chjiae.common.result.PageResult;
import com.github.chjiae.service.dto.ai.AiUpstreamConnectionCreateRequest;
import com.github.chjiae.service.dto.ai.AiUpstreamConnectionResponse;
import com.github.chjiae.service.dto.ai.AiUpstreamConnectionUpdateRequest;
import com.github.chjiae.service.entity.ai.AiUpstreamConnection;
import com.github.chjiae.service.mapper.ai.AiUpstreamConnectionMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * AI 上游连接控制面服务。
 * 只保存非敏感连接元数据，并确保连接和 Provider 均属于当前租户。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiUpstreamConnectionService {

    /** 单页最大数量，避免一次查询过大。 */
    private static final int MAX_PAGE_SIZE = 100;

    /** AI 上游连接数据访问层 */
    private final AiUpstreamConnectionMapper aiUpstreamConnectionMapper;

    /** AI Provider 服务，用于校验 Provider 当前租户归属 */
    private final AiProviderService aiProviderService;

    /** AI 目录租户守卫 */
    private final AiCatalogTenantGuard tenantGuard;

    /** Base URL 规范化组件 */
    private final AiBaseUrlNormalizer baseUrlNormalizer;

    /** 网关快照变更记录器 */
    private final GatewaySnapshotChangeRecorder snapshotChangeRecorder;

    /**
     * 创建上游连接。
     *
     * @param providerId 所属 Provider ID
     * @param request    创建请求
     * @return 创建后的连接响应
     * @throws BusinessException 当前租户为空、Provider 不存在、编码重复或 URL 非法时抛出
     */
    @Transactional(rollbackFor = Exception.class)
    public AiUpstreamConnectionResponse createConnection(Long providerId, AiUpstreamConnectionCreateRequest request) {
        Long tenantId = tenantGuard.requireCurrentTenantId();
        log.info("创建 AI 上游连接，租户 ID: {}，供应商 ID: {}，编码: {}", tenantId, providerId, request.getCode());

        aiProviderService.findProviderOrThrow(tenantId, providerId);
        ensureCodeAvailable(tenantId, providerId, request.getCode(), null);
        String normalizedBaseUrl = baseUrlNormalizer.normalize(request.getBaseUrl());

        LocalDateTime now = LocalDateTime.now();
        AiUpstreamConnection connection = new AiUpstreamConnection();
        connection.setTenantId(tenantId);
        connection.setProviderId(providerId);
        connection.setCode(request.getCode());
        connection.setDisplayName(request.getDisplayName());
        connection.setProtocolType(request.getProtocolType());
        connection.setBaseUrl(normalizedBaseUrl);
        connection.setStatus(request.getStatus());
        connection.setDescription(request.getDescription());
        connection.setCreatedAt(now);
        connection.setUpdatedAt(now);
        aiUpstreamConnectionMapper.insert(connection);
        snapshotChangeRecorder.recordChange(tenantId, GatewaySnapshotChangeTypes.AI_CONNECTION_CHANGED);

        log.info("AI 上游连接创建成功，租户 ID: {}，连接 ID: {}", tenantId, connection.getId());
        return toResponse(connection);
    }

    /**
     * 分页查询某个 Provider 下的连接。
     *
     * @param providerId 所属 Provider ID
     * @param page       页码，从 1 开始
     * @param size       每页数量
     * @param keyword    编码或展示名称关键字，可为空
     * @param status     状态过滤，可为空
     * @return 分页连接响应
     * @throws BusinessException 当前租户为空或 Provider 不属于当前租户时抛出
     */
    public PageResult<AiUpstreamConnectionResponse> listConnections(Long providerId, int page, int size,
                                                                     String keyword, AiCatalogStatus status) {
        Long tenantId = tenantGuard.requireCurrentTenantId();
        aiProviderService.findProviderOrThrow(tenantId, providerId);
        int safePage = normalizePage(page);
        int safeSize = normalizeSize(size);
        log.info("分页查询 AI 上游连接，租户 ID: {}，供应商 ID: {}，页码: {}，每页数量: {}",
                tenantId, providerId, safePage, safeSize);

        Page<AiUpstreamConnection> pageParam = new Page<>(safePage, safeSize);
        LambdaQueryWrapper<AiUpstreamConnection> query = new LambdaQueryWrapper<AiUpstreamConnection>()
                .eq(AiUpstreamConnection::getTenantId, tenantId)
                .eq(AiUpstreamConnection::getProviderId, providerId)
                .orderByDesc(AiUpstreamConnection::getCreatedAt)
                .orderByDesc(AiUpstreamConnection::getId);
        if (keyword != null && !keyword.isBlank()) {
            String trimmedKeyword = keyword.trim();
            query.and(w -> w.like(AiUpstreamConnection::getCode, trimmedKeyword)
                    .or()
                    .like(AiUpstreamConnection::getDisplayName, trimmedKeyword));
        }
        if (status != null) {
            query.eq(AiUpstreamConnection::getStatus, status);
        }

        Page<AiUpstreamConnection> resultPage = aiUpstreamConnectionMapper.selectPage(pageParam, query);
        List<AiUpstreamConnectionResponse> list = resultPage.getRecords().stream()
                .map(this::toResponse)
                .toList();
        return PageResult.of(list, resultPage.getTotal(), safePage, safeSize);
    }

    /**
     * 查询连接详情。
     *
     * @param id 连接 ID
     * @return 连接响应
     * @throws BusinessException 当前租户为空或连接不存在时抛出
     */
    public AiUpstreamConnectionResponse getConnection(Long id) {
        Long tenantId = tenantGuard.requireCurrentTenantId();
        return toResponse(findConnectionOrThrow(tenantId, id));
    }

    /**
     * 更新连接。
     *
     * @param id      连接 ID
     * @param request 更新请求
     * @return 更新后的连接响应
     * @throws BusinessException 当前租户为空、连接不存在、Provider 不存在、编码重复或 URL 非法时抛出
     */
    @Transactional(rollbackFor = Exception.class)
    public AiUpstreamConnectionResponse updateConnection(Long id, AiUpstreamConnectionUpdateRequest request) {
        Long tenantId = tenantGuard.requireCurrentTenantId();
        log.info("更新 AI 上游连接，租户 ID: {}，连接 ID: {}", tenantId, id);

        AiUpstreamConnection connection = findConnectionOrThrow(tenantId, id);
        aiProviderService.findProviderOrThrow(tenantId, request.getProviderId());
        ensureCodeAvailable(tenantId, request.getProviderId(), request.getCode(), id);

        connection.setProviderId(request.getProviderId());
        connection.setCode(request.getCode());
        connection.setDisplayName(request.getDisplayName());
        connection.setProtocolType(request.getProtocolType());
        connection.setBaseUrl(baseUrlNormalizer.normalize(request.getBaseUrl()));
        connection.setStatus(request.getStatus());
        connection.setDescription(request.getDescription());
        connection.setUpdatedAt(LocalDateTime.now());
        aiUpstreamConnectionMapper.updateById(connection);
        snapshotChangeRecorder.recordChange(tenantId, GatewaySnapshotChangeTypes.AI_CONNECTION_CHANGED);

        log.info("AI 上游连接更新成功，租户 ID: {}，连接 ID: {}", tenantId, id);
        return toResponse(connection);
    }

    /**
     * 启用连接。
     *
     * @param id 连接 ID
     * @return 更新后的连接响应
     * @throws BusinessException 当前租户为空或连接不存在时抛出
     */
    @Transactional(rollbackFor = Exception.class)
    public AiUpstreamConnectionResponse enableConnection(Long id) {
        return updateStatus(id, AiCatalogStatus.ENABLED);
    }

    /**
     * 停用连接。
     *
     * @param id 连接 ID
     * @return 更新后的连接响应
     * @throws BusinessException 当前租户为空或连接不存在时抛出
     */
    @Transactional(rollbackFor = Exception.class)
    public AiUpstreamConnectionResponse disableConnection(Long id) {
        return updateStatus(id, AiCatalogStatus.DISABLED);
    }

    /**
     * 更新连接状态。
     *
     * @param id     连接 ID
     * @param status 目标状态
     * @return 更新后的连接响应
     */
    private AiUpstreamConnectionResponse updateStatus(Long id, AiCatalogStatus status) {
        Long tenantId = tenantGuard.requireCurrentTenantId();
        log.info("更新 AI 上游连接状态，租户 ID: {}，连接 ID: {}，目标状态: {}", tenantId, id, status);

        AiUpstreamConnection connection = findConnectionOrThrow(tenantId, id);
        connection.setStatus(status);
        connection.setUpdatedAt(LocalDateTime.now());
        aiUpstreamConnectionMapper.updateById(connection);
        snapshotChangeRecorder.recordChange(tenantId, GatewaySnapshotChangeTypes.AI_CONNECTION_CHANGED);

        log.info("AI 上游连接状态更新成功，租户 ID: {}，连接 ID: {}，状态: {}", tenantId, id, status);
        return toResponse(connection);
    }

    /**
     * 查询当前租户内连接实体（包级私有，供同包其他 Service 校验使用）。
     *
     * @param tenantId 当前租户 ID
     * @param id       连接 ID
     * @return 连接实体
     */
    AiUpstreamConnection findConnectionOrThrow(Long tenantId, Long id) {
        AiUpstreamConnection connection = aiUpstreamConnectionMapper.selectOne(
                new LambdaQueryWrapper<AiUpstreamConnection>()
                        .eq(AiUpstreamConnection::getTenantId, tenantId)
                        .eq(AiUpstreamConnection::getId, id));
        if (connection == null) {
            log.warn("AI 上游连接不存在或不属于当前租户，租户 ID: {}，连接 ID: {}", tenantId, id);
            throw new BusinessException(404, "AI 上游连接不存在");
        }
        return connection;
    }

    /**
     * 校验连接编码在同一 Provider 下是否可用。
     *
     * @param tenantId   当前租户 ID
     * @param providerId Provider ID
     * @param code       连接编码
     * @param excludeId  更新时排除的连接 ID，创建时为 null
     */
    private void ensureCodeAvailable(Long tenantId, Long providerId, String code, Long excludeId) {
        LambdaQueryWrapper<AiUpstreamConnection> query = new LambdaQueryWrapper<AiUpstreamConnection>()
                .eq(AiUpstreamConnection::getTenantId, tenantId)
                .eq(AiUpstreamConnection::getProviderId, providerId)
                .eq(AiUpstreamConnection::getCode, code);
        if (excludeId != null) {
            query.ne(AiUpstreamConnection::getId, excludeId);
        }
        if (aiUpstreamConnectionMapper.selectCount(query) > 0) {
            log.warn("AI 上游连接编码已存在，租户 ID: {}，供应商 ID: {}，编码: {}", tenantId, providerId, code);
            throw new BusinessException(400, "AI 上游连接编码已存在");
        }
    }

    /**
     * 规范化页码。
     *
     * @param page 原始页码
     * @return 合法页码
     */
    private int normalizePage(int page) {
        return Math.max(page, 1);
    }

    /**
     * 规范化每页数量。
     *
     * @param size 原始每页数量
     * @return 合法每页数量
     */
    private int normalizeSize(int size) {
        if (size <= 0) {
            return 10;
        }
        return Math.min(size, MAX_PAGE_SIZE);
    }

    /**
     * 转换为连接响应 DTO。
     *
     * @param connection 连接实体
     * @return 连接响应
     */
    private AiUpstreamConnectionResponse toResponse(AiUpstreamConnection connection) {
        return AiUpstreamConnectionResponse.builder()
                .id(connection.getId())
                .tenantId(connection.getTenantId())
                .providerId(connection.getProviderId())
                .code(connection.getCode())
                .displayName(connection.getDisplayName())
                .protocolType(connection.getProtocolType())
                .baseUrl(connection.getBaseUrl())
                .status(connection.getStatus())
                .description(connection.getDescription())
                .createdAt(connection.getCreatedAt())
                .updatedAt(connection.getUpdatedAt())
                .build();
    }
}
