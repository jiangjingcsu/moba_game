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
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.Set;

@Slf4j
@Component
@RequiredArgsConstructor
public class AuthFilter implements GlobalFilter, Ordered {

    private final JwtService jwtService;
    private final TokenBlacklistService tokenBlacklistService;

    private static final Set<String> EXACT_WHITE_LIST = Set.of(
            "/api/auth/login",
            "/api/auth/register",
            "/api/auth/refresh",
            "/api/health",
            "/actuator/health",
            "/actuator/info"
    );

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getURI().getPath();

        if (path.startsWith("/ws/")) {
            return chain.filter(exchange);
        }

        if (EXACT_WHITE_LIST.contains(path)) {
            return chain.filter(exchange);
        }

        String authHeader = request.getHeaders().getFirst("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return unauthorized(exchange.getResponse());
        }

        String token = authHeader.substring(7);

        return tokenBlacklistService.isBlacklisted(token)
                .flatMap(blacklisted -> {
                    if (blacklisted) {
                        log.warn("已拉黑的令牌被使用");
                        return unauthorized(exchange.getResponse());
                    }

                    if (!jwtService.validateToken(token)) {
                        return unauthorized(exchange.getResponse());
                    }

                    JwtService.TokenInfo tokenInfo = jwtService.parseTokenInfo(token);
                    if (tokenInfo == null || tokenInfo.getPlayerId() == 0) {
                        return unauthorized(exchange.getResponse());
                    }

                    ServerHttpRequest modifiedRequest = request.mutate()
                            .header("X-Player-Id", String.valueOf(tokenInfo.getPlayerId()))
                            .header("X-Username", tokenInfo.getUsername() != null ? tokenInfo.getUsername() : "")
                            .build();

                    return chain.filter(exchange.mutate().request(modifiedRequest).build());
                });
    }

    private Mono<Void> unauthorized(ServerHttpResponse response) {
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        return response.setComplete();
    }

    @Override
    public int getOrder() {
        return -100;
    }
}
