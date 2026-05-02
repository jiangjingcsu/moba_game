package com.moba.gateway.network.handler;

import com.moba.common.util.JwtUtil;
import com.moba.gateway.network.session.GatewaySession;
import com.moba.gateway.network.session.GatewaySessionManager;
import io.jsonwebtoken.Claims;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;
import io.netty.util.AttributeKey;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class GatewayAuthHandler extends ChannelInboundHandlerAdapter {

    public static final String ATTR_PLAYER_ID = "playerId";
    public static final String ATTR_USERNAME = "username";
    public static final String ATTR_RANK_SCORE = "rankScore";

    private final String jwtSecret;
    private final GatewaySessionManager sessionManager;

    public GatewayAuthHandler(String jwtSecret, GatewaySessionManager sessionManager) {
        this.jwtSecret = jwtSecret;
        this.sessionManager = sessionManager;
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        if (!(msg instanceof HttpRequest httpRequest)) {
            ctx.fireChannelRead(msg);
            return;
        }

        String uri = httpRequest.uri();
        String token = null;

        if (uri.contains("token=")) {
            int idx = uri.indexOf("token=");
            token = uri.substring(idx + 6);
            int ampIdx = token.indexOf('&');
            if (ampIdx > 0) {
                token = token.substring(0, ampIdx);
            }
        }

        if (token != null && !token.isEmpty()) {
            try {
                Claims claims = JwtUtil.parseToken(token, jwtSecret);
                if (claims != null) {
                    Long playerId = JwtUtil.getPlayerId(claims);
                    String username = JwtUtil.getUsername(claims);

                    if (playerId != null) {
                        ctx.channel().attr(AttributeKey.valueOf(ATTR_PLAYER_ID)).set(playerId);
                        ctx.channel().attr(AttributeKey.valueOf(ATTR_USERNAME)).set(username != null ? username : "");

                        int rankScore = 0;
                        Object rankScoreClaim = claims.get("rankScore");
                        if (rankScoreClaim instanceof Number number) {
                            rankScore = number.intValue();
                        }
                        ctx.channel().attr(AttributeKey.valueOf(ATTR_RANK_SCORE)).set(rankScore);

                        log.debug("JWT认证成功: playerId={}, username={}, rankScore={}", playerId, username, rankScore);
                    }
                } else {
                    log.warn("JWT验证失败: token无效");
                }
            } catch (Exception e) {
                log.warn("JWT验证异常: {}", e.getMessage());
            }
        }

        ctx.fireChannelRead(msg);
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt instanceof WebSocketServerProtocolHandler.HandshakeComplete) {
            AttributeKey<Object> playerIdKey = AttributeKey.valueOf(ATTR_PLAYER_ID);
            Object playerIdObj = ctx.channel().attr(playerIdKey).get();

            if (playerIdObj != null) {
                long playerId = (Long) playerIdObj;
                String username = (String) ctx.channel().attr(AttributeKey.valueOf(ATTR_USERNAME)).get();

                int rankScore = 0;
                Object rankScoreObj = ctx.channel().attr(AttributeKey.valueOf(ATTR_RANK_SCORE)).get();
                if (rankScoreObj instanceof Integer) {
                    rankScore = (Integer) rankScoreObj;
                }

                GatewaySession session = sessionManager.createSession(ctx, playerId, username != null ? username : "", rankScore);
                ctx.channel().attr(AttributeKey.valueOf("gatewaySession")).set(session);

                log.info("客户端通过网关认证: playerId={}, sessionId={}, rankScore={}", playerId, session.getSessionId(), rankScore);
            } else {
                log.warn("WebSocket握手完成但缺少JWT认证, 关闭连接: channel={}", ctx.channel().id().asShortText());
                ctx.close();
            }
        }
        super.userEventTriggered(ctx, evt);
    }
}
