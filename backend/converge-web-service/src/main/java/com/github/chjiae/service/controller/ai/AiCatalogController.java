package com.github.chjiae.service.controller.ai;

import com.github.chjiae.common.enums.AiCatalogStatus;
import com.github.chjiae.common.result.PageResult;
import com.github.chjiae.common.result.Result;
import com.github.chjiae.service.annotation.Auditable;
import com.github.chjiae.service.dto.ai.AiProviderCreateRequest;
import com.github.chjiae.service.dto.ai.AiProviderResponse;
import com.github.chjiae.service.dto.ai.AiProviderUpdateRequest;
import com.github.chjiae.service.dto.ai.AiPublicModelCreateRequest;
import com.github.chjiae.service.dto.ai.AiPublicModelResponse;
import com.github.chjiae.service.dto.ai.AiPublicModelUpdateRequest;
import com.github.chjiae.service.dto.ai.AiUpstreamConnectionCreateRequest;
import com.github.chjiae.service.dto.ai.AiUpstreamConnectionResponse;
import com.github.chjiae.service.dto.ai.AiUpstreamConnectionUpdateRequest;
import com.github.chjiae.service.service.ai.AiProviderService;
import com.github.chjiae.service.service.ai.AiPublicModelService;
import com.github.chjiae.service.service.ai.AiUpstreamConnectionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * AI 控制面目录控制器。
 * 仅允许租户所有者和租户管理员管理 Provider、Connection 与 PublicModel。
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/ai")
@PreAuthorize("hasAnyRole('TENANT_OWNER', 'TENANT_ADMIN')")
@RequiredArgsConstructor
public class AiCatalogController {

    /** AI 供应商服务 */
    private final AiProviderService aiProviderService;

    /** AI 上游连接服务 */
    private final AiUpstreamConnectionService aiUpstreamConnectionService;

    /** AI 公开模型服务 */
    private final AiPublicModelService aiPublicModelService;

    /**
     * 创建 AI 供应商。
     *
     * @param request 创建请求
     * @return 创建后的供应商
     */
    @Auditable(module = "ai_provider", action = "create", target = "'ai_provider:' + #request.code")
    @PostMapping("/providers")
    public Result<AiProviderResponse> createProvider(@Valid @RequestBody AiProviderCreateRequest request) {
        log.info("创建 AI 供应商接口调用，编码: {}", request.getCode());
        return Result.ok(aiProviderService.createProvider(request));
    }

    /**
     * 分页查询 AI 供应商。
     *
     * @param page    页码，默认 1
     * @param size    每页数量，默认 10
     * @param keyword 关键字，可选
     * @param status  状态过滤，可选
     * @return 分页供应商
     */
    @GetMapping("/providers")
    public Result<PageResult<AiProviderResponse>> listProviders(@RequestParam(defaultValue = "1") int page,
                                                                @RequestParam(defaultValue = "10") int size,
                                                                @RequestParam(required = false) String keyword,
                                                                @RequestParam(required = false) AiCatalogStatus status) {
        log.info("AI 供应商列表接口调用，页码: {}，每页数量: {}，状态: {}", page, size, status);
        return Result.ok(aiProviderService.listProviders(page, size, keyword, status));
    }

    /**
     * 查询 AI 供应商详情。
     *
     * @param id 供应商 ID
     * @return 供应商详情
     */
    @GetMapping("/providers/{id}")
    public Result<AiProviderResponse> getProvider(@PathVariable Long id) {
        log.info("AI 供应商详情接口调用，供应商 ID: {}", id);
        return Result.ok(aiProviderService.getProvider(id));
    }

    /**
     * 更新 AI 供应商。
     *
     * @param id      供应商 ID
     * @param request 更新请求
     * @return 更新后的供应商
     */
    @Auditable(module = "ai_provider", action = "update", target = "'ai_provider:' + #id")
    @PutMapping("/providers/{id}")
    public Result<AiProviderResponse> updateProvider(@PathVariable Long id,
                                                     @Valid @RequestBody AiProviderUpdateRequest request) {
        log.info("更新 AI 供应商接口调用，供应商 ID: {}", id);
        return Result.ok(aiProviderService.updateProvider(id, request));
    }

    /**
     * 启用 AI 供应商。
     *
     * @param id 供应商 ID
     * @return 启用后的供应商
     */
    @Auditable(module = "ai_provider", action = "enable", target = "'ai_provider:' + #id")
    @PostMapping("/providers/{id}/enable")
    public Result<AiProviderResponse> enableProvider(@PathVariable Long id) {
        log.info("启用 AI 供应商接口调用，供应商 ID: {}", id);
        return Result.ok(aiProviderService.enableProvider(id));
    }

    /**
     * 停用 AI 供应商。
     *
     * @param id 供应商 ID
     * @return 停用后的供应商
     */
    @Auditable(module = "ai_provider", action = "disable", target = "'ai_provider:' + #id")
    @PostMapping("/providers/{id}/disable")
    public Result<AiProviderResponse> disableProvider(@PathVariable Long id) {
        log.info("停用 AI 供应商接口调用，供应商 ID: {}", id);
        return Result.ok(aiProviderService.disableProvider(id));
    }

    /**
     * 创建 AI 上游连接。
     *
     * @param providerId Provider ID
     * @param request    创建请求
     * @return 创建后的连接
     */
    @Auditable(module = "ai_connection", action = "create", target = "'ai_connection:' + #request.code")
    @PostMapping("/providers/{providerId}/connections")
    public Result<AiUpstreamConnectionResponse> createConnection(@PathVariable Long providerId,
                                                                 @Valid @RequestBody AiUpstreamConnectionCreateRequest request) {
        log.info("创建 AI 上游连接接口调用，供应商 ID: {}，编码: {}", providerId, request.getCode());
        return Result.ok(aiUpstreamConnectionService.createConnection(providerId, request));
    }

    /**
     * 分页查询某个 Provider 下的 AI 上游连接。
     *
     * @param providerId Provider ID
     * @param page       页码，默认 1
     * @param size       每页数量，默认 10
     * @param keyword    关键字，可选
     * @param status     状态过滤，可选
     * @return 分页连接
     */
    @GetMapping("/providers/{providerId}/connections")
    public Result<PageResult<AiUpstreamConnectionResponse>> listConnections(
            @PathVariable Long providerId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) AiCatalogStatus status) {
        log.info("AI 上游连接列表接口调用，供应商 ID: {}，页码: {}，每页数量: {}", providerId, page, size);
        return Result.ok(aiUpstreamConnectionService.listConnections(providerId, page, size, keyword, status));
    }

    /**
     * 查询 AI 上游连接详情。
     *
     * @param id 连接 ID
     * @return 连接详情
     */
    @GetMapping("/connections/{id}")
    public Result<AiUpstreamConnectionResponse> getConnection(@PathVariable Long id) {
        log.info("AI 上游连接详情接口调用，连接 ID: {}", id);
        return Result.ok(aiUpstreamConnectionService.getConnection(id));
    }

    /**
     * 更新 AI 上游连接。
     *
     * @param id      连接 ID
     * @param request 更新请求
     * @return 更新后的连接
     */
    @Auditable(module = "ai_connection", action = "update", target = "'ai_connection:' + #id")
    @PutMapping("/connections/{id}")
    public Result<AiUpstreamConnectionResponse> updateConnection(@PathVariable Long id,
                                                                 @Valid @RequestBody AiUpstreamConnectionUpdateRequest request) {
        log.info("更新 AI 上游连接接口调用，连接 ID: {}", id);
        return Result.ok(aiUpstreamConnectionService.updateConnection(id, request));
    }

    /**
     * 启用 AI 上游连接。
     *
     * @param id 连接 ID
     * @return 启用后的连接
     */
    @Auditable(module = "ai_connection", action = "enable", target = "'ai_connection:' + #id")
    @PostMapping("/connections/{id}/enable")
    public Result<AiUpstreamConnectionResponse> enableConnection(@PathVariable Long id) {
        log.info("启用 AI 上游连接接口调用，连接 ID: {}", id);
        return Result.ok(aiUpstreamConnectionService.enableConnection(id));
    }

    /**
     * 停用 AI 上游连接。
     *
     * @param id 连接 ID
     * @return 停用后的连接
     */
    @Auditable(module = "ai_connection", action = "disable", target = "'ai_connection:' + #id")
    @PostMapping("/connections/{id}/disable")
    public Result<AiUpstreamConnectionResponse> disableConnection(@PathVariable Long id) {
        log.info("停用 AI 上游连接接口调用，连接 ID: {}", id);
        return Result.ok(aiUpstreamConnectionService.disableConnection(id));
    }

    /**
     * 创建 AI 公开模型。
     *
     * @param request 创建请求
     * @return 创建后的公开模型
     */
    @Auditable(module = "ai_public_model", action = "create", target = "'ai_public_model:' + #request.code")
    @PostMapping("/models")
    public Result<AiPublicModelResponse> createModel(@Valid @RequestBody AiPublicModelCreateRequest request) {
        log.info("创建 AI 公开模型接口调用，编码: {}", request.getCode());
        return Result.ok(aiPublicModelService.createModel(request));
    }

    /**
     * 分页查询 AI 公开模型。
     *
     * @param page    页码，默认 1
     * @param size    每页数量，默认 10
     * @param keyword 关键字，可选
     * @param status  状态过滤，可选
     * @return 分页公开模型
     */
    @GetMapping("/models")
    public Result<PageResult<AiPublicModelResponse>> listModels(@RequestParam(defaultValue = "1") int page,
                                                                @RequestParam(defaultValue = "10") int size,
                                                                @RequestParam(required = false) String keyword,
                                                                @RequestParam(required = false) AiCatalogStatus status) {
        log.info("AI 公开模型列表接口调用，页码: {}，每页数量: {}，状态: {}", page, size, status);
        return Result.ok(aiPublicModelService.listModels(page, size, keyword, status));
    }

    /**
     * 查询 AI 公开模型详情。
     *
     * @param id 公开模型 ID
     * @return 公开模型详情
     */
    @GetMapping("/models/{id}")
    public Result<AiPublicModelResponse> getModel(@PathVariable Long id) {
        log.info("AI 公开模型详情接口调用，模型 ID: {}", id);
        return Result.ok(aiPublicModelService.getModel(id));
    }

    /**
     * 更新 AI 公开模型。
     *
     * @param id      公开模型 ID
     * @param request 更新请求
     * @return 更新后的公开模型
     */
    @Auditable(module = "ai_public_model", action = "update", target = "'ai_public_model:' + #id")
    @PutMapping("/models/{id}")
    public Result<AiPublicModelResponse> updateModel(@PathVariable Long id,
                                                     @Valid @RequestBody AiPublicModelUpdateRequest request) {
        log.info("更新 AI 公开模型接口调用，模型 ID: {}", id);
        return Result.ok(aiPublicModelService.updateModel(id, request));
    }

    /**
     * 启用 AI 公开模型。
     *
     * @param id 公开模型 ID
     * @return 启用后的公开模型
     */
    @Auditable(module = "ai_public_model", action = "enable", target = "'ai_public_model:' + #id")
    @PostMapping("/models/{id}/enable")
    public Result<AiPublicModelResponse> enableModel(@PathVariable Long id) {
        log.info("启用 AI 公开模型接口调用，模型 ID: {}", id);
        return Result.ok(aiPublicModelService.enableModel(id));
    }

    /**
     * 停用 AI 公开模型。
     *
     * @param id 公开模型 ID
     * @return 停用后的公开模型
     */
    @Auditable(module = "ai_public_model", action = "disable", target = "'ai_public_model:' + #id")
    @PostMapping("/models/{id}/disable")
    public Result<AiPublicModelResponse> disableModel(@PathVariable Long id) {
        log.info("停用 AI 公开模型接口调用，模型 ID: {}", id);
        return Result.ok(aiPublicModelService.disableModel(id));
    }
}
