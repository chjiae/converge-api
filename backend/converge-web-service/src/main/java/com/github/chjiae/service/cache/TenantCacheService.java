package com.github.chjiae.service.cache;

import com.github.chjiae.service.entity.Tenant;
import com.github.chjiae.service.mapper.TenantMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * 租户状态缓存服务，使用 Redis 缓存租户状态信息以减少数据库查询。
 * <p>
 * 缓存 key 格式：{@code tenant:status:{tenantId}}，TTL 为 30 分钟。
 * 缓存命中时直接返回状态字符串，缓存未命中时从数据库加载并写入缓存。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TenantCacheService {

    /** Redis 模板 */
    private final StringRedisTemplate redisTemplate;

    /** 租户数据访问层 */
    private final TenantMapper tenantMapper;

    /** 缓存 key 前缀 */
    private static final String CACHE_PREFIX = "tenant:status:";

    /** 缓存过期时间（分钟） */
    private static final long CACHE_TTL_MINUTES = 30;

    /**
     * 获取租户状态（优先从缓存读取）。
     * <p>
     * 缓存命中时直接返回状态字符串；缓存未命中时从数据库查询，
     * 写入缓存后返回。若数据库中租户不存在，则返回 null。
     *
     * @param tenantId 租户 ID
     * @return 租户状态字符串（对应 {@code TenantStatus} 枚举名称），租户不存在返回 null
     */
    public String getTenantStatus(Long tenantId) {
        String key = CACHE_PREFIX + tenantId;

        // 优先从缓存读取
        String status = redisTemplate.opsForValue().get(key);
        if (status != null) {
            log.debug("租户状态缓存命中，tenantId: {}，status: {}", tenantId, status);
            return status;
        }

        // 缓存未命中，从数据库加载
        log.debug("租户状态缓存未命中，从数据库加载，tenantId: {}", tenantId);
        Tenant tenant = tenantMapper.selectById(tenantId);
        if (tenant == null) {
            log.warn("租户不存在，无法缓存状态，tenantId: {}", tenantId);
            return null;
        }

        status = tenant.getStatus().name();
        redisTemplate.opsForValue().set(key, status, CACHE_TTL_MINUTES, TimeUnit.MINUTES);
        log.info("租户状态已写入缓存，tenantId: {}，status: {}，TTL: {} 分钟", tenantId, status, CACHE_TTL_MINUTES);
        return status;
    }

    /**
     * 刷新指定租户的缓存。
     * <p>
     * 强制从数据库重新加载租户状态并覆盖写入缓存，
     * 适用于租户状态发生变更后需要立即更新缓存的场景。
     *
     * @param tenantId 租户 ID
     */
    public void refreshTenantCache(Long tenantId) {
        log.info("刷新租户缓存，tenantId: {}", tenantId);
        // 直接覆盖写入缓存，getTenantStatus 会处理数据库查询和缓存写入
        evictTenantCache(tenantId);
        getTenantStatus(tenantId);
    }

    /**
     * 清除指定租户的缓存。
     *
     * @param tenantId 租户 ID
     */
    public void evictTenantCache(Long tenantId) {
        String key = CACHE_PREFIX + tenantId;
        redisTemplate.delete(key);
        log.info("租户缓存已清除，tenantId: {}", tenantId);
    }
}
