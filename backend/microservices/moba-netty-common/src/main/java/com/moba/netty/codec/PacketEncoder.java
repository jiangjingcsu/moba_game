package com.moba.netty.codec;

import com.moba.common.protocol.GamePacket;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class PacketEncoder extends MessageToByteEncoder<GamePacket> {

    @Override
    protected void encode(ChannelHandlerContext ctx, GamePacket packet, ByteBuf out) throws Exception {
        byte[] body = packet.getBody() != null ? packet.getBody() : new byte[0];
        int totalLength = GamePacket.HEADER_SIZE + body.length;

        out.writeInt(totalLength);
        out.writeShort(GamePacket.MAGIC);
        out.writeByte(packet.getVersion());
        out.writeByte(packet.getSerializeType().getCode());
        out.writeShort(packet.getCommandCode());
        out.writeInt(packet.getSequenceId());
        out.writeShort(0);
        out.writeBytes(body);

        if (log.isDebugEnabled()) {
            log.debug("编码数据包: {} seq={} st={} 总计={}B 发往 {}",
                    packet.getMessageType() != null ? packet.getMessageType().name() : "NULL",
                    packet.getSequenceId(), packet.getSerializeType(), totalLength,
                    ctx.channel().id().asShortText());
        }
    }
}
