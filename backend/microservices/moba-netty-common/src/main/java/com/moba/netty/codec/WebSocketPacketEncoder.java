package com.moba.netty.codec;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.moba.common.protocol.GamePacket;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class WebSocketPacketEncoder extends ChannelOutboundHandlerAdapter {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
        if (msg instanceof GamePacket) {
            GamePacket packet = (GamePacket) msg;

            ObjectNode root = OBJECT_MAPPER.createObjectNode();
            root.put("cmd", packet.getCommandCode());
            root.put("seq", packet.getSequenceId());
            root.put("ver", packet.getVersion());
            root.put("st", packet.getSerializeType().getCode());

            if (packet.getBody() != null && packet.getBody().length > 0) {
                try {
                    JsonNode dataNode = OBJECT_MAPPER.readTree(packet.getBody());
                    root.set("data", dataNode);
                } catch (Exception e) {
                    root.put("data", new String(packet.getBody(), java.nio.charset.StandardCharsets.UTF_8));
                }
            } else {
                root.putNull("data");
            }

            String json = OBJECT_MAPPER.writeValueAsString(root);
            ctx.write(new TextWebSocketFrame(json), promise);

            if (log.isDebugEnabled()) {
                log.debug("WebSocket编码: {} seq={} 发往 {}",
                        packet.getMessageType() != null ? packet.getMessageType().name() : "NULL",
                        packet.getSequenceId(), ctx.channel().id().asShortText());
            }
        } else {
            ctx.write(msg, promise);
        }
    }
}
