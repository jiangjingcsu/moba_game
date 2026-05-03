package com.moba.netty.handler;

import com.moba.common.util.JwtUtil;
import com.moba.netty.session.Session;
import com.moba.netty.session.SessionManager;
import io.jsonwebtoken.Claims;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.QueryStringDecoder;
import io.netty.util.AttributeKey;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;

@Slf4j
public class GatewayAuthHandler extends ChannelInboundHandlerAdapter {

    public static final String ATTR_GATEWAY_MODE = "gatewayMode";
    public static final String ATTR_USER_ID = "userId";
    public static final String ATTR_USERNAME = "username";

    private final String gatewaySecret;
    private final String jwtSecret;
    private final SessionManager sessionManager;

    public GatewayAuthHandler(String gatewaySecret, String jwtSecret, SessionManager sessionManager) {
        this.gatewaySecret = gatewaySecret;
        this.jwtSecret = jwtSecret;
        this.sessionManager = sessionManager;
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        if (!(msg instanceof FullHttpRequest request)) {
            ctx.fireChannelRead(msg);
            return;
        }

        String uri = request.uri();
        QueryStringDecoder decoder = new QueryStringDecoder(uri);
        Map<String, List<String>> params = decoder.parameters();

        String gatewayToken = getFirstParam(params, "gateway");
        if (gatewayToken != null && !gatewayToken.isEmpty()) {
            if (gatewaySecret.equals(gatewayToken)) {
                ctx.channel().attr(AttributeKey.valueOf(ATTR_GATEWAY_MODE)).set(true);
                log.debug("网关可信连接认证成功: {}", ctx.channel().remoteAddress());
            } else {
                log.warn("网关内部密钥不匹配, 拒绝连接: {}", ctx.channel().remoteAddress());
                ctx.close();
                return;
            }
        } else if (jwtSecret != null && !jwtSecret.isEmpty()) {
            String token = getFirstParam(params, "token");
            if (token == null || token.isEmpty()) {
                log.warn("连接缺少JWT令牌, 拒绝连接: {}", ctx.channel().remoteAddress());
                ctx.close();
                return;
            }

            try {
                Claims claims = JwtUtil.parseToken(token, jwtSecret);
                if (claims == null) {
                    log.warn("JWT令牌无效, 拒绝连接: {}", ctx.channel().remoteAddress());
                    ctx.close();
                    return;
                }

                Long userId = JwtUtil.getUserId(claims);
                String username = JwtUtil.getUsername(claims);
                if (userId == null) {
                    log.warn("JWT令牌缺少userId, 拒绝连接: {}", ctx.channel().remoteAddress());
                    ctx.close();
                    return;
                }

                ctx.channel().attr(AttributeKey.valueOf(ATTR_USER_ID)).set(userId);
                ctx.channel().attr(AttributeKey.valueOf(ATTR_USERNAME)).set(username != null ? username : "");

                Session session = sessionManager.getSession(ctx.channel());
                if (session != null) {
                    sessionManager.createUserAndBind(userId, username != null ? username : "", session);
                }

                log.info("JWT认证成功: userId={}, username={}", userId, username);
            } catch (Exception e) {
                log.warn("JWT验证失败: {}", e.getMessage());
                ctx.close();
                return;
            }
        }

        String cleanUri = uri.split("\\?")[0];
        request.setUri(cleanUri);
        ctx.fireChannelRead(msg);
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
