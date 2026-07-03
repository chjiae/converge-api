package com.github.chjiae.service.controller;

import com.github.chjiae.common.result.PageResult;
import com.github.chjiae.common.result.Result;
import com.github.chjiae.service.dto.application.ApplicationResponse;
import com.github.chjiae.service.dto.application.CreateApplicationRequest;
import com.github.chjiae.service.dto.application.ReviewApplicationRequest;
import com.github.chjiae.service.service.ApplicationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * 申请审核控制器，提供租户注册申请的提交、查询和审核接口。
 * 提交和查询为公开接口，审核操作需 SUPER_ADMIN 或 PLATFORM_OPERATOR 角色。
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/applications")
@RequiredArgsConstructor
public class ApplicationController {

    /** 申请审核服务 */
    private final ApplicationService applicationService;

    /**
     * 提交租户申请（公开接口，无需认证）
     *
     * @param request 创建申请请求参数
     * @return 申请信息
     */
    @PostMapping
    public Result<ApplicationResponse> submitApplication(@Valid @RequestBody CreateApplicationRequest request) {
        log.info("提交申请接口调用，公司名称: {}", request.getCompanyName());
        ApplicationResponse response = applicationService.submitApplication(request);
        return Result.ok(response);
    }

    /**
     * 查询申请状态（公开接口，无需认证）
     *
     * @param id 申请 ID
     * @return 申请信息
     */
    @GetMapping("/{id}")
    public Result<ApplicationResponse> getApplication(@PathVariable Long id) {
        log.info("查询申请状态接口调用，申请 ID: {}", id);
        ApplicationResponse response = applicationService.getApplicationById(id);
        return Result.ok(response);
    }

    /**
     * 申请列表（分页，需 SUPER_ADMIN 或 PLATFORM_OPERATOR 角色）
     *
     * @param page 页码，默认 1
     * @param size 每页数量，默认 10
     * @return 分页申请列表
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'PLATFORM_OPERATOR')")
    public Result<PageResult<ApplicationResponse>> listApplications(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        log.info("申请列表接口调用，页码: {}，每页数量: {}", page, size);
        PageResult<ApplicationResponse> response = applicationService.listApplications(page, size);
        return Result.ok(response);
    }

    /**
     * 审核通过（需 SUPER_ADMIN 或 PLATFORM_OPERATOR 角色）
     *
     * @param id 申请 ID
     * @return 更新后的申请信息
     */
    @PostMapping("/{id}/approve")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'PLATFORM_OPERATOR')")
    public Result<ApplicationResponse> approveApplication(@PathVariable Long id) {
        log.info("审核通过接口调用，申请 ID: {}", id);
        ApplicationResponse response = applicationService.approveApplication(id);
        return Result.ok(response);
    }

    /**
     * 审核拒绝（需 SUPER_ADMIN 或 PLATFORM_OPERATOR 角色）
     *
     * @param id      申请 ID
     * @param request 审核请求参数（包含拒绝原因）
     * @return 更新后的申请信息
     */
    @PostMapping("/{id}/reject")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'PLATFORM_OPERATOR')")
    public Result<ApplicationResponse> rejectApplication(
            @PathVariable Long id,
            @RequestBody ReviewApplicationRequest request) {
        log.info("审核拒绝接口调用，申请 ID: {}", id);
        ApplicationResponse response = applicationService.rejectApplication(id, request.getRejectReason());
        return Result.ok(response);
    }
}
