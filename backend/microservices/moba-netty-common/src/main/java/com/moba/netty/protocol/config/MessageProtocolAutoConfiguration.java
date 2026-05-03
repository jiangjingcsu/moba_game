package com.moba.netty.protocol.config;

import com.moba.netty.concurrent.KeyedThreadPoolExecutor;
import com.moba.netty.protocol.dispatcher.ConnectionLifecycleListener;
import com.moba.netty.protocol.dispatcher.MessageDispatcher;
import com.moba.netty.protocol.router.MessageRouteRegistry;
import com.moba.netty.session.SessionManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Configuration
public class MessageProtocolAutoConfiguration {

    @Bean
    public SessionManager sessionManager() {
        return new SessionManager();
    }

    @Bean
    public MessageRouteRegistry messageRouteRegistry(SessionManager sessionManager) {
        MessageRouteRegistry registry = new MessageRouteRegistry();
        registry.setSessionManager(sessionManager);
        return registry;
    }

    @Bean
    public KeyedThreadPoolExecutor businessThreadPool(
            @Value("${netty.business-thread-count:8}") int businessThreadCount) {
        return new KeyedThreadPoolExecutor(businessThreadCount, "biz-msg");
    }

    @Bean
    public MessageDispatcher messageDispatcher(MessageRouteRegistry routeRegistry,
                                               SessionManager sessionManager,
                                               KeyedThreadPoolExecutor businessThreadPool,
                                               ApplicationContext applicationContext) {
        Map<String, ConnectionLifecycleListener> listenerMap =
                applicationContext.getBeansOfType(ConnectionLifecycleListener.class);
        List<ConnectionLifecycleListener> listeners = new ArrayList<>(listenerMap.values());

        MessageDispatcher dispatcher = new MessageDispatcher(
                routeRegistry, sessionManager, listeners, businessThreadPool);

        log.info("MessageDispatcher初始化完成: 路由数={}, 生命周期监听器数={}, 业务线程数={}",
                routeRegistry.getHandlerCount(), listeners.size(), businessThreadPool.getPoolSize());

        return dispatcher;
    }
}
