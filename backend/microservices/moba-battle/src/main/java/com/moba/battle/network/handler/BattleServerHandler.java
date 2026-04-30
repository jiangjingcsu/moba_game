package com.moba.battle.network.handler;

import com.moba.battle.network.codec.GameMessage;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@ChannelHandler.Sharable
public class BattleServerHandler extends SimpleChannelInboundHandler<GameMessage> {
    @Override
    protected void channelRead0(ChannelHandlerContext ctx, GameMessage msg) {
        log.info("Battle server message: {} from channel: {}",
                GameMessage.getMessageName(msg.getMessageId()), ctx.channel().id().asShortText());
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        super.channelActive(ctx);
        log.info("Battle server connected: {}", ctx.channel().id().asShortText());
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        super.channelInactive(ctx);
        log.info("Battle server disconnected: {}", ctx.channel().id().asShortText());
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        log.error("Exception in battle server handler: {}", ctx.channel().id().asShortText(), cause);
        ctx.close();
    }
}
