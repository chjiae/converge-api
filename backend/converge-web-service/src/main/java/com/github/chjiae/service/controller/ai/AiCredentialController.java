package com.github.chjiae.service.controller.ai;

import com.github.chjiae.common.enums.AiCatalogStatus;
import com.github.chjiae.common.result.PageResult;
import com.github.chjiae.common.result.Result;
import com.github.chjiae.service.annotation.Auditable;
import com.github.chjiae.service.dto.ai.AiCredentialCreateRequest;
import com.github.chjiae.service.dto.ai.AiCredentialResponse;
import com.github.chjiae.service.dto.ai.AiCredentialRotateRequest;
import com.github.chjiae.service.dto.ai.AiCredentialUpdateRequest;
import com.github.chjiae.service.service.ai.AiCredentialService;
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
 * AI 凭据控制器。
 * 仅允许租户所有者和租户管理员管理上游 API Key 凭据。
 * 不提供删除接口，不提供读取明文接口。
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/ai")
@PreAuthorize("hasAnyRole('TENANT_OWNER', 'TENANT_ADMIN')")
@RequiredArgsConstructor
public class AiCredentialController {

    /** AI 凭据服务 */
    private final AiCredentialService aiCredentialService;

    /**
     * 创建 AI 凭据。
     *
     * @param providerId 供应商 ID
     * @param request    创建请求（包含明文 API Key，服务端加密后丢弃）
     * @return 凭据响应（不含明文或密文）
     */
    @Auditable(module = "ai_credential", action = "create", target = "'ai_credential:' + #request.code")
    @PostMapping("/providers/{providerId}/credentials")
    public Result<AiCredentialResponse> createCredential(@PathVariable Long providerId,
                                                          @Valid @RequestBody AiCredentialCreateRequest request) {
        log.info("创建 AI 凭据接口调用，供应商 ID: {}，编码: {}", providerId, request.getCode());
        return Result.ok(aiCredentialService.createCredential(providerId, request));
    }

    /**
     * 分页查询某个供应商下的凭据列表。
     *
     * @param providerId 供应商 ID
     * @param page       页码，默认 1
     * @param size       每页数量，默认 10
     * @param keyword    关键字，可选
     * @param status     状态过滤，可选
     * @return 分页凭据列表
     */
    @GetMapping("/providers/{providerId}/credentials")
    public Result<PageResult<AiCredentialResponse>> listCredentials(
            @PathVariable Long providerId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) AiCatalogStatus status) {
        log.info("AI 凭据列表接口调用，供应商 ID: {}，页码: {}，每页数量: {}", providerId, page, size);
        return Result.ok(aiCredentialService.listCredentials(providerId, page, size, keyword, status));
    }

    /**
     * 查询凭据详情（不含明文 API Key）。
     *
     * @param id 凭据 ID
     * @return 凭据响应
     */
    @GetMapping("/credentials/{id}")
    public Result<AiCredentialResponse> getCredential(@PathVariable Long id) {
        log.info("AI 凭据详情接口调用，凭据 ID: {}", id);
        return Result.ok(aiCredentialService.getCredential(id));
    }

    /**
     * 更新凭据管理元数据（不变更 API Key）。
     *
     * @param id      凭据 ID
     * @param request 更新请求
     * @return 更新后的凭据响应
     */
    @Auditable(module = "ai_credential", action = "update", target = "'ai_credential:' + #id")
    @PutMapping("/credentials/{id}")
    public Result<AiCredentialResponse> updateCredential(@PathVariable Long id,
                                                          @Valid @RequestBody AiCredentialUpdateRequest request) {
        log.info("更新 AI 凭据接口调用，凭据 ID: {}", id);
        return Result.ok(aiCredentialService.updateCredential(id, request));
    }

    /**
     * 轮换凭据（保留 ID，更新密文、指纹、掩码、版本与轮换时间）。
     *
     * @param id      凭据 ID
     * @param request 轮换请求（包含新明文 API Key）
     * @return 轮换后的凭据响应
     */
    @Auditable(module = "ai_credential", action = "rotate", target = "'ai_credential:' + #id")
    @PostMapping("/credentials/{id}/rotate")
    public Result<AiCredentialResponse> rotateCredential(@PathVariable Long id,
                                                          @Valid @RequestBody AiCredentialRotateRequest request) {
        log.info("轮换 AI 凭据接口调用，凭据 ID: {}", id);
        return Result.ok(aiCredentialService.rotateCredential(id, request.getNewApiKey()));
    }

    /**
     * 启用凭据。
     *
     * @param id 凭据 ID
     * @return 启用后的凭据响应
     */
    @Auditable(module = "ai_credential", action = "enable", target = "'ai_credential:' + #id")
    @PostMapping("/credentials/{id}/enable")
    public Result<AiCredentialResponse> enableCredential(@PathVariable Long id) {
        log.info("启用 AI 凭据接口调用，凭据 ID: {}", id);
        return Result.ok(aiCredentialService.enableCredential(id));
    }

    /**
     * 停用凭据。
     *
     * @param id 凭据 ID
     * @return 停用后的凭据响应
     */
    @Auditable(module = "ai_credential", action = "disable", target = "'ai_credential:' + #id")
    @PostMapping("/credentials/{id}/disable")
    public Result<AiCredentialResponse> disableCredential(@PathVariable Long id) {
        log.info("停用 AI 凭据接口调用，凭据 ID: {}", id);
        return Result.ok(aiCredentialService.disableCredential(id));
    }
}
