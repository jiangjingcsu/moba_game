package com.moba.battle.network.codec;

import com.moba.battle.protocol.core.GamePacket;
import com.moba.battle.protocol.core.MessageType;
import com.moba.battle.protocol.core.SerializeType;
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
            if (frame.readableBytes() < GamePacket.HEADER_SIZE) {
                log.warn("Frame too short: {} bytes, need at least {}", frame.readableBytes(), GamePacket.HEADER_SIZE);
                return null;
            }

            int totalLength = frame.readInt();
            int magic = frame.readShort();
            if (magic != GamePacket.MAGIC) {
                log.warn("Invalid magic number: 0x{}, expected 0x{}", Integer.toHexString(magic), Integer.toHexString(GamePacket.MAGIC));
                return null;
            }

            byte version = frame.readByte();
            byte serializeTypeCode = frame.readByte();
            int commandCode = frame.readShort() & 0xFFFF;
            int sequenceId = frame.readInt();
            frame.readShort();

            SerializeType serializeType = SerializeType.fromCode(serializeTypeCode);
            MessageType messageType = MessageType.fromCode(commandCode);

            int bodyLength = totalLength - GamePacket.HEADER_SIZE;
            byte[] body = new byte[Math.max(0, bodyLength)];
            if (bodyLength > 0) {
                frame.readBytes(body);
            }

            GamePacket packet = new GamePacket();
            packet.setVersion(version);
            packet.setSerializeType(serializeType);
            packet.setMessageType(messageType);
            packet.setSequenceId(sequenceId);
            packet.setBody(body);

            if (log.isDebugEnabled()) {
                log.debug("Decoded packet: {} seq={} st={} body={}B from {}",
                        messageType != null ? messageType.name() : "UNKNOWN(0x" + Integer.toHexString(commandCode) + ")",
                        sequenceId, serializeType, bodyLength, ctx.channel().id().asShortText());
            }

            return packet;
        } finally {
            frame.release();
        }
    }
}
