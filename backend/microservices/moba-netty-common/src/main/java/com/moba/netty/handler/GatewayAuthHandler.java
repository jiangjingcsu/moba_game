package com.moba.netty.handler;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.QueryStringDecoder;
import io.netty.util.AttributeKey;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;

@Slf4j
public class GatewayAuthHandler extends SimpleChannelInboundHandler<FullHttpRequest> {

    public static final String ATTR_GATEWAY_MODE = "gatewayMode";
    public static final String ATTR_PLAYER_ID = "playerId";
    public static final String ATTR_USERNAME = "username";

    private static final String DEFAULT_GATEWAY_SECRET = "moba-gateway-internal-secret";

    private final String gatewaySecret;
    private final boolean jwtEnabled;
    private final String jwtSecret;

    public GatewayAuthHandler() {
        this(DEFAULT_GATEWAY_SECRET, false, null);
    }

    public GatewayAuthHandler(String gatewaySecret) {
        this(gatewaySecret, false, null);
    }

    public GatewayAuthHandler(String gatewaySecret, boolean jwtEnabled, String jwtSecret) {
        this.gatewaySecret = gatewaySecret != null ? gatewaySecret : DEFAULT_GATEWAY_SECRET;
        this.jwtEnabled = jwtEnabled;
        this.jwtSecret = jwtSecret;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, FullHttpRequest request) {
        String uri = request.uri();
        QueryStringDecoder decoder = new QueryStringDecoder(uri);
        Map<String, List<String>> params = decoder.parameters();

        String gatewayToken = getFirstParam(params, "gateway");
        if (gatewayToken != null && gatewaySecret.equals(gatewayToken)) {
            ctx.channel().attr(AttributeKey.valueOf(ATTR_GATEWAY_MODE)).set(true);
            log.debug("网关可信连接来自 {}", ctx.channel().remoteAddress());
        } else if (jwtEnabled) {
            String token = getFirstParam(params, "token");
            if (token == null || token.isEmpty()) {
                log.warn("连接缺少令牌, 拒绝连接");
                ctx.close();
                return;
            }

            try {
                io.jsonwebtoken.Claims claims = com.moba.common.util.JwtUtil.parseToken(token, jwtSecret);
                if (claims == null) {
                    log.warn("JWT令牌无效, 拒绝连接");
                    ctx.close();
                    return;
                }

                Long playerId = com.moba.common.util.JwtUtil.getPlayerId(claims);
                String username = com.moba.common.util.JwtUtil.getUsername(claims);
                if (playerId == null) {
                    log.warn("JWT令牌缺少playerId, 拒绝连接");
                    ctx.close();
                    return;
                }

                ctx.channel().attr(AttributeKey.valueOf(ATTR_PLAYER_ID)).set(playerId);
                ctx.channel().attr(AttributeKey.valueOf(ATTR_USERNAME)).set(username != null ? username : "");
                log.info("JWT认证成功: playerId={}, username={}", playerId, username);
            } catch (Exception e) {
                log.warn("JWT验证失败: {}", e.getMessage());
                ctx.close();
                return;
            }
        }

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

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        log.error("GatewayAuthHandler处理异常, 来自 {}", ctx.channel().remoteAddress(), cause);
        ctx.close();
    }
}
