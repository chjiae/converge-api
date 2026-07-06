package com.github.chjiae.service.service.ai;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.github.chjiae.common.enums.AiCatalogStatus;
import com.github.chjiae.common.exception.BusinessException;
import com.github.chjiae.common.result.PageResult;
import com.github.chjiae.service.dto.ai.AiPublicModelCreateRequest;
import com.github.chjiae.service.dto.ai.AiPublicModelResponse;
import com.github.chjiae.service.dto.ai.AiPublicModelUpdateRequest;
import com.github.chjiae.service.entity.ai.AiPublicModel;
import com.github.chjiae.service.mapper.ai.AiPublicModelMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * AI 公开模型控制面服务。
 * 管理当前租户对下游暴露的模型别名，不建立上游模型映射。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiPublicModelService {

    /** 单页最大数量，避免一次查询过大。 */
    private static final int MAX_PAGE_SIZE = 100;

    /** AI 公开模型数据访问层 */
    private final AiPublicModelMapper aiPublicModelMapper;

    /** AI 目录租户守卫 */
    private final AiCatalogTenantGuard tenantGuard;

    /**
     * 创建公开模型。
     *
     * @param request 创建请求
     * @return 创建后的公开模型响应
     * @throws BusinessException 当前租户为空或编码重复时抛出
     */
    @Transactional(rollbackFor = Exception.class)
    public AiPublicModelResponse createModel(AiPublicModelCreateRequest request) {
        Long tenantId = tenantGuard.requireCurrentTenantId();
        log.info("创建 AI 公开模型，租户 ID: {}，编码: {}", tenantId, request.getCode());

        ensureCodeAvailable(tenantId, request.getCode(), null);

        LocalDateTime now = LocalDateTime.now();
        AiPublicModel model = new AiPublicModel();
        model.setTenantId(tenantId);
        model.setCode(request.getCode());
        model.setDisplayName(request.getDisplayName());
        model.setModelFamily(request.getModelFamily());
        model.setStatus(request.getStatus());
        model.setDescription(request.getDescription());
        model.setCreatedAt(now);
        model.setUpdatedAt(now);
        aiPublicModelMapper.insert(model);

        log.info("AI 公开模型创建成功，租户 ID: {}，模型 ID: {}，编码: {}", tenantId, model.getId(), model.getCode());
        return toResponse(model);
    }

    /**
     * 分页查询当前租户内公开模型。
     *
     * @param page    页码，从 1 开始
     * @param size    每页数量
     * @param keyword 编码或展示名称关键字，可为空
     * @param status  状态过滤，可为空
     * @return 分页公开模型响应
     * @throws BusinessException 当前租户为空时抛出
     */
    public PageResult<AiPublicModelResponse> listModels(int page, int size, String keyword, AiCatalogStatus status) {
        Long tenantId = tenantGuard.requireCurrentTenantId();
        int safePage = normalizePage(page);
        int safeSize = normalizeSize(size);
        log.info("分页查询 AI 公开模型，租户 ID: {}，页码: {}，每页数量: {}，状态: {}", tenantId, safePage, safeSize, status);

        Page<AiPublicModel> pageParam = new Page<>(safePage, safeSize);
        LambdaQueryWrapper<AiPublicModel> query = new LambdaQueryWrapper<AiPublicModel>()
                .eq(AiPublicModel::getTenantId, tenantId)
                .orderByDesc(AiPublicModel::getCreatedAt)
                .orderByDesc(AiPublicModel::getId);
        if (keyword != null && !keyword.isBlank()) {
            String trimmedKeyword = keyword.trim();
            query.and(w -> w.like(AiPublicModel::getCode, trimmedKeyword)
                    .or()
                    .like(AiPublicModel::getDisplayName, trimmedKeyword)
                    .or()
                    .like(AiPublicModel::getModelFamily, trimmedKeyword));
        }
        if (status != null) {
            query.eq(AiPublicModel::getStatus, status);
        }

        Page<AiPublicModel> resultPage = aiPublicModelMapper.selectPage(pageParam, query);
        List<AiPublicModelResponse> list = resultPage.getRecords().stream()
                .map(this::toResponse)
                .toList();
        return PageResult.of(list, resultPage.getTotal(), safePage, safeSize);
    }

    /**
     * 查询公开模型详情。
     *
     * @param id 公开模型 ID
     * @return 公开模型响应
     * @throws BusinessException 当前租户为空或公开模型不存在时抛出
     */
    public AiPublicModelResponse getModel(Long id) {
        Long tenantId = tenantGuard.requireCurrentTenantId();
        return toResponse(findModelOrThrow(tenantId, id));
    }

    /**
     * 更新公开模型。
     *
     * @param id      公开模型 ID
     * @param request 更新请求
     * @return 更新后的公开模型响应
     * @throws BusinessException 当前租户为空、公开模型不存在或编码重复时抛出
     */
    @Transactional(rollbackFor = Exception.class)
    public AiPublicModelResponse updateModel(Long id, AiPublicModelUpdateRequest request) {
        Long tenantId = tenantGuard.requireCurrentTenantId();
        log.info("更新 AI 公开模型，租户 ID: {}，模型 ID: {}", tenantId, id);

        AiPublicModel model = findModelOrThrow(tenantId, id);
        ensureCodeAvailable(tenantId, request.getCode(), id);

        model.setCode(request.getCode());
        model.setDisplayName(request.getDisplayName());
        model.setModelFamily(request.getModelFamily());
        model.setStatus(request.getStatus());
        model.setDescription(request.getDescription());
        model.setUpdatedAt(LocalDateTime.now());
        aiPublicModelMapper.updateById(model);

        log.info("AI 公开模型更新成功，租户 ID: {}，模型 ID: {}", tenantId, id);
        return toResponse(model);
    }

    /**
     * 启用公开模型。
     *
     * @param id 公开模型 ID
     * @return 更新后的公开模型响应
     * @throws BusinessException 当前租户为空或公开模型不存在时抛出
     */
    @Transactional(rollbackFor = Exception.class)
    public AiPublicModelResponse enableModel(Long id) {
        return updateStatus(id, AiCatalogStatus.ENABLED);
    }

    /**
     * 停用公开模型。
     *
     * @param id 公开模型 ID
     * @return 更新后的公开模型响应
     * @throws BusinessException 当前租户为空或公开模型不存在时抛出
     */
    @Transactional(rollbackFor = Exception.class)
    public AiPublicModelResponse disableModel(Long id) {
        return updateStatus(id, AiCatalogStatus.DISABLED);
    }

    /**
     * 更新公开模型状态。
     *
     * @param id     公开模型 ID
     * @param status 目标状态
     * @return 更新后的公开模型响应
     */
    private AiPublicModelResponse updateStatus(Long id, AiCatalogStatus status) {
        Long tenantId = tenantGuard.requireCurrentTenantId();
        log.info("更新 AI 公开模型状态，租户 ID: {}，模型 ID: {}，目标状态: {}", tenantId, id, status);

        AiPublicModel model = findModelOrThrow(tenantId, id);
        model.setStatus(status);
        model.setUpdatedAt(LocalDateTime.now());
        aiPublicModelMapper.updateById(model);

        log.info("AI 公开模型状态更新成功，租户 ID: {}，模型 ID: {}，状态: {}", tenantId, id, status);
        return toResponse(model);
    }

    /**
     * 查询当前租户内公开模型实体。
     *
     * @param tenantId 当前租户 ID
     * @param id       公开模型 ID
     * @return 公开模型实体
     */
    private AiPublicModel findModelOrThrow(Long tenantId, Long id) {
        AiPublicModel model = aiPublicModelMapper.selectOne(new LambdaQueryWrapper<AiPublicModel>()
                .eq(AiPublicModel::getTenantId, tenantId)
                .eq(AiPublicModel::getId, id));
        if (model == null) {
            log.warn("AI 公开模型不存在或不属于当前租户，租户 ID: {}，模型 ID: {}", tenantId, id);
            throw new BusinessException(404, "AI 公开模型不存在");
        }
        return model;
    }

    /**
     * 校验公开模型编码在当前租户内是否可用。
     *
     * @param tenantId  当前租户 ID
     * @param code      公开模型编码
     * @param excludeId 更新时排除的模型 ID，创建时为 null
     */
    private void ensureCodeAvailable(Long tenantId, String code, Long excludeId) {
        LambdaQueryWrapper<AiPublicModel> query = new LambdaQueryWrapper<AiPublicModel>()
                .eq(AiPublicModel::getTenantId, tenantId)
                .eq(AiPublicModel::getCode, code);
        if (excludeId != null) {
            query.ne(AiPublicModel::getId, excludeId);
        }
        if (aiPublicModelMapper.selectCount(query) > 0) {
            log.warn("AI 公开模型编码已存在，租户 ID: {}，编码: {}", tenantId, code);
            throw new BusinessException(400, "AI 公开模型编码已存在");
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
     * 转换为公开模型响应 DTO。
     *
     * @param model 公开模型实体
     * @return 公开模型响应
     */
    private AiPublicModelResponse toResponse(AiPublicModel model) {
        return AiPublicModelResponse.builder()
                .id(model.getId())
                .tenantId(model.getTenantId())
                .code(model.getCode())
                .displayName(model.getDisplayName())
                .modelFamily(model.getModelFamily())
                .status(model.getStatus())
                .description(model.getDescription())
                .createdAt(model.getCreatedAt())
                .updatedAt(model.getUpdatedAt())
                .build();
    }
}
