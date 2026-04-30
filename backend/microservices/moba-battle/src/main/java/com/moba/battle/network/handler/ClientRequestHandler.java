package com.moba.battle.network.handler;

import com.moba.battle.manager.PlayerManager;
import com.moba.battle.network.codec.GameMessage;
import com.moba.battle.service.MessageDispatchService;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.util.AttributeKey;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@ChannelHandler.Sharable
public class ClientRequestHandler extends SimpleChannelInboundHandler<GameMessage> {
    private final MessageDispatchService dispatchService;

    public ClientRequestHandler() {
        this.dispatchService = MessageDispatchService.getInstance();
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, GameMessage msg) {
        try {
            dispatchService.dispatch(ctx, msg);
        } catch (Exception e) {
            log.error("Error processing message from channel: {}", ctx.channel().id().asShortText(), e);
        }
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        super.channelActive(ctx);

        AttributeKey<Object> playerIdKey = AttributeKey.valueOf(JwtAuthHandler.ATTR_PLAYER_ID);
        AttributeKey<Object> usernameKey = AttributeKey.valueOf(JwtAuthHandler.ATTR_USERNAME);

        Object playerIdObj = ctx.channel().attr(playerIdKey).get();
        Object usernameObj = ctx.channel().attr(usernameKey).get();

        if (playerIdObj != null) {
            long playerId = (Long) playerIdObj;
            String username = usernameObj != null ? (String) usernameObj : "player_" + playerId;
            PlayerManager.getInstance().registerPlayerFromToken(ctx, playerId, username);
            log.info("Client authenticated and registered: playerId={}, channel={}", playerId, ctx.channel().id().asShortText());
        } else {
            log.warn("Client connected without JWT auth: channel={}", ctx.channel().id().asShortText());
        }
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        super.channelInactive(ctx);
        log.info("Client disconnected: {}", ctx.channel().id().asShortText());
        dispatchService.handleDisconnect(ctx);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        log.error("Exception in client handler: {}", ctx.channel().id().asShortText(), cause);
        ctx.close();
    }
}
