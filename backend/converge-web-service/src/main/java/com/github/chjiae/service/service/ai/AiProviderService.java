package com.github.chjiae.service.service.ai;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.github.chjiae.common.enums.AiCatalogStatus;
import com.github.chjiae.common.exception.BusinessException;
import com.github.chjiae.common.result.PageResult;
import com.github.chjiae.service.dto.ai.AiProviderCreateRequest;
import com.github.chjiae.service.dto.ai.AiProviderResponse;
import com.github.chjiae.service.dto.ai.AiProviderUpdateRequest;
import com.github.chjiae.service.entity.ai.AiProvider;
import com.github.chjiae.service.mapper.ai.AiProviderMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * AI 供应商控制面服务。
 * 仅管理当前租户内的非敏感供应商目录，不提供跨租户查询能力。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiProviderService {

    /** 单页最大数量，避免一次查询过大。 */
    private static final int MAX_PAGE_SIZE = 100;

    /** AI 供应商数据访问层 */
    private final AiProviderMapper aiProviderMapper;

    /** AI 目录租户守卫 */
    private final AiCatalogTenantGuard tenantGuard;

    /**
     * 创建 AI 供应商。
     *
     * @param request 创建请求
     * @return 创建后的供应商响应
     * @throws BusinessException 当前租户为空或编码重复时抛出
     */
    @Transactional(rollbackFor = Exception.class)
    public AiProviderResponse createProvider(AiProviderCreateRequest request) {
        Long tenantId = tenantGuard.requireCurrentTenantId();
        log.info("创建 AI 供应商，租户 ID: {}，编码: {}", tenantId, request.getCode());

        ensureCodeAvailable(tenantId, request.getCode(), null);

        LocalDateTime now = LocalDateTime.now();
        AiProvider provider = new AiProvider();
        provider.setTenantId(tenantId);
        provider.setCode(request.getCode());
        provider.setDisplayName(request.getDisplayName());
        provider.setProviderKind(request.getProviderKind());
        provider.setStatus(request.getStatus());
        provider.setDescription(request.getDescription());
        provider.setCreatedAt(now);
        provider.setUpdatedAt(now);
        aiProviderMapper.insert(provider);

        log.info("AI 供应商创建成功，租户 ID: {}，供应商 ID: {}，编码: {}", tenantId, provider.getId(), provider.getCode());
        return toResponse(provider);
    }

    /**
     * 分页查询当前租户内供应商。
     *
     * @param page    页码，从 1 开始
     * @param size    每页数量
     * @param keyword 编码或展示名称关键字，可为空
     * @param status  状态过滤，可为空
     * @return 分页供应商响应
     * @throws BusinessException 当前租户为空时抛出
     */
    public PageResult<AiProviderResponse> listProviders(int page, int size, String keyword, AiCatalogStatus status) {
        Long tenantId = tenantGuard.requireCurrentTenantId();
        int safePage = normalizePage(page);
        int safeSize = normalizeSize(size);
        log.info("分页查询 AI 供应商，租户 ID: {}，页码: {}，每页数量: {}，状态: {}", tenantId, safePage, safeSize, status);

        Page<AiProvider> pageParam = new Page<>(safePage, safeSize);
        LambdaQueryWrapper<AiProvider> query = new LambdaQueryWrapper<AiProvider>()
                .eq(AiProvider::getTenantId, tenantId)
                .orderByDesc(AiProvider::getCreatedAt)
                .orderByDesc(AiProvider::getId);
        if (keyword != null && !keyword.isBlank()) {
            String trimmedKeyword = keyword.trim();
            query.and(w -> w.like(AiProvider::getCode, trimmedKeyword)
                    .or()
                    .like(AiProvider::getDisplayName, trimmedKeyword));
        }
        if (status != null) {
            query.eq(AiProvider::getStatus, status);
        }

        Page<AiProvider> resultPage = aiProviderMapper.selectPage(pageParam, query);
        List<AiProviderResponse> list = resultPage.getRecords().stream()
                .map(this::toResponse)
                .toList();
        return PageResult.of(list, resultPage.getTotal(), safePage, safeSize);
    }

    /**
     * 查询当前租户内供应商详情。
     *
     * @param id 供应商 ID
     * @return 供应商响应
     * @throws BusinessException 当前租户为空或供应商不存在时抛出
     */
    public AiProviderResponse getProvider(Long id) {
        Long tenantId = tenantGuard.requireCurrentTenantId();
        AiProvider provider = findProviderOrThrow(tenantId, id);
        return toResponse(provider);
    }

    /**
     * 更新当前租户内供应商。
     *
     * @param id      供应商 ID
     * @param request 更新请求
     * @return 更新后的供应商响应
     * @throws BusinessException 当前租户为空、供应商不存在或编码重复时抛出
     */
    @Transactional(rollbackFor = Exception.class)
    public AiProviderResponse updateProvider(Long id, AiProviderUpdateRequest request) {
        Long tenantId = tenantGuard.requireCurrentTenantId();
        log.info("更新 AI 供应商，租户 ID: {}，供应商 ID: {}", tenantId, id);

        AiProvider provider = findProviderOrThrow(tenantId, id);
        ensureCodeAvailable(tenantId, request.getCode(), id);

        provider.setCode(request.getCode());
        provider.setDisplayName(request.getDisplayName());
        provider.setProviderKind(request.getProviderKind());
        provider.setStatus(request.getStatus());
        provider.setDescription(request.getDescription());
        provider.setUpdatedAt(LocalDateTime.now());
        aiProviderMapper.updateById(provider);

        log.info("AI 供应商更新成功，租户 ID: {}，供应商 ID: {}", tenantId, id);
        return toResponse(provider);
    }

    /**
     * 启用当前租户内供应商。
     *
     * @param id 供应商 ID
     * @return 更新后的供应商响应
     * @throws BusinessException 当前租户为空或供应商不存在时抛出
     */
    @Transactional(rollbackFor = Exception.class)
    public AiProviderResponse enableProvider(Long id) {
        return updateStatus(id, AiCatalogStatus.ENABLED);
    }

    /**
     * 停用当前租户内供应商。
     *
     * @param id 供应商 ID
     * @return 更新后的供应商响应
     * @throws BusinessException 当前租户为空或供应商不存在时抛出
     */
    @Transactional(rollbackFor = Exception.class)
    public AiProviderResponse disableProvider(Long id) {
        return updateStatus(id, AiCatalogStatus.DISABLED);
    }

    /**
     * 查询当前租户内 Provider 实体，供 Connection 归属校验使用。
     *
     * @param tenantId   当前租户 ID
     * @param providerId Provider ID
     * @return Provider 实体
     * @throws BusinessException Provider 不存在时抛出
     */
    AiProvider findProviderOrThrow(Long tenantId, Long providerId) {
        AiProvider provider = aiProviderMapper.selectOne(new LambdaQueryWrapper<AiProvider>()
                .eq(AiProvider::getTenantId, tenantId)
                .eq(AiProvider::getId, providerId));
        if (provider == null) {
            log.warn("AI 供应商不存在或不属于当前租户，租户 ID: {}，供应商 ID: {}", tenantId, providerId);
            throw new BusinessException(404, "AI 供应商不存在");
        }
        return provider;
    }

    /**
     * 更新供应商状态。
     *
     * @param id     供应商 ID
     * @param status 目标状态
     * @return 更新后的供应商响应
     */
    private AiProviderResponse updateStatus(Long id, AiCatalogStatus status) {
        Long tenantId = tenantGuard.requireCurrentTenantId();
        log.info("更新 AI 供应商状态，租户 ID: {}，供应商 ID: {}，目标状态: {}", tenantId, id, status);

        AiProvider provider = findProviderOrThrow(tenantId, id);
        provider.setStatus(status);
        provider.setUpdatedAt(LocalDateTime.now());
        aiProviderMapper.updateById(provider);

        log.info("AI 供应商状态更新成功，租户 ID: {}，供应商 ID: {}，状态: {}", tenantId, id, status);
        return toResponse(provider);
    }

    /**
     * 校验供应商编码在当前租户内是否可用。
     *
     * @param tenantId  当前租户 ID
     * @param code      供应商编码
     * @param excludeId 更新时排除的供应商 ID，创建时为 null
     */
    private void ensureCodeAvailable(Long tenantId, String code, Long excludeId) {
        LambdaQueryWrapper<AiProvider> query = new LambdaQueryWrapper<AiProvider>()
                .eq(AiProvider::getTenantId, tenantId)
                .eq(AiProvider::getCode, code);
        if (excludeId != null) {
            query.ne(AiProvider::getId, excludeId);
        }
        if (aiProviderMapper.selectCount(query) > 0) {
            log.warn("AI 供应商编码已存在，租户 ID: {}，编码: {}", tenantId, code);
            throw new BusinessException(400, "AI 供应商编码已存在");
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
     * 转换为供应商响应 DTO。
     *
     * @param provider 供应商实体
     * @return 供应商响应
     */
    private AiProviderResponse toResponse(AiProvider provider) {
        return AiProviderResponse.builder()
                .id(provider.getId())
                .tenantId(provider.getTenantId())
                .code(provider.getCode())
                .displayName(provider.getDisplayName())
                .providerKind(provider.getProviderKind())
                .status(provider.getStatus())
                .description(provider.getDescription())
                .createdAt(provider.getCreatedAt())
                .updatedAt(provider.getUpdatedAt())
                .build();
    }
}
