package com.moba.netty.protocol.router;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moba.netty.protocol.MessagePacket;
import com.moba.netty.protocol.exception.MessageDecodeException;
import com.moba.netty.protocol.exception.MessageHandlerException;
import com.moba.netty.session.Session;
import com.moba.netty.session.SessionManager;
import com.moba.netty.user.User;
import io.netty.channel.ChannelHandlerContext;
import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;

@Slf4j
public class HandlerMethod {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final Object bean;
    private final Method method;
    private final Class<?>[] parameterTypes;
    private SessionManager sessionManager;

    public HandlerMethod(Object bean, Method method) {
        this.bean = bean;
        this.method = method;
        this.parameterTypes = method.getParameterTypes();
    }

    public void setSessionManager(SessionManager sessionManager) {
        this.sessionManager = sessionManager;
    }

    public Object invoke(ChannelHandlerContext ctx, MessagePacket packet) throws Exception {
        Object[] args = resolveArguments(ctx, packet);
        return method.invoke(bean, args);
    }

    private Object[] resolveArguments(ChannelHandlerContext ctx, MessagePacket packet) {
        Parameter[] parameters = method.getParameters();
        Object[] args = new Object[parameters.length];

        for (int i = 0; i < parameters.length; i++) {
            Class<?> type = parameterTypes[i];
            if (ChannelHandlerContext.class.isAssignableFrom(type)) {
                args[i] = ctx;
            } else if (MessagePacket.class.isAssignableFrom(type)) {
                args[i] = packet;
            } else if (User.class.isAssignableFrom(type)) {
                args[i] = resolveUser(ctx, packet);
            } else if (Session.class.isAssignableFrom(type)) {
                args[i] = resolveSession(ctx);
            } else if (type == long.class || type == Long.class) {
                args[i] = packet.getUserId();
            } else if (type == String.class) {
                args[i] = packet.getData();
            } else {
                args[i] = deserializeData(packet, type);
            }
        }
        return args;
    }

    private User resolveUser(ChannelHandlerContext ctx, MessagePacket packet) {
        if (sessionManager == null) {
            throw new MessageHandlerException("INTERNAL_ERROR", "SessionManager未初始化, 无法解析User参数");
        }
        long userId = packet.getUserId();
        if (userId <= 0) {
            throw new MessageHandlerException("AUTH_REQUIRED", "用户未认证, 请先登录");
        }
        User user = sessionManager.getUser(userId);
        if (user == null) {
            throw new MessageHandlerException("SESSION_EXPIRED", "用户会话不存在, userId=" + userId);
        }
        if (!user.isActive()) {
            throw new MessageHandlerException("SESSION_EXPIRED", "用户会话已失效, userId=" + userId);
        }
        return user;
    }

    private Session resolveSession(ChannelHandlerContext ctx) {
        if (sessionManager == null) {
            throw new MessageHandlerException("INTERNAL_ERROR", "SessionManager未初始化, 无法解析Session参数");
        }
        Session session = sessionManager.getSession(ctx.channel());
        if (session == null) {
            throw new MessageHandlerException("SESSION_EXPIRED", "会话不存在");
        }
        return session;
    }

    private Object deserializeData(MessagePacket packet, Class<?> targetType) {
        String data = packet.getData();
        if (data == null || data.isEmpty()) {
            throw new MessageDecodeException(packet.getExtensionId(), packet.getCmdId(),
                    "消息data为空, 无法反序列化为: " + targetType.getSimpleName());
        }
        try {
            return OBJECT_MAPPER.readValue(data, targetType);
        } catch (Exception e) {
            throw new MessageDecodeException(packet.getExtensionId(), packet.getCmdId(),
                    "反序列化失败: " + targetType.getSimpleName() + ", data=" + truncate(data, 200), e);
        }
    }

    private String truncate(String str, int maxLen) {
        if (str == null) return null;
        return str.length() <= maxLen ? str : str.substring(0, maxLen) + "...";
    }

    public Method getMethod() { return method; }
    public Object getBean() { return bean; }
    public Class<?>[] getParameterTypes() { return parameterTypes; }
}
