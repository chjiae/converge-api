package com.github.chjiae.service.controller.ai;

import com.github.chjiae.common.enums.AiResourceStatus;
import com.github.chjiae.common.result.PageResult;
import com.github.chjiae.common.result.Result;
import com.github.chjiae.service.annotation.Auditable;
import com.github.chjiae.service.dto.ai.AiExecutionResourceCreateRequest;
import com.github.chjiae.service.dto.ai.AiExecutionResourceResponse;
import com.github.chjiae.service.dto.ai.AiExecutionResourceUpdateRequest;
import com.github.chjiae.service.service.ai.AiExecutionResourceService;
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
 * AI 可执行资源控制器。
 * 仅允许租户所有者和租户管理员管理 Direct API 可执行资源。
 * 资源绑定一经创建不可修改 connectionId、credentialId 或 providerId。
 * 不提供删除接口。
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/ai")
@PreAuthorize("hasAnyRole('TENANT_OWNER', 'TENANT_ADMIN')")
@RequiredArgsConstructor
public class AiExecutionResourceController {

    /** AI 可执行资源服务 */
    private final AiExecutionResourceService aiExecutionResourceService;

    /**
     * 创建可执行资源（静态绑定连接与凭据）。
     *
     * @param request 创建请求
     * @return 资源响应
     */
    @Auditable(module = "ai_resource", action = "create", target = "'ai_resource:' + #request.code")
    @PostMapping("/resources")
    public Result<AiExecutionResourceResponse> createResource(@Valid @RequestBody AiExecutionResourceCreateRequest request) {
        log.info("创建 AI 可执行资源接口调用，编码: {}，连接 ID: {}，凭据 ID: {}",
                request.getCode(), request.getUpstreamConnectionId(), request.getCredentialId());
        return Result.ok(aiExecutionResourceService.createResource(request));
    }

    /**
     * 分页查询可执行资源列表。
     *
     * @param page    页码，默认 1
     * @param size    每页数量，默认 10
     * @param keyword 关键字，可选
     * @param status  状态过滤，可选
     * @return 分页资源列表
     */
    @GetMapping("/resources")
    public Result<PageResult<AiExecutionResourceResponse>> listResources(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) AiResourceStatus status) {
        log.info("AI 可执行资源列表接口调用，页码: {}，每页数量: {}，状态: {}", page, size, status);
        return Result.ok(aiExecutionResourceService.listResources(page, size, keyword, status));
    }

    /**
     * 查询资源详情。
     *
     * @param id 资源 ID
     * @return 资源响应
     */
    @GetMapping("/resources/{id}")
    public Result<AiExecutionResourceResponse> getResource(@PathVariable Long id) {
        log.info("AI 可执行资源详情接口调用，资源 ID: {}", id);
        return Result.ok(aiExecutionResourceService.getResource(id));
    }

    /**
     * 更新资源管理元数据（不允许变更绑定关系）。
     *
     * @param id      资源 ID
     * @param request 更新请求
     * @return 更新后的资源响应
     */
    @Auditable(module = "ai_resource", action = "update", target = "'ai_resource:' + #id")
    @PutMapping("/resources/{id}")
    public Result<AiExecutionResourceResponse> updateResource(@PathVariable Long id,
                                                                @Valid @RequestBody AiExecutionResourceUpdateRequest request) {
        log.info("更新 AI 可执行资源接口调用，资源 ID: {}", id);
        return Result.ok(aiExecutionResourceService.updateResource(id, request));
    }

    /**
     * 启用资源。
     *
     * @param id 资源 ID
     * @return 启用后的资源响应
     */
    @Auditable(module = "ai_resource", action = "enable", target = "'ai_resource:' + #id")
    @PostMapping("/resources/{id}/enable")
    public Result<AiExecutionResourceResponse> enableResource(@PathVariable Long id) {
        log.info("启用 AI 可执行资源接口调用，资源 ID: {}", id);
        return Result.ok(aiExecutionResourceService.enableResource(id));
    }

    /**
     * 停用资源。
     *
     * @param id 资源 ID
     * @return 停用后的资源响应
     */
    @Auditable(module = "ai_resource", action = "disable", target = "'ai_resource:' + #id")
    @PostMapping("/resources/{id}/disable")
    public Result<AiExecutionResourceResponse> disableResource(@PathVariable Long id) {
        log.info("停用 AI 可执行资源接口调用，资源 ID: {}", id);
        return Result.ok(aiExecutionResourceService.disableResource(id));
    }

    /**
     * 排空资源（为后续阶段预留）。
     *
     * @param id 资源 ID
     * @return 排空后的资源响应
     */
    @Auditable(module = "ai_resource", action = "drain", target = "'ai_resource:' + #id")
    @PostMapping("/resources/{id}/drain")
    public Result<AiExecutionResourceResponse> drainResource(@PathVariable Long id) {
        log.info("排空 AI 可执行资源接口调用，资源 ID: {}", id);
        return Result.ok(aiExecutionResourceService.drainResource(id));
    }
}
