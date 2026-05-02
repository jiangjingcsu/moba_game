package com.moba.gateway.network.handler;

import com.moba.gateway.network.session.GatewaySession;
import com.moba.gateway.network.session.GatewaySessionManager;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.util.AttributeKey;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class GatewayHeartbeatHandler extends ChannelInboundHandlerAdapter {

    private final GatewaySessionManager sessionManager;

    public GatewayHeartbeatHandler(GatewaySessionManager sessionManager) {
        this.sessionManager = sessionManager;
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt instanceof IdleStateEvent) {
            Object sessionObj = ctx.channel().attr(AttributeKey.valueOf("gatewaySession")).get();
            if (sessionObj instanceof GatewaySession) {
                GatewaySession session = (GatewaySession) sessionObj;
                log.warn("客户端心跳超时: sessionId={}, playerId={}", session.getSessionId(), session.getPlayerId());
                sessionManager.removeSession(session.getSessionId());
            }
            ctx.close();
        } else {
            super.userEventTriggered(ctx, evt);
        }
    }
}
