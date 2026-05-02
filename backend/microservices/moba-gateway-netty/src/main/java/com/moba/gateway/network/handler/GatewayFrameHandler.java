package com.moba.gateway.network.handler;

import com.moba.common.protocol.GamePacket;
import com.moba.common.protocol.MessageModule;
import com.moba.common.protocol.MessageType;
import com.moba.common.protocol.SerializeType;
import com.moba.gateway.network.session.GatewaySession;
import com.moba.gateway.network.session.GatewaySessionManager;
import com.moba.gateway.route.MessageRouter;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;
import io.netty.util.AttributeKey;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class GatewayFrameHandler extends SimpleChannelInboundHandler<WebSocketFrame> {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private final GatewaySessionManager sessionManager;
    private final MessageRouter messageRouter;

    public GatewayFrameHandler(GatewaySessionManager sessionManager, MessageRouter messageRouter) {
        this.sessionManager = sessionManager;
        this.messageRouter = messageRouter;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, WebSocketFrame frame) throws Exception {
        if (!(frame instanceof TextWebSocketFrame)) {
            log.warn("网关仅支持文本帧: {}", frame.getClass().getSimpleName());
            return;
        }

        String text = ((TextWebSocketFrame) frame).text();
        JsonNode root;
        try {
            root = OBJECT_MAPPER.readTree(text);
        } catch (Exception e) {
            log.warn("网关收到无效JSON");
            return;
        }

        int cmd = root.has("cmd") ? root.get("cmd").asInt() : -1;
        int seq = root.has("seq") ? root.get("seq").asInt() : 0;
        int ver = root.has("ver") ? root.get("ver").asInt() : 1;
        int st = root.has("st") ? root.get("st").asInt() : 1;

        MessageType messageType = MessageType.fromCode(cmd);
        if (messageType == null) {
            log.warn("网关收到未知消息类型: 0x{}", Integer.toHexString(cmd));
            return;
        }

        if (messageType == MessageType.HEARTBEAT_REQ) {
            String resp = "{\"cmd\":" + MessageType.HEARTBEAT_RESP.getCode() + ",\"seq\":" + seq + ",\"ver\":1,\"st\":1,\"data\":null}";
            ctx.writeAndFlush(new TextWebSocketFrame(resp));
            return;
        }

        GatewaySession session = getSession(ctx);
        if (session == null) {
            log.warn("网关收到消息但Session不存在, 关闭连接");
            ctx.close();
            return;
        }

        byte[] body = new byte[0];
        if (root.has("data") && !root.get("data").isNull()) {
            body = OBJECT_MAPPER.writeValueAsBytes(root.get("data"));
        }

        GamePacket packet = new GamePacket();
        packet.setVersion(ver);
        packet.setSerializeType(SerializeType.fromCode(st));
        packet.setMessageType(messageType);
        packet.setSequenceId(seq);
        packet.setBody(body);

        if (messageRouter != null) {
            messageRouter.route(session, packet);
        } else {
            log.warn("MessageRouter未初始化");
        }
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        super.channelInactive(ctx);
        GatewaySession session = getSession(ctx);
        if (session != null) {
            sessionManager.removeSession(session.getSessionId());
            log.info("客户端断开: sessionId={}, playerId={}", session.getSessionId(), session.getPlayerId());
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        log.error("网关处理器异常: channel={}", ctx.channel().id().asShortText(), cause);
        ctx.close();
    }

    private GatewaySession getSession(ChannelHandlerContext ctx) {
        Object sessionObj = ctx.channel().attr(AttributeKey.valueOf("gatewaySession")).get();
        if (sessionObj instanceof GatewaySession) {
            return (GatewaySession) sessionObj;
        }
        return null;
    }
}
