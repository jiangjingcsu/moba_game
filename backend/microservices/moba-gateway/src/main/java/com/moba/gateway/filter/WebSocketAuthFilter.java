package com.moba.gateway.filter;

import com.moba.gateway.service.JwtService;
import com.moba.gateway.service.TokenBlacklistService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Slf4j
@Component
@RequiredArgsConstructor
public class WebSocketAuthFilter implements GlobalFilter, Ordered {

    private final JwtService jwtService;
    private final TokenBlacklistService tokenBlacklistService;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getURI().getPath();

        if (!path.startsWith("/ws/")) {
            return chain.filter(exchange);
        }

        String query = request.getURI().getQuery();
        if (query == null || query.isEmpty()) {
            log.warn("WebSocket连接缺少查询参数: {}", path);
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        }

        String extractedToken = extractToken(query);
        if (extractedToken == null || extractedToken.isEmpty()) {
            log.warn("WebSocket连接缺少token参数: {}", path);
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        }

        final String token = extractedToken;

        return tokenBlacklistService.isBlacklisted(token)
                .flatMap(blacklisted -> {
                    if (blacklisted) {
                        log.warn("WebSocket连接使用已拉黑的令牌");
                        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
                        return exchange.getResponse().setComplete();
                    }

                    if (!jwtService.validateToken(token)) {
                        log.warn("WebSocket连接令牌无效");
                        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
                        return exchange.getResponse().setComplete();
                    }

                    JwtService.TokenInfo tokenInfo = jwtService.parseTokenInfo(token);
                    if (tokenInfo == null || tokenInfo.getPlayerId() == 0) {
                        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
                        return exchange.getResponse().setComplete();
                    }

                    ServerHttpRequest modifiedRequest = request.mutate()
                            .header("X-Player-Id", String.valueOf(tokenInfo.getPlayerId()))
                            .header("X-Username", tokenInfo.getUsername() != null ? tokenInfo.getUsername() : "")
                            .build();

                    log.debug("WebSocket认证成功: playerId={}, username={}", tokenInfo.getPlayerId(), tokenInfo.getUsername());
                    return chain.filter(exchange.mutate().request(modifiedRequest).build());
                });
    }

    private String extractToken(String query) {
        for (String param : query.split("&")) {
            String[] kv = param.split("=", 2);
            if (kv.length == 2 && "token".equals(kv[0])) {
                return kv[1];
            }
        }
        return null;
    }

    @Override
    public int getOrder() {
        return -95;
    }
}
