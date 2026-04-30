package com.moba.battleserver.network.handler;

import com.moba.battleserver.ServiceLocator;
import com.moba.battleserver.network.codec.GameMessage;
import com.moba.battleserver.service.MessageDispatchService;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@ChannelHandler.Sharable
public class ClientRequestHandler extends SimpleChannelInboundHandler<GameMessage> {

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, GameMessage msg) {
        try {
            MessageDispatchService dispatchService = ServiceLocator.getInstance().getMessageDispatchService();
            dispatchService.dispatch(ctx, msg);
        } catch (Exception e) {
            log.error("Error processing message from channel: {}", ctx.channel().id().asShortText(), e);
        }
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        super.channelActive(ctx);
        log.info("Client connected: {}", ctx.channel().id().asShortText());
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        super.channelInactive(ctx);
        log.info("Client disconnected: {}", ctx.channel().id().asShortText());
        MessageDispatchService dispatchService = ServiceLocator.getInstance().getMessageDispatchService();
        dispatchService.handleDisconnect(ctx);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        log.error("Exception in client handler: {}", ctx.channel().id().asShortText(), cause);
        ctx.close();
    }
}
