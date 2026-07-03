package com.github.chjiae.service.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * JWT 令牌提供者，负责生成和验证 JWT Token。
 * 使用 HMAC-SHA256 算法签名，支持访问令牌和刷新令牌两种类型。
 */
@Slf4j
@Component
public class JwtTokenProvider {

    /** JWT 签名密钥 */
    @Value("${jwt.secret-key}")
    private String secretKey;

    /** 访问令牌有效期（毫秒） */
    @Value("${jwt.access-token-expiration}")
    private long accessTokenExpiration;

    /** 刷新令牌有效期（毫秒） */
    @Value("${jwt.refresh-token-expiration}")
    private long refreshTokenExpiration;

    /**
     * 生成访问令牌
     * 在 claims 中存储 userId、tenantId、userType 等用户信息
     *
     * @param principal 用户认证主体
     * @return JWT 访问令牌字符串
     */
    public String generateAccessToken(UserPrincipal principal) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + accessTokenExpiration);

        return Jwts.builder()
                .subject(principal.getUsername())
                .claim("userId", principal.getUserId())
                .claim("tenantId", principal.getTenantId())
                .claim("userType", principal.getUserType().name())
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(getSigningKey())
                .compact();
    }

    /**
     * 生成刷新令牌
     * 刷新令牌仅包含 userId 和 tenantId，用于换取新的访问令牌
     *
     * @param principal 用户认证主体
     * @return JWT 刷新令牌字符串
     */
    public String generateRefreshToken(UserPrincipal principal) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + refreshTokenExpiration);

        return Jwts.builder()
                .subject(principal.getUsername())
                .claim("userId", principal.getUserId())
                .claim("tenantId", principal.getTenantId())
                .claim("type", "refresh")
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(getSigningKey())
                .compact();
    }

    /**
     * 验证令牌是否有效（未过期、签名正确、格式合法）
     *
     * @param token JWT 令牌字符串
     * @return 有效返回 true，否则返回 false
     */
    public boolean validateToken(String token) {
        try {
            parseClaims(token);
            return true;
        } catch (ExpiredJwtException ex) {
            log.warn("JWT 令牌已过期: {}", ex.getMessage());
        } catch (JwtException ex) {
            log.warn("JWT 令牌无效: {}", ex.getMessage());
        } catch (IllegalArgumentException ex) {
            log.warn("JWT 令牌为空或格式错误: {}", ex.getMessage());
        }
        return false;
    }

    /**
     * 从令牌中解析用户 ID
     *
     * @param token JWT 令牌字符串
     * @return 用户 ID
     */
    public Long getUserIdFromToken(String token) {
        Claims claims = parseClaims(token);
        return claims.get("userId", Long.class);
    }

    /**
     * 从令牌中解析租户 ID
     *
     * @param token JWT 令牌字符串
     * @return 租户 ID（超管可能为 null）
     */
    public Long getTenantIdFromToken(String token) {
        Claims claims = parseClaims(token);
        return claims.get("tenantId", Long.class);
    }

    /**
     * 从令牌中解析用户名
     *
     * @param token JWT 令牌字符串
     * @return 用户名
     */
    public String getUsernameFromToken(String token) {
        Claims claims = parseClaims(token);
        return claims.getSubject();
    }

    /**
     * 从令牌中解析用户类型
     *
     * @param token JWT 令牌字符串
     * @return 用户类型字符串
     */
    public String getUserTypeFromToken(String token) {
        Claims claims = parseClaims(token);
        return claims.get("userType", String.class);
    }

    /**
     * 解析 JWT 令牌中的 Claims
     *
     * @param token JWT 令牌字符串
     * @return Claims 对象
     */
    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /**
     * 获取 HMAC-SHA256 签名密钥
     *
     * @return SecretKey 签名密钥
     */
    private SecretKey getSigningKey() {
        byte[] keyBytes = secretKey.getBytes(StandardCharsets.UTF_8);
        return Keys.hmacShaKeyFor(keyBytes);
    }
}
