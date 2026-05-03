package com.moba.netty.protocol.dispatcher;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moba.netty.concurrent.KeyedThreadPoolExecutor;
import com.moba.netty.protocol.MessagePacket;
import com.moba.netty.protocol.exception.MessageDecodeException;
import com.moba.netty.protocol.exception.MessageHandlerException;
import com.moba.netty.protocol.router.HandlerMethod;
import com.moba.netty.protocol.router.MessageRouteRegistry;
import com.moba.netty.session.Session;
import com.moba.netty.session.SessionManager;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.util.AttributeKey;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Slf4j
@ChannelHandler.Sharable
public class MessageDispatcher extends SimpleChannelInboundHandler<MessagePacket> {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final AttributeKey<Long> USER_ID_ATTR = AttributeKey.valueOf("userId");

    private final MessageRouteRegistry routeRegistry;
    private final SessionManager sessionManager;
    private final List<ConnectionLifecycleListener> lifecycleListeners;
    private final KeyedThreadPoolExecutor businessExecutor;

    public MessageDispatcher(MessageRouteRegistry routeRegistry,
                             SessionManager sessionManager,
                             List<ConnectionLifecycleListener> lifecycleListeners,
                             KeyedThreadPoolExecutor businessExecutor) {
        this.routeRegistry = routeRegistry;
        this.sessionManager = sessionManager;
        this.lifecycleListeners = lifecycleListeners != null ? lifecycleListeners : List.of();
        this.businessExecutor = businessExecutor;
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        Session session = sessionManager.createSession(ctx.channel());
        notifySessionActive(session);
        super.channelActive(ctx);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        Session session = sessionManager.getSession(ctx.channel());
        if (session != null) {
            notifySessionInactive(session);
        }
        sessionManager.removeSession(ctx.channel());
        super.channelInactive(ctx);
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, MessagePacket packet) {
        packet.bindUserIdFromChannel(ctx.channel());

        HandlerMethod handler = routeRegistry.getHandler(packet.getExtensionId(), packet.getCmdId());
        if (handler == null) {
            log.warn("未注册的消息路由: extId=0x{}, cmdId=0x{}, from={}",
                    String.format("%04X", packet.getExtensionId()),
                    String.format("%02X", packet.getCmdId()),
                    ctx.channel().remoteAddress());
            sendErrorResponse(ctx, packet, "ROUTE_NOT_FOUND", "未注册的消息路由");
            return;
        }

        long routingKey = resolveRoutingKey(ctx, packet);
        businessExecutor.execute(routingKey, () -> dispatchToHandler(ctx, packet, handler));
    }

    private void notifySessionActive(Session session) {
        for (ConnectionLifecycleListener listener : lifecycleListeners) {
            try {
                listener.onSessionActive(session);
            } catch (Exception e) {
                log.error("连接生命周期监听器异常: onSessionActive, listener={}",
                        listener.getClass().getSimpleName(), e);
            }
        }
    }

    private void notifySessionInactive(Session session) {
        for (ConnectionLifecycleListener listener : lifecycleListeners) {
            try {
                listener.onSessionInactive(session);
            } catch (Exception e) {
                log.error("连接生命周期监听器异常: onSessionInactive, listener={}",
                        listener.getClass().getSimpleName(), e);
            }
        }
    }

    private long resolveRoutingKey(ChannelHandlerContext ctx, MessagePacket packet) {
        if (packet.getUserId() > 0) {
            return packet.getUserId();
        }
        Long attrUserId = ctx.channel().attr(USER_ID_ATTR).get();
        if (attrUserId != null && attrUserId > 0) {
            return attrUserId;
        }
        return ctx.channel().id().hashCode();
    }

    private void dispatchToHandler(ChannelHandlerContext ctx, MessagePacket packet, HandlerMethod handler) {
        try {
            Object result = handler.invoke(ctx, packet);
            if (result != null && ctx.channel().isActive()) {
                sendResponse(ctx, packet, result);
            }
        } catch (MessageDecodeException e) {
            log.warn("消息解码失败: extId=0x{}, cmdId=0x{}, from={}, error={}",
                    String.format("%04X", e.getExtensionId()),
                    String.format("%02X", e.getCmdId()),
                    ctx.channel().remoteAddress(), e.getMessage());
            sendErrorResponse(ctx, packet, "DECODE_ERROR", e.getMessage());
        } catch (MessageHandlerException e) {
            log.warn("业务处理异常: extId=0x{}, cmdId=0x{}, errorCode={}, error={}",
                    String.format("%04X", packet.getExtensionId()),
                    String.format("%02X", packet.getCmdId()),
                    e.getErrorCode(), e.getMessage());
            sendErrorResponse(ctx, packet, e.getErrorCode(), e.getMessage());
        } catch (java.lang.reflect.InvocationTargetException e) {
            handleInvocationTarget(ctx, packet, e);
        } catch (Exception e) {
            log.error("消息分发异常: extId=0x{}, cmdId=0x{}, from={}",
                    String.format("%04X", packet.getExtensionId()),
                    String.format("%02X", packet.getCmdId()),
                    ctx.channel().remoteAddress(), e);
            sendErrorResponse(ctx, packet, "INTERNAL_ERROR", "服务器内部错误");
        }
    }

    private void handleInvocationTarget(ChannelHandlerContext ctx, MessagePacket packet,
                                        java.lang.reflect.InvocationTargetException e) {
        Throwable target = e.getTargetException();
        if (target instanceof MessageDecodeException) {
            MessageDecodeException mde = (MessageDecodeException) target;
            log.warn("消息解码失败: {}", mde.getMessage());
            sendErrorResponse(ctx, packet, "DECODE_ERROR", mde.getMessage());
        } else if (target instanceof MessageHandlerException) {
            MessageHandlerException mhe = (MessageHandlerException) target;
            log.warn("业务处理异常: {}", mhe.getMessage());
            sendErrorResponse(ctx, packet, mhe.getErrorCode(), mhe.getMessage());
        } else {
            log.error("消息处理异常: extId=0x{}, cmdId=0x{}, from={}",
                    String.format("%04X", packet.getExtensionId()),
                    String.format("%02X", packet.getCmdId()),
                    ctx.channel().remoteAddress(), target);
            sendErrorResponse(ctx, packet, "INTERNAL_ERROR", "服务器内部错误");
        }
    }

    private void sendResponse(ChannelHandlerContext ctx, MessagePacket request, Object result) {
        try {
            String json = OBJECT_MAPPER.writeValueAsString(result);
            MessagePacket response = MessagePacket.response(request, json);
            ctx.channel().writeAndFlush(response);
        } catch (Exception e) {
            log.error("序列化响应失败: extId=0x{}, cmdId=0x{}",
                    String.format("%04X", request.getExtensionId()),
                    String.format("%02X", request.getCmdId()), e);
        }
    }

    private void sendErrorResponse(ChannelHandlerContext ctx, MessagePacket request,
                                   String errorCode, String message) {
        if (!ctx.channel().isActive()) {
            return;
        }
        try {
            Map<String, String> errorData = Map.of("errorCode", errorCode, "message", message);
            String json = OBJECT_MAPPER.writeValueAsString(errorData);
            MessagePacket response = MessagePacket.response(request, json);
            ctx.channel().writeAndFlush(response);
        } catch (Exception e) {
            log.error("发送错误响应失败", e);
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        if (isRecoverable(cause)) {
            log.warn("可恢复异常, from={}, cause={}", ctx.channel().remoteAddress(), cause.getMessage());
        } else {
            log.error("不可恢复异常, 关闭连接: from={}", ctx.channel().remoteAddress(), cause);
            ctx.close();
        }
    }

    private boolean isRecoverable(Throwable cause) {
        if (cause instanceof java.io.IOException) return true;
        if (cause instanceof io.netty.handler.codec.DecoderException) return true;
        String msg = cause.getMessage();
        if (msg != null && (msg.contains("Connection reset") || msg.contains("Broken pipe")
                || msg.contains("远程主机强迫关闭") || msg.contains("连接被对方重置"))) {
            return true;
        }
        return false;
    }

    public void shutdown() {
        if (businessExecutor != null) {
            businessExecutor.shutdown();
            try {
                if (!businessExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
                    log.warn("业务线程池未能在10秒内优雅关闭");
                    businessExecutor.shutdown();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
