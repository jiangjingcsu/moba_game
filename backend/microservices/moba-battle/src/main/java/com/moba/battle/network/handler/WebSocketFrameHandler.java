package com.moba.battle.network.handler;

import com.moba.battle.network.codec.GameMessage;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class WebSocketFrameHandler extends SimpleChannelInboundHandler<BinaryWebSocketFrame> {

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, BinaryWebSocketFrame frame) {
        ByteBuf content = frame.content();
        if (content.readableBytes() < 8) {
            log.warn("WebSocket frame too short: {} bytes", content.readableBytes());
            return;
        }

        int totalLength = content.readInt();
        int messageId = content.readInt();
        int bodyLength = totalLength - 4;
        byte[] body = new byte[bodyLength];
        if (bodyLength > 0) {
            content.readBytes(body);
        }

        GameMessage message = new GameMessage();
        message.setMessageId(messageId);
        message.setBody(body);

        if (log.isDebugEnabled()) {
            log.debug("WebSocket decoded message: {} from channel: {}",
                    GameMessage.getMessageName(messageId), ctx.channel().id().asShortText());
        }

        ctx.fireChannelRead(message);
    }
}
