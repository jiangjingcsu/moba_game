package com.moba.gateway.service;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
public class JwtService {

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.expiration}")
    private long expiration;

    private volatile SecretKey cachedSigningKey;

    @Data
    public static class TokenInfo {
        private long playerId;
        private String username;
    }

    private SecretKey getSigningKey() {
        if (cachedSigningKey == null) {
            synchronized (this) {
                if (cachedSigningKey == null) {
                    byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
                    cachedSigningKey = Keys.hmacShaKeyFor(keyBytes);
                }
            }
        }
        return cachedSigningKey;
    }

    public String generateToken(long playerId, String username) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("playerId", playerId);
        claims.put("username", username);

        return Jwts.builder()
                .claims(claims)
                .subject(username)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expiration))
                .signWith(getSigningKey())
                .compact();
    }

    public Claims parseToken(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(getSigningKey())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (ExpiredJwtException e) {
            log.warn("JWT令牌已过期: {}", e.getMessage());
            return null;
        } catch (JwtException e) {
            log.warn("JWT令牌无效: {}", e.getMessage());
            return null;
        }
    }

    public boolean validateToken(String token) {
        return parseToken(token) != null;
    }

    public TokenInfo parseTokenInfo(String token) {
        Claims claims = parseToken(token);
        if (claims == null) return null;

        TokenInfo info = new TokenInfo();
        Object playerId = claims.get("playerId");
        if (playerId instanceof Integer) {
            info.setPlayerId(((Integer) playerId).longValue());
        } else if (playerId instanceof Long) {
            info.setPlayerId((Long) playerId);
        }
        info.setUsername(claims.getSubject());
        return info;
    }

    public long getPlayerIdFromToken(String token) {
        TokenInfo info = parseTokenInfo(token);
        return info != null ? info.getPlayerId() : 0;
    }

    public String getUsernameFromToken(String token) {
        TokenInfo info = parseTokenInfo(token);
        return info != null ? info.getUsername() : null;
    }
}
