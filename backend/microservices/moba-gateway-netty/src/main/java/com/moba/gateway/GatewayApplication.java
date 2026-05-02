package com.moba.gateway;

import com.moba.gateway.config.GatewayBeanConfig;
import com.moba.gateway.config.GatewayConfig;
import com.moba.gateway.discovery.GatewayNacosRegistry;
import com.moba.gateway.discovery.NacosServiceDiscovery;
import com.moba.gateway.network.GatewayNettyServer;
import com.moba.gateway.route.BackendConnectionPool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;

@Slf4j
public class GatewayApplication {

    private static void configureLogging() {
        ((Logger) org.slf4j.LoggerFactory.getLogger("com.alibaba.nacos")).setLevel(Level.WARN);
        ((Logger) org.slf4j.LoggerFactory.getLogger("com.alibaba.nacos.shaded")).setLevel(Level.WARN);
        ((Logger) org.slf4j.LoggerFactory.getLogger("io.netty")).setLevel(Level.INFO);
        ((Logger) org.slf4j.LoggerFactory.getLogger("org.springframework")).setLevel(Level.INFO);
    }

    public static void main(String[] args) {
        configureLogging();
        AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext();
        ctx.register(GatewayBeanConfig.class);
        ctx.refresh();
        ctx.registerShutdownHook();

        GatewayConfig config = ctx.getBean(GatewayConfig.class);
        GatewayNettyServer server = ctx.getBean(GatewayNettyServer.class);
        BackendConnectionPool connectionPool = ctx.getBean(BackendConnectionPool.class);
        NacosServiceDiscovery serviceDiscovery = ctx.getBean(NacosServiceDiscovery.class);
        GatewayNacosRegistry nacosRegistry = ctx.getBean(GatewayNacosRegistry.class);

        log.info("MOBA Netty Gateway 启动中...");
        log.info("监听地址: {}:{}", config.getHost(), config.getPort());
        log.info("匹配服务名: {}", config.getMatchServiceName());
        log.info("战斗服务名: {}", config.getBattleServiceName());
        log.info("Nacos服务发现: enabled={}, serverAddr={}", config.isNacosEnabled(), config.getNacosServerAddr());

        if (config.isNacosEnabled()) {
            serviceDiscovery.start();
            nacosRegistry.start();
            log.info("Nacos服务发现已启动，匹配服务名: {}", config.getMatchServiceName());
        }

        log.info("段位路由区间:");
        for (GatewayConfig.RankTierRange range : config.getRankTierRanges()) {
            log.info("  {} : {} - {}", range.getTierName(), range.getMinScore(), range.getMaxScore());
        }

        new Thread(() -> {
            try {
                connectionPool.init();
                server.start();
            } catch (Exception e) {
                log.error("网关启动失败", e);
            }
        }, "gateway-server-starter").start();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("网关关闭中...");
            nacosRegistry.stop();
            serviceDiscovery.stop();
            connectionPool.shutdown();
            server.stop();
            ctx.close();
            log.info("网关已关闭");
        }));

        server.blockUntilShutdown();
    }
}
