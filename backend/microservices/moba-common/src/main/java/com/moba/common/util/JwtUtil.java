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

    public static String generateToken(long userId, String username, String secret, long expirationMs) {
        return Jwts.builder()
                .claims(Map.of("userId", userId, "username", username))
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

    public static Long getUserId(Claims claims) {
        if (claims == null) return null;
        Object userId = claims.get("userId");
        if (userId instanceof Integer) return ((Integer) userId).longValue();
        if (userId instanceof Long) return (Long) userId;
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
