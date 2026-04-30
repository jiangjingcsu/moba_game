package com.moba.battle.network.handler;

import com.moba.battle.config.ServerConfig;
import com.moba.battle.network.codec.GameMessage;
import io.netty.channel.*;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@ChannelHandler.Sharable
public class HeartbeatHandler extends ChannelInboundHandlerAdapter {
    private final int heartbeatInterval;

    public HeartbeatHandler() {
        this.heartbeatInterval = ServerConfig.defaultConfig().getHeartbeatIntervalSeconds();
    }

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
        ctx.executor().schedule(() -> {
            if (ctx.channel().isActive()) {
                GameMessage heartbeat = new GameMessage();
                heartbeat.setMessageId(GameMessage.HEARTBEAT_REQUEST);
                heartbeat.setBody(new byte[0]);
                ctx.writeAndFlush(heartbeat);
                scheduleHeartbeat(ctx);
            }
        }, heartbeatInterval, java.util.concurrent.TimeUnit.SECONDS);
    }
}
