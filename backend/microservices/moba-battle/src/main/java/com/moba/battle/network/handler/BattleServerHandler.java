package com.moba.battle.network.handler;

import com.moba.battle.protocol.core.GamePacket;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@ChannelHandler.Sharable
public class BattleServerHandler extends SimpleChannelInboundHandler<GamePacket> {
    @Override
    protected void channelRead0(ChannelHandlerContext ctx, GamePacket packet) {
        log.info("战斗服务器数据包: {} seq={} 来自通道: {}",
                packet.getMessageType(), packet.getSequenceId(), ctx.channel().id().asShortText());
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        super.channelActive(ctx);
        log.info("战斗服务器连接建立: {}", ctx.channel().id().asShortText());
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        super.channelInactive(ctx);
        log.info("战斗服务器连接断开: {}", ctx.channel().id().asShortText());
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        log.error("战斗服务器处理器异常: {}", ctx.channel().id().asShortText(), cause);
        ctx.close();
    }
}
