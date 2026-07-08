package com.github.chjiae.gateway.http;

import com.github.chjiae.contract.gateway.GatewayClientPrincipal;
import com.github.chjiae.contract.gateway.GatewaySnapshotSyncState;
import com.github.chjiae.gateway.execution.GatewayExecutionRuntime;
import com.github.chjiae.gateway.governance.GatewayRuntimeAcquireResult;
import com.github.chjiae.gateway.governance.GatewayRuntimeGovernanceRuntime;
import com.github.chjiae.gateway.governance.GatewayRuntimeLease;
import com.github.chjiae.gateway.governance.GatewayRuntimeLeaseAcquireStatus;
import com.github.chjiae.gateway.snapshot.GatewayOpenAiExecutionException;
import com.github.chjiae.gateway.snapshot.GatewayOpenAiExecutionTarget;
import com.github.chjiae.gateway.snapshot.GatewaySnapshotRuntime;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * OpenAI Chat Completions 数据面处理器。
 * 只支持 OpenAI Compatible Direct API，负责认证、请求最小校验、模型映射和执行委托。
 */
public class GatewayOpenAiChatCompletionsHandler {

    /** 快照运行时 */
    private final GatewaySnapshotRuntime snapshotRuntime;

    /** 执行运行时 */
    private final GatewayExecutionRuntime executionRuntime;

    /** 运行时治理运行时 */
    private final GatewayRuntimeGovernanceRuntime governanceRuntime;

    /** 数据面认证器 */
    private final GatewayDataPlaneAuthenticator authenticator;

    /** Direct API 执行器 */
    private final GatewayOpenAiDirectExecutor executor;

    /**
     * 创建处理器。
     *
     * @param snapshotRuntime 快照运行时
     * @param executionRuntime 执行运行时
     */
    public GatewayOpenAiChatCompletionsHandler(GatewaySnapshotRuntime snapshotRuntime,
                                               GatewayExecutionRuntime executionRuntime,
                                               GatewayRuntimeGovernanceRuntime governanceRuntime) {
        this.snapshotRuntime = snapshotRuntime;
        this.executionRuntime = executionRuntime;
        this.governanceRuntime = governanceRuntime;
        this.authenticator = new GatewayDataPlaneAuthenticator(snapshotRuntime);
        this.executor = new GatewayOpenAiDirectExecutor(executionRuntime, governanceRuntime);
    }

    /**
     * 处理 Chat Completions 请求。
     *
     * @param context 路由上下文
     */
    public void handle(RoutingContext context) {
        if (executionRuntime.isDraining()) {
            GatewayDataPlaneResponses.writeError(context, 503,
                    "gateway_shutting_down", "Gateway shutting down");
            return;
        }
        if (snapshotRuntime.status().state() == GatewaySnapshotSyncState.NOT_READY) {
            GatewayDataPlaneResponses.writeError(context, 503,
                    "gateway_not_ready", "Gateway not ready");
            return;
        }
        GatewayClientPrincipal principal = authenticator.authenticate(context);
        if (principal == null) {
            return;
        }
        if (!isJsonContentType(context.request().getHeader("Content-Type"))) {
            GatewayDataPlaneResponses.writeError(context, 415,
                    "unsupported_media_type", "Unsupported media type");
            return;
        }
        readRequestBody(context, principal);
    }

    private void readRequestBody(RoutingContext context, GatewayClientPrincipal principal) {
        Buffer body = Buffer.buffer();
        AtomicBoolean failed = new AtomicBoolean(false);
        long maxBytes = executionRuntime.config().openAiMaxRequestBytes();
        context.request().exceptionHandler(throwable -> {
            if (failed.compareAndSet(false, true)) {
                GatewayDataPlaneResponses.writeError(context, 400,
                        "invalid_request", "Invalid request");
            }
        });
        context.request().handler(chunk -> {
            if (failed.get()) {
                return;
            }
            if ((long) body.length() + chunk.length() > maxBytes) {
                failed.set(true);
                context.request().pause();
                GatewayDataPlaneResponses.writeError(context, 413,
                        "request_too_large", "Request too large");
                return;
            }
            body.appendBuffer(chunk);
        });
        context.request().endHandler(ignored -> {
            if (failed.get()) {
                return;
            }
            handleBody(context, principal, body);
        });
    }

    private void handleBody(RoutingContext context, GatewayClientPrincipal principal, Buffer body) {
        JsonObject requestJson;
        try {
            requestJson = new JsonObject(body);
        } catch (RuntimeException e) {
            GatewayDataPlaneResponses.writeError(context, 400,
                    "invalid_request", "Invalid request");
            return;
        }
        Object modelValue = requestJson.getValue("model");
        if (!(modelValue instanceof String publicModelCode) || publicModelCode.isBlank()) {
            GatewayDataPlaneResponses.writeError(context, 400,
                    "invalid_request", "Invalid request");
            return;
        }
        boolean stream = false;
        Object streamValue = requestJson.getValue("stream");
        if (streamValue != null) {
            if (!(streamValue instanceof Boolean booleanValue)) {
                GatewayDataPlaneResponses.writeError(context, 400,
                        "invalid_request", "Invalid request");
                return;
            }
            stream = booleanValue;
        }

        String requestId = RequestIdHandler.currentRequestId(context);
        boolean streamRequest = stream;
        List<GatewayOpenAiExecutionTarget> targets;
        try {
            targets = snapshotRuntime.resolveOpenAiChatExecutionCandidates(principal, publicModelCode, requestId,
                    governanceRuntime.config().maxCandidateAttempts());
        } catch (GatewayOpenAiExecutionException e) {
            GatewayDataPlaneResponses.writeError(context, e.statusCode(), e.code(), e.safeMessage());
            return;
        }
        governanceRuntime.acquire(targets)
                .onSuccess(result -> executeWithLease(context, requestJson, streamRequest, requestId, result))
                .onFailure(throwable -> GatewayDataPlaneResponses.writeError(context, 503,
                        "runtime_state_unavailable", "Runtime state unavailable"));
    }

    private void executeWithLease(RoutingContext context, JsonObject requestJson, boolean stream,
                                  String requestId, GatewayRuntimeAcquireResult result) {
        if (result.status() == GatewayRuntimeLeaseAcquireStatus.RUNTIME_STATE_UNAVAILABLE) {
            GatewayDataPlaneResponses.writeError(context, 503,
                    "runtime_state_unavailable", "Runtime state unavailable");
            return;
        }
        GatewayRuntimeLease lease = result.lease();
        if (lease == null) {
            GatewayDataPlaneResponses.writeError(context, 503,
                    "no_runtime_eligible_resource", "No runtime eligible resource");
            return;
        }
        GatewayOpenAiExecutionTarget target = lease.target();
        JsonObject upstreamJson = requestJson.copy();
        upstreamJson.put("model", target.upstreamModelName());
        Buffer upstreamBody = Buffer.buffer(upstreamJson.encode(), java.nio.charset.StandardCharsets.UTF_8.name());
        executor.execute(context, target, upstreamBody, stream, requestId, lease);
    }

    private boolean isJsonContentType(String contentType) {
        if (contentType == null || contentType.isBlank()) {
            return false;
        }
        String normalized = contentType.toLowerCase(Locale.ROOT).trim();
        return normalized.equals("application/json") || normalized.startsWith("application/json;");
    }
}
