package com.github.chjiae.gateway.http;

import com.github.chjiae.gateway.execution.GatewayExecutionRuntime;
import com.github.chjiae.gateway.governance.GatewayRuntimeGovernanceRuntime;
import com.github.chjiae.gateway.governance.GatewayRuntimeLease;
import com.github.chjiae.gateway.governance.GatewayRuntimeLeaseOutcome;
import com.github.chjiae.gateway.snapshot.GatewayOpenAiExecutionTarget;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpClientResponse;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.RequestOptions;
import io.vertx.ext.web.RoutingContext;

import java.net.URI;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * OpenAI Compatible Direct API 执行器。
 * 负责构造上游请求、注入 Bearer secret、处理非流式 JSON 与 SSE 响应。
 */
class GatewayOpenAiDirectExecutor {

    /** 执行运行时 */
    private final GatewayExecutionRuntime executionRuntime;

    /** 运行时治理运行时 */
    private final GatewayRuntimeGovernanceRuntime governanceRuntime;

    /**
     * 创建执行器。
     *
     * @param executionRuntime 执行运行时
     */
    GatewayOpenAiDirectExecutor(GatewayExecutionRuntime executionRuntime,
                                GatewayRuntimeGovernanceRuntime governanceRuntime) {
        this.executionRuntime = executionRuntime;
        this.governanceRuntime = governanceRuntime;
    }

    /**
     * 执行上游 OpenAI Chat Completions。
     *
     * @param context 路由上下文
     * @param target 执行目标
     * @param upstreamBody 已替换 model 的上游请求体
     * @param stream 是否流式
     * @param requestId 请求 ID
     */
    void execute(RoutingContext context, GatewayOpenAiExecutionTarget target,
                 Buffer upstreamBody, boolean stream, String requestId,
                 GatewayRuntimeLease lease) {
        URI upstreamUri = target.chatCompletionsUri();
        AtomicBoolean leaseCompleted = new AtomicBoolean(false);
        RequestOptions options = new RequestOptions()
                .setMethod(HttpMethod.POST)
                .setAbsoluteURI(upstreamUri.toString())
                .setFollowRedirects(false)
                .setConnectTimeout(executionRuntime.config().upstreamConnectTimeoutMs())
                .setTimeout(executionRuntime.config().upstreamIdleTimeoutMs())
                .setIdleTimeout(executionRuntime.config().upstreamIdleTimeoutMs());
        executionRuntime.httpClient()
                .request(options)
                .onSuccess(request -> sendUpstreamRequest(context, request, target, upstreamBody,
                        stream, requestId, lease, leaseCompleted))
                .onFailure(throwable -> writeTransportFailure(context, throwable, lease, leaseCompleted));
    }

    private void sendUpstreamRequest(RoutingContext context, HttpClientRequest request,
                                     GatewayOpenAiExecutionTarget target, Buffer upstreamBody,
                                     boolean stream, String requestId,
                                     GatewayRuntimeLease lease, AtomicBoolean leaseCompleted) {
        request.putHeader("Authorization", "Bearer " + target.runtimeSecret());
        request.putHeader("Content-Type", GatewayDataPlaneResponses.JSON_CONTENT_TYPE);
        request.putHeader("Accept", stream ? "text/event-stream" : "application/json");
        request.putHeader("User-Agent", executionRuntime.userAgent());
        request.putHeader(RequestIdHandler.REQUEST_ID_HEADER, requestId);
        request.idleTimeout(executionRuntime.config().upstreamIdleTimeoutMs());
        request.send(upstreamBody)
                .onSuccess(response -> {
                    if (stream) {
                        handleStreamResponse(context, request, response, target, lease, leaseCompleted);
                    } else {
                        handleNonStreamResponse(context, request, response, target, lease, leaseCompleted);
                    }
                })
                .onFailure(throwable -> writeTransportFailure(context, throwable, lease, leaseCompleted));
    }

    private void handleNonStreamResponse(RoutingContext context, HttpClientRequest request,
                                         HttpClientResponse response, GatewayOpenAiExecutionTarget target,
                                         GatewayRuntimeLease lease, AtomicBoolean leaseCompleted) {
        if (!isSuccess(response.statusCode())) {
            handleUpstreamError(context, request, response, lease, leaseCompleted);
            return;
        }
        if (!isJson(response.getHeader("Content-Type"))) {
            request.cancel();
            completeLease(lease, leaseCompleted, GatewayRuntimeLeaseOutcome.UPSTREAM_PROTOCOL_FAILURE);
            GatewayDataPlaneResponses.writeError(context, 502,
                    "upstream_protocol_error", "Upstream protocol error");
            return;
        }
        readLimited(response, request, executionRuntime.config().openAiMaxNonStreamResponseBytes())
                .onSuccess(body -> writeNonStreamSuccess(context, body, target.publicModelCode(),
                        lease, leaseCompleted))
                .onFailure(throwable -> {
                    completeLease(lease, leaseCompleted, GatewayRuntimeLeaseOutcome.UPSTREAM_PROTOCOL_FAILURE);
                    GatewayDataPlaneResponses.writeError(context, 502,
                            "upstream_protocol_error", "Upstream protocol error");
                });
    }

    private void writeNonStreamSuccess(RoutingContext context, Buffer body, String publicModelCode,
                                       GatewayRuntimeLease lease, AtomicBoolean leaseCompleted) {
        try {
            io.vertx.core.json.JsonObject json = new io.vertx.core.json.JsonObject(body);
            json.put("model", publicModelCode);
            completeLease(lease, leaseCompleted, GatewayRuntimeLeaseOutcome.SUCCESS);
            context.response()
                    .setStatusCode(200)
                    .putHeader("content-type", GatewayDataPlaneResponses.JSON_CONTENT_TYPE)
                    .end(json.encode());
        } catch (RuntimeException e) {
            completeLease(lease, leaseCompleted, GatewayRuntimeLeaseOutcome.UPSTREAM_PROTOCOL_FAILURE);
            GatewayDataPlaneResponses.writeError(context, 502,
                    "upstream_protocol_error", "Upstream protocol error");
        }
    }

    private void handleStreamResponse(RoutingContext context, HttpClientRequest request,
                                      HttpClientResponse response, GatewayOpenAiExecutionTarget target,
                                      GatewayRuntimeLease lease, AtomicBoolean leaseCompleted) {
        if (!isSuccess(response.statusCode())) {
            handleUpstreamError(context, request, response, lease, leaseCompleted);
            return;
        }
        if (!isEventStream(response.getHeader("Content-Type"))) {
            request.cancel();
            completeLease(lease, leaseCompleted, GatewayRuntimeLeaseOutcome.UPSTREAM_PROTOCOL_FAILURE);
            GatewayDataPlaneResponses.writeError(context, 502,
                    "upstream_protocol_error", "Upstream protocol error");
            return;
        }
        GatewayOpenAiSseModelRewriter rewriter = new GatewayOpenAiSseModelRewriter(target.publicModelCode(),
                executionRuntime.config().openAiMaxSseEventBytes());
        AtomicBoolean closed = new AtomicBoolean(false);
        long renewTimerId = governanceRuntime.startRenewing(lease, () -> {
            request.cancel();
            safeEndStream(context, closed);
            completeLease(lease, leaseCompleted, GatewayRuntimeLeaseOutcome.LEASE_LOST);
        });
        context.response()
                .setStatusCode(200)
                .setChunked(true)
                .putHeader("content-type", "text/event-stream; charset=utf-8")
                .putHeader("cache-control", "no-cache")
                .putHeader("x-accel-buffering", "no")
                .closeHandler(ignored -> {
                    closed.set(true);
                    request.cancel();
                    governanceRuntime.cancelRenewing(renewTimerId);
                    completeLease(lease, leaseCompleted, GatewayRuntimeLeaseOutcome.CLIENT_CANCELLED);
                });
        response.exceptionHandler(throwable -> {
            governanceRuntime.cancelRenewing(renewTimerId);
            completeLease(lease, leaseCompleted, GatewayRuntimeLeaseOutcome.UPSTREAM_PROTOCOL_FAILURE);
            safeEndStream(context, closed);
        });
        response.handler(chunk -> {
            if (closed.get()) {
                return;
            }
            try {
                for (Buffer output : rewriter.handle(chunk)) {
                    context.response().write(output);
                }
                applyBackpressure(context, response);
            } catch (GatewayOpenAiSseException e) {
                request.cancel();
                governanceRuntime.cancelRenewing(renewTimerId);
                completeLease(lease, leaseCompleted, GatewayRuntimeLeaseOutcome.UPSTREAM_PROTOCOL_FAILURE);
                safeEndStream(context, closed);
            }
        });
        response.endHandler(ignored -> {
            if (closed.get()) {
                return;
            }
            try {
                for (Buffer output : rewriter.end()) {
                    context.response().write(output);
                }
            } catch (GatewayOpenAiSseException e) {
                request.cancel();
                governanceRuntime.cancelRenewing(renewTimerId);
                completeLease(lease, leaseCompleted, GatewayRuntimeLeaseOutcome.UPSTREAM_PROTOCOL_FAILURE);
                safeEndStream(context, closed);
                return;
            }
            governanceRuntime.cancelRenewing(renewTimerId);
            completeLease(lease, leaseCompleted, GatewayRuntimeLeaseOutcome.SUCCESS);
            safeEndStream(context, closed);
        });
    }

    private void applyBackpressure(RoutingContext context, HttpClientResponse response) {
        if (context.response().writeQueueFull()) {
            response.pause();
            context.response().drainHandler(ignored -> response.resume());
        }
    }

    private void safeEndStream(RoutingContext context, AtomicBoolean closed) {
        if (closed.compareAndSet(false, true) && !context.response().ended()) {
            context.response().end();
        }
    }

    private void handleUpstreamError(RoutingContext context, HttpClientRequest request, HttpClientResponse response,
                                     GatewayRuntimeLease lease, AtomicBoolean leaseCompleted) {
        readLimited(response, request, executionRuntime.config().openAiMaxErrorResponseBytes())
                .onComplete(ignored -> {
                    UpstreamError mapped = mapUpstreamStatus(response.statusCode());
                    completeLease(lease, leaseCompleted, mapped.outcome());
                    GatewayDataPlaneResponses.writeError(context, mapped.statusCode(), mapped.code(), mapped.message());
                });
    }

    private Future<Buffer> readLimited(HttpClientResponse response, HttpClientRequest request, long maxBytes) {
        Promise<Buffer> promise = Promise.promise();
        Buffer buffer = Buffer.buffer();
        AtomicBoolean completed = new AtomicBoolean(false);
        response.exceptionHandler(throwable -> {
            if (completed.compareAndSet(false, true)) {
                promise.fail(throwable);
            }
        });
        response.handler(chunk -> {
            if (completed.get()) {
                return;
            }
            if ((long) buffer.length() + chunk.length() > maxBytes) {
                request.cancel();
                if (completed.compareAndSet(false, true)) {
                    promise.fail(new IllegalStateException("响应体超过大小限制"));
                }
                return;
            }
            buffer.appendBuffer(chunk);
        });
        response.endHandler(ignored -> {
            if (completed.compareAndSet(false, true)) {
                promise.complete(buffer);
            }
        });
        return promise.future();
    }

    private void writeTransportFailure(RoutingContext context, Throwable throwable,
                                       GatewayRuntimeLease lease, AtomicBoolean leaseCompleted) {
        String message = throwable == null ? "" : String.valueOf(throwable.getMessage()).toLowerCase(Locale.ROOT);
        if (message.contains("timeout")) {
            completeLease(lease, leaseCompleted, GatewayRuntimeLeaseOutcome.UPSTREAM_TIMEOUT);
            GatewayDataPlaneResponses.writeError(context, 504,
                    "upstream_timeout", "Upstream timeout");
            return;
        }
        completeLease(lease, leaseCompleted, GatewayRuntimeLeaseOutcome.UPSTREAM_CONNECTION_FAILURE);
        GatewayDataPlaneResponses.writeError(context, 502,
                "upstream_connection_error", "Upstream connection error");
    }

    private UpstreamError mapUpstreamStatus(int statusCode) {
        if (statusCode == 401 || statusCode == 403) {
            return new UpstreamError(502, "upstream_authentication_failed",
                    "Upstream authentication failed", GatewayRuntimeLeaseOutcome.UPSTREAM_AUTH_FAILURE);
        }
        if (statusCode == 429) {
            return new UpstreamError(429, "upstream_rate_limited",
                    "Upstream rate limited", GatewayRuntimeLeaseOutcome.UPSTREAM_RATE_LIMITED);
        }
        if (statusCode == 400 || statusCode == 404 || statusCode == 409 || statusCode == 422) {
            return new UpstreamError(400, "upstream_rejected_request",
                    "Upstream rejected request", GatewayRuntimeLeaseOutcome.REACHABLE_CLIENT_REJECTION);
        }
        if (statusCode >= 500) {
            return new UpstreamError(502, "upstream_server_error",
                    "Upstream server error", GatewayRuntimeLeaseOutcome.UPSTREAM_SERVER_FAILURE);
        }
        return new UpstreamError(502, "upstream_protocol_error",
                "Upstream protocol error", GatewayRuntimeLeaseOutcome.UPSTREAM_PROTOCOL_FAILURE);
    }

    private void completeLease(GatewayRuntimeLease lease, AtomicBoolean completed,
                               GatewayRuntimeLeaseOutcome outcome) {
        if (completed.compareAndSet(false, true)) {
            governanceRuntime.complete(lease, outcome);
        }
    }

    private boolean isSuccess(int statusCode) {
        return statusCode >= 200 && statusCode < 300;
    }

    private boolean isJson(String contentType) {
        return contentType != null && contentType.toLowerCase(Locale.ROOT).startsWith("application/json");
    }

    private boolean isEventStream(String contentType) {
        return contentType != null && contentType.toLowerCase(Locale.ROOT).startsWith("text/event-stream");
    }

    private record UpstreamError(int statusCode, String code, String message,
                                 GatewayRuntimeLeaseOutcome outcome) {
    }
}
