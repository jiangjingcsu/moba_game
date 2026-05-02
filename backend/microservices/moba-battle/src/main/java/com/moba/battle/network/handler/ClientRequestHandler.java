package com.moba.battle.network.handler;

import com.moba.battle.manager.PlayerManager;
import com.moba.battle.protocol.core.GamePacket;
import com.moba.battle.service.MessageDispatchService;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.util.AttributeKey;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@ChannelHandler.Sharable
public class ClientRequestHandler extends SimpleChannelInboundHandler<GamePacket> {
    private final MessageDispatchService dispatchService;

    public ClientRequestHandler() {
        this.dispatchService = MessageDispatchService.getInstance();
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, GamePacket packet) {
        try {
            dispatchService.dispatch(ctx, packet);
        } catch (Exception e) {
            log.error("处理数据包异常: {} 来自通道: {}",
                    packet.getMessageType(), ctx.channel().id().asShortText(), e);
        }
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        super.channelActive(ctx);

        AttributeKey<Object> playerIdKey = AttributeKey.valueOf(GatewayTrustHandler.ATTR_PLAYER_ID);
        AttributeKey<Object> usernameKey = AttributeKey.valueOf(GatewayTrustHandler.ATTR_USERNAME);

        Object playerIdObj = ctx.channel().attr(playerIdKey).get();
        Object usernameObj = ctx.channel().attr(usernameKey).get();

        if (playerIdObj != null) {
            long playerId = (Long) playerIdObj;
            String username = usernameObj != null ? (String) usernameObj : "player_" + playerId;
            PlayerManager.getInstance().registerPlayerFromToken(ctx, playerId, username);
            log.info("客户端认证并注册: playerId={}, 通道={}", playerId, ctx.channel().id().asShortText());
        } else {
            log.warn("客户端连接未携带JWT认证: 通道={}", ctx.channel().id().asShortText());
        }
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        super.channelInactive(ctx);
        log.info("客户端断开连接: {}", ctx.channel().id().asShortText());
        dispatchService.handleDisconnect(ctx);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        log.error("客户端处理器异常: {}", ctx.channel().id().asShortText(), cause);
        ctx.close();
    }
}
