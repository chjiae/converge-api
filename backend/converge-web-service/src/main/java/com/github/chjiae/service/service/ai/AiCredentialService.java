package com.github.chjiae.service.service.ai;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.github.chjiae.common.enums.AiCatalogStatus;
import com.github.chjiae.common.enums.AiCredentialType;
import com.github.chjiae.common.exception.BusinessException;
import com.github.chjiae.common.result.PageResult;
import com.github.chjiae.service.dto.ai.AiCredentialCreateRequest;
import com.github.chjiae.service.dto.ai.AiCredentialResponse;
import com.github.chjiae.service.dto.ai.AiCredentialUpdateRequest;
import com.github.chjiae.service.entity.ai.AiCredential;
import com.github.chjiae.service.entity.ai.AiProvider;
import com.github.chjiae.service.mapper.ai.AiCredentialMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * AI 凭据控制面服务。
 * 管理租户级上游 API Key 凭据的创建、查询、更新、轮换和启停。
 * 不暴露任何明文 API Key 读取接口，所有密钥操作均通过加密组件完成。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiCredentialService {

    /** 单页最大数量 */
    private static final int MAX_PAGE_SIZE = 100;

    /** 加密算法标识 */
    private static final String ENCRYPTION_ALGORITHM = "AES-256-GCM";

    /** 掩码保留的前缀长度 */
    private static final int MASKED_PREFIX_LENGTH = 4;

    /** 掩码保留的后缀长度 */
    private static final int MASKED_SUFFIX_LENGTH = 4;

    /** AI 凭据数据访问层 */
    private final AiCredentialMapper aiCredentialMapper;

    /** AI 供应商服务（用于校验 Provider 归属） */
    private final AiProviderService aiProviderService;

    /** AI 目录租户守卫 */
    private final AiCatalogTenantGuard tenantGuard;

    /** AES-256-GCM 加密/解密服务 */
    private final AiCredentialEncryptionService encryptionService;

    /** HMAC 指纹服务 */
    private final AiCredentialFingerprintService fingerprintService;

    /**
     * 创建凭据。
     * 服务端负责：生成 secretReference、加密 API Key、计算指纹、生成掩码。
     *
     * @param providerId 所属供应商 ID
     * @param request    创建请求（包含明文 API Key）
     * @return 凭据响应（不含明文或密文）
     */
    @Transactional(rollbackFor = Exception.class)
    public AiCredentialResponse createCredential(Long providerId, AiCredentialCreateRequest request) {
        Long tenantId = tenantGuard.requireCurrentTenantId();
        log.info("创建 AI 凭据，租户 ID: {}，供应商 ID: {}，编码: {}", tenantId, providerId, request.getCode());

        // 校验 Provider 属于当前租户
        AiProvider provider = aiProviderService.findProviderOrThrow(tenantId, providerId);

        // 校验编码唯一性
        ensureCodeAvailable(tenantId, providerId, request.getCode(), null);

        // 生成服务端 UUID 引用
        String secretReference = UUID.randomUUID().toString();

        // 计算 HMAC 指纹（用于去重）
        String fingerprint = fingerprintService.computeFingerprint(
                request.getApiKey(), tenantId, providerId, AiCredentialType.API_KEY);

        // 检查同租户/同供应商/同类型/同指纹是否已存在
        ensureFingerprintAvailable(tenantId, providerId, fingerprint);

        // AES-256-GCM 加密
        AiCredentialEncryptionService.EncryptionResult encryptionResult =
                encryptionService.encrypt(request.getApiKey(), tenantId, providerId,
                        secretReference, AiCredentialType.API_KEY);

        // 生成掩码预览
        String maskedPreview = generateMaskedPreview(request.getApiKey());

        // 构建实体
        LocalDateTime now = LocalDateTime.now();
        AiCredential credential = new AiCredential();
        credential.setTenantId(tenantId);
        credential.setProviderId(providerId);
        credential.setCode(request.getCode());
        credential.setDisplayName(request.getDisplayName());
        credential.setDescription(request.getDescription());
        credential.setCredentialType(AiCredentialType.API_KEY);
        credential.setAdminStatus(AiCatalogStatus.ENABLED);
        credential.setSecretReference(secretReference);
        credential.setEncryptionKeyId(encryptionService.getActiveKeyId());
        credential.setEncryptionAlgorithm(ENCRYPTION_ALGORITHM);
        credential.setEncryptedSecret(encryptionResult.encryptedSecret());
        credential.setNonce(encryptionResult.nonce());
        credential.setSecretFingerprint(fingerprint);
        credential.setMaskedPreview(maskedPreview);
        credential.setSecretVersion(1);
        credential.setCreatedAt(now);
        credential.setUpdatedAt(now);

        aiCredentialMapper.insert(credential);

        log.info("AI 凭据创建成功，租户 ID: {}，凭据 ID: {}，编码: {}，掩码: {}",
                tenantId, credential.getId(), credential.getCode(), maskedPreview);
        return toResponse(credential);
    }

    /**
     * 分页查询某个供应商下的凭据列表。
     *
     * @param providerId 供应商 ID
     * @param page       页码
     * @param size       每页数量
     * @param keyword    关键字，可选
     * @param status     状态过滤，可选
     * @return 分页凭据列表
     */
    public PageResult<AiCredentialResponse> listCredentials(Long providerId, int page, int size,
                                                             String keyword, AiCatalogStatus status) {
        Long tenantId = tenantGuard.requireCurrentTenantId();
        int safePage = normalizePage(page);
        int safeSize = normalizeSize(size);
        log.info("分页查询 AI 凭据，租户 ID: {}，供应商 ID: {}，页码: {}，每页数量: {}", tenantId, providerId, safePage, safeSize);

        // 校验 Provider 归属
        aiProviderService.findProviderOrThrow(tenantId, providerId);

        Page<AiCredential> pageParam = new Page<>(safePage, safeSize);
        LambdaQueryWrapper<AiCredential> query = new LambdaQueryWrapper<AiCredential>()
                .eq(AiCredential::getTenantId, tenantId)
                .eq(AiCredential::getProviderId, providerId)
                .orderByDesc(AiCredential::getCreatedAt)
                .orderByDesc(AiCredential::getId);

        if (keyword != null && !keyword.isBlank()) {
            String trimmed = keyword.trim();
            query.and(w -> w.like(AiCredential::getCode, trimmed)
                    .or()
                    .like(AiCredential::getDisplayName, trimmed));
        }
        if (status != null) {
            query.eq(AiCredential::getAdminStatus, status);
        }

        Page<AiCredential> resultPage = aiCredentialMapper.selectPage(pageParam, query);
        List<AiCredentialResponse> list = resultPage.getRecords().stream()
                .map(this::toResponse)
                .toList();
        return PageResult.of(list, resultPage.getTotal(), safePage, safeSize);
    }

    /**
     * 查询凭据详情（不含明文 API Key）。
     *
     * @param id 凭据 ID
     * @return 凭据响应
     */
    public AiCredentialResponse getCredential(Long id) {
        Long tenantId = tenantGuard.requireCurrentTenantId();
        AiCredential credential = findCredentialOrThrow(tenantId, id);
        return toResponse(credential);
    }

    /**
     * 更新凭据管理元数据（不变更 API Key）。
     *
     * @param id      凭据 ID
     * @param request 更新请求
     * @return 更新后的凭据响应
     */
    @Transactional(rollbackFor = Exception.class)
    public AiCredentialResponse updateCredential(Long id, AiCredentialUpdateRequest request) {
        Long tenantId = tenantGuard.requireCurrentTenantId();
        log.info("更新 AI 凭据，租户 ID: {}，凭据 ID: {}", tenantId, id);

        AiCredential credential = findCredentialOrThrow(tenantId, id);
        ensureCodeAvailable(tenantId, credential.getProviderId(), request.getCode(), id);

        credential.setCode(request.getCode());
        credential.setDisplayName(request.getDisplayName());
        credential.setDescription(request.getDescription());
        credential.setUpdatedAt(LocalDateTime.now());
        aiCredentialMapper.updateById(credential);

        log.info("AI 凭据更新成功，租户 ID: {}，凭据 ID: {}", tenantId, id);
        return toResponse(credential);
    }

    /**
     * 轮换凭据（保留 ID，更新密文、指纹、掩码、版本与轮换时间）。
     *
     * @param id      凭据 ID
     * @param newApiKey 新的明文 API Key
     * @return 轮换后的凭据响应
     */
    @Transactional(rollbackFor = Exception.class)
    public AiCredentialResponse rotateCredential(Long id, String newApiKey) {
        Long tenantId = tenantGuard.requireCurrentTenantId();
        log.info("轮换 AI 凭据，租户 ID: {}，凭据 ID: {}", tenantId, id);

        AiCredential credential = findCredentialOrThrow(tenantId, id);

        // 计算新指纹
        String newFingerprint = fingerprintService.computeFingerprint(
                newApiKey, tenantId, credential.getProviderId(), AiCredentialType.API_KEY);

        // 检查新指纹是否与其他凭据冲突
        LambdaQueryWrapper<AiCredential> fingerprintCheck = new LambdaQueryWrapper<AiCredential>()
                .eq(AiCredential::getTenantId, tenantId)
                .eq(AiCredential::getProviderId, credential.getProviderId())
                .eq(AiCredential::getCredentialType, AiCredentialType.API_KEY)
                .eq(AiCredential::getSecretFingerprint, newFingerprint)
                .ne(AiCredential::getId, id);
        if (aiCredentialMapper.selectCount(fingerprintCheck) > 0) {
            log.warn("凭据轮换失败，新 API Key 指纹与同供应商下已有凭据重复，租户 ID: {}，供应商 ID: {}",
                    tenantId, credential.getProviderId());
            throw new BusinessException(400, "新 API Key 与同供应商下已有凭据重复");
        }

        // 生成新的 secretReference
        String newSecretReference = UUID.randomUUID().toString();

        // 重新加密
        AiCredentialEncryptionService.EncryptionResult encryptionResult =
                encryptionService.encrypt(newApiKey, tenantId, credential.getProviderId(),
                        newSecretReference, AiCredentialType.API_KEY);

        // 更新凭据字段
        credential.setSecretReference(newSecretReference);
        credential.setEncryptionKeyId(encryptionService.getActiveKeyId());
        credential.setEncryptedSecret(encryptionResult.encryptedSecret());
        credential.setNonce(encryptionResult.nonce());
        credential.setSecretFingerprint(newFingerprint);
        credential.setMaskedPreview(generateMaskedPreview(newApiKey));
        credential.setSecretVersion(credential.getSecretVersion() + 1);
        credential.setRotatedAt(LocalDateTime.now());
        credential.setUpdatedAt(LocalDateTime.now());

        aiCredentialMapper.updateById(credential);

        log.info("AI 凭据轮换成功，租户 ID: {}，凭据 ID: {}，新版本: {}",
                tenantId, id, credential.getSecretVersion());
        return toResponse(credential);
    }

    /**
     * 启用凭据。
     *
     * @param id 凭据 ID
     * @return 启用后的凭据响应
     */
    @Transactional(rollbackFor = Exception.class)
    public AiCredentialResponse enableCredential(Long id) {
        return updateStatus(id, AiCatalogStatus.ENABLED);
    }

    /**
     * 停用凭据。
     *
     * @param id 凭据 ID
     * @return 停用后的凭据响应
     */
    @Transactional(rollbackFor = Exception.class)
    public AiCredentialResponse disableCredential(Long id) {
        return updateStatus(id, AiCatalogStatus.DISABLED);
    }

    /**
     * 查找凭据并校验租户归属（供其他 Service 调用）。
     *
     * @param tenantId     租户 ID
     * @param credentialId 凭据 ID
     * @return 凭据实体
     * @throws BusinessException 凭据不存在或不属于指定租户时抛出
     */
    AiCredential findCredentialOrThrow(Long tenantId, Long credentialId) {
        AiCredential credential = aiCredentialMapper.selectOne(new LambdaQueryWrapper<AiCredential>()
                .eq(AiCredential::getTenantId, tenantId)
                .eq(AiCredential::getId, credentialId));
        if (credential == null) {
            log.warn("AI 凭据不存在或不属于当前租户，租户 ID: {}，凭据 ID: {}", tenantId, credentialId);
            throw new BusinessException(404, "AI 凭据不存在");
        }
        return credential;
    }

    private AiCredentialResponse updateStatus(Long id, AiCatalogStatus status) {
        Long tenantId = tenantGuard.requireCurrentTenantId();
        log.info("更新 AI 凭据状态，租户 ID: {}，凭据 ID: {}，目标状态: {}", tenantId, id, status);

        AiCredential credential = findCredentialOrThrow(tenantId, id);
        credential.setAdminStatus(status);
        credential.setUpdatedAt(LocalDateTime.now());
        aiCredentialMapper.updateById(credential);

        log.info("AI 凭据状态更新成功，租户 ID: {}，凭据 ID: {}，状态: {}", tenantId, id, status);
        return toResponse(credential);
    }

    private void ensureCodeAvailable(Long tenantId, Long providerId, String code, Long excludeId) {
        LambdaQueryWrapper<AiCredential> query = new LambdaQueryWrapper<AiCredential>()
                .eq(AiCredential::getTenantId, tenantId)
                .eq(AiCredential::getProviderId, providerId)
                .eq(AiCredential::getCode, code);
        if (excludeId != null) {
            query.ne(AiCredential::getId, excludeId);
        }
        if (aiCredentialMapper.selectCount(query) > 0) {
            log.warn("AI 凭据编码已存在，租户 ID: {}，供应商 ID: {}，编码: {}", tenantId, providerId, code);
            throw new BusinessException(400, "AI 凭据编码已存在");
        }
    }

    private void ensureFingerprintAvailable(Long tenantId, Long providerId, String fingerprint) {
        LambdaQueryWrapper<AiCredential> query = new LambdaQueryWrapper<AiCredential>()
                .eq(AiCredential::getTenantId, tenantId)
                .eq(AiCredential::getProviderId, providerId)
                .eq(AiCredential::getCredentialType, AiCredentialType.API_KEY)
                .eq(AiCredential::getSecretFingerprint, fingerprint);
        if (aiCredentialMapper.selectCount(query) > 0) {
            log.warn("同供应商下已存在相同 API Key（指纹重复），租户 ID: {}，供应商 ID: {}", tenantId, providerId);
            throw new BusinessException(400, "同供应商下已存在相同 API Key");
        }
    }

    /**
     * 生成掩码预览。如 API Key 长度超过前后缀总和则显示 sk-...abcd 形式，否则全部用星号替代。
     */
    static String generateMaskedPreview(String apiKey) {
        if (apiKey == null || apiKey.length() <= MASKED_PREFIX_LENGTH + MASKED_SUFFIX_LENGTH) {
            return "****";
        }
        return apiKey.substring(0, MASKED_PREFIX_LENGTH) + "..." + apiKey.substring(apiKey.length() - MASKED_SUFFIX_LENGTH);
    }

    private int normalizePage(int page) { return Math.max(page, 1); }

    private int normalizeSize(int size) {
        if (size <= 0) { return 10; }
        return Math.min(size, MAX_PAGE_SIZE);
    }

    private AiCredentialResponse toResponse(AiCredential credential) {
        return AiCredentialResponse.builder()
                .id(credential.getId())
                .tenantId(credential.getTenantId())
                .providerId(credential.getProviderId())
                .code(credential.getCode())
                .displayName(credential.getDisplayName())
                .description(credential.getDescription())
                .credentialType(credential.getCredentialType())
                .adminStatus(credential.getAdminStatus())
                .maskedPreview(credential.getMaskedPreview())
                .secretVersion(credential.getSecretVersion())
                .rotatedAt(credential.getRotatedAt())
                .createdAt(credential.getCreatedAt())
                .updatedAt(credential.getUpdatedAt())
                .build();
    }
}
