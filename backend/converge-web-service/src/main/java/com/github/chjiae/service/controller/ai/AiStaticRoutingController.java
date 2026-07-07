package com.github.chjiae.service.controller.ai;

import com.github.chjiae.common.enums.AiCanonicalOperation;
import com.github.chjiae.common.enums.AiCatalogStatus;
import com.github.chjiae.common.enums.AiRoutePolicyStatus;
import com.github.chjiae.common.result.PageResult;
import com.github.chjiae.common.result.Result;
import com.github.chjiae.service.annotation.Auditable;
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
import com.github.chjiae.service.service.ai.AiStaticRoutingService;
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
 * AI 静态路由控制器。
 * 仅允许租户所有者和租户管理员管理资源池、模型绑定与静态路由策略。
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/ai")
@PreAuthorize("hasAnyRole('TENANT_OWNER', 'TENANT_ADMIN')")
@RequiredArgsConstructor
public class AiStaticRoutingController {

    /** AI 静态路由服务 */
    private final AiStaticRoutingService aiStaticRoutingService;

    /**
     * 创建资源池。
     */
    @Auditable(module = "ai_resource_pool", action = "create", target = "'ai_resource_pool:' + #request.code")
    @PostMapping("/resource-pools")
    public Result<AiResourcePoolResponse> createResourcePool(@Valid @RequestBody AiResourcePoolCreateRequest request) {
        log.info("创建 AI 资源池接口调用，编码: {}", request.getCode());
        return Result.ok(aiStaticRoutingService.createResourcePool(request));
    }

    /**
     * 分页查询资源池。
     */
    @GetMapping("/resource-pools")
    public Result<PageResult<AiResourcePoolResponse>> listResourcePools(@RequestParam(defaultValue = "1") int page,
                                                                        @RequestParam(defaultValue = "10") int size,
                                                                        @RequestParam(required = false) String keyword,
                                                                        @RequestParam(required = false) AiCatalogStatus status) {
        return Result.ok(aiStaticRoutingService.listResourcePools(page, size, keyword, status));
    }

    /**
     * 查询资源池详情。
     */
    @GetMapping("/resource-pools/{id}")
    public Result<AiResourcePoolResponse> getResourcePool(@PathVariable Long id) {
        return Result.ok(aiStaticRoutingService.getResourcePool(id));
    }

    /**
     * 更新资源池。
     */
    @Auditable(module = "ai_resource_pool", action = "update", target = "'ai_resource_pool:' + #id")
    @PutMapping("/resource-pools/{id}")
    public Result<AiResourcePoolResponse> updateResourcePool(@PathVariable Long id,
                                                             @Valid @RequestBody AiResourcePoolUpdateRequest request) {
        return Result.ok(aiStaticRoutingService.updateResourcePool(id, request));
    }

    /**
     * 启用资源池。
     */
    @Auditable(module = "ai_resource_pool", action = "enable", target = "'ai_resource_pool:' + #id")
    @PostMapping("/resource-pools/{id}/enable")
    public Result<AiResourcePoolResponse> enableResourcePool(@PathVariable Long id) {
        return Result.ok(aiStaticRoutingService.enableResourcePool(id));
    }

    /**
     * 停用资源池。
     */
    @Auditable(module = "ai_resource_pool", action = "disable", target = "'ai_resource_pool:' + #id")
    @PostMapping("/resource-pools/{id}/disable")
    public Result<AiResourcePoolResponse> disableResourcePool(@PathVariable Long id) {
        return Result.ok(aiStaticRoutingService.disableResourcePool(id));
    }

    /**
     * 创建资源池成员。
     */
    @Auditable(module = "ai_pool_member", action = "create", target = "'ai_pool_member:' + #poolId")
    @PostMapping("/resource-pools/{poolId}/members")
    public Result<AiResourcePoolMemberResponse> createPoolMember(@PathVariable Long poolId,
                                                                 @Valid @RequestBody AiResourcePoolMemberCreateRequest request) {
        return Result.ok(aiStaticRoutingService.createPoolMember(poolId, request));
    }

    /**
     * 查询资源池成员。
     */
    @GetMapping("/resource-pools/{poolId}/members")
    public Result<PageResult<AiResourcePoolMemberResponse>> listPoolMembers(@PathVariable Long poolId,
                                                                            @RequestParam(defaultValue = "1") int page,
                                                                            @RequestParam(defaultValue = "10") int size) {
        return Result.ok(aiStaticRoutingService.listPoolMembers(poolId, page, size));
    }

    /**
     * 更新资源池成员。
     */
    @Auditable(module = "ai_pool_member", action = "update", target = "'ai_pool_member:' + #id")
    @PutMapping("/resource-pool-members/{id}")
    public Result<AiResourcePoolMemberResponse> updatePoolMember(@PathVariable Long id,
                                                                 @Valid @RequestBody AiResourcePoolMemberUpdateRequest request) {
        return Result.ok(aiStaticRoutingService.updatePoolMember(id, request));
    }

    /**
     * 启用资源池成员。
     */
    @Auditable(module = "ai_pool_member", action = "enable", target = "'ai_pool_member:' + #id")
    @PostMapping("/resource-pool-members/{id}/enable")
    public Result<AiResourcePoolMemberResponse> enablePoolMember(@PathVariable Long id) {
        return Result.ok(aiStaticRoutingService.enablePoolMember(id));
    }

    /**
     * 停用资源池成员。
     */
    @Auditable(module = "ai_pool_member", action = "disable", target = "'ai_pool_member:' + #id")
    @PostMapping("/resource-pool-members/{id}/disable")
    public Result<AiResourcePoolMemberResponse> disablePoolMember(@PathVariable Long id) {
        return Result.ok(aiStaticRoutingService.disablePoolMember(id));
    }

    /**
     * 创建资源模型绑定。
     */
    @Auditable(module = "ai_model_binding", action = "create", target = "'ai_model_binding:' + #request.executionResourceId")
    @PostMapping("/resource-model-bindings")
    public Result<AiResourceModelBindingResponse> createModelBinding(
            @Valid @RequestBody AiResourceModelBindingCreateRequest request) {
        return Result.ok(aiStaticRoutingService.createModelBinding(request));
    }

    /**
     * 分页查询资源模型绑定。
     */
    @GetMapping("/resource-model-bindings")
    public Result<PageResult<AiResourceModelBindingResponse>> listModelBindings(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) Long publicModelId,
            @RequestParam(required = false) AiCanonicalOperation operation) {
        return Result.ok(aiStaticRoutingService.listModelBindings(page, size, publicModelId, operation));
    }

    /**
     * 查询资源模型绑定详情。
     */
    @GetMapping("/resource-model-bindings/{id}")
    public Result<AiResourceModelBindingResponse> getModelBinding(@PathVariable Long id) {
        return Result.ok(aiStaticRoutingService.getModelBinding(id));
    }

    /**
     * 更新资源模型绑定。
     */
    @Auditable(module = "ai_model_binding", action = "update", target = "'ai_model_binding:' + #id")
    @PutMapping("/resource-model-bindings/{id}")
    public Result<AiResourceModelBindingResponse> updateModelBinding(@PathVariable Long id,
                                                                     @Valid @RequestBody AiResourceModelBindingUpdateRequest request) {
        return Result.ok(aiStaticRoutingService.updateModelBinding(id, request));
    }

    /**
     * 启用资源模型绑定。
     */
    @Auditable(module = "ai_model_binding", action = "enable", target = "'ai_model_binding:' + #id")
    @PostMapping("/resource-model-bindings/{id}/enable")
    public Result<AiResourceModelBindingResponse> enableModelBinding(@PathVariable Long id) {
        return Result.ok(aiStaticRoutingService.enableModelBinding(id));
    }

    /**
     * 停用资源模型绑定。
     */
    @Auditable(module = "ai_model_binding", action = "disable", target = "'ai_model_binding:' + #id")
    @PostMapping("/resource-model-bindings/{id}/disable")
    public Result<AiResourceModelBindingResponse> disableModelBinding(@PathVariable Long id) {
        return Result.ok(aiStaticRoutingService.disableModelBinding(id));
    }

    /**
     * 创建路由策略。
     */
    @Auditable(module = "ai_route_policy", action = "create", target = "'ai_route_policy:' + #request.publicModelId")
    @PostMapping("/route-policies")
    public Result<AiRoutePolicyResponse> createRoutePolicy(@Valid @RequestBody AiRoutePolicyCreateRequest request) {
        return Result.ok(aiStaticRoutingService.createRoutePolicy(request));
    }

    /**
     * 分页查询路由策略。
     */
    @GetMapping("/route-policies")
    public Result<PageResult<AiRoutePolicyResponse>> listRoutePolicies(@RequestParam(defaultValue = "1") int page,
                                                                       @RequestParam(defaultValue = "10") int size,
                                                                       @RequestParam(required = false) Long publicModelId,
                                                                       @RequestParam(required = false) AiRoutePolicyStatus status) {
        return Result.ok(aiStaticRoutingService.listRoutePolicies(page, size, publicModelId, status));
    }

    /**
     * 查询路由策略详情。
     */
    @GetMapping("/route-policies/{id}")
    public Result<AiRoutePolicyResponse> getRoutePolicy(@PathVariable Long id) {
        return Result.ok(aiStaticRoutingService.getRoutePolicy(id));
    }

    /**
     * 更新路由策略。
     */
    @Auditable(module = "ai_route_policy", action = "update", target = "'ai_route_policy:' + #id")
    @PutMapping("/route-policies/{id}")
    public Result<AiRoutePolicyResponse> updateRoutePolicy(@PathVariable Long id,
                                                           @Valid @RequestBody AiRoutePolicyUpdateRequest request) {
        return Result.ok(aiStaticRoutingService.updateRoutePolicy(id, request));
    }

    /**
     * 启用路由策略。
     */
    @Auditable(module = "ai_route_policy", action = "enable", target = "'ai_route_policy:' + #id")
    @PostMapping("/route-policies/{id}/enable")
    public Result<AiRoutePolicyResponse> enableRoutePolicy(@PathVariable Long id) {
        return Result.ok(aiStaticRoutingService.enableRoutePolicy(id));
    }

    /**
     * 停用路由策略。
     */
    @Auditable(module = "ai_route_policy", action = "disable", target = "'ai_route_policy:' + #id")
    @PostMapping("/route-policies/{id}/disable")
    public Result<AiRoutePolicyResponse> disableRoutePolicy(@PathVariable Long id) {
        return Result.ok(aiStaticRoutingService.disableRoutePolicy(id));
    }

    /**
     * 创建路由目标池。
     */
    @Auditable(module = "ai_route_target", action = "create", target = "'ai_route_target:' + #policyId")
    @PostMapping("/route-policies/{policyId}/targets")
    public Result<AiRouteTargetResponse> createRouteTarget(@PathVariable Long policyId,
                                                           @Valid @RequestBody AiRouteTargetCreateRequest request) {
        return Result.ok(aiStaticRoutingService.createRouteTarget(policyId, request));
    }

    /**
     * 查询路由目标池。
     */
    @GetMapping("/route-policies/{policyId}/targets")
    public Result<PageResult<AiRouteTargetResponse>> listRouteTargets(@PathVariable Long policyId,
                                                                      @RequestParam(defaultValue = "1") int page,
                                                                      @RequestParam(defaultValue = "10") int size) {
        return Result.ok(aiStaticRoutingService.listRouteTargets(policyId, page, size));
    }

    /**
     * 更新路由目标池。
     */
    @Auditable(module = "ai_route_target", action = "update", target = "'ai_route_target:' + #id")
    @PutMapping("/route-targets/{id}")
    public Result<AiRouteTargetResponse> updateRouteTarget(@PathVariable Long id,
                                                           @Valid @RequestBody AiRouteTargetUpdateRequest request) {
        return Result.ok(aiStaticRoutingService.updateRouteTarget(id, request));
    }

    /**
     * 启用路由目标池。
     */
    @Auditable(module = "ai_route_target", action = "enable", target = "'ai_route_target:' + #id")
    @PostMapping("/route-targets/{id}/enable")
    public Result<AiRouteTargetResponse> enableRouteTarget(@PathVariable Long id) {
        return Result.ok(aiStaticRoutingService.enableRouteTarget(id));
    }

    /**
     * 停用路由目标池。
     */
    @Auditable(module = "ai_route_target", action = "disable", target = "'ai_route_target:' + #id")
    @PostMapping("/route-targets/{id}/disable")
    public Result<AiRouteTargetResponse> disableRouteTarget(@PathVariable Long id) {
        return Result.ok(aiStaticRoutingService.disableRouteTarget(id));
    }

    /**
     * 预览静态路由。
     * 该接口不执行上游请求，不应用动态状态。
     */
    @PostMapping("/routes/preview")
    public Result<AiRoutePreviewResponse> previewRoute(@Valid @RequestBody AiRoutePreviewRequest request) {
        return Result.ok(aiStaticRoutingService.previewRoute(request));
    }
}
