package com.github.chjiae.service.controller.ai;

import com.github.chjiae.common.result.Result;
import com.github.chjiae.service.dto.ai.AiGatewayChatTestRequest;
import com.github.chjiae.service.dto.ai.AiGatewayChatTestResponse;
import com.github.chjiae.service.dto.ai.AiOptionsResponse;
import com.github.chjiae.service.service.ai.AiAdminConsoleOptionsService;
import com.github.chjiae.service.service.ai.AiGatewayAdminProxyService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * AI Gateway 管理控制台支撑接口。
 * 仅允许租户所有者和租户管理员访问，不记录密钥、Authorization 或测试消息正文。
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/ai")
@PreAuthorize("hasAnyRole('TENANT_OWNER', 'TENANT_ADMIN')")
@RequiredArgsConstructor
public class AiAdminConsoleController {

    /** 枚举选项服务。 */
    private final AiAdminConsoleOptionsService optionsService;

    /** Gateway 管理代理服务。 */
    private final AiGatewayAdminProxyService gatewayAdminProxyService;

    /**
     * 获取 AI 管理台枚举选项。
     *
     * @return 枚举选项集合
     */
    @GetMapping("/options")
    public Result<AiOptionsResponse> options() {
        log.info("AI 管理台枚举选项接口调用");
        return Result.ok(optionsService.getOptions());
    }

    /**
     * 查询 Gateway ready 状态。
     *
     * @return 安全 ready 状态
     */
    @GetMapping("/gateway/ready")
    public Result<Map<String, Object>> gatewayReady() {
        log.info("AI Gateway ready 状态代理接口调用");
        return Result.ok(gatewayAdminProxyService.ready());
    }

    /**
     * 查询 Gateway snapshot 状态。
     *
     * @return 安全 snapshot 状态
     */
    @GetMapping("/gateway/snapshot-status")
    public Result<Map<String, Object>> gatewaySnapshotStatus() {
        log.info("AI Gateway snapshot 状态代理接口调用");
        return Result.ok(gatewayAdminProxyService.snapshotStatus());
    }

    /**
     * 查询 Gateway runtime 状态。
     *
     * @return 安全 runtime 状态
     */
    @GetMapping("/gateway/runtime-status")
    public Result<Map<String, Object>> gatewayRuntimeStatus() {
        log.info("AI Gateway runtime 状态代理接口调用");
        return Result.ok(gatewayAdminProxyService.runtimeStatus());
    }

    /**
     * 发起 Chat Completions 非流式测试。
     *
     * @param request 测试请求
     * @return 测试代理响应
     */
    @PostMapping("/gateway/test-chat-completions")
    public Result<AiGatewayChatTestResponse> testChatCompletions(
            @Valid @RequestBody AiGatewayChatTestRequest request) {
        log.info("AI Gateway Chat Completions 测试代理接口调用，模型: {}", request.getModel());
        return Result.ok(gatewayAdminProxyService.testChatCompletions(request));
    }
}
