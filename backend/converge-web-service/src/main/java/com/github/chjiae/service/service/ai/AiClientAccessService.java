package com.github.chjiae.service.service.ai;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.github.chjiae.common.enums.AiCanonicalOperation;
import com.github.chjiae.common.enums.AiCatalogStatus;
import com.github.chjiae.common.enums.AiClientApiKeyStatus;
import com.github.chjiae.common.exception.BusinessException;
import com.github.chjiae.common.result.PageResult;
import com.github.chjiae.contract.gateway.GatewayClientKeyCrypto;
import com.github.chjiae.service.dto.ai.AiAccessGroupCreateRequest;
import com.github.chjiae.service.dto.ai.AiAccessGroupModelGrantCreateRequest;
import com.github.chjiae.service.dto.ai.AiAccessGroupModelGrantResponse;
import com.github.chjiae.service.dto.ai.AiAccessGroupModelGrantUpdateRequest;
import com.github.chjiae.service.dto.ai.AiAccessGroupResponse;
import com.github.chjiae.service.dto.ai.AiAccessGroupUpdateRequest;
import com.github.chjiae.service.dto.ai.AiClientApiKeyAccessGroupCreateRequest;
import com.github.chjiae.service.dto.ai.AiClientApiKeyAccessGroupResponse;
import com.github.chjiae.service.dto.ai.AiClientApiKeyAccessGroupUpdateRequest;
import com.github.chjiae.service.dto.ai.AiClientApiKeyCreateRequest;
import com.github.chjiae.service.dto.ai.AiClientApiKeyResponse;
import com.github.chjiae.service.dto.ai.AiClientApiKeySecretResponse;
import com.github.chjiae.service.dto.ai.AiClientApiKeyUpdateRequest;
import com.github.chjiae.service.entity.ai.AiAccessGroup;
import com.github.chjiae.service.entity.ai.AiAccessGroupModelGrant;
import com.github.chjiae.service.entity.ai.AiClientApiKey;
import com.github.chjiae.service.entity.ai.AiClientApiKeyAccessGroup;
import com.github.chjiae.service.entity.ai.AiPublicModel;
import com.github.chjiae.service.mapper.ai.AiAccessGroupMapper;
import com.github.chjiae.service.mapper.ai.AiAccessGroupModelGrantMapper;
import com.github.chjiae.service.mapper.ai.AiClientApiKeyAccessGroupMapper;
import com.github.chjiae.service.mapper.ai.AiClientApiKeyMapper;
import com.github.chjiae.service.mapper.ai.AiPublicModelMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Locale;

/**
 * AI 下游 Client API Key 与访问组控制面服务。
 * 所有写操作均在当前事务内记录网关快照变更，供既有 outbox/projector 发布 V3 快照。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiClientAccessService {

    /** 单页最大数量 */
    private static final int MAX_PAGE_SIZE = 100;

    /** 访问组 Mapper */
    private final AiAccessGroupMapper accessGroupMapper;

    /** 访问组模型授权 Mapper */
    private final AiAccessGroupModelGrantMapper modelGrantMapper;

    /** Client API Key Mapper */
    private final AiClientApiKeyMapper clientApiKeyMapper;

    /** Client API Key 访问组绑定 Mapper */
    private final AiClientApiKeyAccessGroupMapper keyAccessGroupMapper;

    /** 公开模型 Mapper */
    private final AiPublicModelMapper publicModelMapper;

    /** 租户守卫 */
    private final AiCatalogTenantGuard tenantGuard;

    /** 快照变更记录器 */
    private final GatewaySnapshotChangeRecorder snapshotChangeRecorder;

    /**
     * 创建访问组。
     *
     * @param request 创建请求
     * @return 访问组响应
     */
    @Transactional(rollbackFor = Exception.class)
    public AiAccessGroupResponse createAccessGroup(AiAccessGroupCreateRequest request) {
        Long tenantId = tenantGuard.requireCurrentTenantId();
        ensureAccessGroupCodeAvailable(tenantId, request.getCode(), null);
        LocalDateTime now = LocalDateTime.now();
        AiAccessGroup group = new AiAccessGroup();
        group.setTenantId(tenantId);
        group.setCode(request.getCode());
        group.setDisplayName(request.getDisplayName());
        group.setDescription(request.getDescription());
        group.setAdminStatus(request.getAdminStatus());
        group.setCreatedAt(now);
        group.setUpdatedAt(now);
        accessGroupMapper.insert(group);
        snapshotChangeRecorder.recordChange(tenantId, GatewaySnapshotChangeTypes.AI_ACCESS_GROUP_CHANGED);
        log.info("AI 访问组创建成功，租户 ID: {}，访问组 ID: {}", tenantId, group.getId());
        return toAccessGroupResponse(group);
    }

    /**
     * 分页查询访问组。
     *
     * @param page 页码
     * @param size 每页数量
     * @param keyword 关键字
     * @param status 状态过滤
     * @return 分页结果
     */
    public PageResult<AiAccessGroupResponse> listAccessGroups(int page, int size,
                                                              String keyword, AiCatalogStatus status) {
        Long tenantId = tenantGuard.requireCurrentTenantId();
        Page<AiAccessGroup> pageParam = new Page<>(normalizePage(page), normalizeSize(size));
        LambdaQueryWrapper<AiAccessGroup> query = new LambdaQueryWrapper<AiAccessGroup>()
                .eq(AiAccessGroup::getTenantId, tenantId)
                .orderByDesc(AiAccessGroup::getCreatedAt)
                .orderByDesc(AiAccessGroup::getId);
        if (keyword != null && !keyword.isBlank()) {
            String value = keyword.trim();
            query.and(wrapper -> wrapper.like(AiAccessGroup::getCode, value)
                    .or()
                    .like(AiAccessGroup::getDisplayName, value));
        }
        if (status != null) {
            query.eq(AiAccessGroup::getAdminStatus, status);
        }
        Page<AiAccessGroup> result = accessGroupMapper.selectPage(pageParam, query);
        return PageResult.of(result.getRecords().stream().map(this::toAccessGroupResponse).toList(),
                result.getTotal(), (int) pageParam.getCurrent(), (int) pageParam.getSize());
    }

    /**
     * 查询访问组详情。
     *
     * @param id 访问组 ID
     * @return 访问组响应
     */
    public AiAccessGroupResponse getAccessGroup(Long id) {
        Long tenantId = tenantGuard.requireCurrentTenantId();
        return toAccessGroupResponse(findAccessGroupOrThrow(tenantId, id));
    }

    /**
     * 更新访问组。
     *
     * @param id 访问组 ID
     * @param request 更新请求
     * @return 访问组响应
     */
    @Transactional(rollbackFor = Exception.class)
    public AiAccessGroupResponse updateAccessGroup(Long id, AiAccessGroupUpdateRequest request) {
        Long tenantId = tenantGuard.requireCurrentTenantId();
        AiAccessGroup group = findAccessGroupOrThrow(tenantId, id);
        ensureAccessGroupCodeAvailable(tenantId, request.getCode(), id);
        group.setCode(request.getCode());
        group.setDisplayName(request.getDisplayName());
        group.setDescription(request.getDescription());
        group.setAdminStatus(request.getAdminStatus());
        group.setUpdatedAt(LocalDateTime.now());
        accessGroupMapper.updateById(group);
        snapshotChangeRecorder.recordChange(tenantId, GatewaySnapshotChangeTypes.AI_ACCESS_GROUP_CHANGED);
        return toAccessGroupResponse(group);
    }

    /**
     * 启用访问组。
     *
     * @param id 访问组 ID
     * @return 访问组响应
     */
    @Transactional(rollbackFor = Exception.class)
    public AiAccessGroupResponse enableAccessGroup(Long id) {
        return updateAccessGroupStatus(id, AiCatalogStatus.ENABLED);
    }

    /**
     * 停用访问组。
     *
     * @param id 访问组 ID
     * @return 访问组响应
     */
    @Transactional(rollbackFor = Exception.class)
    public AiAccessGroupResponse disableAccessGroup(Long id) {
        return updateAccessGroupStatus(id, AiCatalogStatus.DISABLED);
    }

    /**
     * 创建访问组模型授权。
     *
     * @param accessGroupId 访问组 ID
     * @param request 创建请求
     * @return 授权响应
     */
    @Transactional(rollbackFor = Exception.class)
    public AiAccessGroupModelGrantResponse createModelGrant(Long accessGroupId,
                                                            AiAccessGroupModelGrantCreateRequest request) {
        Long tenantId = tenantGuard.requireCurrentTenantId();
        findAccessGroupOrThrow(tenantId, accessGroupId);
        findPublicModelOrThrow(tenantId, request.getPublicModelId());
        ensureGrantAvailable(tenantId, accessGroupId, request.getPublicModelId(),
                request.getCanonicalOperation(), null);
        LocalDateTime now = LocalDateTime.now();
        AiAccessGroupModelGrant grant = new AiAccessGroupModelGrant();
        grant.setTenantId(tenantId);
        grant.setAccessGroupId(accessGroupId);
        grant.setPublicModelId(request.getPublicModelId());
        grant.setCanonicalOperation(request.getCanonicalOperation());
        grant.setAdminStatus(request.getAdminStatus());
        grant.setCreatedAt(now);
        grant.setUpdatedAt(now);
        modelGrantMapper.insert(grant);
        snapshotChangeRecorder.recordChange(tenantId, GatewaySnapshotChangeTypes.AI_ACCESS_GROUP_MODEL_GRANT_CHANGED);
        return toGrantResponse(grant);
    }

    /**
     * 分页查询访问组模型授权。
     *
     * @param accessGroupId 访问组 ID
     * @param page 页码
     * @param size 每页数量
     * @return 分页结果
     */
    public PageResult<AiAccessGroupModelGrantResponse> listModelGrants(Long accessGroupId, int page, int size) {
        Long tenantId = tenantGuard.requireCurrentTenantId();
        findAccessGroupOrThrow(tenantId, accessGroupId);
        Page<AiAccessGroupModelGrant> pageParam = new Page<>(normalizePage(page), normalizeSize(size));
        LambdaQueryWrapper<AiAccessGroupModelGrant> query = new LambdaQueryWrapper<AiAccessGroupModelGrant>()
                .eq(AiAccessGroupModelGrant::getTenantId, tenantId)
                .eq(AiAccessGroupModelGrant::getAccessGroupId, accessGroupId)
                .orderByDesc(AiAccessGroupModelGrant::getCreatedAt)
                .orderByDesc(AiAccessGroupModelGrant::getId);
        Page<AiAccessGroupModelGrant> result = modelGrantMapper.selectPage(pageParam, query);
        return PageResult.of(result.getRecords().stream().map(this::toGrantResponse).toList(),
                result.getTotal(), (int) pageParam.getCurrent(), (int) pageParam.getSize());
    }

    /**
     * 查询模型授权详情。
     *
     * @param id 授权 ID
     * @return 授权响应
     */
    public AiAccessGroupModelGrantResponse getModelGrant(Long id) {
        Long tenantId = tenantGuard.requireCurrentTenantId();
        return toGrantResponse(findGrantOrThrow(tenantId, id));
    }

    /**
     * 更新模型授权。
     *
     * @param id 授权 ID
     * @param request 更新请求
     * @return 授权响应
     */
    @Transactional(rollbackFor = Exception.class)
    public AiAccessGroupModelGrantResponse updateModelGrant(Long id,
                                                            AiAccessGroupModelGrantUpdateRequest request) {
        Long tenantId = tenantGuard.requireCurrentTenantId();
        AiAccessGroupModelGrant grant = findGrantOrThrow(tenantId, id);
        grant.setAdminStatus(request.getAdminStatus());
        grant.setUpdatedAt(LocalDateTime.now());
        modelGrantMapper.updateById(grant);
        snapshotChangeRecorder.recordChange(tenantId, GatewaySnapshotChangeTypes.AI_ACCESS_GROUP_MODEL_GRANT_CHANGED);
        return toGrantResponse(grant);
    }

    /**
     * 启用模型授权。
     *
     * @param id 授权 ID
     * @return 授权响应
     */
    @Transactional(rollbackFor = Exception.class)
    public AiAccessGroupModelGrantResponse enableModelGrant(Long id) {
        return updateModelGrantStatus(id, AiCatalogStatus.ENABLED);
    }

    /**
     * 停用模型授权。
     *
     * @param id 授权 ID
     * @return 授权响应
     */
    @Transactional(rollbackFor = Exception.class)
    public AiAccessGroupModelGrantResponse disableModelGrant(Long id) {
        return updateModelGrantStatus(id, AiCatalogStatus.DISABLED);
    }

    /**
     * 创建 Client API Key，并一次性返回 raw key。
     *
     * @param request 创建请求
     * @return 含 raw key 的响应
     */
    @Transactional(rollbackFor = Exception.class)
    public AiClientApiKeySecretResponse createClientApiKey(AiClientApiKeyCreateRequest request) {
        Long tenantId = tenantGuard.requireCurrentTenantId();
        String displayName = requireDisplayName(request.getDisplayName());
        String code = normalizeClientKeyCode(tenantId, request.getCode(), displayName, null);
        GatewayClientKeyCrypto.GeneratedClientKey generated = GatewayClientKeyCrypto.generate();
        byte[] salt = GatewayClientKeyCrypto.generateSalt();
        byte[] verifier = GatewayClientKeyCrypto.verifier(generated.rawKey(), generated.keyId(), 1, salt);
        LocalDateTime now = LocalDateTime.now();
        AiClientApiKey key = new AiClientApiKey();
        key.setTenantId(tenantId);
        key.setCode(code);
        key.setDisplayName(displayName);
        key.setDescription(request.getDescription());
        key.setKeyId(generated.keyId());
        key.setAdminStatus(AiClientApiKeyStatus.ENABLED);
        key.setSecretHashAlgorithm(GatewayClientKeyCrypto.HASH_ALGORITHM);
        key.setSecretVerifierSalt(salt);
        key.setSecretVerifierHash(verifier);
        key.setMaskedPreview(generated.maskedPreview());
        key.setKeyVersion(1);
        key.setExpiresAt(toLocalDateTime(request.getExpiresAt()));
        key.setCreatedAt(now);
        key.setUpdatedAt(now);
        clientApiKeyMapper.insert(key);
        snapshotChangeRecorder.recordChange(tenantId, GatewaySnapshotChangeTypes.AI_CLIENT_API_KEY_CHANGED);
        log.info("AI Client API Key 创建成功，租户 ID: {}，Key ID: {}", tenantId, key.getId());
        return toSecretResponse(key, generated.rawKey());
    }

    /**
     * 分页查询 Client API Key。
     *
     * @param page 页码
     * @param size 每页数量
     * @param status 状态过滤
     * @return 分页结果
     */
    public PageResult<AiClientApiKeyResponse> listClientApiKeys(int page, int size, AiClientApiKeyStatus status) {
        Long tenantId = tenantGuard.requireCurrentTenantId();
        Page<AiClientApiKey> pageParam = new Page<>(normalizePage(page), normalizeSize(size));
        LambdaQueryWrapper<AiClientApiKey> query = new LambdaQueryWrapper<AiClientApiKey>()
                .eq(AiClientApiKey::getTenantId, tenantId)
                .orderByDesc(AiClientApiKey::getCreatedAt)
                .orderByDesc(AiClientApiKey::getId);
        if (status != null) {
            query.eq(AiClientApiKey::getAdminStatus, status);
        }
        Page<AiClientApiKey> result = clientApiKeyMapper.selectPage(pageParam, query);
        return PageResult.of(result.getRecords().stream().map(this::toKeyResponse).toList(),
                result.getTotal(), (int) pageParam.getCurrent(), (int) pageParam.getSize());
    }

    /**
     * 查询 Client API Key 详情。
     *
     * @param id Key ID
     * @return 安全响应
     */
    public AiClientApiKeyResponse getClientApiKey(Long id) {
        Long tenantId = tenantGuard.requireCurrentTenantId();
        return toKeyResponse(findClientApiKeyOrThrow(tenantId, id));
    }

    /**
     * 更新 Client API Key 元数据。
     *
     * @param id Key ID
     * @param request 更新请求
     * @return 安全响应
     */
    @Transactional(rollbackFor = Exception.class)
    public AiClientApiKeyResponse updateClientApiKey(Long id, AiClientApiKeyUpdateRequest request) {
        Long tenantId = tenantGuard.requireCurrentTenantId();
        AiClientApiKey key = findClientApiKeyOrThrow(tenantId, id);
        ensureKeyNotRevoked(key);
        ensureClientKeyCodeAvailable(tenantId, request.getCode(), id);
        key.setCode(request.getCode());
        key.setDisplayName(request.getDisplayName());
        key.setDescription(request.getDescription());
        key.setExpiresAt(toLocalDateTime(request.getExpiresAt()));
        key.setUpdatedAt(LocalDateTime.now());
        clientApiKeyMapper.updateById(key);
        snapshotChangeRecorder.recordChange(tenantId, GatewaySnapshotChangeTypes.AI_CLIENT_API_KEY_CHANGED);
        return toKeyResponse(key);
    }

    /**
     * 轮换 Client API Key，并一次性返回新 raw key。
     *
     * @param id Key ID
     * @return 含 raw key 的响应
     */
    @Transactional(rollbackFor = Exception.class)
    public AiClientApiKeySecretResponse rotateClientApiKey(Long id) {
        Long tenantId = tenantGuard.requireCurrentTenantId();
        AiClientApiKey key = findClientApiKeyOrThrow(tenantId, id);
        ensureKeyNotRevoked(key);
        GatewayClientKeyCrypto.GeneratedClientKey generated = GatewayClientKeyCrypto.generateForKeyId(key.getKeyId());
        int nextVersion = key.getKeyVersion() + 1;
        byte[] salt = GatewayClientKeyCrypto.generateSalt();
        byte[] verifier = GatewayClientKeyCrypto.verifier(generated.rawKey(), key.getKeyId(), nextVersion, salt);
        LocalDateTime now = LocalDateTime.now();
        key.setSecretVerifierSalt(salt);
        key.setSecretVerifierHash(verifier);
        key.setMaskedPreview(generated.maskedPreview());
        key.setKeyVersion(nextVersion);
        key.setRotatedAt(now);
        key.setUpdatedAt(now);
        clientApiKeyMapper.updateById(key);
        snapshotChangeRecorder.recordChange(tenantId, GatewaySnapshotChangeTypes.AI_CLIENT_API_KEY_CHANGED);
        log.info("AI Client API Key 轮换成功，租户 ID: {}，Key ID: {}，新版本: {}",
                tenantId, key.getId(), key.getKeyVersion());
        return toSecretResponse(key, generated.rawKey());
    }

    /**
     * 启用 Client API Key。
     *
     * @param id Key ID
     * @return 安全响应
     */
    @Transactional(rollbackFor = Exception.class)
    public AiClientApiKeyResponse enableClientApiKey(Long id) {
        return updateClientApiKeyStatus(id, AiClientApiKeyStatus.ENABLED);
    }

    /**
     * 停用 Client API Key。
     *
     * @param id Key ID
     * @return 安全响应
     */
    @Transactional(rollbackFor = Exception.class)
    public AiClientApiKeyResponse disableClientApiKey(Long id) {
        return updateClientApiKeyStatus(id, AiClientApiKeyStatus.DISABLED);
    }

    /**
     * 撤销 Client API Key。
     *
     * @param id Key ID
     * @return 安全响应
     */
    @Transactional(rollbackFor = Exception.class)
    public AiClientApiKeyResponse revokeClientApiKey(Long id) {
        Long tenantId = tenantGuard.requireCurrentTenantId();
        AiClientApiKey key = findClientApiKeyOrThrow(tenantId, id);
        ensureKeyNotRevoked(key);
        LocalDateTime now = LocalDateTime.now();
        key.setAdminStatus(AiClientApiKeyStatus.REVOKED);
        key.setRevokedAt(now);
        key.setUpdatedAt(now);
        clientApiKeyMapper.updateById(key);
        snapshotChangeRecorder.recordChange(tenantId, GatewaySnapshotChangeTypes.AI_CLIENT_API_KEY_CHANGED);
        return toKeyResponse(key);
    }

    /**
     * 创建 Client API Key 与访问组绑定。
     *
     * @param keyId Key ID
     * @param request 创建请求
     * @return 绑定响应
     */
    @Transactional(rollbackFor = Exception.class)
    public AiClientApiKeyAccessGroupResponse createKeyAccessGroup(Long keyId,
                                                                  AiClientApiKeyAccessGroupCreateRequest request) {
        Long tenantId = tenantGuard.requireCurrentTenantId();
        findClientApiKeyOrThrow(tenantId, keyId);
        findAccessGroupOrThrow(tenantId, request.getAccessGroupId());
        ensureKeyGroupAvailable(tenantId, keyId, request.getAccessGroupId(), null);
        LocalDateTime now = LocalDateTime.now();
        AiClientApiKeyAccessGroup binding = new AiClientApiKeyAccessGroup();
        binding.setTenantId(tenantId);
        binding.setClientApiKeyId(keyId);
        binding.setAccessGroupId(request.getAccessGroupId());
        binding.setAdminStatus(request.getAdminStatus());
        binding.setCreatedAt(now);
        binding.setUpdatedAt(now);
        keyAccessGroupMapper.insert(binding);
        snapshotChangeRecorder.recordChange(tenantId, GatewaySnapshotChangeTypes.AI_CLIENT_API_KEY_ACCESS_GROUP_CHANGED);
        return toKeyGroupResponse(binding);
    }

    /**
     * 分页查询 Client API Key 访问组绑定。
     *
     * @param keyId Key ID
     * @param page 页码
     * @param size 每页数量
     * @return 分页结果
     */
    public PageResult<AiClientApiKeyAccessGroupResponse> listKeyAccessGroups(Long keyId, int page, int size) {
        Long tenantId = tenantGuard.requireCurrentTenantId();
        findClientApiKeyOrThrow(tenantId, keyId);
        Page<AiClientApiKeyAccessGroup> pageParam = new Page<>(normalizePage(page), normalizeSize(size));
        LambdaQueryWrapper<AiClientApiKeyAccessGroup> query = new LambdaQueryWrapper<AiClientApiKeyAccessGroup>()
                .eq(AiClientApiKeyAccessGroup::getTenantId, tenantId)
                .eq(AiClientApiKeyAccessGroup::getClientApiKeyId, keyId)
                .orderByDesc(AiClientApiKeyAccessGroup::getCreatedAt)
                .orderByDesc(AiClientApiKeyAccessGroup::getId);
        Page<AiClientApiKeyAccessGroup> result = keyAccessGroupMapper.selectPage(pageParam, query);
        return PageResult.of(result.getRecords().stream().map(this::toKeyGroupResponse).toList(),
                result.getTotal(), (int) pageParam.getCurrent(), (int) pageParam.getSize());
    }

    /**
     * 查询 Key 访问组绑定详情。
     *
     * @param id 绑定 ID
     * @return 绑定响应
     */
    public AiClientApiKeyAccessGroupResponse getKeyAccessGroup(Long id) {
        Long tenantId = tenantGuard.requireCurrentTenantId();
        return toKeyGroupResponse(findKeyGroupOrThrow(tenantId, id));
    }

    /**
     * 更新 Key 访问组绑定。
     *
     * @param id 绑定 ID
     * @param request 更新请求
     * @return 绑定响应
     */
    @Transactional(rollbackFor = Exception.class)
    public AiClientApiKeyAccessGroupResponse updateKeyAccessGroup(Long id,
                                                                  AiClientApiKeyAccessGroupUpdateRequest request) {
        Long tenantId = tenantGuard.requireCurrentTenantId();
        AiClientApiKeyAccessGroup binding = findKeyGroupOrThrow(tenantId, id);
        binding.setAdminStatus(request.getAdminStatus());
        binding.setUpdatedAt(LocalDateTime.now());
        keyAccessGroupMapper.updateById(binding);
        snapshotChangeRecorder.recordChange(tenantId, GatewaySnapshotChangeTypes.AI_CLIENT_API_KEY_ACCESS_GROUP_CHANGED);
        return toKeyGroupResponse(binding);
    }

    /**
     * 启用 Key 访问组绑定。
     *
     * @param id 绑定 ID
     * @return 绑定响应
     */
    @Transactional(rollbackFor = Exception.class)
    public AiClientApiKeyAccessGroupResponse enableKeyAccessGroup(Long id) {
        return updateKeyGroupStatus(id, AiCatalogStatus.ENABLED);
    }

    /**
     * 停用 Key 访问组绑定。
     *
     * @param id 绑定 ID
     * @return 绑定响应
     */
    @Transactional(rollbackFor = Exception.class)
    public AiClientApiKeyAccessGroupResponse disableKeyAccessGroup(Long id) {
        return updateKeyGroupStatus(id, AiCatalogStatus.DISABLED);
    }

    private AiAccessGroupResponse updateAccessGroupStatus(Long id, AiCatalogStatus status) {
        Long tenantId = tenantGuard.requireCurrentTenantId();
        AiAccessGroup group = findAccessGroupOrThrow(tenantId, id);
        group.setAdminStatus(status);
        group.setUpdatedAt(LocalDateTime.now());
        accessGroupMapper.updateById(group);
        snapshotChangeRecorder.recordChange(tenantId, GatewaySnapshotChangeTypes.AI_ACCESS_GROUP_CHANGED);
        return toAccessGroupResponse(group);
    }

    private AiAccessGroupModelGrantResponse updateModelGrantStatus(Long id, AiCatalogStatus status) {
        Long tenantId = tenantGuard.requireCurrentTenantId();
        AiAccessGroupModelGrant grant = findGrantOrThrow(tenantId, id);
        grant.setAdminStatus(status);
        grant.setUpdatedAt(LocalDateTime.now());
        modelGrantMapper.updateById(grant);
        snapshotChangeRecorder.recordChange(tenantId, GatewaySnapshotChangeTypes.AI_ACCESS_GROUP_MODEL_GRANT_CHANGED);
        return toGrantResponse(grant);
    }

    private AiClientApiKeyResponse updateClientApiKeyStatus(Long id, AiClientApiKeyStatus status) {
        Long tenantId = tenantGuard.requireCurrentTenantId();
        AiClientApiKey key = findClientApiKeyOrThrow(tenantId, id);
        ensureKeyNotRevoked(key);
        key.setAdminStatus(status);
        key.setUpdatedAt(LocalDateTime.now());
        clientApiKeyMapper.updateById(key);
        snapshotChangeRecorder.recordChange(tenantId, GatewaySnapshotChangeTypes.AI_CLIENT_API_KEY_CHANGED);
        return toKeyResponse(key);
    }

    private AiClientApiKeyAccessGroupResponse updateKeyGroupStatus(Long id, AiCatalogStatus status) {
        Long tenantId = tenantGuard.requireCurrentTenantId();
        AiClientApiKeyAccessGroup binding = findKeyGroupOrThrow(tenantId, id);
        binding.setAdminStatus(status);
        binding.setUpdatedAt(LocalDateTime.now());
        keyAccessGroupMapper.updateById(binding);
        snapshotChangeRecorder.recordChange(tenantId, GatewaySnapshotChangeTypes.AI_CLIENT_API_KEY_ACCESS_GROUP_CHANGED);
        return toKeyGroupResponse(binding);
    }

    private AiAccessGroup findAccessGroupOrThrow(Long tenantId, Long id) {
        AiAccessGroup group = accessGroupMapper.selectOne(new LambdaQueryWrapper<AiAccessGroup>()
                .eq(AiAccessGroup::getTenantId, tenantId)
                .eq(AiAccessGroup::getId, id));
        if (group == null) {
            throw new BusinessException(404, "AI 访问组不存在");
        }
        return group;
    }

    private AiAccessGroupModelGrant findGrantOrThrow(Long tenantId, Long id) {
        AiAccessGroupModelGrant grant = modelGrantMapper.selectOne(new LambdaQueryWrapper<AiAccessGroupModelGrant>()
                .eq(AiAccessGroupModelGrant::getTenantId, tenantId)
                .eq(AiAccessGroupModelGrant::getId, id));
        if (grant == null) {
            throw new BusinessException(404, "AI 访问组模型授权不存在");
        }
        return grant;
    }

    private AiClientApiKey findClientApiKeyOrThrow(Long tenantId, Long id) {
        AiClientApiKey key = clientApiKeyMapper.selectOne(new LambdaQueryWrapper<AiClientApiKey>()
                .eq(AiClientApiKey::getTenantId, tenantId)
                .eq(AiClientApiKey::getId, id));
        if (key == null) {
            throw new BusinessException(404, "AI Client API Key 不存在");
        }
        return key;
    }

    private AiClientApiKeyAccessGroup findKeyGroupOrThrow(Long tenantId, Long id) {
        AiClientApiKeyAccessGroup binding = keyAccessGroupMapper.selectOne(
                new LambdaQueryWrapper<AiClientApiKeyAccessGroup>()
                        .eq(AiClientApiKeyAccessGroup::getTenantId, tenantId)
                        .eq(AiClientApiKeyAccessGroup::getId, id));
        if (binding == null) {
            throw new BusinessException(404, "AI Client API Key 访问组绑定不存在");
        }
        return binding;
    }

    private AiPublicModel findPublicModelOrThrow(Long tenantId, Long id) {
        AiPublicModel model = publicModelMapper.selectOne(new LambdaQueryWrapper<AiPublicModel>()
                .eq(AiPublicModel::getTenantId, tenantId)
                .eq(AiPublicModel::getId, id));
        if (model == null) {
            throw new BusinessException(404, "AI 公开模型不存在");
        }
        return model;
    }

    private void ensureAccessGroupCodeAvailable(Long tenantId, String code, Long excludeId) {
        LambdaQueryWrapper<AiAccessGroup> query = new LambdaQueryWrapper<AiAccessGroup>()
                .eq(AiAccessGroup::getTenantId, tenantId)
                .eq(AiAccessGroup::getCode, code);
        if (excludeId != null) {
            query.ne(AiAccessGroup::getId, excludeId);
        }
        if (accessGroupMapper.selectCount(query) > 0) {
            throw new BusinessException(400, "AI 访问组编码已存在");
        }
    }

    private void ensureGrantAvailable(Long tenantId, Long groupId, Long modelId,
                                      AiCanonicalOperation operation, Long excludeId) {
        LambdaQueryWrapper<AiAccessGroupModelGrant> query = new LambdaQueryWrapper<AiAccessGroupModelGrant>()
                .eq(AiAccessGroupModelGrant::getTenantId, tenantId)
                .eq(AiAccessGroupModelGrant::getAccessGroupId, groupId)
                .eq(AiAccessGroupModelGrant::getPublicModelId, modelId)
                .eq(AiAccessGroupModelGrant::getCanonicalOperation, operation);
        if (excludeId != null) {
            query.ne(AiAccessGroupModelGrant::getId, excludeId);
        }
        if (modelGrantMapper.selectCount(query) > 0) {
            throw new BusinessException(400, "AI 访问组模型授权已存在");
        }
    }

    private void ensureClientKeyCodeAvailable(Long tenantId, String code, Long excludeId) {
        LambdaQueryWrapper<AiClientApiKey> query = new LambdaQueryWrapper<AiClientApiKey>()
                .eq(AiClientApiKey::getTenantId, tenantId)
                .eq(AiClientApiKey::getCode, code);
        if (excludeId != null) {
            query.ne(AiClientApiKey::getId, excludeId);
        }
        if (clientApiKeyMapper.selectCount(query) > 0) {
            throw new BusinessException(400, "AI Client API Key 编码已存在");
        }
    }

    private void ensureKeyGroupAvailable(Long tenantId, Long keyId, Long groupId, Long excludeId) {
        LambdaQueryWrapper<AiClientApiKeyAccessGroup> query = new LambdaQueryWrapper<AiClientApiKeyAccessGroup>()
                .eq(AiClientApiKeyAccessGroup::getTenantId, tenantId)
                .eq(AiClientApiKeyAccessGroup::getClientApiKeyId, keyId)
                .eq(AiClientApiKeyAccessGroup::getAccessGroupId, groupId);
        if (excludeId != null) {
            query.ne(AiClientApiKeyAccessGroup::getId, excludeId);
        }
        if (keyAccessGroupMapper.selectCount(query) > 0) {
            throw new BusinessException(400, "AI Client API Key 访问组绑定已存在");
        }
    }

    private void ensureKeyNotRevoked(AiClientApiKey key) {
        if (key.getAdminStatus() == AiClientApiKeyStatus.REVOKED) {
            throw new BusinessException(400, "已撤销的 Client API Key 不能重新启用或轮换");
        }
    }

    private String normalizeClientKeyCode(Long tenantId, String code, String displayName, Long excludeId) {
        String value = code == null || code.isBlank() ? displayName : code;
        String normalized = value.trim()
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9_-]", "_")
                .replaceAll("_+", "_");
        if (normalized.isBlank()) {
            normalized = "client_key_" + System.currentTimeMillis();
        }
        if (normalized.length() > 64) {
            normalized = normalized.substring(0, 64);
        }
        ensureClientKeyCodeAvailable(tenantId, normalized, excludeId);
        return normalized;
    }

    private String requireDisplayName(String displayName) {
        if (displayName == null || displayName.isBlank()) {
            throw new BusinessException(400, "Key 名称不能为空");
        }
        return displayName.trim();
    }

    private LocalDateTime toLocalDateTime(Instant instant) {
        if (instant == null) {
            return null;
        }
        return LocalDateTime.ofInstant(instant, ZoneId.systemDefault());
    }

    private int normalizePage(int page) {
        return Math.max(page, 1);
    }

    private int normalizeSize(int size) {
        if (size <= 0) {
            return 10;
        }
        return Math.min(size, MAX_PAGE_SIZE);
    }

    private AiAccessGroupResponse toAccessGroupResponse(AiAccessGroup group) {
        return AiAccessGroupResponse.builder()
                .id(group.getId())
                .tenantId(group.getTenantId())
                .code(group.getCode())
                .displayName(group.getDisplayName())
                .description(group.getDescription())
                .adminStatus(group.getAdminStatus())
                .createdAt(group.getCreatedAt())
                .updatedAt(group.getUpdatedAt())
                .build();
    }

    private AiAccessGroupModelGrantResponse toGrantResponse(AiAccessGroupModelGrant grant) {
        return AiAccessGroupModelGrantResponse.builder()
                .id(grant.getId())
                .tenantId(grant.getTenantId())
                .accessGroupId(grant.getAccessGroupId())
                .publicModelId(grant.getPublicModelId())
                .canonicalOperation(grant.getCanonicalOperation())
                .adminStatus(grant.getAdminStatus())
                .createdAt(grant.getCreatedAt())
                .updatedAt(grant.getUpdatedAt())
                .build();
    }

    private AiClientApiKeyResponse toKeyResponse(AiClientApiKey key) {
        return AiClientApiKeyResponse.builder()
                .id(key.getId())
                .tenantId(key.getTenantId())
                .code(key.getCode())
                .displayName(key.getDisplayName())
                .description(key.getDescription())
                .keyId(key.getKeyId())
                .adminStatus(key.getAdminStatus())
                .maskedPreview(key.getMaskedPreview())
                .keyVersion(key.getKeyVersion())
                .expiresAt(key.getExpiresAt())
                .rotatedAt(key.getRotatedAt())
                .revokedAt(key.getRevokedAt())
                .createdAt(key.getCreatedAt())
                .updatedAt(key.getUpdatedAt())
                .build();
    }

    private AiClientApiKeySecretResponse toSecretResponse(AiClientApiKey key, String rawKey) {
        return AiClientApiKeySecretResponse.builder()
                .id(key.getId())
                .tenantId(key.getTenantId())
                .code(key.getCode())
                .displayName(key.getDisplayName())
                .description(key.getDescription())
                .keyId(key.getKeyId())
                .adminStatus(key.getAdminStatus())
                .maskedPreview(key.getMaskedPreview())
                .keyVersion(key.getKeyVersion())
                .expiresAt(key.getExpiresAt())
                .rotatedAt(key.getRotatedAt())
                .revokedAt(key.getRevokedAt())
                .createdAt(key.getCreatedAt())
                .updatedAt(key.getUpdatedAt())
                .rawKey(rawKey)
                .build();
    }

    private AiClientApiKeyAccessGroupResponse toKeyGroupResponse(AiClientApiKeyAccessGroup binding) {
        return AiClientApiKeyAccessGroupResponse.builder()
                .id(binding.getId())
                .tenantId(binding.getTenantId())
                .clientApiKeyId(binding.getClientApiKeyId())
                .accessGroupId(binding.getAccessGroupId())
                .adminStatus(binding.getAdminStatus())
                .createdAt(binding.getCreatedAt())
                .updatedAt(binding.getUpdatedAt())
                .build();
    }
}
