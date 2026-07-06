package com.github.chjiae.service.service.ai;

import lombok.Data;

/**
 * 网关快照执行资源查询行。
 * 包含构建运行时快照所需的非敏感元数据和数据库密文字段。
 */
@Data
public class GatewaySnapshotExecutionResourceRow {

    /** 租户 ID */
    private Long tenantId;

    /** 执行资源 ID */
    private Long resourceId;

    /** Provider ID */
    private Long providerId;

    /** Connection ID */
    private Long connectionId;

    /** Credential ID */
    private Long credentialId;

    /** 资源类型 */
    private String resourceType;

    /** 资源管理状态 */
    private String adminStatus;

    /** Provider 类型 */
    private String providerKind;

    /** 协议类型 */
    private String protocolType;

    /** 规范化 Base URL */
    private String baseUrl;

    /** 凭据类型 */
    private String credentialType;

    /** 服务端凭据引用 */
    private String secretReference;

    /** 数据库密文 */
    private byte[] encryptedSecret;

    /** 数据库 AES-GCM Nonce */
    private byte[] nonce;
}
