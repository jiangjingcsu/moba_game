package com.moba.battle.network.handler;

import com.moba.battle.protocol.core.GamePacket;
import com.moba.battle.protocol.core.MessageType;
import com.moba.battle.protocol.core.SerializeType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class WebSocketFrameHandler extends SimpleChannelInboundHandler<WebSocketFrame> {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, WebSocketFrame frame) throws Exception {
        if (frame instanceof TextWebSocketFrame) {
            handleTextFrame(ctx, (TextWebSocketFrame) frame);
        } else if (frame instanceof BinaryWebSocketFrame) {
            handleBinaryFrame(ctx, (BinaryWebSocketFrame) frame);
        } else {
            log.warn("Unsupported WebSocket frame type: {}", frame.getClass().getSimpleName());
        }
    }

    private void handleTextFrame(ChannelHandlerContext ctx, TextWebSocketFrame frame) throws Exception {
        String text = frame.text();
        JsonNode root;
        try {
            root = objectMapper.readTree(text);
        } catch (Exception e) {
            log.warn("Invalid JSON from WebSocket: {}", text.substring(0, Math.min(text.length(), 100)));
            return;
        }

        int cmd = root.has("cmd") ? root.get("cmd").asInt() : -1;
        int seq = root.has("seq") ? root.get("seq").asInt() : 0;
        int ver = root.has("ver") ? root.get("ver").asInt() : 1;
        int st = root.has("st") ? root.get("st").asInt() : 1;

        MessageType messageType = MessageType.fromCode(cmd);
        SerializeType serializeType = SerializeType.fromCode(st);

        byte[] body = new byte[0];
        if (root.has("data") && !root.get("data").isNull()) {
            body = objectMapper.writeValueAsBytes(root.get("data"));
        }

        GamePacket packet = new GamePacket();
        packet.setVersion(ver);
        packet.setSerializeType(serializeType);
        packet.setMessageType(messageType);
        packet.setSequenceId(seq);
        packet.setBody(body);

        if (log.isDebugEnabled()) {
            log.debug("WebSocket text decoded: {} seq={} body={}B from {}",
                    messageType != null ? messageType.name() : "UNKNOWN(0x" + Integer.toHexString(cmd) + ")",
                    seq, body.length, ctx.channel().id().asShortText());
        }

        ctx.fireChannelRead(packet);
    }

    private void handleBinaryFrame(ChannelHandlerContext ctx, BinaryWebSocketFrame frame) throws Exception {
        com.moba.battle.network.codec.MessageDecoder decoder = new com.moba.battle.network.codec.MessageDecoder(65536);
        Object decoded = decoder.decode(ctx, frame.content());
        if (decoded instanceof GamePacket) {
            ctx.fireChannelRead(decoded);
        }
    }
}
