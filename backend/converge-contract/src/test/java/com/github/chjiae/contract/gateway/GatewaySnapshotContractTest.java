package com.github.chjiae.contract.gateway;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 网关快照契约测试。
 * 覆盖 JSON 兼容、Manifest 签名、秘密 envelope AAD 绑定和契约模块依赖边界。
 */
class GatewaySnapshotContractTest {

    @Test
    void contract_快照Manifest和秘密Envelope可序列化并验证() {
        byte[] encryptionKey = filledKey((byte) 0x03);
        byte[] signingKey = filledKey((byte) 0x04);
        GatewaySecretEnvelope envelope = GatewaySnapshotCrypto.encryptSecret("contract-secret",
                encryptionKey,
                GatewaySnapshotCrypto.secretAad(GatewaySnapshotSchema.CURRENT_VERSION,
                        "tenant-1", "resource-1", "credential-1", 7, "gateway-key"),
                "gateway-key");
        GatewayTenantSnapshot snapshot = new GatewayTenantSnapshot(GatewaySnapshotSchema.CURRENT_VERSION,
                "tenant-1", 7, 1000L,
                List.of(new GatewayPublicModelSnapshot("tenant-1", "model-1", "gpt-public", "GPT", "gpt")),
                List.of(new GatewayExecutionResourceSnapshot("tenant-1", "resource-1", "provider-1",
                        "connection-1", "credential-1", "DIRECT_API", "ENABLED", "OPENAI",
                        "OPENAI_COMPATIBLE", "https://api.example.test/v1/", envelope)));

        byte[] payload = GatewaySnapshotJson.toBytes(snapshot);
        GatewaySnapshotManifest unsigned = new GatewaySnapshotManifest(GatewaySnapshotSchema.CURRENT_VERSION,
                "tenant-1", 7, GatewaySnapshotRedisKeys.payloadKey("tenant-1", 7),
                GatewaySnapshotCrypto.sha256Hex(payload), "", "gateway-key", 1001L);
        GatewaySnapshotManifest signed = new GatewaySnapshotManifest(unsigned.schemaVersion(), unsigned.tenantId(),
                unsigned.revision(), unsigned.payloadRedisKey(), unsigned.payloadSha256Hex(),
                GatewaySnapshotCrypto.signManifest(unsigned, signingKey), unsigned.gatewayKeyId(),
                unsigned.publishedAtEpochMillis());

        GatewaySnapshotManifest parsedManifest = GatewaySnapshotJson.fromJson(
                GatewaySnapshotJson.toJson(signed), GatewaySnapshotManifest.class);
        GatewayTenantSnapshot parsedSnapshot = GatewaySnapshotJson.fromBytes(payload, GatewayTenantSnapshot.class);

        assertThat(parsedManifest.tenantId()).isEqualTo("tenant-1");
        assertThat(parsedSnapshot.executionResources()).hasSize(1);
        assertThat(GatewaySnapshotCrypto.verifyManifest(parsedManifest, signingKey)).isTrue();
        assertThat(GatewaySnapshotCrypto.decryptSecret(envelope, encryptionKey,
                GatewaySnapshotCrypto.secretAad(GatewaySnapshotSchema.CURRENT_VERSION,
                        "tenant-1", "resource-1", "credential-1", 7, "gateway-key"),
                "gateway-key")).isEqualTo("contract-secret");
    }

    @Test
    void contract_ClientKeyVerifier和V3快照可跨模块序列化() {
        GatewayClientKeyCrypto.GeneratedClientKey generated = GatewayClientKeyCrypto.generate();
        byte[] salt = GatewayClientKeyCrypto.generateSalt();
        byte[] verifier = GatewayClientKeyCrypto.verifier(generated.rawKey(), generated.keyId(), 1, salt);

        assertThat(generated.rawKey()).startsWith(GatewayClientKeyCrypto.RAW_KEY_PREFIX);
        assertThat(GatewayClientKeyCrypto.parse(generated.rawKey()).valid()).isTrue();
        assertThat(verifier).hasSize(GatewayClientKeyCrypto.HASH_BYTES);
        assertThat(GatewayClientKeyCrypto.verify(generated.rawKey(), generated.keyId(), 1, salt, verifier)).isTrue();

        GatewayClientKeyCrypto.GeneratedClientKey rotated = GatewayClientKeyCrypto.generateForKeyId(generated.keyId());
        byte[] rotatedSalt = GatewayClientKeyCrypto.generateSalt();
        byte[] rotatedVerifier = GatewayClientKeyCrypto.verifier(rotated.rawKey(), generated.keyId(), 2, rotatedSalt);
        assertThat(GatewayClientKeyCrypto.verify(generated.rawKey(), generated.keyId(), 2,
                rotatedSalt, rotatedVerifier)).isFalse();
        assertThat(GatewayClientKeyCrypto.verify(rotated.rawKey(), generated.keyId(), 2,
                rotatedSalt, rotatedVerifier)).isTrue();

        GatewayTenantSnapshot snapshot = new GatewayTenantSnapshot(GatewaySnapshotSchema.VERSION_3,
                "tenant-1", 8, 1000L,
                List.of(new GatewayPublicModelSnapshot("tenant-1", "model-1", "gpt-public", "GPT", "gpt")),
                List.of(), List.of(), List.of(), List.of(),
                List.of(new GatewayAccessGroupSnapshot("tenant-1", "group-1", "default", "ENABLED")),
                List.of(new GatewayAccessGroupModelGrantSnapshot("tenant-1", "grant-1", "group-1",
                        "model-1", "gpt-public", "CHAT_COMPLETIONS", "ENABLED")),
                List.of(new GatewayClientApiKeySnapshot("tenant-1", "key-1", generated.keyId(), "ENABLED",
                        GatewayClientKeyCrypto.HASH_ALGORITHM,
                        Base64.getEncoder().encodeToString(salt),
                        Base64.getEncoder().encodeToString(verifier),
                        1, 0L)),
                List.of(new GatewayClientApiKeyAccessGroupSnapshot("tenant-1", "binding-1", "key-1",
                        "group-1", "ENABLED")));

        String json = GatewaySnapshotJson.toJson(snapshot);
        GatewayTenantSnapshot parsed = GatewaySnapshotJson.fromJson(json, GatewayTenantSnapshot.class);

        assertThat(json).doesNotContain(generated.rawKey());
        assertThat(parsed.schemaVersion()).isEqualTo(GatewaySnapshotSchema.VERSION_3);
        assertThat(parsed.clientApiKeys()).hasSize(1);
        assertThat(parsed.accessGroupModelGrants()).hasSize(1);
    }

    @Test
    void contract_不依赖禁止的服务框架和存储客户端() throws Exception {
        String pom = Files.readString(Path.of("pom.xml"));

        assertThat(pom)
                .doesNotContain("spring-boot")
                .doesNotContain("vertx")
                .doesNotContain("redis")
                .doesNotContain("mybatis")
                .doesNotContain("flyway")
                .doesNotContain("postgresql")
                .doesNotContain("servlet");
    }

    private byte[] filledKey(byte value) {
        byte[] key = new byte[32];
        java.util.Arrays.fill(key, value);
        return Base64.getDecoder().decode(Base64.getEncoder().encodeToString(key));
    }
}
