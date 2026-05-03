package com.moba.gateway.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Duration;

@Slf4j
@Service
@RequiredArgsConstructor
public class TokenBlacklistService {

    private final ReactiveStringRedisTemplate reactiveRedisTemplate;

    private static final String BLACKLIST_PREFIX = "token:blacklist:";
    private static final String REFRESH_PREFIX = "token:refresh:";

    public Mono<Void> addToBlacklist(String token, long remainingSeconds) {
        String key = BLACKLIST_PREFIX + token;
        return reactiveRedisTemplate.opsForValue().set(key, "revoked", Duration.ofSeconds(remainingSeconds))
                .doOnSuccess(v -> log.info("令牌已加入黑名单, {}秒后过期", remainingSeconds))
                .then();
    }

    public Mono<Boolean> isBlacklisted(String token) {
        return reactiveRedisTemplate.hasKey(BLACKLIST_PREFIX + token);
    }

    public Mono<Void> storeRefreshToken(long userId, String refreshToken, long expireSeconds) {
        String key = REFRESH_PREFIX + userId;
        return reactiveRedisTemplate.opsForValue().set(key, refreshToken, Duration.ofSeconds(expireSeconds))
                .then();
    }

    public Mono<Boolean> validateRefreshToken(long userId, String refreshToken) {
        String key = REFRESH_PREFIX + userId;
        return reactiveRedisTemplate.opsForValue().get(key)
                .map(stored -> stored.equals(refreshToken))
                .defaultIfEmpty(false);
    }

    public Mono<Void> removeRefreshToken(long userId) {
        return reactiveRedisTemplate.delete(REFRESH_PREFIX + userId).then();
    }
}
