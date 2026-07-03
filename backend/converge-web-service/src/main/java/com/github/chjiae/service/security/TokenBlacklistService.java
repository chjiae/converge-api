package com.github.chjiae.service.security;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * Token 黑名单服务，使用 Redis 存储已注销的 Token。
 * 通过 JWT 的 jti（唯一标识）进行标记，TTL 设为 Token 剩余有效期。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TokenBlacklistService {

    /** Redis 模板 */
    private final StringRedisTemplate redisTemplate;

    /** 黑名单 key 前缀 */
    private static final String BLACKLIST_PREFIX = "token:blacklist:";

    /**
     * 将 Token 加入黑名单
     *
     * @param jti       Token 唯一标识
     * @param ttlMillis Token 剩余有效期（毫秒）
     */
    public void blacklist(String jti, long ttlMillis) {
        if (jti == null || ttlMillis <= 0) {
            return;
        }
        String key = BLACKLIST_PREFIX + jti;
        redisTemplate.opsForValue().set(key, "1", ttlMillis, TimeUnit.MILLISECONDS);
        log.info("Token 已加入黑名单，jti: {}，TTL: {} 毫秒", jti, ttlMillis);
    }

    /**
     * 检查 Token 是否在黑名单中
     *
     * @param jti Token 唯一标识
     * @return 在黑名单中返回 true
     */
    public boolean isBlacklisted(String jti) {
        if (jti == null) {
            return false;
        }
        String key = BLACKLIST_PREFIX + jti;
        return Boolean.TRUE.equals(redisTemplate.hasKey(key));
    }
}
