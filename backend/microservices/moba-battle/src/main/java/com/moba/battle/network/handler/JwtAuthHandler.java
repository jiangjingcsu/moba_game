package com.moba.battle.network.handler;

import com.moba.common.util.JwtUtil;
import io.jsonwebtoken.Claims;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.QueryStringDecoder;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;

@Slf4j
public class JwtAuthHandler extends SimpleChannelInboundHandler<FullHttpRequest> {

    private static final String JWT_SECRET = "moba-game-jwt-secret-key-2024-must-be-at-least-256-bits";
    public static final String ATTR_PLAYER_ID = "playerId";
    public static final String ATTR_USERNAME = "username";

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, FullHttpRequest request) {
        String uri = request.uri();
        QueryStringDecoder decoder = new QueryStringDecoder(uri);
        Map<String, List<String>> params = decoder.parameters();

        String token = getFirstParam(params, "token");
        if (token == null || token.isEmpty()) {
            log.warn("WebSocket connection without token, rejecting");
            ctx.close();
            return;
        }

        Claims claims = JwtUtil.parseToken(token, JWT_SECRET);
        if (claims == null) {
            log.warn("Invalid JWT token, rejecting connection");
            ctx.close();
            return;
        }

        Long playerId = JwtUtil.getPlayerId(claims);
        String username = JwtUtil.getUsername(claims);
        if (playerId == null) {
            log.warn("JWT token missing playerId, rejecting connection");
            ctx.close();
            return;
        }

        ctx.channel().attr(io.netty.util.AttributeKey.valueOf(ATTR_PLAYER_ID)).set(playerId);
        ctx.channel().attr(io.netty.util.AttributeKey.valueOf(ATTR_USERNAME)).set(username != null ? username : "");

        log.info("WebSocket JWT auth success: playerId={}, username={}", playerId, username);

        String cleanUri = uri.split("\\?")[0];
        FullHttpRequest modifiedRequest = request.copy();
        modifiedRequest.setUri(cleanUri);

        ctx.fireChannelRead(modifiedRequest);
    }

    private String getFirstParam(Map<String, List<String>> params, String key) {
        List<String> values = params.get(key);
        if (values != null && !values.isEmpty()) {
            return values.get(0);
        }
        return null;
    }
}
