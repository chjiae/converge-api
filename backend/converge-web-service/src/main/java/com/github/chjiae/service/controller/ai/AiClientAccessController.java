package com.github.chjiae.service.controller.ai;

import com.github.chjiae.common.enums.AiCatalogStatus;
import com.github.chjiae.common.enums.AiClientApiKeyStatus;
import com.github.chjiae.common.result.PageResult;
import com.github.chjiae.common.result.Result;
import com.github.chjiae.service.annotation.Auditable;
import com.github.chjiae.service.dto.ai.AiAccessGroupCreateRequest;
import com.github.chjiae.service.dto.ai.AiAccessGroupModelGrantCreateRequest;
import com.github.chjiae.service.dto.ai.AiAccessGroupModelGrantResponse;
import com.github.chjiae.service.dto.ai.AiAccessGroupModelGrantUpdateRequest;
import com.github.chjiae.service.dto.ai.AiAccessGroupResponse;
import com.github.chjiae.service.dto.ai.AiAccessGroupUpdateRequest;
import com.github.chjiae.service.dto.ai.AiClientApiKeyAccessGroupCreateRequest;
import com.github.chjiae.service.dto.ai.AiClientApiKeyAccessGroupResponse;
import com.github.chjiae.service.dto.ai.AiClientApiKeyAccessGroupUpdateRequest;
import com.github.chjiae.service.dto.ai.AiClientApiKeyCreateRequest;
import com.github.chjiae.service.dto.ai.AiClientApiKeyResponse;
import com.github.chjiae.service.dto.ai.AiClientApiKeySecretResponse;
import com.github.chjiae.service.dto.ai.AiClientApiKeyUpdateRequest;
import com.github.chjiae.service.service.ai.AiClientAccessService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
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
 * AI 下游 Client API Key 与访问组控制器。
 * 仅允许租户所有者和租户管理员管理，不记录 raw key、Authorization、x-api-key 或 verifier。
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/ai")
@PreAuthorize("hasAnyRole('TENANT_OWNER', 'TENANT_ADMIN')")
@RequiredArgsConstructor
public class AiClientAccessController {

    /** AI Client 访问控制服务 */
    private final AiClientAccessService aiClientAccessService;

    /**
     * 创建访问组。
     *
     * @param request 创建请求
     * @return 访问组响应
     */
    @Auditable(module = "ai_access_group", action = "create", target = "'ai_access_group:' + #request.code")
    @PostMapping("/access-groups")
    public Result<AiAccessGroupResponse> createAccessGroup(@Valid @RequestBody AiAccessGroupCreateRequest request) {
        log.info("创建 AI 访问组接口调用，编码: {}", request.getCode());
        return Result.ok(aiClientAccessService.createAccessGroup(request));
    }

    /**
     * 分页查询访问组。
     *
     * @param page 页码
     * @param size 每页数量
     * @param keyword 关键字
     * @param status 状态过滤
     * @return 分页结果
     */
    @GetMapping("/access-groups")
    public Result<PageResult<AiAccessGroupResponse>> listAccessGroups(@RequestParam(defaultValue = "1") int page,
                                                                      @RequestParam(defaultValue = "10") int size,
                                                                      @RequestParam(required = false) String keyword,
                                                                      @RequestParam(required = false) AiCatalogStatus status) {
        log.info("AI 访问组列表接口调用，页码: {}，每页数量: {}，状态: {}", page, size, status);
        return Result.ok(aiClientAccessService.listAccessGroups(page, size, keyword, status));
    }

    /**
     * 查询访问组详情。
     *
     * @param id 访问组 ID
     * @return 访问组响应
     */
    @GetMapping("/access-groups/{id}")
    public Result<AiAccessGroupResponse> getAccessGroup(@PathVariable Long id) {
        log.info("AI 访问组详情接口调用，访问组 ID: {}", id);
        return Result.ok(aiClientAccessService.getAccessGroup(id));
    }

    /**
     * 更新访问组。
     *
     * @param id 访问组 ID
     * @param request 更新请求
     * @return 访问组响应
     */
    @Auditable(module = "ai_access_group", action = "update", target = "'ai_access_group:' + #id")
    @PutMapping("/access-groups/{id}")
    public Result<AiAccessGroupResponse> updateAccessGroup(@PathVariable Long id,
                                                           @Valid @RequestBody AiAccessGroupUpdateRequest request) {
        log.info("更新 AI 访问组接口调用，访问组 ID: {}", id);
        return Result.ok(aiClientAccessService.updateAccessGroup(id, request));
    }

    /**
     * 启用访问组。
     *
     * @param id 访问组 ID
     * @return 访问组响应
     */
    @Auditable(module = "ai_access_group", action = "enable", target = "'ai_access_group:' + #id")
    @PostMapping("/access-groups/{id}/enable")
    public Result<AiAccessGroupResponse> enableAccessGroup(@PathVariable Long id) {
        log.info("启用 AI 访问组接口调用，访问组 ID: {}", id);
        return Result.ok(aiClientAccessService.enableAccessGroup(id));
    }

    /**
     * 停用访问组。
     *
     * @param id 访问组 ID
     * @return 访问组响应
     */
    @Auditable(module = "ai_access_group", action = "disable", target = "'ai_access_group:' + #id")
    @PostMapping("/access-groups/{id}/disable")
    public Result<AiAccessGroupResponse> disableAccessGroup(@PathVariable Long id) {
        log.info("停用 AI 访问组接口调用，访问组 ID: {}", id);
        return Result.ok(aiClientAccessService.disableAccessGroup(id));
    }

    /**
     * 创建访问组模型授权。
     *
     * @param accessGroupId 访问组 ID
     * @param request 创建请求
     * @return 授权响应
     */
    @Auditable(module = "ai_access_group_model_grant", action = "create",
            target = "'ai_access_group:' + #accessGroupId")
    @PostMapping("/access-groups/{accessGroupId}/model-grants")
    public Result<AiAccessGroupModelGrantResponse> createModelGrant(
            @PathVariable Long accessGroupId,
            @Valid @RequestBody AiAccessGroupModelGrantCreateRequest request) {
        log.info("创建 AI 访问组模型授权接口调用，访问组 ID: {}，公开模型 ID: {}，操作: {}",
                accessGroupId, request.getPublicModelId(), request.getCanonicalOperation());
        return Result.ok(aiClientAccessService.createModelGrant(accessGroupId, request));
    }

    /**
     * 查询访问组模型授权。
     *
     * @param accessGroupId 访问组 ID
     * @param page 页码
     * @param size 每页数量
     * @return 分页结果
     */
    @GetMapping("/access-groups/{accessGroupId}/model-grants")
    public Result<PageResult<AiAccessGroupModelGrantResponse>> listModelGrants(
            @PathVariable Long accessGroupId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return Result.ok(aiClientAccessService.listModelGrants(accessGroupId, page, size));
    }

    /**
     * 查询模型授权详情。
     *
     * @param id 授权 ID
     * @return 授权响应
     */
    @GetMapping("/access-group-model-grants/{id}")
    public Result<AiAccessGroupModelGrantResponse> getModelGrant(@PathVariable Long id) {
        return Result.ok(aiClientAccessService.getModelGrant(id));
    }

    /**
     * 更新模型授权。
     *
     * @param id 授权 ID
     * @param request 更新请求
     * @return 授权响应
     */
    @Auditable(module = "ai_access_group_model_grant", action = "update",
            target = "'ai_access_group_model_grant:' + #id")
    @PutMapping("/access-group-model-grants/{id}")
    public Result<AiAccessGroupModelGrantResponse> updateModelGrant(
            @PathVariable Long id,
            @Valid @RequestBody AiAccessGroupModelGrantUpdateRequest request) {
        return Result.ok(aiClientAccessService.updateModelGrant(id, request));
    }

    /**
     * 启用模型授权。
     *
     * @param id 授权 ID
     * @return 授权响应
     */
    @Auditable(module = "ai_access_group_model_grant", action = "enable",
            target = "'ai_access_group_model_grant:' + #id")
    @PostMapping("/access-group-model-grants/{id}/enable")
    public Result<AiAccessGroupModelGrantResponse> enableModelGrant(@PathVariable Long id) {
        return Result.ok(aiClientAccessService.enableModelGrant(id));
    }

    /**
     * 停用模型授权。
     *
     * @param id 授权 ID
     * @return 授权响应
     */
    @Auditable(module = "ai_access_group_model_grant", action = "disable",
            target = "'ai_access_group_model_grant:' + #id")
    @PostMapping("/access-group-model-grants/{id}/disable")
    public Result<AiAccessGroupModelGrantResponse> disableModelGrant(@PathVariable Long id) {
        return Result.ok(aiClientAccessService.disableModelGrant(id));
    }

    /**
     * 创建 Client API Key。
     *
     * @param request 创建请求
     * @return 一次性 raw key 响应
     */
    @Auditable(module = "ai_client_api_key", action = "create", target = "'ai_client_api_key:create'")
    @PostMapping("/client-api-keys")
    public ResponseEntity<Result<AiClientApiKeySecretResponse>> createClientApiKey(
            @Valid @RequestBody AiClientApiKeyCreateRequest request) {
        log.info("创建 AI Client API Key 接口调用，展示名称: {}", request.getDisplayName());
        return noStore(Result.ok(aiClientAccessService.createClientApiKey(request)));
    }

    /**
     * 分页查询 Client API Key。
     *
     * @param page 页码
     * @param size 每页数量
     * @param status 状态过滤
     * @return 分页结果
     */
    @GetMapping("/client-api-keys")
    public Result<PageResult<AiClientApiKeyResponse>> listClientApiKeys(@RequestParam(defaultValue = "1") int page,
                                                                        @RequestParam(defaultValue = "10") int size,
                                                                        @RequestParam(required = false) AiClientApiKeyStatus status) {
        log.info("AI Client API Key 列表接口调用，页码: {}，每页数量: {}，状态: {}", page, size, status);
        return Result.ok(aiClientAccessService.listClientApiKeys(page, size, status));
    }

    /**
     * 查询 Client API Key 详情。
     *
     * @param id Key ID
     * @return 安全响应
     */
    @GetMapping("/client-api-keys/{id}")
    public Result<AiClientApiKeyResponse> getClientApiKey(@PathVariable Long id) {
        log.info("AI Client API Key 详情接口调用，Key ID: {}", id);
        return Result.ok(aiClientAccessService.getClientApiKey(id));
    }

    /**
     * 更新 Client API Key 元数据。
     *
     * @param id Key ID
     * @param request 更新请求
     * @return 安全响应
     */
    @Auditable(module = "ai_client_api_key", action = "update", target = "'ai_client_api_key:' + #id")
    @PutMapping("/client-api-keys/{id}")
    public Result<AiClientApiKeyResponse> updateClientApiKey(@PathVariable Long id,
                                                             @Valid @RequestBody AiClientApiKeyUpdateRequest request) {
        log.info("更新 AI Client API Key 接口调用，Key ID: {}", id);
        return Result.ok(aiClientAccessService.updateClientApiKey(id, request));
    }

    /**
     * 轮换 Client API Key。
     *
     * @param id Key ID
     * @return 一次性 raw key 响应
     */
    @Auditable(module = "ai_client_api_key", action = "rotate", target = "'ai_client_api_key:' + #id")
    @PostMapping("/client-api-keys/{id}/rotate")
    public ResponseEntity<Result<AiClientApiKeySecretResponse>> rotateClientApiKey(@PathVariable Long id) {
        log.info("轮换 AI Client API Key 接口调用，Key ID: {}", id);
        return noStore(Result.ok(aiClientAccessService.rotateClientApiKey(id)));
    }

    /**
     * 启用 Client API Key。
     *
     * @param id Key ID
     * @return 安全响应
     */
    @Auditable(module = "ai_client_api_key", action = "enable", target = "'ai_client_api_key:' + #id")
    @PostMapping("/client-api-keys/{id}/enable")
    public Result<AiClientApiKeyResponse> enableClientApiKey(@PathVariable Long id) {
        return Result.ok(aiClientAccessService.enableClientApiKey(id));
    }

    /**
     * 停用 Client API Key。
     *
     * @param id Key ID
     * @return 安全响应
     */
    @Auditable(module = "ai_client_api_key", action = "disable", target = "'ai_client_api_key:' + #id")
    @PostMapping("/client-api-keys/{id}/disable")
    public Result<AiClientApiKeyResponse> disableClientApiKey(@PathVariable Long id) {
        return Result.ok(aiClientAccessService.disableClientApiKey(id));
    }

    /**
     * 撤销 Client API Key。
     *
     * @param id Key ID
     * @return 安全响应
     */
    @Auditable(module = "ai_client_api_key", action = "revoke", target = "'ai_client_api_key:' + #id")
    @PostMapping("/client-api-keys/{id}/revoke")
    public Result<AiClientApiKeyResponse> revokeClientApiKey(@PathVariable Long id) {
        return Result.ok(aiClientAccessService.revokeClientApiKey(id));
    }

    /**
     * 创建 Client API Key 访问组绑定。
     *
     * @param keyId Key ID
     * @param request 创建请求
     * @return 绑定响应
     */
    @Auditable(module = "ai_client_api_key_access_group", action = "create",
            target = "'ai_client_api_key:' + #keyId")
    @PostMapping("/client-api-keys/{keyId}/access-groups")
    public Result<AiClientApiKeyAccessGroupResponse> createKeyAccessGroup(
            @PathVariable Long keyId,
            @Valid @RequestBody AiClientApiKeyAccessGroupCreateRequest request) {
        log.info("创建 AI Client API Key 访问组绑定接口调用，Key ID: {}，访问组 ID: {}",
                keyId, request.getAccessGroupId());
        return Result.ok(aiClientAccessService.createKeyAccessGroup(keyId, request));
    }

    /**
     * 查询 Client API Key 访问组绑定。
     *
     * @param keyId Key ID
     * @param page 页码
     * @param size 每页数量
     * @return 分页结果
     */
    @GetMapping("/client-api-keys/{keyId}/access-groups")
    public Result<PageResult<AiClientApiKeyAccessGroupResponse>> listKeyAccessGroups(
            @PathVariable Long keyId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return Result.ok(aiClientAccessService.listKeyAccessGroups(keyId, page, size));
    }

    /**
     * 查询 Key 访问组绑定详情。
     *
     * @param id 绑定 ID
     * @return 绑定响应
     */
    @GetMapping("/client-api-key-access-groups/{id}")
    public Result<AiClientApiKeyAccessGroupResponse> getKeyAccessGroup(@PathVariable Long id) {
        return Result.ok(aiClientAccessService.getKeyAccessGroup(id));
    }

    /**
     * 更新 Key 访问组绑定。
     *
     * @param id 绑定 ID
     * @param request 更新请求
     * @return 绑定响应
     */
    @Auditable(module = "ai_client_api_key_access_group", action = "update",
            target = "'ai_client_api_key_access_group:' + #id")
    @PutMapping("/client-api-key-access-groups/{id}")
    public Result<AiClientApiKeyAccessGroupResponse> updateKeyAccessGroup(
            @PathVariable Long id,
            @Valid @RequestBody AiClientApiKeyAccessGroupUpdateRequest request) {
        return Result.ok(aiClientAccessService.updateKeyAccessGroup(id, request));
    }

    /**
     * 启用 Key 访问组绑定。
     *
     * @param id 绑定 ID
     * @return 绑定响应
     */
    @Auditable(module = "ai_client_api_key_access_group", action = "enable",
            target = "'ai_client_api_key_access_group:' + #id")
    @PostMapping("/client-api-key-access-groups/{id}/enable")
    public Result<AiClientApiKeyAccessGroupResponse> enableKeyAccessGroup(@PathVariable Long id) {
        return Result.ok(aiClientAccessService.enableKeyAccessGroup(id));
    }

    /**
     * 停用 Key 访问组绑定。
     *
     * @param id 绑定 ID
     * @return 绑定响应
     */
    @Auditable(module = "ai_client_api_key_access_group", action = "disable",
            target = "'ai_client_api_key_access_group:' + #id")
    @PostMapping("/client-api-key-access-groups/{id}/disable")
    public Result<AiClientApiKeyAccessGroupResponse> disableKeyAccessGroup(@PathVariable Long id) {
        return Result.ok(aiClientAccessService.disableKeyAccessGroup(id));
    }

    private ResponseEntity<Result<AiClientApiKeySecretResponse>> noStore(
            Result<AiClientApiKeySecretResponse> result) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(result);
    }
}
