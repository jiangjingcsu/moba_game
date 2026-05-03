package com.moba.netty.protocol.router;

import com.moba.netty.protocol.MessagePacket;
import com.moba.netty.protocol.annotation.MessageHandler;
import com.moba.netty.protocol.annotation.MessageMapping;
import com.moba.netty.session.SessionManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class MessageRouteRegistry implements BeanPostProcessor {

    private final Map<Integer, HandlerMethod> handlerMap = new ConcurrentHashMap<>();
    private volatile SessionManager sessionManager;

    public void setSessionManager(SessionManager sessionManager) {
        this.sessionManager = sessionManager;
        for (HandlerMethod handler : handlerMap.values()) {
            handler.setSessionManager(sessionManager);
        }
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) {
        Class<?> beanClass = bean.getClass();
        MessageHandler classAnnotation = beanClass.getAnnotation(MessageHandler.class);
        if (classAnnotation == null) {
            return bean;
        }

        short extensionId = classAnnotation.extensionId();
        java.lang.reflect.Method[] methods = beanClass.getDeclaredMethods();

        for (java.lang.reflect.Method method : methods) {
            MessageMapping methodAnnotation = method.getAnnotation(MessageMapping.class);
            if (methodAnnotation == null) {
                continue;
            }

            byte cmdId = methodAnnotation.cmdId();
            int routeKey = MessagePacket.routeKey(extensionId, cmdId);

            HandlerMethod existing = handlerMap.get(routeKey);
            if (existing != null) {
                log.warn("路由冲突: extId=0x{}, cmdId=0x{}, 已有={}, 新={}",
                        String.format("%04X", extensionId), String.format("%02X", cmdId),
                        existing.getMethod().getDeclaringClass().getSimpleName() + "." + existing.getMethod().getName(),
                        method.getDeclaringClass().getSimpleName() + "." + method.getName());
                continue;
            }

            HandlerMethod handlerMethod = new HandlerMethod(bean, method);
            if (sessionManager != null) {
                handlerMethod.setSessionManager(sessionManager);
            }
            handlerMap.put(routeKey, handlerMethod);
            log.info("注册消息路由: extId=0x{}, cmdId=0x{} → {}.{}",
                    String.format("%04X", extensionId), String.format("%02X", cmdId),
                    beanClass.getSimpleName(), method.getName());
        }

        return bean;
    }

    public HandlerMethod getHandler(short extensionId, byte cmdId) {
        return handlerMap.get(MessagePacket.routeKey(extensionId, cmdId));
    }

    public int getHandlerCount() {
        return handlerMap.size();
    }

    public boolean hasHandler(short extensionId, byte cmdId) {
        return handlerMap.containsKey(MessagePacket.routeKey(extensionId, cmdId));
    }
}
