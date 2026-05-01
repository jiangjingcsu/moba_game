package com.moba.battle.network.handler;

import com.moba.battle.config.ServerConfig;
import com.moba.battle.config.SpringContextHolder;
import com.moba.battle.protocol.core.GamePacket;
import com.moba.battle.protocol.core.MessageType;
import io.netty.channel.*;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@ChannelHandler.Sharable
public class HeartbeatHandler extends ChannelInboundHandlerAdapter {

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt instanceof IdleStateEvent) {
            IdleStateEvent event = (IdleStateEvent) evt;
            if (event.state() == IdleState.READER_IDLE) {
                log.warn("Client heartbeat timeout: {}", ctx.channel().id().asShortText());
                ctx.close();
            }
        } else {
            super.userEventTriggered(ctx, evt);
        }
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        super.channelActive(ctx);
        scheduleHeartbeat(ctx);
    }

    private void scheduleHeartbeat(ChannelHandlerContext ctx) {
        ServerConfig config = SpringContextHolder.getBean(ServerConfig.class);
        int heartbeatInterval = config != null ? config.getHeartbeatIntervalSeconds() : 30;
        ctx.executor().schedule(() -> {
            if (ctx.channel().isActive()) {
                GamePacket heartbeat = new GamePacket(MessageType.HEARTBEAT_REQ, 0);
                heartbeat.setBody(new byte[0]);
                ctx.writeAndFlush(heartbeat);
                scheduleHeartbeat(ctx);
            }
        }, heartbeatInterval, java.util.concurrent.TimeUnit.SECONDS);
    }
}
