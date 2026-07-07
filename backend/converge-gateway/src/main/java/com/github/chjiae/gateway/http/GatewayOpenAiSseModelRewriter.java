package com.github.chjiae.gateway.http;

import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonObject;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * OpenAI Compatible SSE 顶层 model 增量重写器。
 * 以完整 SSE event 为单位解码 UTF-8，支持网络 chunk 拆分与 CRLF/LF 分隔。
 */
class GatewayOpenAiSseModelRewriter {

    /** 公开模型编码 */
    private final String publicModelCode;

    /** 单 event 最大字节数 */
    private final long maxEventBytes;

    /** 待处理 event 字节 */
    private final ByteArrayOutputStream pending = new ByteArrayOutputStream();

    /**
     * 创建 SSE 重写器。
     *
     * @param publicModelCode 公开模型编码
     * @param maxEventBytes 单 event 最大字节数
     */
    GatewayOpenAiSseModelRewriter(String publicModelCode, long maxEventBytes) {
        this.publicModelCode = publicModelCode;
        this.maxEventBytes = maxEventBytes;
    }

    /**
     * 处理上游 chunk。
     *
     * @param chunk 上游网络 chunk
     * @return 可写入下游的 event buffer
     */
    List<Buffer> handle(Buffer chunk) {
        pending.writeBytes(chunk.getBytes());
        if (pending.size() > maxEventBytes) {
            throw new GatewayOpenAiSseException("SSE event 超过大小限制");
        }
        return drainCompleteEvents();
    }

    /**
     * 结束时处理残留 event。
     *
     * @return 残留输出
     */
    List<Buffer> end() {
        if (pending.size() == 0) {
            return List.of();
        }
        byte[] eventBytes = pending.toByteArray();
        pending.reset();
        return List.of(rewriteEvent(eventBytes));
    }

    private List<Buffer> drainCompleteEvents() {
        List<Buffer> events = new ArrayList<>();
        while (true) {
            byte[] bytes = pending.toByteArray();
            Delimiter delimiter = findDelimiter(bytes);
            if (delimiter == null) {
                return events;
            }
            byte[] eventBytes = new byte[delimiter.index()];
            System.arraycopy(bytes, 0, eventBytes, 0, delimiter.index());
            byte[] remain = new byte[bytes.length - delimiter.endIndex()];
            System.arraycopy(bytes, delimiter.endIndex(), remain, 0, remain.length);
            pending.reset();
            pending.writeBytes(remain);
            events.add(rewriteEvent(eventBytes));
            if (pending.size() > maxEventBytes) {
                throw new GatewayOpenAiSseException("SSE event 超过大小限制");
            }
        }
    }

    private Delimiter findDelimiter(byte[] bytes) {
        for (int i = 0; i < bytes.length - 1; i++) {
            if (bytes[i] == '\n' && bytes[i + 1] == '\n') {
                return new Delimiter(i, i + 2);
            }
            if (i < bytes.length - 3
                    && bytes[i] == '\r'
                    && bytes[i + 1] == '\n'
                    && bytes[i + 2] == '\r'
                    && bytes[i + 3] == '\n') {
                return new Delimiter(i, i + 4);
            }
        }
        return null;
    }

    private Buffer rewriteEvent(byte[] eventBytes) {
        String event = new String(eventBytes, StandardCharsets.UTF_8);
        String[] lines = event.split("\\r?\\n", -1);
        StringBuilder rewritten = new StringBuilder();
        for (int i = 0; i < lines.length; i++) {
            if (i > 0) {
                rewritten.append('\n');
            }
            rewritten.append(rewriteLine(lines[i]));
        }
        rewritten.append("\n\n");
        return Buffer.buffer(rewritten.toString(), StandardCharsets.UTF_8.name());
    }

    private String rewriteLine(String line) {
        if (!line.startsWith("data:")) {
            return line;
        }
        String prefix = "data:";
        String data = line.substring(prefix.length());
        String leading = "";
        if (data.startsWith(" ")) {
            leading = " ";
            data = data.substring(1);
        }
        if ("[DONE]".equals(data)) {
            return line;
        }
        try {
            JsonObject json = new JsonObject(data);
            json.put("model", publicModelCode);
            return prefix + leading + json.encode();
        } catch (RuntimeException e) {
            throw new GatewayOpenAiSseException("SSE data JSON 不合法");
        }
    }

    private record Delimiter(int index, int endIndex) {
    }
}
