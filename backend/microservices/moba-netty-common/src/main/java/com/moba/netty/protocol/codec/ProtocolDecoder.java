package com.moba.netty.protocol.codec;

import com.moba.netty.protocol.MessagePacket;
import com.moba.netty.protocol.ProtocolConstants;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import lombok.extern.slf4j.Slf4j;

import java.nio.charset.StandardCharsets;

@Slf4j
public class ProtocolDecoder extends LengthFieldBasedFrameDecoder {

    public ProtocolDecoder() {
        this(ProtocolConstants.DEFAULT_MAX_FRAME_LENGTH);
    }

    public ProtocolDecoder(int maxFrameLength) {
        super(maxFrameLength,
                ProtocolConstants.LENGTH_FIELD_OFFSET,
                ProtocolConstants.LENGTH_FIELD_LENGTH,
                ProtocolConstants.LENGTH_ADJUSTMENT,
                ProtocolConstants.INITIAL_BYTES_TO_STRIP);
    }

    @Override
    protected Object decode(ChannelHandlerContext ctx, ByteBuf in) throws Exception {
        ByteBuf frame = (ByteBuf) super.decode(ctx, in);
        if (frame == null) {
            return null;
        }

        try {
            if (frame.readableBytes() < ProtocolConstants.HEADER_SIZE) {
                log.warn("数据帧过短: {}字节, 至少需要{}字节",
                        frame.readableBytes(), ProtocolConstants.HEADER_SIZE);
                return null;
            }

            int totalLength = frame.readInt();
            int sequenceId = frame.readInt();
            short extensionId = frame.readShort();
            byte cmdId = frame.readByte();

            int dataLength = totalLength - ProtocolConstants.HEADER_SIZE;
            if (dataLength < 0) {
                log.warn("无效的数据长度: totalLength={}, headerSize={}",
                        totalLength, ProtocolConstants.HEADER_SIZE);
                return null;
            }

            String data = "";
            if (dataLength > 0) {
                byte[] dataBytes = new byte[dataLength];
                frame.readBytes(dataBytes);
                data = new String(dataBytes, StandardCharsets.UTF_8);
            }

            MessagePacket packet = new MessagePacket(extensionId, cmdId, sequenceId, data);
            packet.bindUserIdFromChannel(ctx.channel());

            if (log.isDebugEnabled()) {
                log.debug("解码消息: extId={}, cmdId={}, seq={}, dataLen={}, userId={}, from={}",
                        String.format("0x%04X", extensionId),
                        String.format("0x%02X", cmdId),
                        sequenceId,
                        dataLength, packet.getUserId(),
                        ctx.channel().id().asShortText());
            }

            return packet;
        } finally {
            frame.release();
        }
    }
}
