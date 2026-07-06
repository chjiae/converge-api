package com.github.chjiae.service.service.ai;

import com.github.chjiae.contract.gateway.GatewaySnapshotJson;
import com.github.chjiae.contract.gateway.GatewaySnapshotRedisKeys;
import com.github.chjiae.service.config.GatewaySnapshotProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Set;

/**
 * 网关快照 Redis 发布器。
 * 按 payload → manifest → tenant index → history → Pub/Sub 的顺序发布，
 * Redis 中只保存含 envelope 密文的快照，不保存 API Key 明文。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GatewaySnapshotRedisPublisher {

    /** Redis 字符串模板 */
    private final StringRedisTemplate redisTemplate;

    /** 快照配置 */
    private final GatewaySnapshotProperties properties;

    /**
     * 发布租户快照。
     *
     * @param publication 发布包
     */
    public void publish(GatewaySnapshotPublication publication) {
        Boolean inserted = redisTemplate.opsForValue()
                .setIfAbsent(publication.payloadRedisKey(), publication.payloadJson());
        if (Boolean.FALSE.equals(inserted)) {
            String existing = redisTemplate.opsForValue().get(publication.payloadRedisKey());
            if (!publication.payloadJson().equals(existing)) {
                throw new IllegalStateException("不可变网关快照 payload 已存在且内容不一致");
            }
        }

        redisTemplate.opsForValue().set(GatewaySnapshotRedisKeys.currentManifestKey(publication.tenantId()),
                GatewaySnapshotJson.toJson(publication.manifest()));
        redisTemplate.opsForSet().add(GatewaySnapshotRedisKeys.tenantIndexKey(), publication.tenantId());
        redisTemplate.opsForZSet().add(GatewaySnapshotRedisKeys.historyKey(publication.tenantId()),
                publication.payloadRedisKey(), publication.revision());
        pruneHistory(publication.tenantId(), publication.payloadRedisKey());
        redisTemplate.convertAndSend(GatewaySnapshotRedisKeys.changedChannel(),
                GatewaySnapshotJson.toJson(publication.changedEvent()));

        log.info("AI 网关快照已写入 Redis，租户 ID: {}，revision: {}", publication.tenantId(), publication.revision());
    }

    /**
     * 清理旧历史版本，绝不删除 current 指向的 payload。
     *
     * @param tenantId 租户 ID
     * @param currentPayloadKey 当前 payload key
     */
    private void pruneHistory(String tenantId, String currentPayloadKey) {
        String historyKey = GatewaySnapshotRedisKeys.historyKey(tenantId);
        Long size = redisTemplate.opsForZSet().zCard(historyKey);
        if (size == null || size <= properties.getHistoryRetainCount()) {
            return;
        }
        long removeEnd = size - properties.getHistoryRetainCount() - 1;
        Set<String> stalePayloadKeys = redisTemplate.opsForZSet().range(historyKey, 0, removeEnd);
        if (stalePayloadKeys == null || stalePayloadKeys.isEmpty()) {
            return;
        }
        for (String payloadKey : stalePayloadKeys) {
            if (!currentPayloadKey.equals(payloadKey)) {
                redisTemplate.delete(payloadKey);
                redisTemplate.opsForZSet().remove(historyKey, payloadKey);
            }
        }
    }
}
