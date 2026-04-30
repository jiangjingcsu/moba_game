package com.moba.battleserver.network.codec;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class MessageDecoder extends LengthFieldBasedFrameDecoder {
    private static final int LENGTH_FIELD_OFFSET = 0;
    private static final int LENGTH_FIELD_LENGTH = 4;
    private static final int LENGTH_ADJUSTMENT = -4;
    private static final int INITIAL_BYTES_TO_STRIP = 0;

    public MessageDecoder(int maxFrameLength) {
        super(maxFrameLength, LENGTH_FIELD_OFFSET, LENGTH_FIELD_LENGTH, LENGTH_ADJUSTMENT, INITIAL_BYTES_TO_STRIP);
    }

    @Override
    protected Object decode(ChannelHandlerContext ctx, ByteBuf in) throws Exception {
        ByteBuf frame = (ByteBuf) super.decode(ctx, in);
        if (frame == null) {
            return null;
        }

        try {
            int messageId = frame.readInt();
            int bodyLength = frame.readableBytes();
            byte[] body = new byte[bodyLength];
            frame.readBytes(body);

            GameMessage message = new GameMessage();
            message.setMessageId(messageId);
            message.setBody(body);

            if (log.isDebugEnabled()) {
                log.debug("Decoded message: {} from channel: {}",
                        GameMessage.getMessageName(messageId), ctx.channel().id().asShortText());
            }
            return message;
        } finally {
            frame.release();
        }
    }
}
