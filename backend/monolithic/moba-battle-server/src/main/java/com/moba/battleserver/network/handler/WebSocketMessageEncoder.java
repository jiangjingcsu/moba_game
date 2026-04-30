package com.moba.battleserver.network.handler;

import com.moba.battleserver.network.codec.GameMessage;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageEncoder;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

@Slf4j
public class WebSocketMessageEncoder extends MessageToMessageEncoder<GameMessage> {
    @Override
    protected void encode(ChannelHandlerContext ctx, GameMessage msg, List<Object> out) {
        int messageId = msg.getMessageId();
        byte[] body = msg.getBody() != null ? msg.getBody() : new byte[0];
        int totalLength = 4 + body.length;

        ByteBuf buf = ctx.alloc().buffer(4 + totalLength);
        buf.writeInt(totalLength);
        buf.writeInt(messageId);
        buf.writeBytes(body);

        out.add(new BinaryWebSocketFrame(buf));

        if (log.isDebugEnabled()) {
            log.debug("WebSocket encoded message: {} ({} bytes) to channel: {}",
                    GameMessage.getMessageName(messageId), totalLength, ctx.channel().id().asShortText());
        }
    }
}
