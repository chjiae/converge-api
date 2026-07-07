# 阶段 06｜Client API Key 与授权快照协议

## 1. 下游 Client Key wire format

```text
cvg_live_<keyId>_<secret>
```

解析必须严格：

```text
prefix            = "cvg_live"
separator         = "_"
keyId             = URL-safe Base64 without padding, fixed/minimum length
secret            = URL-safe Base64 without padding, fixed/minimum length
```

不得：

```text
自动 trim 后静默接受
接受多个空 segment
接受 JWT / session / cookie
接受 URL query parameter
接受 keyId-only
把 full key 写入异常信息
```

## 2. Verifier protocol

### 输入

```text
domain = UTF-8("CONVERGE_CLIENT_KEY_V1")
keyId
keyVersion
salt
secret
```

二进制拼接必须使用明确长度前缀或固定编码，禁止字符串自然拼接歧义。

### 算法

```text
SHA-256(domain + keyId + keyVersion + salt + secret)
```

存储：

```text
algorithm = SHA-256
salt = random 16+ bytes
hash = 32 bytes
```

验证：

```text
computedHash = SHA-256(...)
MessageDigest.isEqual(computedHash, storedHash)
```

安全前提：

```text
secret is generated server-side with SecureRandom and >= 256 bits
client cannot choose secret
raw secret is never persisted
```

## 3. V3 snapshot additions

`GatewayTenantSnapshot` V3:

```text
accessGroups[]
accessGroupModelGrants[]
clientApiKeys[]
clientApiKeyAccessGroups[]
```

`GatewayClientApiKeySnapshot` must contain:

```text
tenantId
clientApiKeyId
keyId
adminStatus
secretHashAlgorithm
secretVerifierSaltBase64
secretVerifierHashBase64
keyVersion
expiresAtEpochMillis
accessGroupIds
```

It must not contain:

```text
rawKey
secret
provider credential
route internal ids beyond normal tenant snapshot data
Redis key
manifest HMAC
```

## 4. Authentication and authorization

```text
Authorization: Bearer cvg_live_<keyId>_<secret>
        ↓
GatewayClientKeyExtractor
        ↓
GatewayClientKeyVerifier
        ↓
GatewayClientPrincipal(tenantId, clientApiKeyId, accessGroupIds)
        ↓
GatewayModelAuthorizer
        ↓
GET /v1/models
```

The router context holds `GatewayClientPrincipal`, never raw key.

## 5. Error contract

```json
{
  "error": {
    "message": "Invalid API key",
    "type": "invalid_request_error",
    "param": null,
    "code": "invalid_api_key"
  }
}
```

```text
401 invalid_api_key:
- missing Authorization
- malformed bearer format
- unknown keyId
- bad verifier
- disabled/revoked/expired key

403 access_denied:
- valid Client Key but no requested model/operation grant

503 gateway_not_ready:
- initial snapshot never completed
- client-key index unavailable
- snapshot max staleness exceeded
```

Do not distinguish existence/expiry/revocation in 401 response.

## 6. `GET /v1/models`

```text
GET /v1/models
Authorization: Bearer <client-key>
```

Return only models meeting both:

```text
effective key grant exists for at least one operation
AND
a valid static route plan exists for that model+operation in key tenant
```

Response:

```json
{
  "object": "list",
  "data": [
    {
      "id": "example-model",
      "object": "model",
      "created": 0,
      "owned_by": "converge"
    }
  ]
}
```

The model endpoint is inventory/authentication only:

```text
no upstream request
no pricing
no usage ledger
no retry
no SSE
```
