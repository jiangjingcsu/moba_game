package com.moba.battle.config;

import com.moba.battle.network.NettyServer;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
public class BattleBeanConfig {

    private NettyServer nettyServer;

    @Bean
    public NettyServer nettyServer(ServerConfig serverConfig) {
        nettyServer = new NettyServer(serverConfig);
        return nettyServer;
    }

    @Bean
    public SmartInitializingSingleton nettyServerStarter() {
        return () -> {
            NettyServer server = nettyServer;
            new Thread(() -> {
                try {
                    server.start();
                    log.info("Netty server started successfully");
                } catch (Exception e) {
                    log.error("Netty server start failed", e);
                }
            }, "netty-server-starter").start();
        };
    }

    @PreDestroy
    public void stopNettyServer() {
        if (nettyServer != null) {
            nettyServer.stop();
            log.info("Netty server stopped");
        }
    }
}
