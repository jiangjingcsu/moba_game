package com.moba.netty.protocol.codec;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.moba.netty.protocol.MessagePacket;
import com.moba.netty.protocol.ProtocolConstants;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class WebSocketProtocolEncoder extends ChannelOutboundHandlerAdapter {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final boolean binaryMode;

    public WebSocketProtocolEncoder() {
        this(false);
    }

    public WebSocketProtocolEncoder(boolean binaryMode) {
        this.binaryMode = binaryMode;
    }

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
        if (!(msg instanceof MessagePacket)) {
            ctx.write(msg, promise);
            return;
        }

        MessagePacket packet = (MessagePacket) msg;

        if (binaryMode) {
            writeBinaryFrame(ctx, packet, promise);
        } else {
            writeTextFrame(ctx, packet, promise);
        }
    }

    private void writeTextFrame(ChannelHandlerContext ctx, MessagePacket packet, ChannelPromise promise) throws Exception {
        ObjectNode root = OBJECT_MAPPER.createObjectNode();
        root.put("extId", packet.getExtensionId());
        root.put("cmdId", packet.getCmdId());
        root.put("seq", packet.getSequenceId());

        if (packet.getData() != null && !packet.getData().isEmpty()) {
            try {
                root.set("data", OBJECT_MAPPER.readTree(packet.getData()));
            } catch (Exception e) {
                root.put("data", packet.getData());
            }
        } else {
            root.putNull("data");
        }

        String json = OBJECT_MAPPER.writeValueAsString(root);
        ctx.write(new TextWebSocketFrame(json), promise);

        if (log.isDebugEnabled()) {
            log.debug("WS文本编码: extId={}, cmdId={}, seq={}, to={}",
                    String.format("0x%04X", packet.getExtensionId()),
                    String.format("0x%02X", packet.getCmdId()),
                    packet.getSequenceId(),
                    ctx.channel().id().asShortText());
        }
    }

    private void writeBinaryFrame(ChannelHandlerContext ctx, MessagePacket packet, ChannelPromise promise) {
        byte[] dataBytes = packet.encodeData();
        int totalLength = ProtocolConstants.HEADER_SIZE + dataBytes.length;

        ByteBuf buf = ctx.alloc().buffer(totalLength);
        buf.writeInt(totalLength);
        buf.writeInt(packet.getSequenceId());
        buf.writeShort(packet.getExtensionId());
        buf.writeByte(packet.getCmdId());
        buf.writeBytes(dataBytes);

        ctx.write(new BinaryWebSocketFrame(buf), promise);

        if (log.isDebugEnabled()) {
            log.debug("WS二进制编码: extId={}, cmdId={}, seq={}, totalLen={}, to={}",
                    String.format("0x%04X", packet.getExtensionId()),
                    String.format("0x%02X", packet.getCmdId()),
                    packet.getSequenceId(),
                    totalLength, ctx.channel().id().asShortText());
        }
    }
}
