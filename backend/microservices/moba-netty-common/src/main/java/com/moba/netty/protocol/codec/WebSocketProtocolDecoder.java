package com.moba.netty.protocol.codec;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moba.netty.protocol.MessagePacket;
import com.moba.netty.protocol.ProtocolConstants;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;
import lombok.extern.slf4j.Slf4j;

import java.nio.charset.StandardCharsets;

@Slf4j
public class WebSocketProtocolDecoder extends SimpleChannelInboundHandler<WebSocketFrame> {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, WebSocketFrame frame) throws Exception {
        if (frame instanceof TextWebSocketFrame textFrame) {
            handleTextFrame(ctx, textFrame);
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
            root = OBJECT_MAPPER.readTree(text);
        } catch (Exception e) {
            log.warn("无效JSON消息: {}", text.substring(0, Math.min(text.length(), 200)));
            return;
        }

        short extensionId = root.has("extId") ? (short) root.get("extId").asInt() : -1;
        byte cmdId = root.has("cmdId") ? (byte) root.get("cmdId").asInt() : -1;

        if (extensionId < 0 || cmdId < 0) {
            log.warn("消息缺少extId或cmdId: {}", text.substring(0, Math.min(text.length(), 200)));
            return;
        }

        int sequenceId = root.has("seq") ? root.get("seq").asInt() : 0;

        String data = "";
        if (root.has("data") && !root.get("data").isNull()) {
            data = OBJECT_MAPPER.writeValueAsString(root.get("data"));
        }

        MessagePacket packet = new MessagePacket(extensionId, cmdId, sequenceId, data);
        packet.bindUserIdFromChannel(ctx.channel());

        if (log.isDebugEnabled()) {
            log.debug("WS文本解码: extId={}, cmdId={}, seq={}, userId={}, from={}",
                    String.format("0x%04X", extensionId),
                    String.format("0x%02X", cmdId),
                    sequenceId,
                    packet.getUserId(), ctx.channel().id().asShortText());
        }

        ctx.fireChannelRead(packet);
    }

    private void handleBinaryFrame(ChannelHandlerContext ctx, BinaryWebSocketFrame frame) throws Exception {
        ByteBuf buf = frame.content();
        if (buf.readableBytes() < ProtocolConstants.HEADER_SIZE) {
            log.warn("WS二进制帧过短: {}字节, 至少需要{}字节",
                    buf.readableBytes(), ProtocolConstants.HEADER_SIZE);
            return;
        }

        int totalLength = buf.readInt();
        int sequenceId = buf.readInt();
        short extensionId = buf.readShort();
        byte cmdId = buf.readByte();

        int dataLength = totalLength - ProtocolConstants.HEADER_SIZE;
        if (dataLength < 0) {
            log.warn("WS二进制帧数据长度无效: totalLength={}, headerSize={}",
                    totalLength, ProtocolConstants.HEADER_SIZE);
            return;
        }

        String data = "";
        if (dataLength > 0 && buf.readableBytes() >= dataLength) {
            byte[] dataBytes = new byte[dataLength];
            buf.readBytes(dataBytes);
            data = new String(dataBytes, StandardCharsets.UTF_8);
        } else if (dataLength > 0 && buf.readableBytes() > 0) {
            log.warn("WS二进制帧数据截断: expected={}字节, actual={}字节, extId={}, cmdId={}",
                    dataLength, buf.readableBytes(),
                    String.format("0x%04X", extensionId), String.format("0x%02X", cmdId));
            byte[] dataBytes = new byte[buf.readableBytes()];
            buf.readBytes(dataBytes);
            data = new String(dataBytes, StandardCharsets.UTF_8);
        }

        MessagePacket packet = new MessagePacket(extensionId, cmdId, sequenceId, data);
        packet.bindUserIdFromChannel(ctx.channel());

        if (log.isDebugEnabled()) {
            log.debug("WS二进制解码: extId={}, cmdId={}, seq={}, userId={}, from={}",
                    String.format("0x%04X", extensionId),
                    String.format("0x%02X", cmdId),
                    sequenceId,
                    packet.getUserId(), ctx.channel().id().asShortText());
        }

        ctx.fireChannelRead(packet);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        log.error("WebSocketProtocolDecoder异常, 来自 {}", ctx.channel().remoteAddress(), cause);
        ctx.close();
    }
}
