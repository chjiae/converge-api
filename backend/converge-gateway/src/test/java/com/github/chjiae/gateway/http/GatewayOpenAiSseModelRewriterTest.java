package com.github.chjiae.gateway.http;

import io.vertx.core.buffer.Buffer;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * OpenAI SSE model 增量重写器测试。
 */
class GatewayOpenAiSseModelRewriterTest {

    @Test
    void handle_支持多ChunkUtf8和Done透传() {
        GatewayOpenAiSseModelRewriter rewriter = new GatewayOpenAiSseModelRewriter("public-chat", 4096);

        List<Buffer> first = rewriter.handle(Buffer.buffer("event: message\n"
                + "data: {\"model\":\"up", StandardCharsets.UTF_8.name()));
        List<Buffer> second = rewriter.handle(Buffer.buffer("stream-chat\",\"choices\":[{\"delta\":{\"content\":\"你\"}}]}\n\n"
                + "data: [DONE]\r\n\r\n", StandardCharsets.UTF_8.name()));

        String output = second.get(0).toString(StandardCharsets.UTF_8)
                + second.get(1).toString(StandardCharsets.UTF_8);

        assertTrue(first.isEmpty());
        assertTrue(output.contains("event: message"));
        assertTrue(output.contains("\"model\":\"public-chat\""));
        assertTrue(output.contains("data: [DONE]"));
        assertFalse(output.contains("upstream-chat"));
    }

    @Test
    void handle_单Event超过上限时拒绝() {
        GatewayOpenAiSseModelRewriter rewriter = new GatewayOpenAiSseModelRewriter("public-chat", 16);

        assertThrows(GatewayOpenAiSseException.class,
                () -> rewriter.handle(Buffer.buffer("data: {\"model\":\"too-long\"}", StandardCharsets.UTF_8.name())));
    }
}
