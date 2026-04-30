package com.moba.common.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Map;

public class JwtUtil {

    public static String generateToken(long playerId, String username, String secret, long expirationMs) {
        return Jwts.builder()
                .claims(Map.of("playerId", playerId, "username", username))
                .subject(username)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expirationMs))
                .signWith(getSigningKey(secret))
                .compact();
    }

    public static Claims parseToken(String token, String secret) {
        try {
            return Jwts.parser()
                    .verifyWith(getSigningKey(secret))
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (JwtException e) {
            return null;
        }
    }

    public static boolean validateToken(String token, String secret) {
        return parseToken(token, secret) != null;
    }

    public static Long getPlayerId(Claims claims) {
        if (claims == null) return null;
        Object playerId = claims.get("playerId");
        if (playerId instanceof Integer) return ((Integer) playerId).longValue();
        if (playerId instanceof Long) return (Long) playerId;
        return null;
    }

    public static String getUsername(Claims claims) {
        if (claims == null) return null;
        return claims.get("username", String.class);
    }

    private static SecretKey getSigningKey(String secret) {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }
}
