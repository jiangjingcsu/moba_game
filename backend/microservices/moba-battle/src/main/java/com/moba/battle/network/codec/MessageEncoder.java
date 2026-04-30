package com.moba.battle.network.codec;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class MessageEncoder extends MessageToByteEncoder<GameMessage> {
    @Override
    protected void encode(ChannelHandlerContext ctx, GameMessage msg, ByteBuf out) throws Exception {
        int messageId = msg.getMessageId();
        byte[] body = msg.getBody() != null ? msg.getBody() : new byte[0];

        int totalLength = 4 + body.length;
        out.writeInt(totalLength);
        out.writeInt(messageId);
        out.writeBytes(body);

        if (log.isDebugEnabled()) {
            log.debug("Encoded message: {} ({} bytes) to channel: {}",
                    GameMessage.getMessageName(messageId), totalLength, ctx.channel().id().asShortText());
        }
    }
}
