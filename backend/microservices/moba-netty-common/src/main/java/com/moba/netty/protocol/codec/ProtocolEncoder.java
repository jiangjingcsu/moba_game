package com.moba.netty.protocol.codec;

import com.moba.netty.protocol.MessagePacket;
import com.moba.netty.protocol.ProtocolConstants;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ProtocolEncoder extends MessageToByteEncoder<MessagePacket> {

    @Override
    protected void encode(ChannelHandlerContext ctx, MessagePacket packet, ByteBuf out) throws Exception {
        byte[] dataBytes = packet.encodeData();
        int totalLength = ProtocolConstants.HEADER_SIZE + dataBytes.length;

        out.writeInt(totalLength);
        out.writeInt(packet.getSequenceId());
        out.writeShort(packet.getExtensionId());
        out.writeByte(packet.getCmdId());
        out.writeBytes(dataBytes);

        if (log.isDebugEnabled()) {
            log.debug("编码消息: extId={}, cmdId={}, seq={}, totalLen={}, to={}",
                    String.format("0x%04X", packet.getExtensionId()),
                    String.format("0x%02X", packet.getCmdId()),
                    packet.getSequenceId(),
                    totalLength, ctx.channel().id().asShortText());
        }
    }
}
