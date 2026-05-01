package com.moba.battle.network.codec;

import com.moba.battle.protocol.core.GamePacket;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class MessageEncoder extends MessageToByteEncoder<GamePacket> {

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
            log.debug("Encoded packet: {} seq={} st={} total={}B to {}",
                    packet.getMessageType() != null ? packet.getMessageType().name() : "NULL",
                    packet.getSequenceId(), packet.getSerializeType(), totalLength,
                    ctx.channel().id().asShortText());
        }
    }
}
