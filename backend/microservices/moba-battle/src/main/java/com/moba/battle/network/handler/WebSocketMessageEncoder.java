package com.moba.battle.network.handler;

import com.moba.battle.network.codec.GameMessage;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class WebSocketMessageEncoder extends ChannelOutboundHandlerAdapter {

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) {
        if (msg instanceof GameMessage) {
            GameMessage gameMsg = (GameMessage) msg;
            int messageId = gameMsg.getMessageId();
            byte[] body = gameMsg.getBody() != null ? gameMsg.getBody() : new byte[0];
            int totalLength = 4 + body.length;

            ByteBuf buffer = ctx.alloc().buffer(4 + totalLength);
            buffer.writeInt(totalLength);
            buffer.writeInt(messageId);
            buffer.writeBytes(body);

            if (log.isDebugEnabled()) {
                log.debug("WebSocket encoded message: {} ({} bytes) to channel: {}",
                        GameMessage.getMessageName(messageId), totalLength, ctx.channel().id().asShortText());
            }

            ctx.write(new BinaryWebSocketFrame(buffer), promise);
        } else {
            ctx.write(msg, promise);
        }
    }
}
