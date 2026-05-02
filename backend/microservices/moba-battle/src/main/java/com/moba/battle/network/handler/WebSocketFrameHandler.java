package com.moba.battle.network.handler;

import com.moba.battle.protocol.core.GamePacket;
import com.moba.battle.protocol.core.MessageType;
import com.moba.battle.protocol.core.SerializeType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;
import io.netty.util.AttributeKey;
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
            log.warn("不支持的WebSocket帧类型: {}", frame.getClass().getSimpleName());
        }
    }

    private void handleTextFrame(ChannelHandlerContext ctx, TextWebSocketFrame frame) throws Exception {
        String text = frame.text();
        JsonNode root;
        try {
            root = objectMapper.readTree(text);
        } catch (Exception e) {
            log.warn("WebSocket收到无效JSON: {}", text.substring(0, Math.min(text.length(), 100)));
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

        Object gatewayMode = ctx.channel().attr(AttributeKey.valueOf(GatewayTrustHandler.ATTR_GATEWAY_MODE)).get();
        if (gatewayMode != null && (Boolean) gatewayMode) {
            if (root.has("playerId")) {
                long playerId = root.get("playerId").asLong();
                ctx.channel().attr(AttributeKey.valueOf(GatewayTrustHandler.ATTR_PLAYER_ID)).set(playerId);
            }
            if (root.has("sessionId")) {
                long sessionId = root.get("sessionId").asLong();
                ctx.channel().attr(AttributeKey.valueOf("sessionId")).set(sessionId);
            }
        }

        GamePacket packet = new GamePacket();
        packet.setVersion(ver);
        packet.setSerializeType(serializeType);
        packet.setMessageType(messageType);
        packet.setSequenceId(seq);
        packet.setBody(body);

        if (log.isDebugEnabled()) {
            log.debug("WebSocket文本帧解码: {} seq={} body={}B 来自 {}",
                    messageType != null ? messageType.name() : "UNKNOWN(0x" + Integer.toHexString(cmd) + ")",
                    seq, body.length, ctx.channel().id().asShortText());
        }

        ctx.fireChannelRead(packet);
    }

    private void handleBinaryFrame(ChannelHandlerContext ctx, BinaryWebSocketFrame frame) throws Exception {
        io.netty.buffer.ByteBuf buf = frame.content();
        if (buf.readableBytes() < GamePacket.HEADER_SIZE) {
            log.warn("二进制帧过短: {}字节", buf.readableBytes());
            return;
        }

        int totalLength = buf.readInt();
        int magic = buf.readShort();
        if (magic != GamePacket.MAGIC) {
            log.warn("二进制帧魔数无效: 0x{}", Integer.toHexString(magic));
            return;
        }

        byte version = buf.readByte();
        byte serializeTypeCode = buf.readByte();
        int commandCode = buf.readShort() & 0xFFFF;
        int sequenceId = buf.readInt();
        buf.readShort();

        SerializeType serializeType = SerializeType.fromCode(serializeTypeCode);
        MessageType messageType = MessageType.fromCode(commandCode);

        int bodyLength = totalLength - GamePacket.HEADER_SIZE;
        byte[] body = new byte[Math.max(0, bodyLength)];
        if (bodyLength > 0) {
            buf.readBytes(body);
        }

        GamePacket packet = new GamePacket();
        packet.setVersion(version);
        packet.setSerializeType(serializeType);
        packet.setMessageType(messageType);
        packet.setSequenceId(sequenceId);
        packet.setBody(body);

        if (log.isDebugEnabled()) {
            log.debug("WebSocket二进制帧解码: {} seq={} body={}B 来自 {}",
                    messageType != null ? messageType.name() : "UNKNOWN(0x" + Integer.toHexString(commandCode) + ")",
                    sequenceId, body.length, ctx.channel().id().asShortText());
        }

        ctx.fireChannelRead(packet);
    }
}
