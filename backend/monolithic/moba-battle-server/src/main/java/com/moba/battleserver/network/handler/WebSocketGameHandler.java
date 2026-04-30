package com.moba.battleserver.network.handler;

import com.moba.battleserver.ServiceLocator;
import com.moba.battleserver.network.codec.GameMessage;
import com.moba.battleserver.service.MessageDispatchService;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@ChannelHandler.Sharable
public class WebSocketGameHandler extends SimpleChannelInboundHandler<BinaryWebSocketFrame> {

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, BinaryWebSocketFrame frame) {
        ByteBuf buf = frame.content();
        if (buf.readableBytes() < 8) {
            log.warn("WebSocket frame too short: {} bytes", buf.readableBytes());
            return;
        }

        int totalLength = buf.readInt();
        int messageId = buf.readInt();
        int bodyLength = totalLength - 4;
        byte[] body = new byte[Math.max(0, bodyLength)];
        if (bodyLength > 0) {
            buf.readBytes(body);
        }

        GameMessage msg = new GameMessage();
        msg.setMessageId(messageId);
        msg.setBody(body);

        if (log.isDebugEnabled()) {
            log.debug("WebSocket message: {} from channel: {}",
                    GameMessage.getMessageName(messageId), ctx.channel().id().asShortText());
        }

        try {
            MessageDispatchService dispatchService = ServiceLocator.getInstance().getMessageDispatchService();
            dispatchService.dispatch(ctx, msg);
        } catch (Exception e) {
            log.error("Error processing WebSocket message from channel: {}", ctx.channel().id().asShortText(), e);
        }
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        super.channelActive(ctx);
        log.info("WebSocket client connected: {}", ctx.channel().id().asShortText());
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        super.channelInactive(ctx);
        log.info("WebSocket client disconnected: {}", ctx.channel().id().asShortText());
        MessageDispatchService dispatchService = ServiceLocator.getInstance().getMessageDispatchService();
        dispatchService.handleDisconnect(ctx);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        log.error("WebSocket exception in channel: {}", ctx.channel().id().asShortText(), cause);
        ctx.close();
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt instanceof io.netty.handler.timeout.IdleStateEvent) {
            io.netty.handler.timeout.IdleStateEvent event = (io.netty.handler.timeout.IdleStateEvent) evt;
            if (event.state() == io.netty.handler.timeout.IdleState.READER_IDLE) {
                log.warn("Client heartbeat timeout: {}", ctx.channel().id().asShortText());
                ctx.close();
            }
        } else {
            super.userEventTriggered(ctx, evt);
        }
    }
}
