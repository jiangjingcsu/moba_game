package com.moba.battle.network.handler;

import com.moba.battle.config.ServerConfig;
import com.moba.battle.protocol.core.GamePacket;
import com.moba.battle.protocol.core.MessageType;
import com.moba.netty.spring.SpringContextHolder;
import io.netty.channel.ChannelHandlerContext;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class HeartbeatHandler extends com.moba.netty.handler.HeartbeatHandler {

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
