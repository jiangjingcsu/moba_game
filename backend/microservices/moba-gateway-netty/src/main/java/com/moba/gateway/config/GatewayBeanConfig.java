package com.moba.gateway.config;

import com.moba.gateway.discovery.GatewayNacosRegistry;
import com.moba.gateway.discovery.NacosServiceDiscovery;
import com.moba.gateway.network.GatewayNettyServer;
import com.moba.gateway.network.session.GatewaySessionManager;
import com.moba.gateway.route.BackendConnectionPool;
import com.moba.gateway.route.MessageRouter;
import com.moba.gateway.route.RankTierRouter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@ComponentScan(basePackages = "com.moba.gateway")
public class GatewayBeanConfig {

    @Bean
    public GatewayConfig gatewayConfig() {
        return GatewayConfig.load();
    }

    @Bean
    public GatewaySessionManager gatewaySessionManager() {
        return new GatewaySessionManager();
    }

    @Bean
    public NacosServiceDiscovery nacosServiceDiscovery(GatewayConfig config) {
        return new NacosServiceDiscovery(config);
    }

    @Bean
    public GatewayNacosRegistry gatewayNacosRegistry(GatewayConfig config, NacosServiceDiscovery serviceDiscovery) {
        return new GatewayNacosRegistry(config, serviceDiscovery);
    }

    @Bean
    public RankTierRouter rankTierRouter(GatewayConfig config, NacosServiceDiscovery serviceDiscovery) {
        return new RankTierRouter(config, serviceDiscovery);
    }

    @Bean
    public BackendConnectionPool backendConnectionPool(GatewayConfig config, NacosServiceDiscovery serviceDiscovery) {
        return new BackendConnectionPool(config, serviceDiscovery);
    }

    @Bean
    public MessageRouter messageRouter(BackendConnectionPool connectionPool, RankTierRouter rankTierRouter) {
        MessageRouter router = new MessageRouter(connectionPool, rankTierRouter);
        connectionPool.setMessageRouter(router);
        return router;
    }

    @Bean
    public GatewayNettyServer gatewayNettyServer(GatewayConfig config, GatewaySessionManager sessionManager, MessageRouter messageRouter) {
        return new GatewayNettyServer(config, sessionManager, messageRouter);
    }
}
