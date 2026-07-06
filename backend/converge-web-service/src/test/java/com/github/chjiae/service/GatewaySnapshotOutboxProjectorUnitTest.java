package com.github.chjiae.service;

import com.github.chjiae.service.config.GatewaySnapshotProperties;
import com.github.chjiae.service.entity.ai.AiGatewaySnapshotOutbox;
import com.github.chjiae.service.entity.ai.AiGatewaySnapshotRevision;
import com.github.chjiae.service.mapper.ai.AiGatewaySnapshotOutboxMapper;
import com.github.chjiae.service.mapper.ai.AiGatewaySnapshotRevisionMapper;
import com.github.chjiae.service.mapper.ai.GatewaySnapshotQueryMapper;
import com.github.chjiae.service.service.ai.AiCredentialEncryptionService;
import com.github.chjiae.service.service.ai.GatewaySnapshotOutboxProjector;
import com.github.chjiae.service.service.ai.GatewaySnapshotRedisPublisher;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 网关快照 outbox 投影器单元测试。
 */
class GatewaySnapshotOutboxProjectorUnitTest {

    @Test
    void projector_Redis发布失败时标记失败并保留重试() {
        AiGatewaySnapshotRevisionMapper revisionMapper = mock(AiGatewaySnapshotRevisionMapper.class);
        AiGatewaySnapshotOutboxMapper outboxMapper = mock(AiGatewaySnapshotOutboxMapper.class);
        GatewaySnapshotQueryMapper queryMapper = mock(GatewaySnapshotQueryMapper.class);
        AiCredentialEncryptionService encryptionService = mock(AiCredentialEncryptionService.class);
        GatewaySnapshotRedisPublisher publisher = mock(GatewaySnapshotRedisPublisher.class);
        GatewaySnapshotProperties properties = mock(GatewaySnapshotProperties.class);

        AiGatewaySnapshotOutbox outbox = new AiGatewaySnapshotOutbox();
        outbox.setId(100L);
        outbox.setTenantId(7L);
        outbox.setRevision(3L);
        outbox.setChangeType("AI_PROVIDER_CHANGED");
        outbox.setStatus("PROCESSING");
        outbox.setAttemptCount(1);
        outbox.setCreatedAt(LocalDateTime.now());

        AiGatewaySnapshotRevision revision = new AiGatewaySnapshotRevision();
        revision.setTenantId(7L);
        revision.setCurrentRevision(3L);

        when(properties.getOutboxBatchSize()).thenReturn(10);
        when(properties.getOutboxLockTimeoutMs()).thenReturn(30000L);
        when(properties.getKeyId()).thenReturn("gateway-unit-key");
        when(properties.getSigningKeyBytes()).thenReturn(filledKey((byte) 0x04));
        when(outboxMapper.claimDue(anyString(), any(LocalDateTime.class), any(LocalDateTime.class), anyInt()))
                .thenReturn(List.of(outbox));
        when(revisionMapper.selectById(7L)).thenReturn(revision);
        when(queryMapper.selectTenantStatus(7L)).thenReturn("ACTIVE");
        when(queryMapper.selectEnabledPublicModels(7L)).thenReturn(List.of());
        when(queryMapper.selectEligibleExecutionResources(7L)).thenReturn(List.of());
        org.mockito.Mockito.doThrow(new IllegalStateException("Redis 发布失败"))
                .when(publisher).publish(any());

        GatewaySnapshotOutboxProjector projector = new GatewaySnapshotOutboxProjector(
                revisionMapper, outboxMapper, queryMapper, encryptionService, publisher, properties);

        GatewaySnapshotOutboxProjector.ProjectorResult result = projector.projectPendingOnce("unit-test");

        assertThat(result.publishedTenantCount()).isZero();
        assertThat(result.failedTenantCount()).isEqualTo(1);
        verify(outboxMapper, never()).markPublished(any(), any());

        ArgumentCaptor<String> errorCaptor = ArgumentCaptor.forClass(String.class);
        verify(outboxMapper).markFailed(eq(List.of(100L)), errorCaptor.capture(),
                any(LocalDateTime.class), any(LocalDateTime.class));
        assertThat(errorCaptor.getValue()).contains("Redis 发布失败");
        assertThat(errorCaptor.getValue()).doesNotContain("secret");
    }

    private byte[] filledKey(byte value) {
        byte[] key = new byte[32];
        java.util.Arrays.fill(key, value);
        return key;
    }
}
