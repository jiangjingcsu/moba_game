package com.moba.netty.codec;

import com.moba.common.protocol.GamePacket;
import com.moba.common.protocol.MessageType;
import com.moba.common.protocol.SerializeType;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class PacketDecoder extends LengthFieldBasedFrameDecoder {

    public PacketDecoder(int maxFrameLength) {
        super(maxFrameLength, 0, 4, -4, 0);
    }

    @Override
    protected Object decode(ChannelHandlerContext ctx, ByteBuf in) throws Exception {
        ByteBuf frame = (ByteBuf) super.decode(ctx, in);
        if (frame == null) {
            return null;
        }

        try {
            if (frame.readableBytes() < GamePacket.HEADER_SIZE) {
                log.warn("数据帧过短: {}字节, 至少需要{}字节", frame.readableBytes(), GamePacket.HEADER_SIZE);
                return null;
            }

            int totalLength = frame.readInt();
            int magic = frame.readShort();
            if (magic != GamePacket.MAGIC) {
                log.warn("无效的魔数: 0x{}, 期望0x{}", Integer.toHexString(magic), Integer.toHexString(GamePacket.MAGIC));
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
                log.debug("解码数据包: {} seq={} st={} body={}B 来自 {}",
                        messageType != null ? messageType.name() : "UNKNOWN(0x" + Integer.toHexString(commandCode) + ")",
                        sequenceId, serializeType, bodyLength, ctx.channel().id().asShortText());
            }

            return packet;
        } finally {
            frame.release();
        }
    }
}
